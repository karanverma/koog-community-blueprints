package dev.karan.koog.guardedtoolagent

import java.nio.file.Files

fun main() {
    val workspace = Files.createTempDirectory("guarded-tool-agent-demo")
    Files.writeString(workspace.resolve("notes.txt"), "This file is inside the permitted workspace.")
    Files.writeString(workspace.resolve("safe.md"), "A safe markdown note.")

    val tools = GuardedWorkspaceTools(
        workspaceRoot = workspace,
        approvalGateway = ConsoleApprovalGateway(),
        auditSink = ConsoleAuditSink()
    )

    println("Workspace: $workspace")
    println()

    println("1) List files")
    println(tools.list("."))
    println()

    println("2) Read safe file")
    println(tools.read("notes.txt"))
    println()

    println("3) Write requiring approval")
    try {
        println("Written: ${tools.write("draft.txt", "awaiting approval")}")
    } catch (error: GuardPolicyException) {
        println("Blocked: ${error.message}")
    }
    println()

    println("4) Delete requiring approval")
    try {
        println("Deleted: ${tools.delete("safe.md")}")
    } catch (error: GuardPolicyException) {
        println("Blocked: ${error.message}")
    }
    println()

    println("5) Traversal denial")
    try {
        tools.read("../secret.txt")
    } catch (error: GuardPolicyException) {
        println("Blocked: ${error.message}")
    }
    println()

    println("6) Hidden-file denial")
    try {
        tools.read(".env")
    } catch (error: GuardPolicyException) {
        println("Blocked: ${error.message}")
    }
    println()

    println("7) Sensitive-file denial")
    try {
        tools.read("credentials.json")
    } catch (error: GuardPolicyException) {
        println("Blocked: ${error.message}")
    }
}
