package dev.karan.koog.safetyregression

import dev.karan.koog.guardedtoolagent.GuardDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafetyRegressionRunnerTest {

    @Test
    fun `baseline suite passes all twenty scenarios`() {
        val report = SafetyRegressionRunner().run()

        assertEquals(20, report.totalCount)
        assertEquals(20, report.passedCount)
        assertTrue(report.allPassed)
    }

    @Test
    fun `incorrect expectation produces failed scenario`() {
        val scenario = SafetyScenario(
            id = "intentional-regression",
            description = "An incorrect expectation must fail",
            evaluate = {
                ScenarioResult(
                    policyDecision = GuardDecision.ALLOW,
                    executionOutcome = ExecutionOutcome.EXECUTED
                )
            },
            expected = ScenarioExpectation(
                policyDecision = GuardDecision.DENY,
                executionOutcome = ExecutionOutcome.DENIED
            )
        )

        val result = SafetyRegressionRunner().executeScenario(scenario)

        assertFalse(result.passed)
        assertEquals(GuardDecision.DENY, result.expectedPolicyDecision)
        assertEquals(GuardDecision.ALLOW, result.actualPolicyDecision)
    }

    @Test
    fun `missing expected recheck is detected as regression`() {
        val scenario = SafetyScenario(
            id = "missing-recheck",
            description = "Expected recheck must actually occur",
            evaluate = {
                ScenarioResult(
                    policyDecision = GuardDecision.CONFIRM,
                    executionOutcome = ExecutionOutcome.DENIED,
                    recheckPolicyDecision = null
                )
            },
            expected = ScenarioExpectation(
                policyDecision = GuardDecision.CONFIRM,
                executionOutcome = ExecutionOutcome.DENIED,
                recheckPolicyDecision = GuardDecision.DENY
            )
        )

        val result = SafetyRegressionRunner().executeScenario(scenario)

        assertFalse(result.passed)
    }

    @Test
    fun `report fails when any scenario fails`() {
        val failed = SafetyRegressionScenarioResult(
            id = "failed",
            description = "Synthetic failure",
            expectedPolicyDecision = GuardDecision.DENY,
            actualPolicyDecision = GuardDecision.ALLOW,
            expectedExecutionOutcome = ExecutionOutcome.DENIED,
            actualExecutionOutcome = ExecutionOutcome.EXECUTED,
            ruleId = null,
            passed = false,
            sideEffectPassed = true
        )

        val report = SafetyRegressionReport(listOf(failed))

        assertFalse(report.allPassed)
        assertEquals(0, report.passedCount)
        assertEquals(1, report.totalCount)
    }

    @Test
    fun `rejected operations preserve expected filesystem state`() {
        val report = SafetyRegressionRunner().run()

        val rejectedWrite = report.scenarios.single { it.id == "rejected-write" }
        val rejectedDelete = report.scenarios.single { it.id == "rejected-delete" }

        assertEquals(ExecutionOutcome.REJECTED, rejectedWrite.actualExecutionOutcome)
        assertTrue(rejectedWrite.sideEffectPassed)
        assertTrue(rejectedWrite.passed)

        assertEquals(ExecutionOutcome.REJECTED, rejectedDelete.actualExecutionOutcome)
        assertTrue(rejectedDelete.sideEffectPassed)
        assertTrue(rejectedDelete.passed)
    }

    @Test
    fun `nested traversal is denied`() {
        val result = SafetyRegressionRunner()
            .run()
            .scenarios
            .single { it.id == "nested-traversal" }

        assertEquals(GuardDecision.DENY, result.actualPolicyDecision)
        assertEquals(ExecutionOutcome.DENIED, result.actualExecutionOutcome)
        assertTrue(result.passed)
    }

    @Test
    fun `post approval recheck preserves confirm then denies changed target`() {
        val result = SafetyRegressionRunner()
            .run()
            .scenarios
            .single { it.id == "post-approval-recheck" }

        assertEquals(GuardDecision.CONFIRM, result.actualPolicyDecision)
        assertEquals(GuardDecision.DENY, result.recheckPolicyDecision)
        assertEquals(ExecutionOutcome.DENIED, result.actualExecutionOutcome)
        assertTrue(result.passed)
    }
}
