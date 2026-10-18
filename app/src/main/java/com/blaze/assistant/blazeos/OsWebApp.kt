package com.blaze.assistant.blazeos

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * v0.33.2 BlazeOS web apps and the BlazeOS Browser. Each runs in its own WebView with BlazeOS's own WebView profile
 * ("blazeos": its cookies and storage are apart from Blaze's browser, when the phone's WebView supports profiles), no
 * file or content access, and pages that leave the app open in the BlazeOS Browser instead.
 *
 * A packaged web app is served from https://<app>.apps.blazeos/ straight out of its folder on the BlazeOS disk (so each
 * app has its own origin and storage), with a small JS bridge `window.BlazeOS`: getItem / setItem / removeItem / keys
 * (the app's data folder), notify(title, body) (a BlazeOS banner, after the app's notification permission) and theme()
 * (dark, accent, fontScale as JSON). Open WebViews are kept while their app is open, so leaving an app and coming back
 * keeps its page; Pause or closing the app frees them.
 */
object OsWeb {
    const val PROFILE = "blazeos"
    private val live = HashMap<String, WebView>()

    fun host(manifestId: String) = manifestId.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("") + ".apps.blazeos"
    fun origin(manifestId: String) = "https://" + host(manifestId)

    fun release(appId: String) { live.remove(appId)?.let { try { (it.parent as? ViewGroup)?.removeView(it); it.destroy() } catch (_: Exception) { } } }
    fun pauseAll() { for (w in live.values) try { w.onPause() } catch (_: Exception) { } ; try { live.values.firstOrNull()?.pauseTimers() } catch (_: Exception) { } }
    fun resumeAll() { for (w in live.values) try { w.onResume() } catch (_: Exception) { } ; try { live.values.firstOrNull()?.resumeTimers() } catch (_: Exception) { } }
    fun releaseAll() { for (id in live.keys.toList()) release(id) }

    /** Factory reset: BlazeOS's own WebView profile (the BlazeOS Browser's cookies and storage) is emptied. Never Blaze's own. */
    fun wipeProfile() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        try {
            val p = androidx.webkit.ProfileStore.getInstance().getOrCreateProfile(PROFILE)
            p.cookieManager.removeAllCookies(null)
            p.webStorage.deleteAllData()
        } catch (_: Exception) { }
    }

    /** Factory reset / uninstall: the app origins' web storage goes too. */
    fun forgetOrigin(manifestId: String) { try { WebStorage.getInstance().deleteOrigin(origin(manifestId)) } catch (_: Exception) { } }

    @SuppressLint("SetJavaScriptEnabled")
    fun obtain(ctx: Context, appId: String, setup: (WebView) -> Unit): WebView {
        live[appId]?.let { (it.parent as? ViewGroup)?.removeView(it); return it }
        val w = WebView(ctx.applicationContext)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) { try { WebViewCompat.setProfile(w, PROFILE) } catch (_: Exception) { } }
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
            textZoom = (BlazeOs.prefs.value.fontScale * 100).toInt()
        }
        setup(w)
        live[appId] = w
        return w
    }

    fun mime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"; "js", "mjs" -> "text/javascript"; "css" -> "text/css"; "json" -> "application/json"
        "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; "svg" -> "image/svg+xml"; "webp" -> "image/webp"
        "wasm" -> "application/wasm"; "mp3" -> "audio/mpeg"; "ogg" -> "audio/ogg"; "wav" -> "audio/wav"; "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}

/** The bridge an app's page sees as window.BlazeOS. Calls arrive on a WebView thread. */
class OsBridge(private val ctx: Context, private val appId: String, private val label: String) {
    private val file = File(OsDisk.dataDir(ctx, appId), "storage.json")
    private val lock = Any()
    private fun read(): JSONObject = try { JSONObject(OsDisk.readText(file) ?: "{}") } catch (_: Exception) { JSONObject() }

    @JavascriptInterface fun getItem(k: String): String? = synchronized(lock) { read().optString(k, "").ifEmpty { null } }
    @JavascriptInterface fun setItem(k: String, v: String) {
        synchronized(lock) {
            val o = read().put(k.take(200), v)
            // Keeps one app's storage reasonable (2 MB) so it can't fill the BlazeOS disk.
            if (o.toString().length <= 2 * 1024 * 1024) OsDisk.writeText(file, o.toString())
        }
    }
    @JavascriptInterface fun removeItem(k: String) { synchronized(lock) { val o = read(); o.remove(k); OsDisk.writeText(file, o.toString()) } }
    @JavascriptInterface fun keys(): String = synchronized(lock) { org.json.JSONArray(read().keys().asSequence().toList()).toString() }
    @JavascriptInterface fun notify(title: String, body: String) {
        MainScope().launch { if (BlazeOs.request(appId, listOf(OsPerms.NOTIFICATIONS))) BlazeOs.notify(label, title, body) }
    }
    @JavascriptInterface fun theme(): String {
        val p = BlazeOs.prefs.value
        return JSONObject().put("dark", p.dark).put("accent", p.accent.ifEmpty { "#FF9A3C" }).put("fontScale", p.fontScale.toDouble()).toString()
    }
    @JavascriptInterface fun version(): String = BlazeOs.VERSION
}

@Composable
fun OsWebApp(inst: OsInstalled) {
    val m = inst.manifest
    val appId = inst.appId
    var web by remember { mutableStateOf<WebView?>(null) }
    BackHandler {
        val w = web
        if (w == null) BlazeOs.home()
        else w.evaluateJavascript("(window.blazeosBack && window.blazeosBack()) ? 'y' : 'n'") { r ->
            if (r?.contains("y") != true) { if (w.canGoBack()) w.goBack() else BlazeOs.home() }
        }
    }
    AndroidView(modifier = Modifier.fillMaxSize().background(osBg()).imePadding(), factory = { ctx ->
        OsWeb.obtain(ctx, appId) { w ->
            val dir = OsDisk.appDir(ctx, m.id)
            val base = OsWeb.origin(m.id)
            val hosted = m.entry.url.isNotEmpty()
            val hostedHost = if (hosted) Uri.parse(m.entry.url).host.orEmpty() else ""
            w.addJavascriptInterface(OsBridge(ctx, appId, m.name), "BlazeOS")
            w.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                    val u = req.url
                    if (hosted || u.host != OsWeb.host(m.id)) return null
                    val path = u.path.orEmpty().trimStart('/').ifEmpty { m.entry.path }
                    if (!OsManifest.safePath(path)) return WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), null)
                    val f = File(dir, path)
                    if (!f.canonicalPath.startsWith(dir.canonicalPath + File.separator) || !f.isFile)
                        return WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), null)
                    return WebResourceResponse(OsWeb.mime(f.name), "utf-8", 200, "OK",
                        mapOf("Cache-Control" to "no-cache", "Content-Security-Policy" to "frame-ancestors 'none'"), f.inputStream())
                }
                override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                    val h = req.url.host.orEmpty()
                    val inside = if (hosted) h == hostedHost else h == OsWeb.host(m.id)
                    if (inside) return false
                    // Leaving the app: open in the BlazeOS Browser.
                    if (req.url.scheme == "https" || req.url.scheme == "http") { OsBrowser.pending.value = req.url.toString(); BlazeOs.launch("blaze:browser") }
                    return true
                }
            }
            w.webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    val perms = request.resources.mapNotNull {
                        when (it) { PermissionRequest.RESOURCE_VIDEO_CAPTURE -> OsPerms.CAMERA; PermissionRequest.RESOURCE_AUDIO_CAPTURE -> OsPerms.MIC; else -> null }
                    }
                    if (perms.isEmpty()) { request.deny(); return }
                    MainScope().launch { if (BlazeOs.request(appId, perms)) request.grant(request.resources) else request.deny() }
                }
            }
            w.loadUrl(if (hosted) m.entry.url else "$base/${m.entry.path}")
        }.also { web = it }
    }, onRelease = { w -> (w.parent as? ViewGroup)?.removeView(w) })
}

/** What the BlazeOS Browser should open next (a link out of a web app). */
object OsBrowser { val pending = kotlinx.coroutines.flow.MutableStateFlow("") }

/** BlazeOS's own browser: nothing signed in, its own WebView profile, DuckDuckGo for searches. */
@Composable
fun OsBrowserApp() {
    val look = LocalOsLook.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var address by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    fun go(t: String) {
        val s = t.trim(); if (s.isEmpty()) return
        val url = if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(s)) s
            else if (!s.contains(' ') && s.contains('.')) "https://$s"
            else "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(s, "UTF-8")
        web?.loadUrl(url)
    }
    BackHandler { val w = web; if (w != null && w.canGoBack()) w.goBack() else BlazeOs.home() }
    Column(Modifier.fillMaxSize().background(osBg()).imePadding()) {
        AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { ctx ->
            OsWeb.obtain(ctx, "blaze:browser") { w ->
                w.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) { address = url.orEmpty(); title = view.title.orEmpty() }
                    override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean = req.url.scheme !in setOf("http", "https")
                }
                w.loadUrl(OsBrowser.pending.value.ifEmpty { "https://duckduckgo.com/" })
                OsBrowser.pending.value = ""
            }.also { web = it; address = it.url.orEmpty()
                val p = OsBrowser.pending.value; if (p.isNotEmpty()) { it.loadUrl(p); OsBrowser.pending.value = "" } }
        }, onRelease = { w -> (w.parent as? ViewGroup)?.removeView(w) })
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ArrowBackIosNew, "Back", tint = look.accent, modifier = Modifier.size(34.dp).clip(CircleShape).clickable { web?.let { if (it.canGoBack()) it.goBack() } }.padding(8.dp))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(look.ink.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 11.dp)) {
                if (address.isEmpty()) Text("Search or enter website", style = osText(look, 15f, color = look.sub))
                BasicTextField(address, { address = it }, singleLine = true, textStyle = osText(look, 15f), cursorBrush = SolidColor(look.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { go(address) }), modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.Refresh, "Reload", tint = look.accent, modifier = Modifier.size(34.dp).clip(CircleShape).clickable { web?.reload() }.padding(7.dp))
        }
        Spacer(Modifier.height(2.dp))
    }
    @Suppress("UNUSED_VARIABLE") val t = title
}
