package dev.karan.koog.safeagent

import java.nio.file.Files

fun main() {
    val workspace = Files.createTempDirectory("safe-tool-agent-demo")

    Files.writeString(
        workspace.resolve("notes.txt"),
        "This file is inside the permitted workspace."
    )

    val policy = WorkspacePolicy(workspace)
    val tools = WorkspaceTools(policy)

    println("Workspace: $workspace")
    println()
    println("Files:")
    tools.listFiles().forEach { println("- $it") }

    println()
    println("Reading notes.txt:")
    println(tools.readFile("notes.txt"))

    println()
    println("Attempting an unsafe read:")
    try {
        tools.readFile("../secret.txt")
    } catch (error: PolicyViolation) {
        println("Blocked: ${error.message}")
    }

    println()
    println("Attempting deletion without confirmation:")
    try {
        tools.delete("notes.txt", confirmed = false)
    } catch (error: PolicyViolation) {
        println("Blocked: ${error.message}")
    }

    println()
    println("Deleting with confirmation:")
    println("Deleted: ${tools.delete("notes.txt", confirmed = true)}")
}
