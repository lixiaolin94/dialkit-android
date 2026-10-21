package io.github.lixiaolin94.dialkit

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class DialCellTest {
    @After fun clearHooks() {
        DialCell.readHook = null
        DialCell.writeHook = null
    }

    @Test fun listenersFireOnlyOnChangeAndCanUnsubscribe() {
        val cell = DialCell(1)
        val seen = mutableListOf<Int>()
        val stop = cell.listen { seen += it }
        cell.value = 1 // same value: no event
        cell.value = 2
        stop()
        cell.value = 3
        assertEquals(listOf(2), seen)
    }

    @Test fun hooksSeeReadsAndWrites() {
        // dialkit-compose mirrors cells into Compose state through these.
        var reads = 0
        var writes = 0
        DialCell.readHook = { reads++ }
        DialCell.writeHook = { writes++ }
        val cell = DialCell("a")
        cell.value
        cell.value = "b"
        cell.value = "b"
        assertEquals(1, reads)
        assertEquals(1, writes)
        assertEquals("b", cell.peek) // library bookkeeping does not count as a read
        assertEquals(1, reads)
    }
}
