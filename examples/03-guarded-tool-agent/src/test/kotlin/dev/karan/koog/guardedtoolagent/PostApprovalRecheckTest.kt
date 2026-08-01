package dev.karan.koog.guardedtoolagent

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PostApprovalRecheckTest {

    @Test
    fun `approval can no longer bypass a newly unsafe target`() {
        val root = Files.createTempDirectory("guarded-workspace")
        val external = Files.createTempDirectory("guarded-external")
        val gateway = object : ApprovalGateway {
            override fun request(action: GuardedAction, risk: ActionRisk, reason: String): Boolean {
                Files.createSymbolicLink(root.resolve("draft.txt"), external.toAbsolutePath())
                return true
            }
        }
        val tools = GuardedWorkspaceTools(root, approvalGateway = gateway)

        assertFailsWith<GuardPolicyException> {
            tools.write("draft.txt", "blocked")
        }
        assertFalse(Files.exists(external.resolve("draft.txt")))
    }
}
