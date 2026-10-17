package com.imad.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V7Test {
    @Test fun heroSessionsSpreadBetweenShortVideos() {
        val steps = Content.monthSteps(listOf(ContentType("هب", 2, 1, 60), ContentType("هيرو", 1, 3, 90, listOf("رحلة"))))
        assertEquals(5, steps.size)
        assertEquals("هيرو «رحلة» · جلسة 1/3", steps[0].name)
        assertEquals("نشر هب 1", steps[1].name)
        assertEquals("هيرو «رحلة» · جلسة 2/3", steps[2].name)
        assertEquals("نشر هيرو «رحلة»", steps[3].name)
        assertEquals(90, steps[3].minutes)
        assertEquals("نشر هب 2", steps.last().name)
    }

    @Test fun stagesKeepTheirOwnMinutes() {
        val p = Plan("pod", "بودكاست", "cycle", configured = true, pointer = 0, minutesPerUnit = 60,
            stages = Stages.encode(listOf(Stage("تحضير", 60), Stage("تصوير", 120), Stage("نشر", 30))))
        assertEquals(60, PlanMath.unitMinutes(p, 0, 1))
        assertEquals(120, PlanMath.unitMinutes(p, 1, 1))
        assertEquals(210, PlanMath.unitMinutes(p, 0, 3))
        assertEquals("بودكاست: تصوير", PlanMath.phaseTitle(PlanMath.applyUnits(p, 1, 0), 0, 1, 0))
    }

    @Test fun oldStageListsStillWork() {
        val p = Plan("x", "x", "cycle", stages = """["أ","ب"]""", configured = true, pointer = 0)
        assertEquals(listOf("أ", "ب"), p.stageList)
    }

    @Test fun pastedChatGptStepsAreParsed() {
        val text = """
            1. مراجعة المتاجر المنافسة | 60 | شوف 5 متاجر ألعاب
            2. تصميم الصفحة الرئيسية | 2 ساعة | ألوان وشعار
            - رفع المنتجات | ٩٠ | 20 منتج
        """.trimIndent()
        val st = Stages.fromText(text)
        assertEquals(3, st.size)
        assertEquals("مراجعة المتاجر المنافسة", st[0].name)
        assertEquals(120, st[1].minutes)
        assertEquals(90, st[2].minutes)
        assertEquals("20 منتج", st[2].details)
    }

    @Test fun oneTimeProjectStopsAfterItsRound() {
        val p = Plan("s", "متجر", "cycle", total = 1, stages = Stages.encode(listOf(Stage("a", 30), Stage("b", 30))), configured = true, pointer = 0, perWeek = 3.5)
        val done = PlanMath.applyUnits(PlanMath.applyUnits(p, 1, 0), 1, 0)
        assertEquals(1, done.done)
        assertEquals(0, PlanMath.quota(done, java.time.LocalDate.of(2026, 10, 18), 0))
    }
}
