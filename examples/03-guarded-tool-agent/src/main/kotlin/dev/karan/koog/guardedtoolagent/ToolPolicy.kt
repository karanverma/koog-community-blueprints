package dev.karan.koog.guardedtoolagent

import java.nio.file.Files
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

    private val engine = RuntimePolicyEngine(
        listOf(
            WorkspaceBoundaryRule(workspaceRoot),
            HiddenPathRule(),
            SensitiveFileRule(blockedNames),
            SymbolicLinkEscapeRule(workspaceRoot),
            ActionRule()
        )
    )

    init {
        Files.createDirectories(workspaceRoot)
    }

    fun evaluate(action: GuardedAction): GuardDecisionResult {
        val normalized = action.targetPath.trim()
        if (normalized.isBlank()) {
            return GuardDecisionResult(GuardDecision.DENY, "The target path must not be blank")
        }

        val suppliedPath = try {
            Path.of(normalized)
        } catch (_: Exception) {
            return GuardDecisionResult(GuardDecision.DENY, "The target path is invalid")
        }

        val candidate = workspaceRoot.resolve(suppliedPath).normalize()
        val evaluation = engine.evaluate(
            PolicyContext(
                action = action,
                workspaceRoot = workspaceRoot,
                candidatePath = candidate
            )
        )

        return GuardDecisionResult(
            decision = evaluation.decision,
            reason = evaluation.reason,
            ruleId = evaluation.ruleId,
            matchedRuleIds = evaluation.matchedRuleIds,
            requiresConfirmation = evaluation.requiresConfirmation
        )
    }

    fun resolvePath(targetPath: String): Path {
        val decision = evaluate(GuardedAction(action = "read", targetPath = targetPath))
        if (decision.decision != GuardDecision.ALLOW) {
            throw GuardPolicyException(decision.reason)
        }
        return workspaceRoot.resolve(targetPath).normalize()
    }
}
