package com.blaze.assistant.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.33.2 BlazeOS Home: grid layout, page packing, Home order, the dock, icon maths and prefs migration. */
class BlazeOsLogicTest {
    private fun real(n: Int) = (1..n).map { OsApp(OsApps.realId("com.example.app$it", "com.example.app$it.Main"), "App $it", "com.example.app$it", "com.example.app$it.Main") }
    private fun ids(n: Int, p: String = "a") = (1..n).map { "$p$it" }

    // ---------------- grid ----------------

    @Test fun gridParsesTheThreeSizesAndFallsBack() {
        assertEquals(OsGrid(4, 5), OsGrid.parse("4x5"))
        assertEquals(OsGrid(5, 6), OsGrid.parse(" 5X6 "))
        assertEquals(OsGrid.DEFAULT, OsGrid.parse("7x9"))
        assertEquals(OsGrid.DEFAULT, OsGrid.parse(null))
        assertEquals(24, OsGrid(4, 6).slots)
        assertEquals("4x6", OsGrid.DEFAULT.id)
    }

    @Test fun widgetRowsPairHalvesAndKeepFullsAlone() {
        assertEquals(listOf(listOf("calendar", "ask"), listOf("music"), listOf("devices"), listOf("board")), OsWidgets.rows(OsWidgets.DEFAULT))
        // A half followed by a full sits alone.
        assertEquals(listOf(listOf("ask"), listOf("music"), listOf("calendar")), OsWidgets.rows(listOf("ask", "music", "calendar")))
        // Unknown kinds and repeats are dropped.
        assertEquals(listOf(listOf("music")), OsWidgets.rows(listOf("music", "nope", "music")))
    }

    // ---------------- page packing ----------------

    @Test fun mockupLayoutTwoWidgetRowsThenTwoIconRows() {
        val pages = OsPages.pack(OsWidgets.rows(OsWidgets.DEFAULT), ids(41), OsGrid(4, 6))
        // Page 1: calendar + ask, music, then 2 rows of 4 icons (like the mockup).
        assertEquals(listOf(listOf("calendar", "ask"), listOf("music")), pages[0].widgetRows)
        assertEquals(ids(8), pages[0].apps)
        // Page 2: devices, board, 8 more icons.
        assertEquals(listOf(listOf("devices"), listOf("board")), pages[1].widgetRows)
        assertEquals((9..16).map { "a$it" }, pages[1].apps)
        // Page 3: a full page of icons, then the rest.
        assertEquals(24, pages[2].apps.size)
        assertEquals(listOf("a41"), pages[3].apps)
        assertEquals(4, pages.size)
        assertEquals(41, pages.sumOf { it.apps.size })
    }

    @Test fun smallGridKeepsOneWidgetRowAPage() {
        val pages = OsPages.pack(OsWidgets.rows(OsWidgets.DEFAULT), ids(5), OsGrid(4, 5))
        assertTrue(pages.take(4).all { it.widgetRows.size == 1 })
        assertEquals(5, pages[0].apps.size)
        assertEquals(5, pages.sumOf { it.apps.size })
        assertEquals(4, pages.count { it.widgetRows.isNotEmpty() })
    }

    @Test fun noWidgetsAndNoAppsStillGivesOnePage() {
        val p = OsPages.pack(emptyList(), emptyList(), OsGrid(4, 6))
        assertEquals(1, p.size); assertTrue(p[0].apps.isEmpty())
        val q = OsPages.pack(emptyList(), ids(48), OsGrid(4, 6))
        assertEquals(2, q.size); assertEquals(24, q[1].apps.size)
        val r = OsPages.pack(emptyList(), ids(49), OsGrid(4, 6))
        assertEquals(3, r.size); assertEquals(listOf("a49"), r[2].apps)
    }

    @Test fun fiveColumnGridFillsWider() {
        val p = OsPages.pack(listOf(listOf("music")), ids(40), OsGrid(5, 6))
        assertEquals(20, p[0].apps.size)
        assertEquals(20, p[1].apps.size)
    }

    @Test fun pageLookupsAndDragSlots() {
        val pages = OsPages.pack(OsWidgets.rows(OsWidgets.DEFAULT), ids(30), OsGrid(4, 6))
        assertEquals(0, OsPages.pageOf(pages, "a3"))
        assertEquals(1, OsPages.pageOf(pages, "a9"))
        assertEquals(-1, OsPages.pageOf(pages, "zz"))
        assertEquals(8 + 2, OsPages.flatIndex(pages, 1, 2))
        // One cell right, one row down on a 4-wide grid.
        assertEquals(6, OsPages.dropSlot(1, 1, 1, 4, 8))
        assertEquals(7, OsPages.dropSlot(6, 3, 3, 4, 8))
        assertEquals(0, OsPages.dropSlot(2, -5, 0, 4, 8))
    }

    // ---------------- Home order ----------------

    @Test fun homeOrderDropsUninstalledRemovedAndDockedThenAppendsNewApps() {
        val all = OsApps.merge(real(3))
        val a1 = real(3)[0].id; val a2 = real(3)[1].id; val a3 = real(3)[2].id
        val saved = listOf(a2, "app:gone/x", a1, a2, "blaze:music")
        val o = OsHome.order(saved, all, removed = setOf("blaze:video"), dock = listOf("blaze:browser"))
        assertEquals(listOf(a2, a1, "blaze:music"), o.take(3))
        assertFalse("app:gone/x" in o)
        assertFalse("blaze:video" in o)
        assertFalse("blaze:browser" in o)
        assertTrue(a3 in o)
        assertEquals(o.distinct(), o)
    }

    @Test fun firstRunIsBuiltinsThenRealAppsAToZ() {
        val all = OsApps.merge(listOf(OsApp("app:z/Z", "Zed", "z"), OsApp("app:b/B", "bravo", "b")))
        val o = OsHome.order(emptyList(), all, emptySet(), emptyList())
        assertEquals(OsApps.builtinApps().map { it.id } + listOf("app:b/B", "app:z/Z"), o)
    }

    @Test fun moveRemoveRestore() {
        val o = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), OsHome.move(o, "a", 2))
        assertEquals(listOf("a", "b", "c", "d"), OsHome.move(o, "x", 0))
        assertEquals(listOf("d", "a", "b", "c"), OsHome.move(o, "d", -3))
        val (o2, r2) = OsHome.remove(o, emptySet(), "b")
        assertEquals(listOf("a", "c", "d"), o2); assertEquals(setOf("b"), r2)
        val (o3, r3) = OsHome.restore(o2, r2, "b")
        assertEquals(listOf("a", "c", "d", "b"), o3); assertTrue(r3.isEmpty())
    }

    // ---------------- dock ----------------

    @Test fun dockRules() {
        val all = OsApps.merge(listOf(OsApp("app:com.dialer/Main", "Phone", "com.dialer")))
        assertEquals(listOf("app:com.dialer/Main", "blaze:browser", "blaze:music", "blaze:camera"), OsDock.defaults(all, "com.dialer"))
        assertEquals(listOf("blaze:browser", "blaze:music", "blaze:camera", "blaze:chat"), OsDock.defaults(all, ""))
        val installed = all.map { it.id }.toSet()
        assertEquals(listOf("blaze:music", "blaze:chat"), OsDock.normalize(listOf("blaze:music", "app:gone/x", "blaze:music", "blaze:chat"), installed))
        assertEquals(4, OsDock.normalize(OsApps.builtinApps().map { it.id }, installed).size)
        val full = listOf("a", "b", "c", "d")
        assertFalse(OsDock.canAdd(full, "e"))
        assertEquals(full, OsDock.toggle(full, "e"))
        assertEquals(listOf("a", "c", "d"), OsDock.toggle(full, "b"))
        assertEquals(listOf("a", "c", "d", "e"), OsDock.toggle(listOf("a", "c", "d"), "e"))
        assertEquals(listOf("b", "a", "c", "d"), OsDock.move(full, "a", 1))
        assertEquals(full, OsDock.move(full, "a", -1))
    }

    // ---------------- search / library ----------------

    @Test fun searchPrefersLabelStarts() {
        val apps = listOf(OsApp("1", "Messages", "com.msg"), OsApp("2", "Google Maps", "com.google.maps"), OsApp("3", "Gmail", "com.google.gm"), OsApp("4", "Camera", "com.cam"))
        assertEquals(listOf("3", "2"), OsApps.search(apps, "g").map { it.id }.take(2))
        assertEquals(listOf("2"), OsApps.search(apps, "maps").map { it.id })
        assertEquals(listOf("2", "3"), OsApps.search(apps, "google").map { it.id })
        assertEquals(4, OsApps.search(apps, " ").size)
        val g = OsApps.groups(apps + OsApp("5", "1Password", "p"))
        assertEquals(listOf("C", "G", "M", "#"), g.map { it.first })
    }

    // ---------------- icons ----------------

    @Test fun iconMaths() {
        assertEquals(96, OsIcons.sizePx(48f, 2f))
        assertEquals(OsIcons.MAX_PX, OsIcons.sizePx(60f, 4f))
        assertEquals(24, OsIcons.sizePx(4f, 1f))
        val pts = OsIcons.squircle(64)
        assertEquals(64, pts.size)
        assertTrue(pts.all { (x, y) -> x in 0f..1f && y in 0f..1f })
        // A squircle reaches further into the corner than a circle (|x|^5 + |y|^5 = 1 at 45° ≈ 0.87 of the half-size).
        val corner = pts[8]
        assertTrue(corner.first > 0.5f + 0.35355f + 0.05f)
        assertEquals(0xFF4DA3FFL, OsIcons.parseColor("#4da3ff"))
        assertNull(OsIcons.parseColor("#12345"))
        assertNull(OsIcons.parseColor("zzzzzz"))
        assertEquals("#4DA3FF", OsIcons.hex(0xFF4DA3FFL))
        assertEquals("squircle", OsIcons.normShape("blob"))
        assertEquals("circle", OsIcons.normShape("Circle"))
    }

    @Test fun tintMatrixIsIdentityAtZeroAndGreyTimesTintAtOne() {
        val id = OsIcons.tintMatrix(1f, 0f, 0f, strength = 0f)
        assertEquals(20, id.size)
        val expect = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        for (i in 0 until 20) assertEquals("i=$i", expect[i], id[i], 1e-6f)
        val red = OsIcons.tintMatrix(1f, 0f, 0f, strength = 1f)
        // Green and blue rows are zero; the red row is the luminance weights (lifted); alpha untouched.
        for (i in 5 until 15) assertEquals(0f, red[i], 1e-6f)
        assertTrue(red[1] > red[0] && red[0] > red[2])
        assertEquals(1f, red[18], 1e-6f)
    }

    // ---------------- prefs + migration ----------------

    @Test fun prefsRoundTrip() {
        val p = OsPrefs(classic = true, wallpaper = "solid:#1f2a44", iconShape = "circle", iconTint = "#4da3ff", grid = OsGrid(5, 6), labels = false,
            dockOn = false, dock = listOf("blaze:music", "blaze:camera"), glass = "high", accent = "#69DB7C", theme = "light", font = "l",
            order = listOf("blaze:chat", "app:a/A"), removed = setOf("blaze:video"), widgets = listOf("music", "ask"), launcher = true)
        val m = OsPrefs.write(p)
        val back = OsPrefs.read { m[it] }
        assertEquals(p.copy(wallpaper = "solid:#1F2A44", iconTint = "#4DA3FF"), back)
        assertEquals(1.15f, back.fontScale, 1e-6f)
        assertFalse(back.dark)
    }

    @Test fun emptyDockAndNoWidgetsSurviveARoundTrip() {
        val m = OsPrefs.write(OsPrefs(dock = emptyList(), widgets = emptyList()))
        val back = OsPrefs.read { m[it] }
        assertEquals(emptyList<String>(), back.dock)
        assertEquals(emptyList<String>(), back.widgets)
    }

    @Test fun missingOrJunkValuesReadAsDefaults() {
        val junk = mapOf(OsPrefs.K_GRID to "9x9", OsPrefs.K_WALL to "preset:mars", OsPrefs.K_GLASS to "ultra", OsPrefs.K_FONT to "xl",
            OsPrefs.K_TINT to "red", OsPrefs.K_LABELS to "maybe", OsPrefs.K_WIDGETS to "nope\nmusic")
        val p = OsPrefs.read { junk[it] }
        assertEquals(OsGrid.DEFAULT, p.grid)
        assertEquals(OsWallpaper.DEFAULT, p.wallpaper)
        assertEquals("medium", p.glass)
        assertEquals("m", p.font)
        assertEquals("", p.iconTint)
        assertTrue(p.labels)
        assertEquals(listOf("music"), p.widgets)
        // Nothing stored at all = all defaults (dock never set, widgets = the default set).
        assertEquals(OsPrefs(), OsPrefs.read { null })
        assertNull(OsPrefs().dock)
    }

    @Test fun migrationCarriesTheOldBoardLookOnce() {
        val old = mapOf("home_bg" to "image", "home_accent" to "teal", "theme_mode" to "light", "home_onboarded" to "true")
        val w = OsPrefs.migrate { old[it] }
        assertEquals("photo", w[OsPrefs.K_WALL])
        assertEquals("#66D9E8", w[OsPrefs.K_ACCENT])
        assertEquals("light", w[OsPrefs.K_THEME])
        assertEquals("1", w[OsPrefs.K_VERSION])
        // Never writes an old key.
        assertTrue(w.keys.all { it.startsWith("bos_") })
        // Applied → read back.
        val p = OsPrefs.read { (old + w)[it] }
        assertEquals("photo", p.wallpaper); assertEquals("#66D9E8", p.accent); assertEquals("light", p.theme)
        assertFalse(p.classic)
        // Second run: nothing to do.
        assertTrue(OsPrefs.migrate { (old + w)[it] }.isEmpty())
    }

    @Test fun migrationOfAFreshInstallOnlyStampsTheVersion() {
        assertEquals(mapOf(OsPrefs.K_VERSION to "1"), OsPrefs.migrate { null })
        // A glass / sky board and the system theme keep BlazeOS's own defaults.
        val m = OsPrefs.migrate { mapOf("home_bg" to "glass", "theme_mode" to "system", "home_accent" to "")[it] }
        assertEquals(mapOf(OsPrefs.K_VERSION to "1"), m)
        // A user who already picked a BlazeOS wallpaper keeps it.
        val keep = OsPrefs.migrate { mapOf("home_bg" to "image", OsPrefs.K_WALL to "preset:ocean")[it] }
        assertFalse(OsPrefs.K_WALL in keep)
    }

    @Test fun wallpaperValues() {
        assertEquals("photo", OsWallpaper.norm("photo"))
        assertEquals("preset:ember", OsWallpaper.norm("preset:ember"))
        assertEquals("solid:#E9E4DC", OsWallpaper.norm("solid:e9e4dc"))
        assertEquals(OsWallpaper.DEFAULT, OsWallpaper.norm("solid:nope"))
        assertEquals("ocean", OsWallpaper.preset("preset:ocean"))
        assertEquals("", OsWallpaper.preset("photo"))
    }
}
