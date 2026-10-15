package com.imad.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PlanMathTest {
    private val today = LocalDate.of(2026, 10, 15)

    private fun memorize(done: Int, perDay: Int = 0, end: LocalDate = today.plusDays(99)) =
        Plan("m", "حفظ", "memorize", done = done, perDay = perDay, endDay = end.toString(), configured = true)

    @Test fun memorizeSuggestionFinishesByEndDate() {
        // 604 - 404 = 200 pages over 100 days -> 2 a day
        assertEquals(2, PlanMath.suggestedPerDay(memorize(404), today))
    }

    @Test fun memorizeTodayIsNextPages() {
        assertEquals(PageRange(405, 406), PlanMath.today(memorize(404), today))
        assertEquals(PageRange(405, 405), PlanMath.today(memorize(404, perDay = 1), today))
    }

    @Test fun userPerDayOverridesSuggestion() {
        val s = PlanMath.suggestion(memorize(0, perDay = 1), today)
        assertEquals(1, s.perDay)
        assertEquals(7, s.week)
        assertEquals(17, s.month) // 15..31 October
        assertEquals(today.plusDays(603), s.projectedEnd)
    }

    @Test fun memorizeAdvanceMovesDone() {
        val p = memorize(10, perDay = 2)
        val r = PlanMath.today(p, today)!!
        assertEquals(12, PlanMath.advance(p, r).done)
    }

    @Test fun finishedPlanHasNothingToday() {
        assertNull(PlanMath.today(memorize(604), today))
    }

    @Test fun readWrapsAroundMushaf() {
        val p = Plan("r", "ورد", "read", perDay = 10, pointer = 600, configured = true)
        val r = PlanMath.today(p, today)!!
        assertEquals(listOf(600, 601, 602, 603, 604, 1, 2, 3, 4, 5), PlanMath.pages(r, 604))
        assertEquals(6, PlanMath.advance(p, r).pointer)
    }

    @Test fun reviewSplitsMemorizedPagesOverCycle() {
        val p = Plan("v", "مراجعة", "review", cycleDays = 7, pointer = 1, configured = true)
        // 30 memorized pages over 7 days -> 5 pages a day
        assertEquals(PageRange(1, 5), PlanMath.today(p, today, memorizedLast = 30))
        val after = PlanMath.advance(p, PageRange(1, 5), 30)
        assertEquals(6, after.pointer)
        // last chunk wraps back to the first page
        val end = p.copy(pointer = 26)
        assertEquals(PageRange(26, 30), PlanMath.today(end, today, 30))
        assertEquals(1, PlanMath.advance(end, PageRange(26, 30), 30).pointer)
    }

    @Test fun reviewNeedsMemorizedPages() {
        assertNull(PlanMath.today(Plan("v", "", "review", configured = true), today, memorizedLast = 0))
    }

    @Test fun titles() {
        assertEquals("حفظ: صفحة 5", PlanMath.title(memorize(4), PageRange(5, 5)))
        assertEquals("ورد القرآن: 10 صفحات (من ص 41)", PlanMath.title(Plan("r", "", "read"), PageRange(41, 50)))
    }

    @Test fun goalMilestonesAreLinear() {
        val start = LocalDate.of(2026, 1, 1)
        val target = start.plusDays(100)
        assertEquals(200.0, PlanMath.goalValueAt(150.0, 250.0, start, target, start.plusDays(50)), 1e-9)
        // lower-is-better: time goes down
        assertEquals(48.4, PlanMath.goalValueAt(55.0, 41.8, start, target, start.plusDays(50)), 1e-9)
    }

    @Test fun weekEndsFriday() {
        assertEquals(LocalDate.of(2026, 10, 16), PlanMath.endOfWeek(today)) // Thursday -> Friday
        assertEquals(LocalDate.of(2026, 10, 23), PlanMath.endOfWeek(LocalDate.of(2026, 10, 17))) // Saturday
    }
}
