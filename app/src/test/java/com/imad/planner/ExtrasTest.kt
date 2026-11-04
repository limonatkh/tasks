package com.imad.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtrasTest {
    @Test fun playlistIdFromLinks() {
        assertEquals("PLabc123DEF_-x", YtLink.listId("https://www.youtube.com/playlist?list=PLabc123DEF_-x"))
        assertEquals("PLabc123DEF", YtLink.listId("https://youtube.com/watch?v=xyz&list=PLabc123DEF&index=3"))
        assertEquals("PLabc123DEF", YtLink.listId("PLabc123DEF"))
        assertNull(YtLink.listId("hello"))
    }

    @Test fun equalYearParts() {
        assertEquals(listOf(6, 6), YearPart.equal(2).map { it.months })
        assertEquals(listOf(4, 4, 4), YearPart.equal(3).map { it.months })
        assertEquals(12, YearPart.equal(5).sumOf { it.months })
        assertEquals(12, YearPart.equal(12).size)
    }

    @Test fun monthsFallInTheirPart() {
        val parts = listOf(YearPart("a", 5), YearPart("b", 7))
        assertEquals(0, YearPart.partOfMonth(parts, 4))
        assertEquals(1, YearPart.partOfMonth(parts, 5))
        assertEquals(1, YearPart.partOfMonth(parts, 11))
    }
}
