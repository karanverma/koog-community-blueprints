package dev.karan.koog.capabilitydiff

import dev.karan.koog.guardedtoolagent.GuardDecision
import dev.karan.koog.guardedtoolagent.GuardedAction
import dev.karan.koog.guardedtoolagent.HiddenPathRule
import dev.karan.koog.guardedtoolagent.PolicyContext
import dev.karan.koog.guardedtoolagent.PolicyEvaluation
import dev.karan.koog.guardedtoolagent.PolicyRule
import dev.karan.koog.guardedtoolagent.RuntimePolicyEngine
import dev.karan.koog.guardedtoolagent.SensitiveFileRule
import dev.karan.koog.guardedtoolagent.SymbolicLinkEscapeRule
import dev.karan.koog.guardedtoolagent.WorkspaceBoundaryRule
import java.nio.file.Files
import java.nio.file.Path

enum class CapabilityChange {
    EXPANSION,
    TIGHTENING,
    UNCHANGED
}

fun classifyChange(baseline: GuardDecision, candidate: GuardDecision): CapabilityChange {
    return when {
        baseline == candidate -> CapabilityChange.UNCHANGED
        baseline == GuardDecision.DENY || candidate == GuardDecision.ALLOW -> CapabilityChange.EXPANSION
        else -> CapabilityChange.TIGHTENING
    }
}

data class CapabilityDiffScenario(
    val id: String,
    val action: GuardedAction,
    val baseline: PolicyProfile,
    val candidate: PolicyProfile,
    val expectedChange: CapabilityChange? = null,
    val description: String = id
)

data class CapabilityDiffResult(
    val scenario: CapabilityDiffScenario,
    val baseline: PolicyEvaluation,
    val candidate: PolicyEvaluation,
    val change: CapabilityChange,
    val expectationMatched: Boolean
)

data class CapabilityDiffReport(val results: List<CapabilityDiffResult>) {
    val totalScenarios: Int get() = results.size
    val expansionCount: Int get() = results.count { it.change == CapabilityChange.EXPANSION }
    val tighteningCount: Int get() = results.count { it.change == CapabilityChange.TIGHTENING }
    val unchangedCount: Int get() = results.count { it.change == CapabilityChange.UNCHANGED }
    val hasExpansions: Boolean get() = expansionCount > 0
    val allExpectationsMatched: Boolean get() = results.all { it.expectationMatched }
}

class CapabilityDiffRunner {
    fun run(scenarios: List<CapabilityDiffScenario>): CapabilityDiffReport {
        val workspaceRoot = Files.createTempDirectory("capability-diff")
        val results = scenarios.map { scenario ->
            val context = PolicyContext(
                action = scenario.action,
                workspaceRoot = workspaceRoot,
                candidatePath = workspaceRoot.resolve(scenario.action.targetPath).normalize()
            )
            val baseline = scenario.baseline.engine(workspaceRoot).evaluate(context)
            val candidate = scenario.candidate.engine(workspaceRoot).evaluate(context)
            val change = classifyChange(baseline.decision, candidate.decision)
            CapabilityDiffResult(scenario, baseline, candidate, change, scenario.expectedChange == null || scenario.expectedChange == change)
        }
        return CapabilityDiffReport(results)
    }
}

data class PolicyProfile(
    val name: String,
    private val protectSensitivePaths: Boolean = true,
    private val actionDecisions: Map<String, GuardDecision> = defaultActionDecisions()
) {
    fun engine(workspaceRoot: Path): RuntimePolicyEngine {
        val rules = buildList {
            add(WorkspaceBoundaryRule(workspaceRoot))
            add(HiddenPathRule())
            if (protectSensitivePaths) {
                add(SensitiveFileRule(setOf(".env", "credentials.json", "secrets.txt", "id_rsa")))
            }
            add(SymbolicLinkEscapeRule(workspaceRoot))
            add(ConfiguredActionRule(actionDecisions))
        }
        return RuntimePolicyEngine(rules)
    }
}

fun workspacePolicy(
    name: String,
    protectSensitivePaths: Boolean = true,
    actionDecisions: Map<String, GuardDecision> = defaultActionDecisions()
) = PolicyProfile(name, protectSensitivePaths, actionDecisions)

private fun defaultActionDecisions() = mapOf(
    "list" to GuardDecision.ALLOW,
    "read" to GuardDecision.ALLOW,
    "write" to GuardDecision.CONFIRM,
    "delete" to GuardDecision.CONFIRM
)

private class ConfiguredActionRule(private val decisions: Map<String, GuardDecision>) : PolicyRule {
    override val id = "action-policy"
    override val description = "Apply configured action decisions"

    override fun evaluate(context: PolicyContext): PolicyEvaluation {
        val decision = decisions[context.action.action.lowercase()] ?: GuardDecision.DENY
        val reason = when (decision) {
            GuardDecision.ALLOW -> "Configured workspace operation is permitted"
            GuardDecision.CONFIRM -> "Configured workspace operation requires approval"
            GuardDecision.DENY -> "Configured workspace operation is denied"
        }
        return PolicyEvaluation(decision, reason, id)
    }
}