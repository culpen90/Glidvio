package com.glidvio.app.sponsor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipPlannerTest {
    private fun segment(start: Double, end: Double, uuid: String, category: SponsorCategory = SponsorCategory.SPONSOR) =
        Segment(start, end, category, uuid)

    @Test fun `unsorted overlapping nested and adjacent submissions form one window`() {
        val input = listOf(
            segment(20.0, 25.0, "adjacent", SponsorCategory.INTERACTION),
            segment(10.0, 20.0, "outer"),
            segment(12.0, 14.0, "nested"),
            segment(18.0, 23.0, "overlap", SponsorCategory.SELFPROMO),
        )
        val window = SkipPlanner.normalize(input).single()
        assertEquals(10.0, window.startSeconds, 0.0)
        assertEquals(25.0, window.endSeconds, 0.0)
        assertEquals(setOf("outer", "nested", "overlap", "adjacent"), window.uuids)
        assertEquals(SponsorCategory.defaults, window.categories)
        assertEquals(SkipPlanner.normalize(input.reversed()), listOf(window))
    }

    @Test fun `unmarked content gaps are preserved`() {
        val segments = listOf(segment(10.0, 20.0, "first"), segment(20.001, 30.0, "second"))
        assertEquals(2, SkipPlanner.normalize(segments).size)
        assertNull(SkipPlanner.nextSkip(20.0005, segments))
        assertEquals(30.0, SkipPlanner.nextSkip(20.001, segments)!!.endSeconds, 0.0)
    }

    @Test fun `start boundary skips end boundary does not and seeking inside skips`() {
        val segments = listOf(segment(10.0, 20.0, "sponsor"))
        assertNull(SkipPlanner.nextSkip(9.999, segments))
        assertNotNull(SkipPlanner.nextSkip(10.0, segments))
        assertNotNull(SkipPlanner.nextSkip(19.999, segments))
        assertNull(SkipPlanner.nextSkip(20.0, segments))
        assertNull(SkipPlanner.nextSkip(25.0, segments))
    }

    @Test fun `nonfinite negative reversed empty and unidentified ranges are discarded`() {
        val input = listOf(
            segment(Double.NaN, 10.0, "nan"),
            segment(0.0, Double.POSITIVE_INFINITY, "infinite"),
            segment(-1.0, 10.0, "negative"),
            segment(5.0, 4.0, "reversed"),
            segment(5.0, 5.0, "empty"),
            segment(0.0, 10.0, "  "),
            segment(0.0, 10.0, "valid"),
        )
        assertEquals(setOf("valid"), SkipPlanner.normalize(input).single().uuids)
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0).forEach {
            assertNull(SkipPlanner.nextSkip(it, input))
        }
    }

    @Test fun `known duration clips end and discards segments beyond video`() {
        val segments = listOf(segment(90.0, 150.0, "end"), segment(110.0, 130.0, "beyond"))
        val decision = SkipPlanner.nextSkip(95.0, segments, durationSeconds = 100.0)!!
        assertEquals(100.0, decision.endSeconds, 0.0)
        assertEquals(setOf("end"), decision.uuids)
        assertNull(SkipPlanner.nextSkip(100.0, segments, durationSeconds = 100.0))
        assertEquals(150.0, SkipPlanner.normalize(segments, Double.NaN).single().endSeconds, 0.0)
    }

    @Test fun `duplicate segments do not create duplicate windows or sources`() {
        val first = segment(0.0, 10.0, "first")
        val windows = SkipPlanner.normalize(listOf(first, first))
        assertEquals(listOf(first), windows.single().segments)
    }

    @Test fun `undo restores observed position and suppresses every overlapping submission`() {
        val segments = listOf(
            segment(10.0, 20.0, "first"),
            segment(18.0, 30.0, "overlapping"),
            segment(40.0, 50.0, "later"),
        )
        val skip = SkipPlanner.nextSkip(10.25, segments)!!
        val undo = SkipPlanner.undo(skip, setOf("previous"))
        assertEquals(10.25, undo.positionSeconds, 0.0)
        assertEquals(setOf("previous", "first", "overlapping"), undo.ignoredUuids)
        assertNull(SkipPlanner.nextSkip(undo.positionSeconds, segments, undo.ignoredUuids))
        assertNull(SkipPlanner.nextSkip(25.0, segments, undo.ignoredUuids))
        assertNotNull(SkipPlanner.nextSkip(40.0, segments, undo.ignoredUuids))
        // Changing videos clears caller-owned Undo state, restoring auto skip.
        assertNotNull(SkipPlanner.nextSkip(10.25, segments))
    }

    @Test fun `single previously ignored member prevents another overlap from immediately reskipping`() {
        val segments = listOf(segment(10.0, 20.0, "one"), segment(15.0, 30.0, "two"))
        assertNull(SkipPlanner.nextSkip(16.0, segments, setOf("one")))
    }

    @Test fun `defaults enable only advertising and engagement categories`() {
        assertEquals(setOf("sponsor", "selfpromo", "interaction"), SponsorCategory.defaults.map { it.apiName }.toSet())
        assertTrue(SponsorCategory.SPONSOR in SponsorCategory.defaults)
        assertFalse(SponsorCategory.INTRO in SponsorCategory.defaults)
    }
}
