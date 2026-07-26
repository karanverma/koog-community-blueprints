package dev.karan.koog.guardedtoolagent

import java.nio.file.Files

fun main() {
    val workspace = Files.createTempDirectory("guarded-tool-agent-demo")
    Files.writeString(workspace.resolve("notes.txt"), "This file is inside the permitted workspace.")
    Files.writeString(workspace.resolve("safe.md"), "A safe markdown note.")

    val auditSink = InMemoryAuditSink()
    val tools = GuardedWorkspaceTools(
        workspaceRoot = workspace,
        approvalGateway = ConsoleApprovalGateway(),
        auditSink = auditSink
    )

    println("Workspace: $workspace")
    println()

    println("1) Allowed operation")
    println(tools.read("notes.txt"))
    println()

    println("2) Confirmation required")
    println("Write result: ${tools.write("draft.txt", "awaiting approval")}")
    println()

    println("3) Denied operation")
    try {
        tools.read("../secret.txt")
    } catch (error: GuardPolicyException) {
        println("Blocked: ${error.message}")
    }
    println()

    println("4) Audit trail")
    auditSink.events.forEach { event ->
        println("- ${event.action} -> ${event.policyDecision} / ${event.executionOutcome} / ${event.reason}")
    }
}
