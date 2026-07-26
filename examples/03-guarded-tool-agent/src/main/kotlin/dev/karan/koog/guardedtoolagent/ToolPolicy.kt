package dev.karan.koog.guardedtoolagent

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

class GuardPolicyException(message: String) : IllegalArgumentException(message)

class ToolPolicy(private val root: Path) {

    val workspaceRoot: Path = root.toAbsolutePath().normalize()

    private val blockedNames = setOf(
        ".env",
        ".git",
        ".ssh",
        "id_rsa",
        "id_ed25519",
        "credentials",
        "credentials.json",
        "secrets.txt"
    )

    init {
        Files.createDirectories(workspaceRoot)
    }

    fun evaluate(action: GuardedAction): GuardDecisionResult {
        val normalized = action.targetPath.trim()
        if (normalized.isBlank()) {
            return GuardDecisionResult(GuardDecision.DENY, "The target path must not be blank")
        }

        val suppliedPath = Path.of(normalized)
        if (suppliedPath.isAbsolute) {
            return GuardDecisionResult(GuardDecision.DENY, "Absolute paths are not allowed")
        }

        val candidate = workspaceRoot.resolve(suppliedPath).normalize()
        if (!candidate.startsWith(workspaceRoot)) {
            return GuardDecisionResult(GuardDecision.DENY, "Path escapes the permitted workspace")
        }

        val segments = workspaceRoot.relativize(candidate)
        segments.forEach { segment ->
            val name = segment.toString()
            if (name.startsWith(".")) {
                return GuardDecisionResult(GuardDecision.DENY, "Hidden files and directories are blocked")
            }
            if (name.lowercase() in blockedNames) {
                return GuardDecisionResult(GuardDecision.DENY, "Sensitive filename is blocked")
            }
        }

        rejectSymbolicLinkEscape(candidate)

        return when (action.action.lowercase()) {
            "list" -> GuardDecisionResult(GuardDecision.ALLOW, "Listing workspace files is permitted")
            "read" -> GuardDecisionResult(GuardDecision.ALLOW, "Reading regular workspace files is permitted")
            "write" -> GuardDecisionResult(GuardDecision.CONFIRM, "Writing a new file requires approval")
            "delete" -> GuardDecisionResult(GuardDecision.CONFIRM, "Deleting a workspace file requires approval")
            else -> GuardDecisionResult(GuardDecision.DENY, "Unknown operation is denied")
        }
    }

    fun resolvePath(targetPath: String): Path {
        val decision = evaluate(GuardedAction(action = "read", targetPath = targetPath))
        if (decision.decision != GuardDecision.ALLOW) {
            throw GuardPolicyException(decision.reason)
        }
        return workspaceRoot.resolve(targetPath).normalize()
    }

    private fun rejectSymbolicLinkEscape(candidate: Path) {
        var current = workspaceRoot
        for (segment in workspaceRoot.relativize(candidate)) {
            current = current.resolve(segment)
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                val target = current.toRealPath()
                if (!target.startsWith(workspaceRoot.toRealPath())) {
                    throw GuardPolicyException("Symbolic link escapes the permitted workspace")
                }
            }
        }
    }
}
