package dev.karan.koog.guardedtoolagent

enum class GuardDecision {
    ALLOW,
    CONFIRM,
    DENY
}

data class GuardDecisionResult(
    val decision: GuardDecision,
    val reason: String,
    val ruleId: String? = null,
    val matchedRuleIds: List<String> = emptyList(),
    val requiresConfirmation: Boolean = decision == GuardDecision.CONFIRM
)
