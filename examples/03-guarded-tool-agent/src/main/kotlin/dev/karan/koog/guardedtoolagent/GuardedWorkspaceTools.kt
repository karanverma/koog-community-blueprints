package dev.karan.koog.guardedtoolagent

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class GuardedWorkspaceTools(
    private val workspaceRoot: Path,
    private val approvalGateway: ApprovalGateway,
    private val auditSink: AuditSink? = null
) {
    private val policy = ToolPolicy(workspaceRoot)

    fun list(target: String = "."): List<String> {
        val action = GuardedAction(action = "list", targetPath = target, operationCategory = "read")
        return executeWithGuard(action) { resolved ->
            if (!Files.isDirectory(resolved)) {
                throw GuardPolicyException("Path is not a directory")
            }
            Files.list(resolved).use { stream ->
                stream.iterator().asSequence()
                    .map { policy.workspaceRoot.relativize(it).toString() }
                    .sorted()
                    .toList()
            }
        }
    }

    fun read(target: String): String {
        val action = GuardedAction(action = "read", targetPath = target, operationCategory = "read")
        return executeWithGuard(action) { resolved ->
            if (!Files.isRegularFile(resolved)) {
                throw GuardPolicyException("Path is not a regular file")
            }
            Files.readString(resolved)
        }
    }

    fun write(target: String, contents: String): Boolean {
        val action = GuardedAction(action = "write", targetPath = target, operationCategory = "write", destructive = false)
        return executeWithGuard(action) { resolved ->
            Files.writeString(resolved, contents)
            true
        }
    }

    fun delete(target: String): Boolean {
        val action = GuardedAction(action = "delete", targetPath = target, operationCategory = "delete", destructive = true)
        return executeWithGuard(action) { resolved ->
            if (resolved == policy.workspaceRoot) {
                throw GuardPolicyException("Workspace root cannot be deleted")
            }
            Files.deleteIfExists(resolved)
        }
    }

    fun execute(action: GuardedAction): Any? {
        return executeWithGuard(action) { resolved ->
            when (action.action.lowercase()) {
                "read" -> Files.readString(resolved)
                "write" -> {
                    Files.writeString(resolved, action.metadata["content"] ?: "")
                    true
                }
                "delete" -> {
                    Files.deleteIfExists(resolved)
                }
                else -> throw GuardPolicyException("Unsupported operation")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> executeWithGuard(action: GuardedAction, operation: (Path) -> T): T {
        val decision = policy.evaluate(action)
        val risk = resolveRisk(action, decision)
        val approvalRequested = decision.decision == GuardDecision.CONFIRM
        val approvalResult = if (approvalRequested) {
            approvalGateway.request(action, risk, decision.reason)
        } else {
            null
        }

        if (decision.decision == GuardDecision.DENY) {
            recordAudit(event = AuditEvent(
                action = action.action,
                target = action.targetPath,
                risk = risk,
                policyDecision = decision.decision,
                reason = decision.reason,
                matchedRuleIds = decision.matchedRuleIds,
                approvalRequested = false,
                approvalGranted = null,
                executionOutcome = "denied"
            ))
            throw GuardPolicyException(decision.reason)
        }

        if (approvalRequested && approvalResult != true) {
            recordAudit(event = AuditEvent(
                action = action.action,
                target = action.targetPath,
                risk = risk,
                policyDecision = decision.decision,
                reason = decision.reason,
                matchedRuleIds = decision.matchedRuleIds,
                approvalRequested = true,
                approvalGranted = false,
                executionOutcome = "rejected"
            ))
            return false as T
        }

        val resolved = resolvePath(action.targetPath)
        val result = operation(resolved)
        recordAudit(event = AuditEvent(
            action = action.action,
            target = action.targetPath,
            risk = risk,
            policyDecision = decision.decision,
            reason = decision.reason,
            matchedRuleIds = decision.matchedRuleIds,
            approvalRequested = approvalRequested,
            approvalGranted = approvalResult,
            executionOutcome = "executed"
        ))
        return result
    }

    private fun resolveRisk(action: GuardedAction, decision: GuardDecisionResult): ActionRisk {
        return if (decision.decision == GuardDecision.DENY) {
            ActionRisk.FORBIDDEN
        } else if (action.destructive) {
            ActionRisk.HIGH
        } else if (action.action.lowercase() == "write") {
            ActionRisk.MEDIUM
        } else {
            ActionRisk.LOW
        }
    }

    private fun resolvePath(targetPath: String): Path {
        val normalized = targetPath.trim()
        val suppliedPath = Paths.get(normalized)
        val resolved = policy.workspaceRoot.resolve(suppliedPath).normalize()
        if (!resolved.startsWith(policy.workspaceRoot)) {
            throw GuardPolicyException("Path escapes the permitted workspace")
        }
        return resolved
    }

    private fun recordAudit(event: AuditEvent) {
        auditSink?.record(event)
    }
}
