package io.github.lixiaolin94.dialkit

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.lixiaolin94.dialkit.store.DialStore

/** Registration, retention and persistence in the real store (it needs the main looper and SharedPreferences). */
class StoreLifecycleTest {
    private val prefs get() = instrumentation.targetContext.getSharedPreferences("io.github.lixiaolin94.dialkit.values", Context.MODE_PRIVATE)

    @Test fun aRecreatedScreenKeepsItsPanelWhenTheOldOneReleasesLate() = onMain {
        val id = "store-recreate"
        val first = DialStore.acquire(id, "Card", false, null) { TestDials() }
        // The recreated Activity registers before the old one is destroyed: the same panel, mounted twice.
        val second = DialStore.acquire(id, "Card", false, null) { TestDials() }
        assertSame(first, second)
        DialStore.release(first)
        assertTrue(DialStore.panels.peek.any { it.id == id })
        DialStore.release(second)
        assertFalse(DialStore.panels.peek.any { it.id == id })
    }

    @Test fun valuesOutliveTheRegistrationForTheProcess() = onMain {
        val id = "store-retain"
        val panel = DialStore.acquire(id, "Card", false, null) { TestDials() }
        (panel.group as TestDials).radius = 40f
        DialStore.release(panel)
        val again = DialStore.acquire(id, "Card", false, null) { TestDials() }
        assertEquals(40f, (again.group as TestDials).radius)
        DialStore.release(again)
    }

    @Test fun persistedValuesLoadWhenAGroupRegisters() {
        prefs.edit().putString("test:load", """{"version":1,"values":{"radius":48}}""").commit()
        onMain {
            val panel = DialStore.acquire("store-load", "Card", false, DialPersist(key = "test:load")) { TestDials() }
            assertEquals(48f, (panel.group as TestDials).radius)
            DialStore.release(panel)
        }
    }

    @Test fun changesAreWrittenToDisk() {
        prefs.edit().remove("test:write").commit()
        val panel = onMain { DialStore.acquire("store-write", "Card", false, DialPersist(key = "test:write")) { TestDials() } }
        onMain { (panel.group as TestDials).radius = 12f }
        waitUntil("radius 12 persisted") {
            prefs.getString("test:write", null)?.let { JSONObject(it).getJSONObject("values").optDouble("radius") } == 12.0
        }
        onMain { DialStore.release(panel) }
    }

    @Test fun aGroupShownInAPageLeavesTheFloatingListAndStaysOpen() = onMain {
        val panel = DialStore.acquire("store-embed", "Card", false, null) { TestDials() }
        assertTrue(panel in DialStore.floating.peek)
        DialStore.embed(panel)
        DialStore.embed(panel) // two page views
        assertFalse(panel in DialStore.floating.peek)
        assertEquals(true, panel.group.getOpen())
        DialStore.unembed(panel)
        assertFalse("still shown by the second view", panel in DialStore.floating.peek)
        DialStore.unembed(panel)
        assertTrue(panel in DialStore.floating.peek)
        DialStore.release(panel)
        assertNull(panel.group.getOpen())
    }
}
