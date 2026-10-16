package com.imad.planner

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Days ahead as the app would plan them: real tasks plus the recurring ones that will come due. */
object Projector {
    data class Entry(val task: Task, val virtual: Boolean)

    fun project(
        from: LocalDate, days: Int, today: LocalDate,
        tasks: List<Task>, rules: List<Rule>, plans: List<Plan>, s: Settings,
    ): Map<LocalDate, List<Entry>> {
        val out = linkedMapOf<LocalDate, List<Entry>>()
        val active = rules.filter { it.active }
        val ruleMap = rules.associateBy { it.id }
        val fromS = from.toString(); val todayS = today.toString()
        val simLast = mutableMapOf<String, String?>()
        for (r in active) {
            val inst = tasks.filter { it.ruleId == r.id && it.state != "skipped" && it.day < fromS && (it.done || it.day >= todayS) }
                .maxOfOrNull { it.day }
            simLast[r.id] = listOfNotNull(r.lastDoneDay, inst).maxOrNull()
        }
        val mem = plans.firstOrNull { it.kind == "memorize" && it.configured }?.done ?: 0
        val ahead = ChronoUnit.DAYS.between(today, from).toInt().coerceAtLeast(0)
        val planSim = plans.associate { it.id to PlanMath.project(it, ahead, mem, today) }.toMutableMap()
        var carry = listOf<Task>()
        for (i in 0 until days) {
            val d = from.plusDays(i.toLong())
            val ds = d.toString()
            val real = tasks.filter { it.day == ds && it.state != "skipped" }
            real.forEach { t -> t.ruleId?.let { if (it in simLast) simLast[it] = maxOf(simLast[it] ?: "", ds) } }
            if (!d.isAfter(today)) {
                out[d] = real.sortedBy { if (it.planned == 0L) Long.MAX_VALUE else it.planned }.map { Entry(it, false) }
                carry = emptyList()
                continue
            }
            val virtual = mutableListOf<Task>()
            for (r in active) {
                if (tasks.any { it.ruleId == r.id && it.day == ds }) continue
                val last = simLast[r.id]?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
                if (!Scheduler.isDue(r.everyHours, r.weekdays, LocalDate.parse(r.anchorDay), last, d)) continue
                val plan = if (r.id.startsWith("plan-")) planSim[r.id.removePrefix("plan-")] ?: continue else null
                virtual += buildInstances(r, d, plan, mem) { k -> "v:${r.id}:$ds:$k" }
            }
            val open = real.filter { it.open } + carry.map { it.copy(day = ds, planned = if (it.pinned) it.planned else 0) } + virtual
            val dp = Scheduler.plan(open.map { itemOf(it, ruleMap, s, null, null) }, s.config, s.dayStart)
            val virtualIds = virtual.map { it.id }.toSet()
            val placed = mutableListOf<Entry>()
            val nextCarry = mutableListOf<Task>()
            val placedRules = mutableSetOf<String>()
            for (t in open) {
                val p = dp.placed[t.id]
                if (p == null || p.overflow) {
                    if (t.id !in virtualIds) nextCarry += t
                    continue
                }
                placed += Entry(t.copy(planned = Clock.epoch(d, p.start), parallelTo = p.parallelTo), t.id in virtualIds)
                t.ruleId?.let { placedRules += it }
            }
            for (rid in placedRules) {
                simLast[rid] = ds
                if (rid.startsWith("plan-") && virtual.any { it.ruleId == rid }) {
                    val id = rid.removePrefix("plan-")
                    planSim[id]?.let { p -> planSim[id] = PlanMath.applyUnits(p, PlanMath.quota(p, d, mem), mem) }
                }
            }
            out[d] = (real.filter { !it.open }.map { Entry(it, false) } + placed)
                .sortedBy { if (it.task.planned == 0L) Long.MAX_VALUE else it.task.planned }
            carry = nextCarry
        }
        return out
    }
}
