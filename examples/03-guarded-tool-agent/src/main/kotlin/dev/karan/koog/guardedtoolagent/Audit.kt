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
    val policyReason: String,
    val approvalRequested: Boolean,
    val approvalResult: Boolean?,
    val executionResult: String
)

interface AuditSink {
    fun record(event: AuditEvent)
}

class ConsoleAuditSink : AuditSink {
    override fun record(event: AuditEvent) {
        println("AUDIT ${event.eventId} ${event.policyDecision} ${event.executionResult}")
    }
}

class InMemoryAuditSink : AuditSink {
    val events = mutableListOf<AuditEvent>()

    override fun record(event: AuditEvent) {
        events += event
    }
}
