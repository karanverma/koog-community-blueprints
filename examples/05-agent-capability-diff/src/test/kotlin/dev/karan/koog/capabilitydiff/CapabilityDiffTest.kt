package dev.karan.koog.capabilitydiff

import dev.karan.koog.guardedtoolagent.GuardDecision
import dev.karan.koog.guardedtoolagent.GuardedAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapabilityDiffTest {
    @Test
    fun `all directional transitions classify through the real engine`() {
        val scenarios = listOf(
            scenario("deny-confirm", GuardDecision.DENY, GuardDecision.CONFIRM, CapabilityChange.EXPANSION),
            scenario("deny-allow", GuardDecision.DENY, GuardDecision.ALLOW, CapabilityChange.EXPANSION),
            scenario("confirm-allow", GuardDecision.CONFIRM, GuardDecision.ALLOW, CapabilityChange.EXPANSION),
            scenario("allow-confirm", GuardDecision.ALLOW, GuardDecision.CONFIRM, CapabilityChange.TIGHTENING),
            scenario("allow-deny", GuardDecision.ALLOW, GuardDecision.DENY, CapabilityChange.TIGHTENING),
            scenario("confirm-deny", GuardDecision.CONFIRM, GuardDecision.DENY, CapabilityChange.TIGHTENING),
            scenario("deny-deny", GuardDecision.DENY, GuardDecision.DENY, CapabilityChange.UNCHANGED),
            scenario("confirm-confirm", GuardDecision.CONFIRM, GuardDecision.CONFIRM, CapabilityChange.UNCHANGED),
            scenario("allow-allow", GuardDecision.ALLOW, GuardDecision.ALLOW, CapabilityChange.UNCHANGED)
        )

        val report = CapabilityDiffRunner().run(scenarios)

        assertTrue(report.allExpectationsMatched)
        assertEquals(scenarios.map { it.expectedChange }, report.results.map { it.change })
    }

    @Test
    fun `report counts expansions tightening and unchanged results`() {
        val report = CapabilityDiffRunner().run(
            listOf(
                scenario("expansion", GuardDecision.DENY, GuardDecision.ALLOW, CapabilityChange.EXPANSION),
                scenario("tightening", GuardDecision.ALLOW, GuardDecision.DENY, CapabilityChange.TIGHTENING),
                scenario("unchanged", GuardDecision.CONFIRM, GuardDecision.CONFIRM, CapabilityChange.UNCHANGED)
            )
        )

        assertEquals(3, report.totalScenarios)
        assertEquals(1, report.expansionCount)
        assertEquals(1, report.tighteningCount)
        assertEquals(1, report.unchangedCount)
        assertTrue(report.hasExpansions)
    }

    @Test
    fun `tightening only is not an expansion`() {
        val report = CapabilityDiffRunner().run(
            listOf(scenario("tightening", GuardDecision.ALLOW, GuardDecision.CONFIRM, CapabilityChange.TIGHTENING))
        )

        assertFalse(report.hasExpansions)
    }

    @Test
    fun `realistic policy changes are detected`() {
        val report = CapabilityDiffRunner().run(referenceScenarios())

        assertEquals(6, report.totalScenarios)
        assertEquals(3, report.expansionCount)
        assertEquals(1, report.tighteningCount)
        assertEquals(2, report.unchangedCount)
        assertTrue(report.allExpectationsMatched)
        assertEquals(GuardDecision.DENY, report.results[0].baseline.decision)
        assertEquals(GuardDecision.ALLOW, report.results[0].candidate.decision)
        assertEquals(GuardDecision.CONFIRM, report.results[1].baseline.decision)
        assertEquals(GuardDecision.ALLOW, report.results[1].candidate.decision)
    }

    @Test
    fun `incorrect expectation is surfaced as a failed result`() {
        val report = CapabilityDiffRunner().run(
            listOf(scenario("incorrect", GuardDecision.DENY, GuardDecision.ALLOW, CapabilityChange.TIGHTENING))
        )

        assertFalse(report.allExpectationsMatched)
        assertFalse(report.results.single().expectationMatched)
        assertEquals(CapabilityChange.EXPANSION, report.results.single().change)
    }

    private fun scenario(
        id: String,
        baselineDecision: GuardDecision,
        candidateDecision: GuardDecision,
        expectedChange: CapabilityChange
    ) = CapabilityDiffScenario(
        id = id,
        action = GuardedAction("$id-operation", "workspace.txt"),
        baseline = workspacePolicy("$id-baseline", actionDecisions = mapOf("$id-operation" to baselineDecision)),
        candidate = workspacePolicy("$id-candidate", actionDecisions = mapOf("$id-operation" to candidateDecision)),
        expectedChange = expectedChange
    )
}