package dev.karan.koog.safeagent

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkspacePolicyTest {

    @Test
    fun `allows normal files inside workspace`() {
        val root = Files.createTempDirectory("safe-agent")
        val policy = WorkspacePolicy(root)

        val result = policy.authorizeRead("documents/report.txt")

        assertEquals(
            root.resolve("documents/report.txt").toAbsolutePath().normalize(),
            result
        )
    }

    @Test
    fun `blocks path traversal`() {
        val root = Files.createTempDirectory("safe-agent")
        val policy = WorkspacePolicy(root)

        assertFailsWith<PolicyViolation> {
            policy.authorizeRead("../secret.txt")
        }
    }

    @Test
    fun `blocks absolute paths`() {
        val root = Files.createTempDirectory("safe-agent")
        val policy = WorkspacePolicy(root)

        assertFailsWith<PolicyViolation> {
            policy.authorizeRead("/etc/passwd")
        }
    }

    @Test
    fun `blocks hidden files`() {
        val root = Files.createTempDirectory("safe-agent")
        val policy = WorkspacePolicy(root)

        assertFailsWith<PolicyViolation> {
            policy.authorizeRead(".env")
        }
    }

    @Test
    fun `requires confirmation before deletion`() {
        val root = Files.createTempDirectory("safe-agent")
        val policy = WorkspacePolicy(root)

        val error = assertFailsWith<PolicyViolation> {
            policy.authorizeDelete("report.txt", confirmed = false)
        }

        assertTrue(error.message!!.contains("confirmation"))
    }

    @Test
    fun `allows confirmed deletion inside workspace`() {
        val root = Files.createTempDirectory("safe-agent")
        val policy = WorkspacePolicy(root)

        val result = policy.authorizeDelete(
            "report.txt",
            confirmed = true
        )

        assertEquals(
            root.resolve("report.txt").toAbsolutePath().normalize(),
            result
        )
    }
}
