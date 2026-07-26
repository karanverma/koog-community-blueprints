package dev.karan.koog.guardedtoolagent

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths

interface PolicyRule {
    val id: String
    val description: String
    fun evaluate(context: PolicyContext): PolicyEvaluation?
}

data class PolicyContext(
    val action: GuardedAction,
    val workspaceRoot: Path? = null,
    val candidatePath: Path? = null
)

data class PolicyEvaluation(
    val decision: GuardDecision,
    val reason: String,
    val ruleId: String,
    val matchedRuleIds: List<String> = listOf(ruleId),
    val requiresConfirmation: Boolean = decision == GuardDecision.CONFIRM,
    val isDefault: Boolean = false
)

class RuntimePolicyEngine(private val rules: List<PolicyRule>) {
    fun evaluate(context: PolicyContext): PolicyEvaluation {
        val evaluations = rules.mapNotNull { it.evaluate(context) }
        if (evaluations.isEmpty()) {
            return PolicyEvaluation(
                decision = GuardDecision.DENY,
                reason = "No policy rule matched; default deny",
                ruleId = "default-deny",
                matchedRuleIds = listOf("default-deny"),
                requiresConfirmation = false,
                isDefault = true
            )
        }

        val selected = evaluations
            .sortedWith(compareByDescending<PolicyEvaluation> { precedence(it.decision) }.thenBy { it.ruleId })
            .first()

        return selected.copy(
            matchedRuleIds = evaluations.map { it.ruleId }.distinct(),
            requiresConfirmation = selected.decision == GuardDecision.CONFIRM
        )
    }

    private fun precedence(decision: GuardDecision): Int = when (decision) {
        GuardDecision.DENY -> 3
        GuardDecision.CONFIRM -> 2
        GuardDecision.ALLOW -> 1
    }
}

class WorkspaceBoundaryRule(private val workspaceRoot: Path) : PolicyRule {
    override val id = "workspace-boundary"
    override val description = "Enforce workspace boundary"

    override fun evaluate(context: PolicyContext): PolicyEvaluation? {
        val target = context.action.targetPath.trim()
        if (target.isBlank()) {
            return PolicyEvaluation(GuardDecision.DENY, "The target path must not be blank", id)
        }

        val suppliedPath = try {
            Path.of(target)
        } catch (_: InvalidPathException) {
            return PolicyEvaluation(GuardDecision.DENY, "The target path is invalid", id)
        }

        if (suppliedPath.isAbsolute) {
            return PolicyEvaluation(GuardDecision.DENY, "Absolute paths are not allowed", id)
        }

        val candidatePath = workspaceRoot.resolve(suppliedPath).normalize()
        return if (!candidatePath.startsWith(workspaceRoot)) {
            PolicyEvaluation(GuardDecision.DENY, "Path escapes the permitted workspace", id)
        } else {
            null
        }
    }
}

class HiddenPathRule : PolicyRule {
    override val id = "hidden-path"
    override val description = "Block hidden paths"

    override fun evaluate(context: PolicyContext): PolicyEvaluation? {
        val candidate = context.candidatePath ?: return null
        val segmentNames = candidate.toString().split(Paths.get("/").toString()).filter { it.isNotBlank() }
        return if (segmentNames.any { it.startsWith(".") }) {
            PolicyEvaluation(GuardDecision.DENY, "Hidden files and directories are blocked", id)
        } else {
            null
        }
    }
}

class SensitiveFileRule(private val blockedNames: Set<String>) : PolicyRule {
    override val id = "sensitive-file"
    override val description = "Block sensitive file names"

    override fun evaluate(context: PolicyContext): PolicyEvaluation? {
        val candidate = context.candidatePath ?: return null
        val workspaceRoot = context.workspaceRoot ?: return null

        val relativeSegments = try {
            workspaceRoot.relativize(candidate).map { it.toString() }
        } catch (_: IllegalArgumentException) {
            return null
        }

        return if (relativeSegments.any { it.lowercase() in blockedNames }) {
            PolicyEvaluation(GuardDecision.DENY, "Sensitive filename is blocked", id)
        } else {
            null
        }
    }
}

class SymbolicLinkEscapeRule(private val workspaceRoot: Path) : PolicyRule {
    override val id = "symlink-escape"
    override val description = "Prevent symlink escapes"

    override fun evaluate(context: PolicyContext): PolicyEvaluation? {
        val candidate = context.candidatePath ?: return null
        val realRoot = try {
            workspaceRoot.toRealPath()
        } catch (_: Exception) {
            return null
        }

        var current = workspaceRoot
        try {
            for (segment in workspaceRoot.relativize(candidate)) {
                current = current.resolve(segment)
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                    val target = current.toRealPath()
                    if (!target.startsWith(realRoot)) {
                        return PolicyEvaluation(GuardDecision.DENY, "Symbolic link escapes the permitted workspace", id)
                    }
                }
            }
        } catch (_: Exception) {
            return PolicyEvaluation(GuardDecision.DENY, "Symbolic link escapes the permitted workspace", id)
        }

        return null
    }
}

class ActionRule : PolicyRule {
    override val id = "action-policy"
    override val description = "Apply action-specific defaults"

    override fun evaluate(context: PolicyContext): PolicyEvaluation? {
        return when (context.action.action.lowercase()) {
            "list" -> PolicyEvaluation(GuardDecision.ALLOW, "Listing workspace files is permitted", id)
            "read" -> PolicyEvaluation(GuardDecision.ALLOW, "Reading regular workspace files is permitted", id)
            "write" -> PolicyEvaluation(GuardDecision.CONFIRM, "Writing a new file requires approval", id)
            "delete" -> PolicyEvaluation(GuardDecision.CONFIRM, "Deleting a workspace file requires approval", id)
            else -> PolicyEvaluation(GuardDecision.DENY, "Unknown operation is denied", id)
        }
    }
}
