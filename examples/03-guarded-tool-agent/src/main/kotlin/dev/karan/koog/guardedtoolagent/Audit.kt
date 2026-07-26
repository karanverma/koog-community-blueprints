package dev.karan.koog.guardedtoolagent

import java.time.Instant
import java.util.UUID

data class AuditEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val timestamp: String = Instant.now().toString(),
    val action: String,
    val target: String,
    val risk: ActionRisk,
    val policyDecision: GuardDecision,
    val reason: String,
    val matchedRuleIds: List<String>,
    val approvalRequested: Boolean,
    val approvalGranted: Boolean?,
    val executionOutcome: String,
    val executionResult: String = executionOutcome,
    val policyReason: String = reason
)

interface AuditSink {
    fun record(event: AuditEvent)
}

class ConsoleAuditSink : AuditSink {
    override fun record(event: AuditEvent) {
        println("AUDIT ${event.eventId} ${event.policyDecision} ${event.executionOutcome}")
    }
}

class InMemoryAuditSink : AuditSink {
    val events = mutableListOf<AuditEvent>()

    override fun record(event: AuditEvent) {
        events += event
    }
}
