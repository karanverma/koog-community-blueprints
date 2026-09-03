package dev.karan.koog.capabilitydiff

import dev.karan.koog.guardedtoolagent.GuardDecision
import dev.karan.koog.guardedtoolagent.GuardedAction

fun referenceScenarios(): List<CapabilityDiffScenario> {
    val safe = workspacePolicy("safe")
    val relaxedSensitive = workspacePolicy("relaxed-sensitive", protectSensitivePaths = false)
    val approvalRemoved = workspacePolicy("approval-removed", actionDecisions = mapOf("write" to GuardDecision.ALLOW))
    val broaderRule = workspacePolicy("broader-rule", actionDecisions = mapOf("export" to GuardDecision.ALLOW))
    val tightenedDelete = workspacePolicy("tightened-delete", actionDecisions = mapOf("delete" to GuardDecision.CONFIRM))
    return listOf(
        CapabilityDiffScenario("read-sensitive-config", GuardedAction("read", "credentials.json"), safe, relaxedSensitive, CapabilityChange.EXPANSION, "Sensitive-path protection was removed"),
        CapabilityDiffScenario("operation-requiring-approval", GuardedAction("write", "draft.txt"), safe, approvalRemoved, CapabilityChange.EXPANSION, "Approval requirement was removed"),
        CapabilityDiffScenario("new-export-operation", GuardedAction("export", "report.txt"), safe, broaderRule, CapabilityChange.EXPANSION, "A candidate rule grants a previously denied operation"),
        CapabilityDiffScenario("delete-operation", GuardedAction("delete", "temp.txt"), workspacePolicy("baseline-delete", actionDecisions = mapOf("delete" to GuardDecision.ALLOW)), tightenedDelete, CapabilityChange.TIGHTENING),
        CapabilityDiffScenario("safe-read", GuardedAction("read", "notes.txt"), safe, safe, CapabilityChange.UNCHANGED),
        CapabilityDiffScenario("denied-unknown-operation", GuardedAction("unknown", "notes.txt"), safe, safe, CapabilityChange.UNCHANGED)
    )
}