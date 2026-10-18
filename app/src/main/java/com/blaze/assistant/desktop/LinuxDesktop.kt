package com.blaze.assistant.desktop

import android.content.Context
import com.blaze.assistant.agent.Settings
import com.blaze.assistant.desktop.DesktopPlan.Phase
import com.blaze.assistant.terminal.Distros
import com.blaze.assistant.terminal.Linux
import com.blaze.assistant.terminal.StopToken
import com.blaze.assistant.terminal.Terminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * v0.25.1: a real Linux desktop (XFCE under TigerVNC) inside Blaze's Alpine Linux, started from the Desktop view's
 * one Start button or by the agent's `desktop` tool. Installs on demand ([DesktopPlan.installScript]), runs the X
 * server + session as a background job, and hands the viewer a fresh random password each start. Stop kills every
 * desktop process. If Android kills the session (low memory), the view offers Resume.
 */
object LinuxDesktop {
    data class State(val phase: Phase = Phase.IDLE, val line: String = "", val pct: Int? = null, val error: String = "")

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var jobId = 0
    @Volatile var password: String = ""; private set

    private const val PREF_ON = "linux_desktop_on"
    private const val PREF_MIRROR = "linux_desktop_mirror"
    /** v0.33.5: which Linux the desktop (and the Terminal, BlazeOS Linux apps, the agent's linux=true) use. Missing = Alpine. */
    private const val PREF_DISTRO = "linux_desktop_distro"
    /** v0.33.5: a download the user paused (shown as "Download paused" after Blaze restarts). */
    private const val PREF_PAUSED = "linux_desktop_paused"

    /** v0.33.5: Stop for the running download / install (page button and the notification's Stop). */
    private val stopToken = StopToken()

    /** The chosen Linux (Alpine unless the user picked another). */
    fun distro(): DesktopDistro.Info = DesktopDistro.of(try { Settings.str(PREF_DISTRO, DesktopDistro.DEFAULT) } catch (_: Throwable) { DesktopDistro.DEFAULT })
    val distroId = MutableStateFlow("")

    /** Picks the Linux for the desktop. Not while downloading, starting or running (the page says so). */
    fun setDistro(id: String): Boolean {
        if (!DesktopFlow.canSwitch(_state.value.phase)) return false
        val d = DesktopDistro.of(id)
        Settings.setStr(PREF_DISTRO, d.id); distroId.value = d.id
        Settings.setBool(PREF_PAUSED, false)
        set(Phase.IDLE, "")
        refresh()
        return true
    }

    /**
     * The Linux the Terminal and the agent's `linux=true` commands run in: the chosen one once its base is installed,
     * else Alpine (which Blaze's own tools always set up).
     */
    fun shellDistro(): String = try { distro().id.takeIf { Distros.installed(it) } ?: DesktopDistro.DEFAULT } catch (_: Throwable) { DesktopDistro.DEFAULT }

    /** On opening the page: a paused download that never finished shows as paused. */
    fun refresh() {
        try {
            distroId.value = distro().id
            val s = _state.value
            val p = DesktopFlow.onLaunch(s.phase, Settings.bool(PREF_PAUSED, false), installed())
            if (p != s.phase) set(p, DesktopFlow.PAUSED_LINE, s.pct)
        } catch (_: Throwable) { }
    }

    private fun did() = distro().id

    /** Set by the live desktop card: open the full-screen viewer as soon as the Desktop view shows. */
    val viewerRequest = MutableStateFlow(false)
    fun requestViewer() { viewerRequest.value = true }

    /**
     * v0.25.5: starts (and installs if needed) in Blaze's own scope, not the screen's, so leaving the Desktop view or
     * closing Settings doesn't cancel a download half-way. The caller can await the plain result.
     */
    fun startAsync(ctx: Context): Deferred<String> { val app = ctx.applicationContext; return scope.async { start(app) } }

    private fun set(p: Phase, line: String, pct: Int? = null, err: String = "") {
        // v0.33.5: once Stop was tapped, late progress lines from the download being torn down don't flip the page back.
        if (stopToken.stopped && (p == Phase.INSTALLING || p == Phase.ERROR)) return
        _state.value = State(p, line, pct, err)
    }

    fun cpuOk(): Boolean = DesktopPlan.supported(android.os.Build.SUPPORTED_ABIS.firstOrNull()) && Linux.supported()

    fun installed(): Boolean = installed(distro())

    /** [d]'s base Linux and its desktop packages are both installed (for Alpine: the same check as before v0.33.5). */
    fun installed(d: DesktopDistro.Info): Boolean = try {
        Distros.installed(d.id) && File(Linux.rootfs(d.id), DesktopPlan.INSTALLED_MARK.removePrefix("/")).exists()
    } catch (_: Throwable) { false }

    /** True when it was running and Android ended it (shown as a Resume card). */
    fun wasKilled(): Boolean = Settings.bool(PREF_ON, false) && _state.value.phase != Phase.RUNNING && _state.value.phase != Phase.STARTING

    fun freeMb(ctx: Context): Long = try {
        val mi = android.app.ActivityManager.MemoryInfo()
        ctx.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(mi); mi.availMem / 1_000_000
    } catch (_: Throwable) { 1_000 }

    /** Installs if needed, then starts. Safe to call twice. Returns a plain sentence. */
    suspend fun start(ctx: Context): String = lock.withLock {
        if (!cpuOk()) { set(Phase.UNSUPPORTED, DesktopPlan.UNSUPPORTED); return DesktopPlan.UNSUPPORTED }
        if (alive()) { set(Phase.RUNNING, "Running"); return "The desktop is running." }
        if (freeMb(ctx) < DesktopPlan.MIN_FREE_MB) {
            val m = "Your phone is low on free memory right now. Close a few apps, then tap Start again."
            set(Phase.ERROR, m, err = m); return m
        }
        stopToken.reset()
        Settings.setBool(PREF_PAUSED, false)
        DesktopKeeper.clearPaused(ctx.applicationContext)
        val d = distro()
        try {
            if (!Distros.installed(d.id) || !installed(d)) {
                // Keep the process (and Wi-Fi) awake while downloading, even with the screen off.
                set(Phase.INSTALLING, "Getting ready to download\u2026", 0)
                DesktopKeeper.start(ctx.applicationContext)
                try {
                    if (!Distros.installed(d.id)) {
                        set(Phase.INSTALLING, if (d.id == DesktopDistro.DEFAULT) "Setting up Linux first\u2026" else "Setting up ${d.name} first\u2026", 0)
                        val flow = if (d.id == DesktopDistro.DEFAULT) Linux.status else Distros.status
                        val mirror = scope.launch { flow.collect { if (it.isNotBlank()) set(Phase.INSTALLING, it, _state.value.pct) } }
                        val r = try { Distros.install(d.id, stopToken) } finally { mirror.cancel() }
                        if (stopToken.stopped) return paused(ctx)
                        if (!Distros.installed(d.id)) { set(Phase.ERROR, r, err = r); return r }
                    }
                    if (!installed(d)) {
                        val r = install(d)
                        if (r == DesktopFlow.STOPPED_MARK || stopToken.stopped) return paused(ctx)
                        if (r != null) { set(Phase.ERROR, r, err = r); return r }
                    }
                } finally { DesktopKeeper.stop(ctx.applicationContext) }
            }
            if (stopToken.stopped) return paused(ctx)
            set(Phase.STARTING, "Starting the desktop\u2026")
            password = DesktopPlan.password()
            Terminal.run(DesktopPlan.passwdScript(password), timeoutSec = 30, linux = true, distro = d.id)
            Terminal.run(DesktopPlan.STOP_SCRIPT, timeoutSec = 20, linux = true, distro = d.id)
            val out = Terminal.startJob(DesktopPlan.startScript(), linux = true, maxMinutes = 0, distro = d.id)
            jobId = Regex("Started job (\\d+)").find(out)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            repeat(40) { if (alive()) { Settings.setBool(PREF_ON, true); set(Phase.RUNNING, "Running"); return "The desktop is running. Open the Desktop view to see it." }; delay(500) }
            val log = Terminal.run("tail -n 5 /tmp/xvnc.log 2>/dev/null", timeoutSec = 10, linux = true, distro = d.id)
            val m = "The desktop didn't start. " + log.lines().drop(1).joinToString(" ").take(200)
            set(Phase.ERROR, m, err = m); m
        } catch (e: Exception) {
            if (stopToken.stopped) return paused(ctx)
            val m = "The desktop didn't start: ${e.message ?: e.javaClass.simpleName}"
            set(Phase.ERROR, m, err = m); m
        }
    }

    /**
     * apk add with progress, step by step with retries and mirror fallback ([DesktopInstall]); null on success, else a
     * plain error. The mirror that last worked is remembered for next time.
     */
    private suspend fun install(d: DesktopDistro.Info): String? = withContext(Dispatchers.IO) {
        set(Phase.INSTALLING, "Downloading the desktop\u2026 " + d.sizeLine(), 0)
        val alpine = d.id == DesktopDistro.DEFAULT
        val start = Settings.str(PREF_MIRROR, "0").toIntOrNull() ?: 0
        DesktopInstall.run(
            exec = { script, onLine -> runStep(script, onLine, d.id) },
            status = { pct, line -> set(Phase.INSTALLING, line, pct ?: _state.value.pct) },
            sleep = stopToken.sleep,
            startMirror = if (alpine) start else 0,
            onMirror = { if (alpine) Settings.setStr(PREF_MIRROR, it.toString()) },
            prefetch = if (alpine) ::prefetch else null,
            distro = d,
            stopped = stopToken.cancelled,
        )
    }

    /** v0.33.5: the download stopped by the user: keep everything, show "Download paused". */
    private fun paused(ctx: Context): String {
        val pct = _state.value.pct
        _state.value = State(Phase.PAUSED, DesktopFlow.PAUSED_LINE, pct)
        try { Settings.setBool(PREF_PAUSED, true) } catch (_: Throwable) { }
        DesktopKeeper.showPaused(ctx.applicationContext, keptBytes())
        return "The desktop download is paused. What's downloaded so far is kept; Start carries on from there."
    }

    /**
     * Stop from the page or the notification. During a download or install it pauses (kills the install process,
     * closes the download, keeps every file); otherwise it stops the running desktop.
     */
    fun stopInstall(ctx: Context): String {
        val ph = _state.value.phase
        // Only the desktop's own install (it holds the token); a Terminal-started Linux install isn't ours to stop.
        val downloading = ph == Phase.INSTALLING
        return if (downloading) {
            stopToken.stop()
            _state.value = State(Phase.PAUSED, "Stopping the download\u2026", _state.value.pct)
            "Pausing the download."
        } else { scope.launch { stop() }; "Stopping the desktop." }
    }

    /** Bytes kept on the phone for the chosen Linux's unfinished download (partial archives, package caches, staged packages). */
    fun keptBytes(): Long = try {
        val d = distro()
        DesktopDistro.removalPlan(d, Distros.installed(d.id), alpineArchive()).sumOf { p -> sizeOf(resolve(p)) }
    } catch (_: Throwable) { 0 }

    private fun alpineArchive() = "alpine-${com.blaze.assistant.terminal.AlpineNet.VER}-${com.blaze.assistant.terminal.AlpineNet.arch(android.os.Build.SUPPORTED_ABIS.firstOrNull())}.tar.gz"

    private fun resolve(p: String): File = File(Linux.base(), p.removeSuffix("/*"))

    /** Size without following symlinks (a rootfs links to /proc, /sdcard…). */
    private fun sizeOf(f: File): Long {
        val path = f.toPath()
        if (!java.nio.file.Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return 0
        if (java.nio.file.Files.isSymbolicLink(path)) return 0
        if (!f.isDirectory) return f.length()
        return (f.listFiles() ?: emptyArray()).sumOf { sizeOf(it) }
    }

    /** Deletes without following symlinks; [keepDir] keeps [f] itself (only its contents go). */
    private fun delete(f: File, keepDir: Boolean = false) {
        val path = f.toPath()
        if (!java.nio.file.Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        if (!java.nio.file.Files.isSymbolicLink(path) && f.isDirectory) (f.listFiles() ?: emptyArray()).forEach { delete(it, keepDir = keepDir && it.isDirectory && it.name == "partial") }
        if (!keepDir) runCatching { java.nio.file.Files.deleteIfExists(path) }
    }

    /**
     * v0.33.5 "Remove downloaded files": deletes the chosen Linux's partial downloads and package caches (and the
     * Linux itself when it never finished installing; an installed Alpine always stays). Not while anything runs.
     */
    suspend fun removeDownloads(): String = withContext(Dispatchers.IO) {
        if (!DesktopFlow.canSwitch(_state.value.phase)) return@withContext "Stop the download first."
        lock.withLock {
            val d = distro()
            val before = keptBytes()
            for (p in DesktopDistro.removalPlan(d, Distros.installed(d.id), alpineArchive())) delete(resolve(p), keepDir = p.endsWith("/*"))
            Settings.setBool(PREF_PAUSED, false)
            _state.value = State(DesktopFlow.afterRemove(), "")
            "Removed %.1f MB of downloaded files.".format(java.util.Locale.US, before / 1_000_000.0)
        }
    }

    /**
     * v0.33.3: downloads a batch's missing packages with [com.blaze.assistant.terminal.Fetch] (resumable, many retries,
     * mirrors) into the rootfs stage folder. Null when apk couldn't say what is missing (the step then uses plain apk add).
     */
    private fun prefetch(mirror: Int, pkgs: List<String>, update: Boolean): List<DesktopPrefetch.Pkg>? {
        val mirrors = com.blaze.assistant.terminal.AlpineNet.MIRRORS
        val full = StringBuilder()
        val sim = runStep(DesktopPrefetch.simulateScript(mirrors[mirror], pkgs, update), { full.append(it).append('\n') })
        if (sim.code != 0 || "BLAZE_SIM_OK" !in sim.tail) return null
        val list = DesktopPrefetch.parseSimulate(full.toString())
        if (list.isEmpty()) return emptyList()
        val arch = com.blaze.assistant.terminal.AlpineNet.arch(android.os.Build.SUPPORTED_ABIS.firstOrNull())
        val stage = File(Linux.rootfs(), DesktopPrefetch.STAGE).also { it.mkdirs() }
        var done = 0L
        list.forEachIndexed { i, p ->
            val dest = File(stage, p.file)
            if (dest.isFile && dest.length() > 0) { done += dest.length(); return@forEachIndexed }
            val resuming = File(dest.path + ".part").let { it.exists() && it.length() > 0 }
            val base = done
            com.blaze.assistant.terminal.Fetch.download(DesktopPrefetch.urls(p, arch, mirrors, mirror), dest, "",
                progress = { got, _, retry ->
                    set(Phase.INSTALLING, retry ?: DesktopPrefetch.batchLine(p, i, list.size, base + got, resuming), _state.value.pct)
                }, sleep = stopToken.sleep, open = stopToken::open, cancelled = stopToken.cancelled)
            done += dest.length()
        }
        return list
    }

    /** Runs one install step inside Alpine, streaming its output; never throws. */
    private fun runStep(script: String, onLine: (String) -> Unit, distro: String = DesktopDistro.DEFAULT): DesktopInstall.Result {
        if (stopToken.stopped) return DesktopInstall.Result(-1, DesktopFlow.STOPPED_MARK)
        val p = try {
            ProcessBuilder(Linux.command(script, distro = distro)).redirectErrorStream(true)
                .also { it.environment().putAll(Linux.env()) }.start()
        } catch (e: Exception) { return DesktopInstall.Result(-1, "ERROR: couldn't start the installer: ${e.message}") }
        stopToken.track(p)
        try { p.outputStream.close() } catch (_: Exception) { }
        val tail = ArrayDeque<String>()
        try {
            p.inputStream.bufferedReader().forEachLine { line ->
                tail.addLast(line); if (tail.size > 12) tail.removeFirst()
                onLine(line)
            }
        } catch (e: Exception) { tail.addLast("ERROR: ${e.message}") }
        val code = try { p.waitFor() } catch (_: Exception) { -1 }
        stopToken.track(null)
        return DesktopInstall.Result(code, tail.joinToString("\n"))
    }

    suspend fun alive(): Boolean = try {
        val d = did()
        Distros.installed(d) && Terminal.run(DesktopPlan.ALIVE_SCRIPT, timeoutSec = 10, linux = true, distro = d).lines().any { it.trim() == "up" }
    } catch (_: Exception) { false }

    /** Kills every desktop process and frees its memory. */
    suspend fun stop(): String {
        set(Phase.STOPPING, "Stopping\u2026")
        Settings.setBool(PREF_ON, false)
        try { Terminal.run(DesktopPlan.STOP_SCRIPT, timeoutSec = 20, linux = true, distro = did()) } catch (_: Exception) { }
        if (jobId > 0) try { Terminal.stopJob(jobId) } catch (_: Exception) { }
        jobId = 0; password = ""; clickOk = false
        System.gc()
        set(Phase.STOPPED, "Stopped. Memory freed.")
        return "The desktop is stopped."
    }

    /** The viewer lost its connection: if the X server is gone, Android (or a crash) ended it. */
    fun lost() { scope.launch { if (!alive()) set(Phase.KILLED, DesktopPlan.KILLED_NOTE) } }

    // ---------- agent tool ----------
    fun tool(): JSONObject = JSONObject().put("type", "function").put("function", JSONObject().put("name", "desktop")
        .put("description", "A real Linux desktop (XFCE) inside Blaze's ${distro().name} Linux (the user's choice on the Desktop page; " +
            "install software in it with ${distro().pm.tool}), shown in the Desktop view. " +
            "start (installs on first use, ~${distro().downloadMb} MB; while it downloads, stop pauses it and start resumes), stop, status, screenshot (attached as an image; coordinates are in that picture's pixels), " +
            "click x y [button 1 left / 3 right] [double], type text, key keys (xdotool names like ctrl+s Return), run app (terminal, files, browser, editor or a command), open (show the Desktop view). " +
            "For click and type, set label to what you're clicking or typing into in 1-3 plain words (\"Save\", \"Terminal\"): the user watches a live view with that label.")
        .put("parameters", JSONObject().put("type", "object").put("properties", JSONObject()
            .put("action", JSONObject().put("type", "string").put("enum", JSONArray(listOf("start", "stop", "status", "screenshot", "click", "type", "key", "run", "open"))))
            .put("x", JSONObject().put("type", "integer")).put("y", JSONObject().put("type", "integer"))
            .put("button", JSONObject().put("type", "integer")).put("double", JSONObject().put("type", "boolean"))
            .put("text", JSONObject().put("type", "string")).put("keys", JSONObject().put("type", "string"))
            .put("app", JSONObject().put("type", "string"))
            .put("label", JSONObject().put("type", "string").put("description", "What you're clicking or typing into, 1-3 plain words, shown to the user (e.g. Save, Terminal, File menu).")))
            .put("required", JSONArray(listOf("action")))))

    /** The screenshot as a data URL (≤ 1024 px), or null. */
    suspend fun screenshot(ctx: Context): String? = withContext(Dispatchers.IO) {
        val r = Terminal.run(DesktopPlan.screenshotCmd(), timeoutSec = 30, linux = true, distro = did())
        if (!r.contains("ok")) return@withContext null
        val f = File(File(ctx.filesDir, "home"), ".desktop_shot.png")
        if (!f.exists()) return@withContext null
        val b = f.readBytes(); f.delete()
        DesktopLive.shot(b)
        "data:image/png;base64," + android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP)
    }

    /**
     * Runs one tool call. [confirm] asks the user (Blaze's usual approval card); start (a big download the first time),
     * type, key and run always ask; click asks unless the user already approved desktop control this session.
     */
    suspend fun run(ctx: Context, args: JSONObject, shots: MutableList<String>, confirm: suspend (String, String) -> Boolean, open: () -> Unit): String {
        val a = args.optString("action")
        // v0.25.5: the user tapped X on the live desktop card.
        if (DesktopLive.stopped && a !in setOf("status", "stop")) return "The user tapped X on the live desktop card to stop your desktop work. " +
            "Don't use the desktop tool again in this task unless the user asks; tell them you stopped and what was done so far."
        val words = DesktopNarration.status(args, installed())
        val r = act(ctx, a, args, shots, confirm, open, words)
        return r
    }

    /** Wraps one desktop action in the live card's begin / end (only after the user's yes, so the card shows real work). */
    private suspend fun <T> live(words: String?, x: Int = -1, y: Int = -1, block: suspend () -> T): T {
        if (words == null) return block()
        DesktopLive.begin(words, x, y)
        try { return block() } finally { DesktopLive.end() }
    }

    private suspend fun act(ctx: Context, a: String, args: JSONObject, shots: MutableList<String>, confirm: suspend (String, String) -> Boolean, open: () -> Unit, words: String?): String {
        if (a !in setOf("status", "stop", "open") && !cpuOk()) return DesktopPlan.UNSUPPORTED
        val needsUp = a in setOf("screenshot", "click", "type", "key", "run")
        if (needsUp && !alive()) return "The desktop isn't running. Call desktop action=start first."
        return when (a) {
            "start" -> {
                if (!installed()) {
                    if (!confirm("Install the Linux desktop (${distro().name})?", distro().sizeLine())) "The user said no; nothing was installed."
                    // The first install takes minutes: it runs in the background and the Desktop view shows progress.
                    else { scope.launch { start(ctx) }; open(); "Installing the ${distro().name} desktop in the background (${distro().sizeLine()}). Call desktop action=status in a few minutes." }
                } else live(words) { start(ctx) }
            }
            "stop" -> if (_state.value.phase == Phase.INSTALLING) stopInstall(ctx) else live(words) { stop() }
            "status" -> when {
                !cpuOk() -> DesktopPlan.UNSUPPORTED
                _state.value.phase == Phase.INSTALLING -> "Installing ${distro().name}: ${_state.value.line}"
                _state.value.phase == Phase.PAUSED -> "${distro().name} download paused by the user; start resumes it."
                alive() -> "Running ${distro().name} at ${DesktopPlan.WIDTH}x${DesktopPlan.HEIGHT}, ${DesktopPlan.DEPTH}-bit colour, 127.0.0.1 only."
                installed() -> "${distro().name} desktop installed, not running."
                else -> "Not installed (start installs ${distro().name}: ${distro().sizeLine()})"
            }
            "open" -> { open(); "Opened the Desktop view." }
            "screenshot" -> live(words) { screenshot(ctx) }?.let { shots += it; "Desktop screenshot attached in the next message (coordinates are in its pixels; the desktop is ${DesktopPlan.WIDTH}x${DesktopPlan.HEIGHT})." }
                ?: "Couldn't take a desktop screenshot."
            "click" -> {
                val x = args.optInt("x", -1); val y = args.optInt("y", -1)
                if (x < 0 || y < 0) "click needs x and y."
                else if (!clickOk && !confirm("Let Blaze click in the Linux desktop?", "At $x, $y. You'll be asked once per session.").also { clickOk = it }) "The user said no."
                else live(words, x, y) { Terminal.run(DesktopPlan.clickCmd(x, y, args.optInt("button", 1), args.optBoolean("double")), timeoutSec = 15, linux = true, distro = did()) }.let { "Clicked at $x, $y." }
            }
            "type" -> {
                val t = args.optString("text")
                if (t.isBlank()) "type needs text."
                else if (!confirm("Type in the Linux desktop?", "\u201c${t.take(120)}\u201d")) "The user said no."
                else live(words) { Terminal.run(DesktopPlan.typeCmd(t), timeoutSec = 60, linux = true, distro = did()) }.let { "Typed ${t.length} characters." }
            }
            "key" -> {
                val k = args.optString("keys")
                if (k.isBlank()) "key needs keys."
                else if (!confirm("Press keys in the Linux desktop?", k)) "The user said no."
                else live(words) { Terminal.run(DesktopPlan.keyCmd(k), timeoutSec = 15, linux = true, distro = did()) }.let { "Pressed $k." }
            }
            "run" -> {
                val app = args.optString("app")
                if (app.isBlank()) "run needs app."
                else if (!confirm("Open in the Linux desktop?", DesktopPlan.appCommand(app))) "The user said no."
                else live(words) { Terminal.run(DesktopPlan.runCmd(app), timeoutSec = 15, linux = true, distro = did()) }.let { "Started ${DesktopPlan.appCommand(app)}. Take a screenshot to see it." }
            }
            else -> "Unknown action. Use start, stop, status, screenshot, click, type, key, run or open."
        }
    }

    @Volatile private var clickOk = false
}
