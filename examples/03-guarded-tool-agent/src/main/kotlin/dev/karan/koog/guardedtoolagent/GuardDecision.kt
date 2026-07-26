package dev.karan.koog.guardedtoolagent

enum class GuardDecision {
    ALLOW,
    CONFIRM,
    DENY
}

data class GuardDecisionResult(
    val decision: GuardDecision,
    val reason: String
)
