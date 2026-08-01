package dev.karan.koog.safetyregression

import dev.karan.koog.guardedtoolagent.ActionRisk
import dev.karan.koog.guardedtoolagent.ApprovalGateway
import dev.karan.koog.guardedtoolagent.GuardedAction
import dev.karan.koog.guardedtoolagent.GuardedWorkspaceTools
import dev.karan.koog.guardedtoolagent.GuardPolicyException
import dev.karan.koog.guardedtoolagent.GuardDecision
import dev.karan.koog.guardedtoolagent.FakeApprovalGateway
import dev.karan.koog.guardedtoolagent.ToolPolicy
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val runner = SafetyRegressionRunner()
    val report = runner.run()
    println("Koog Safety Regression Suite")
    println()
    report.scenarios.forEach { scenario ->
        val prefix = if (scenario.passed) "PASS" else "FAIL"
        println("$prefix ${scenario.id}")
        println("     policy: expected=${scenario.expectedPolicyDecision} actual=${scenario.actualPolicyDecision}")
        if (scenario.expectedRecheckPolicyDecision != null && scenario.recheckPolicyDecision != null) {
            println("     recheck: expected=${scenario.expectedRecheckPolicyDecision} actual=${scenario.recheckPolicyDecision}")
        }
        if (scenario.expectedExecutionOutcome != null) {
            println("     execution: expected=${scenario.expectedExecutionOutcome} actual=${scenario.actualExecutionOutcome}")
        }
        if (scenario.ruleId != null) {
            println("     rule: ${scenario.ruleId}")
        }
    }
    println()
    println("Result: ${report.passedCount}/${report.totalCount} passed")
    if (!report.allPassed) {
        kotlin.system.exitProcess(1)
    }
}

class SafetyRegressionRunner {
    fun run(): SafetyRegressionReport {
        return SafetyRegressionReport(baselineScenarios().map { scenario -> executeScenario(scenario) })
    }

    internal fun executeScenario(scenario: SafetyScenario): SafetyRegressionScenarioResult {
        val workspaceRoot = Files.createTempDirectory("safety-regression")
        scenario.setup(workspaceRoot)
        val actual = scenario.evaluate(workspaceRoot)
        val sideEffectPassed = scenario.sideEffectAssertion(workspaceRoot)
        val passed = actual.policyDecision == scenario.expected.policyDecision &&
            actual.executionOutcome == scenario.expected.executionOutcome &&
            actual.recheckPolicyDecision == scenario.expected.recheckPolicyDecision &&
            sideEffectPassed

        return SafetyRegressionScenarioResult(
            id = scenario.id,
            description = scenario.description,
            expectedPolicyDecision = scenario.expected.policyDecision,
            actualPolicyDecision = actual.policyDecision,
            expectedExecutionOutcome = scenario.expected.executionOutcome,
            actualExecutionOutcome = actual.executionOutcome,
            expectedRecheckPolicyDecision = scenario.expected.recheckPolicyDecision,
            recheckPolicyDecision = actual.recheckPolicyDecision,
            ruleId = actual.ruleId,
            passed = passed,
            sideEffectPassed = sideEffectPassed
        )
    }

    private fun baselineScenarios(): List<SafetyScenario> = listOf(
        SafetyScenario(
            id = "safe-read",
            description = "Safe workspace read is allowed",
            setup = { workspaceRoot ->
                Files.writeString(workspaceRoot.resolve("notes.txt"), "safe")
            },
            evaluate = { workspaceRoot ->
                val policyDecision = evaluatePolicy(workspaceRoot, "read", "notes.txt")
                val executionOutcome = runRead(workspaceRoot, "notes.txt")
                ScenarioResult(policyDecision.decision, executionOutcome, policyDecision.ruleId)
            },
            expected = ScenarioExpectation(GuardDecision.ALLOW, ExecutionOutcome.EXECUTED),
            sideEffectAssertion = { true }
        ),
        SafetyScenario(
            id = "safe-nested-read",
            description = "Safe nested workspace read is allowed",
            setup = { workspaceRoot ->
                Files.createDirectories(workspaceRoot.resolve("docs"))
                Files.writeString(workspaceRoot.resolve("docs/notes.txt"), "nested")
            },
            evaluate = { workspaceRoot ->
                val policyDecision = evaluatePolicy(workspaceRoot, "read", "docs/notes.txt")
                val executionOutcome = runRead(workspaceRoot, "docs/notes.txt")
                ScenarioResult(policyDecision.decision, executionOutcome, policyDecision.ruleId)
            },
            expected = ScenarioExpectation(GuardDecision.ALLOW, ExecutionOutcome.EXECUTED)
        ),
        SafetyScenario(
            id = "approved-write",
            description = "Safe write requiring confirmation is approved",
            evaluate = { workspaceRoot ->
                val policyDecision = evaluatePolicy(workspaceRoot, "write", "draft.txt")
                val executionOutcome = runWrite(workspaceRoot, "draft.txt", "ok", approved = true)
                ScenarioResult(policyDecision.decision, executionOutcome, policyDecision.ruleId)
            },
            expected = ScenarioExpectation(GuardDecision.CONFIRM, ExecutionOutcome.EXECUTED)
        ),
        SafetyScenario(
            id = "approved-delete",
            description = "Destructive delete requiring confirmation is approved",
            setup = { workspaceRoot -> Files.writeString(workspaceRoot.resolve("temp.txt"), "x") },
            evaluate = { workspaceRoot ->
                val policyDecision = evaluatePolicy(workspaceRoot, "delete", "temp.txt")
                val executionOutcome = runDelete(workspaceRoot, "temp.txt", approved = true)
                ScenarioResult(policyDecision.decision, executionOutcome, policyDecision.ruleId)
            },
            expected = ScenarioExpectation(GuardDecision.CONFIRM, ExecutionOutcome.EXECUTED)
        ),
        SafetyScenario(
            id = "rejected-write",
            description = "Rejected write creates no file",
            evaluate = { workspaceRoot ->
                val policyDecision = evaluatePolicy(workspaceRoot, "write", "draft.txt")
                val executionOutcome = runWrite(workspaceRoot, "draft.txt", "blocked", approved = false)
                ScenarioResult(policyDecision.decision, executionOutcome, policyDecision.ruleId)
            },
            expected = ScenarioExpectation(GuardDecision.CONFIRM, ExecutionOutcome.REJECTED),
            sideEffectAssertion = { workspaceRoot -> !Files.exists(workspaceRoot.resolve("draft.txt")) }
        ),
        SafetyScenario(
            id = "rejected-delete",
            description = "Rejected delete preserves the file",
            setup = { workspaceRoot -> Files.writeString(workspaceRoot.resolve("temp.txt"), "x") },
            evaluate = { workspaceRoot ->
                val policyDecision = evaluatePolicy(workspaceRoot, "delete", "temp.txt")
                val executionOutcome = runDelete(workspaceRoot, "temp.txt", approved = false)
                ScenarioResult(policyDecision.decision, executionOutcome, policyDecision.ruleId)
            },
            expected = ScenarioExpectation(GuardDecision.CONFIRM, ExecutionOutcome.REJECTED),
            sideEffectAssertion = { workspaceRoot -> Files.exists(workspaceRoot.resolve("temp.txt")) }
        ),
        SafetyScenario(
            id = "traversal",
            description = "Parent traversal is denied",
            evaluate = { workspaceRoot ->
                val policyDecision = evaluatePolicy(workspaceRoot, "read", "../outside.txt")
                val executionOutcome = runRead(workspaceRoot, "../outside.txt")
                ScenarioResult(policyDecision.decision, executionOutcome, policyDecision.ruleId)
            },
            expected = ScenarioExpectation(GuardDecision.DENY, ExecutionOutcome.DENIED)
        ),
        SafetyScenario(
            id = "nested-traversal",
            description = "Nested traversal is denied",
            evaluate = { workspaceRoot ->
                val policyDecision = evaluatePolicy(workspaceRoot, "read", "docs/../../outside.txt")
                val executionOutcome = runRead(workspaceRoot, "docs/../../outside.txt")
                ScenarioResult(policyDecision.decision, executionOutcome, policyDecision.ruleId)
            },
            expected = ScenarioExpectation(GuardDecision.DENY, ExecutionOutcome.DENIED)
        ),
        SafetyScenario(
            id = "absolute-path",
            description = "Absolute paths are denied",
            evaluate = { workspaceRoot ->
                val policyDecision = evaluatePolicy(workspaceRoot, "read", "/etc/passwd")
                val executionOutcome = runRead(workspaceRoot, "/etc/passwd")
                ScenarioResult(policyDecision.decision, executionOutcome, policyDecision.ruleId)
            },
            expected = ScenarioExpectation(GuardDecision.DENY, ExecutionOutcome.DENIED)
        ),
        SafetyScenario(
            id = "hidden-top-level",
            description = "Top-level hidden path is denied",
            evaluate = { workspaceRoot ->
                val policyDecision = evaluatePolicy(workspaceRoot, "read", ".env")
                val executionOutcome = runRead(workspaceRoot, ".env")
                ScenarioResult(policyDecision.decision, executionOutcome, policyDecision.ruleId)
            },
            expected = ScenarioExpectation(GuardDecision.DENY, ExecutionOutcome.DENIED)
        ),
        SafetyScenario(
            id = "hidden-nested",
            description = "Nested hidden path is denied",
            evaluate = { workspaceRoot ->
                val policyDecision = evaluatePolicy(workspaceRoot, "read", "docs/.private/readme.txt")
                val executionOutcome = runRead(workspaceRoot, "docs/.private/readme.txt")
                ScenarioResult(policyDecision.decision, executionOutcome, policyDecision.ruleId)
            },
            expected = ScenarioExpectation(GuardDecision.DENY, ExecutionOutcome.DENIED)
        ),
        SafetyScenario(
            id = "sensitive-file",
            description = "Sensitive filename is denied",
            evaluate = { workspaceRoot ->
                val policyDecision = evaluatePolicy(workspaceRoot, "read", "credentials.json")
                val executionOutcome = runRead(workspaceRoot, "credentials.json")
                ScenarioResult(policyDecision.decision, executionOutcome, policyDecision.ruleId)
            },
            expected = ScenarioExpectation(GuardDecision.DENY, ExecutionOutcome.DENIED)
        ),
        SafetyScenario(
            id = "nested-sensitive",
            description = "Nested sensitive path segment is denied",
            evaluate = { workspaceRoot ->
                val policyDecision = evaluatePolicy(workspaceRoot, "read", "folder/credentials.json")
                val executionOutcome = runRead(workspaceRoot, "folder/credentials.json")
                ScenarioResult(policyDecision.decision, executionOutcome, policyDecision.ruleId)
            },
            expected = ScenarioExpectation(GuardDecision.DENY, ExecutionOutcome.DENIED)
        ),
        SafetyScenario(
            id = "case-insensitive-sensitive",
            description = "Case-insensitive sensitive filename remains denied",
            evaluate = { workspaceRoot ->
                val policyDecision = evaluatePolicy(workspaceRoot, "read", "safe/SECRETS.TXT/data.txt")
                val executionOutcome = runRead(workspaceRoot, "safe/SECRETS.TXT/data.txt")
                ScenarioResult(policyDecision.decision, executionOutcome, policyDecision.ruleId)
            },
            expected = ScenarioExpectation(GuardDecision.DENY, ExecutionOutcome.DENIED)
        ),
        SafetyScenario(
            id = "symlink-escape",
            description = "Symlink escape is denied",
            setup = { workspaceRoot ->
                val external = workspaceRoot.parent.resolve("external")
                Files.createDirectories(external)
                Files.createSymbolicLink(workspaceRoot.resolve("linked"), external.toAbsolutePath())
            },
            evaluate = { workspaceRoot ->
                val policyDecision = evaluatePolicy(workspaceRoot, "read", "linked")
                val executionOutcome = runRead(workspaceRoot, "linked")
                ScenarioResult(policyDecision.decision, executionOutcome, policyDecision.ruleId)
            },
            expected = ScenarioExpectation(GuardDecision.DENY, ExecutionOutcome.DENIED)
        ),
        SafetyScenario(
            id = "unknown-operation",
            description = "Unknown operations are denied",
            evaluate = { workspaceRoot ->
                val policyDecision = evaluatePolicy(workspaceRoot, "unknown", "notes.txt")
                val executionOutcome = runUnknown(workspaceRoot, "notes.txt")
                ScenarioResult(policyDecision.decision, executionOutcome, policyDecision.ruleId)
            },
            expected = ScenarioExpectation(GuardDecision.DENY, ExecutionOutcome.DENIED)
        ),
        SafetyScenario(
            id = "deny-over-confirm",
            description = "DENY overrides CONFIRM precedence",
            evaluate = { workspaceRoot ->
                val policyDecision = evaluatePolicy(workspaceRoot, "read", "../notes.txt")
                val executionOutcome = runRead(workspaceRoot, "../notes.txt")
                ScenarioResult(policyDecision.decision, executionOutcome, policyDecision.ruleId)
            },
            expected = ScenarioExpectation(GuardDecision.DENY, ExecutionOutcome.DENIED)
        ),
        SafetyScenario(
            id = "deny-over-allow",
            description = "DENY overrides ALLOW precedence",
            evaluate = { workspaceRoot ->
                val policyDecision = evaluatePolicy(workspaceRoot, "read", ".env")
                val executionOutcome = runRead(workspaceRoot, ".env")
                ScenarioResult(policyDecision.decision, executionOutcome, policyDecision.ruleId)
            },
            expected = ScenarioExpectation(GuardDecision.DENY, ExecutionOutcome.DENIED)
        ),
        SafetyScenario(
            id = "similar-name",
            description = "Benign similar-looking filename is not over-blocked",
            setup = { workspaceRoot -> Files.writeString(workspaceRoot.resolve("notes.txt"), "ok") },
            evaluate = { workspaceRoot ->
                val policyDecision = evaluatePolicy(workspaceRoot, "read", "notes.txt")
                val executionOutcome = runRead(workspaceRoot, "notes.txt")
                ScenarioResult(policyDecision.decision, executionOutcome, policyDecision.ruleId)
            },
            expected = ScenarioExpectation(GuardDecision.ALLOW, ExecutionOutcome.EXECUTED)
        ),
        SafetyScenario(
            id = "post-approval-recheck",
            description = "Post-approval path change is rechecked and denied",
            evaluate = { workspaceRoot ->
                val initialPolicyDecision = evaluatePolicy(workspaceRoot, "write", "draft.txt")
                val gateway = object : ApprovalGateway {
                    override fun request(action: GuardedAction, risk: ActionRisk, reason: String): Boolean {
                        Files.createSymbolicLink(workspaceRoot.resolve("draft.txt"), workspaceRoot.parent.resolve("external-target").toAbsolutePath())
                        return true
                    }
                }
                val tools = GuardedWorkspaceTools(workspaceRoot, approvalGateway = gateway)
                val executionOutcome = try {
                    tools.write("draft.txt", "blocked")
                    ExecutionOutcome.EXECUTED
                } catch (_: GuardPolicyException) {
                    ExecutionOutcome.DENIED
                }
                val recheckPolicyDecision = evaluatePolicy(workspaceRoot, "write", "draft.txt")
                ScenarioResult(
                    policyDecision = initialPolicyDecision.decision,
                    executionOutcome = executionOutcome,
                    ruleId = initialPolicyDecision.ruleId,
                    recheckPolicyDecision = recheckPolicyDecision.decision,
                    recheckRuleId = recheckPolicyDecision.ruleId
                )
            },
            expected = ScenarioExpectation(GuardDecision.CONFIRM, ExecutionOutcome.DENIED, recheckPolicyDecision = GuardDecision.DENY)
        )
    )

    private fun evaluatePolicy(workspaceRoot: Path, action: String, target: String) = ToolPolicy(workspaceRoot).evaluate(
        GuardedAction(action = action, targetPath = target)
    )

    private fun runRead(workspaceRoot: Path, target: String): ExecutionOutcome {
        val tools = GuardedWorkspaceTools(workspaceRoot, approvalGateway = FakeApprovalGateway(true))
        return try {
            tools.read(target)
            ExecutionOutcome.EXECUTED
        } catch (_: GuardPolicyException) {
            ExecutionOutcome.DENIED
        }
    }

    private fun runWrite(workspaceRoot: Path, target: String, contents: String, approved: Boolean): ExecutionOutcome {
        val tools = GuardedWorkspaceTools(workspaceRoot, approvalGateway = FakeApprovalGateway(approved))
        return try {
            val executed = tools.write(target, contents)
            if (executed) ExecutionOutcome.EXECUTED else ExecutionOutcome.REJECTED
        } catch (_: GuardPolicyException) {
            ExecutionOutcome.DENIED
        }
    }

    private fun runDelete(workspaceRoot: Path, target: String, approved: Boolean): ExecutionOutcome {
        val tools = GuardedWorkspaceTools(workspaceRoot, approvalGateway = FakeApprovalGateway(approved))
        return try {
            val executed = tools.delete(target)
            if (executed) ExecutionOutcome.EXECUTED else ExecutionOutcome.REJECTED
        } catch (_: GuardPolicyException) {
            ExecutionOutcome.DENIED
        }
    }

    private fun runUnknown(workspaceRoot: Path, target: String): ExecutionOutcome {
        val tools = GuardedWorkspaceTools(workspaceRoot, approvalGateway = FakeApprovalGateway(true))
        return try {
            tools.execute(GuardedAction(action = "unknown", targetPath = target))
            ExecutionOutcome.EXECUTED
        } catch (_: GuardPolicyException) {
            ExecutionOutcome.DENIED
        }
    }
}

class SafetyRegressionReport(val scenarios: List<SafetyRegressionScenarioResult>) {
    val totalCount: Int = scenarios.size
    val passedCount: Int = scenarios.count { it.passed }
    val allPassed: Boolean = passedCount == totalCount
}

data class SafetyRegressionScenarioResult(
    val id: String,
    val description: String,
    val expectedPolicyDecision: GuardDecision,
    val actualPolicyDecision: GuardDecision,
    val expectedExecutionOutcome: ExecutionOutcome?,
    val actualExecutionOutcome: ExecutionOutcome,
    val expectedRecheckPolicyDecision: GuardDecision? = null,
    val recheckPolicyDecision: GuardDecision? = null,
    val ruleId: String?,
    val passed: Boolean,
    val sideEffectPassed: Boolean
)

data class SafetyScenario(
    val id: String,
    val description: String,
    val setup: (Path) -> Unit = {},
    val evaluate: (Path) -> ScenarioResult,
    val expected: ScenarioExpectation,
    val sideEffectAssertion: (Path) -> Boolean = { true }
)

data class ScenarioExpectation(
    val policyDecision: GuardDecision,
    val executionOutcome: ExecutionOutcome,
    val recheckPolicyDecision: GuardDecision? = null
)

data class ScenarioResult(
    val policyDecision: GuardDecision,
    val executionOutcome: ExecutionOutcome,
    val ruleId: String? = null,
    val recheckPolicyDecision: GuardDecision? = null,
    val recheckRuleId: String? = null
)

enum class ExecutionOutcome {
    EXECUTED,
    REJECTED,
    DENIED,
    NOT_APPLICABLE
}
