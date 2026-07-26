package dev.karan.koog.guardedtoolagent

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GuardedWorkspaceToolsTest {

    @Test
    fun `safe list is allowed`() {
        val root = Files.createTempDirectory("guarded-workspace")
        Files.writeString(root.resolve("notes.txt"), "hello")
        Files.writeString(root.resolve("readme.md"), "docs")

        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(true))

        assertEquals(listOf("notes.txt", "readme.md"), tools.list("."))
    }

    @Test
    fun `safe read is allowed`() {
        val root = Files.createTempDirectory("guarded-workspace")
        Files.writeString(root.resolve("notes.txt"), "safe content")

        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(true))

        assertEquals("safe content", tools.read("notes.txt"))
    }

    @Test
    fun `write requires confirmation`() {
        val root = Files.createTempDirectory("guarded-workspace")
        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(false))

        assertFalse(tools.write("draft.txt", "draft"))
        assertFalse(Files.exists(root.resolve("draft.txt")))
    }

    @Test
    fun `delete requires confirmation`() {
        val root = Files.createTempDirectory("guarded-workspace")
        Files.writeString(root.resolve("temporary.txt"), "temporary")
        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(false))

        assertFalse(tools.delete("temporary.txt"))
        assertTrue(Files.exists(root.resolve("temporary.txt")))
    }

    @Test
    fun `approved write creates the file`() {
        val root = Files.createTempDirectory("guarded-workspace")
        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(true))

        assertTrue(tools.write("draft.txt", "approved"))
        assertEquals("approved", Files.readString(root.resolve("draft.txt")))
    }

    @Test
    fun `rejected write does not create the file`() {
        val root = Files.createTempDirectory("guarded-workspace")
        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(false))

        assertFalse(tools.write("draft.txt", "blocked"))
        assertFalse(Files.exists(root.resolve("draft.txt")))
    }

    @Test
    fun `approved delete removes the file`() {
        val root = Files.createTempDirectory("guarded-workspace")
        Files.writeString(root.resolve("temporary.txt"), "temporary")
        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(true))

        assertTrue(tools.delete("temporary.txt"))
        assertFalse(Files.exists(root.resolve("temporary.txt")))
    }

    @Test
    fun `rejected delete keeps the file`() {
        val root = Files.createTempDirectory("guarded-workspace")
        Files.writeString(root.resolve("temporary.txt"), "temporary")
        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(false))

        assertFalse(tools.delete("temporary.txt"))
        assertTrue(Files.exists(root.resolve("temporary.txt")))
    }

    @Test
    fun `traversal denial is enforced`() {
        val root = Files.createTempDirectory("guarded-workspace")
        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(true))

        assertFailsWith<GuardPolicyException> {
            tools.read("../outside.txt")
        }
    }

    @Test
    fun `external path denial is enforced`() {
        val root = Files.createTempDirectory("guarded-workspace")
        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(true))

        assertFailsWith<GuardPolicyException> {
            tools.read("/etc/passwd")
        }
    }

    @Test
    fun `hidden file denial is enforced`() {
        val root = Files.createTempDirectory("guarded-workspace")
        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(true))

        assertFailsWith<GuardPolicyException> {
            tools.read(".env")
        }
    }

    @Test
    fun `hidden directory denial is enforced`() {
        val root = Files.createTempDirectory("guarded-workspace")
        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(true))

        assertFailsWith<GuardPolicyException> {
            tools.read("docs/.private/readme.txt")
        }
    }

    @Test
    fun `env file denial is enforced`() {
        val root = Files.createTempDirectory("guarded-workspace")
        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(true))

        assertFailsWith<GuardPolicyException> {
            tools.read(".env")
        }
    }

    @Test
    fun `secret filename denial is enforced`() {
        val root = Files.createTempDirectory("guarded-workspace")
        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(true))

        assertFailsWith<GuardPolicyException> {
            tools.read("credentials.json")
        }
    }

    @Test
    fun `symlink escape denial is enforced`() {
        val root = Files.createTempDirectory("guarded-workspace")
        val external = Files.createTempDirectory("guarded-external")
        val link = root.resolve("linked")
        Files.createSymbolicLink(link, external.toAbsolutePath())

        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(true))

        assertFailsWith<GuardPolicyException> {
            tools.read("linked")
        }
    }

    @Test
    fun `unknown operation denial is enforced`() {
        val root = Files.createTempDirectory("guarded-workspace")
        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(true))

        assertFailsWith<GuardPolicyException> {
            tools.execute(GuardedAction(action = "unknown", targetPath = "notes.txt"))
        }
    }

    @Test
    fun `approval cannot override deny`() {
        val root = Files.createTempDirectory("guarded-workspace")
        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(true))

        assertFailsWith<GuardPolicyException> {
            tools.read(".env")
        }
    }

    @Test
    fun `denied operations do not execute the underlying filesystem operation`() {
        val root = Files.createTempDirectory("guarded-workspace")
        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(true))

        assertFailsWith<GuardPolicyException> {
            tools.write(".env", "blocked")
        }
        assertFalse(Files.exists(root.resolve(".env")))
    }

    @Test
    fun `approved operations execute exactly once`() {
        val root = Files.createTempDirectory("guarded-workspace")
        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(true))

        assertTrue(tools.write("notes.txt", "once"))
        assertEquals("once", Files.readString(root.resolve("notes.txt")))
    }

    @Test
    fun `audit event is generated`() {
        val root = Files.createTempDirectory("guarded-workspace")
        Files.writeString(root.resolve("notes.txt"), "safe")
        val sink = InMemoryAuditSink()
        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(true), auditSink = sink)

        tools.read("notes.txt")

        assertTrue(sink.events.isNotEmpty())
    }

    @Test
    fun `audit does not contain secrets`() {
        val root = Files.createTempDirectory("guarded-workspace")
        Files.writeString(root.resolve("notes.txt"), "safe")
        val sink = InMemoryAuditSink()
        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(true), auditSink = sink)

        tools.read("notes.txt")

        val serialized = sink.events.joinToString(separator = "\n")
        assertFalse(serialized.contains("secret", ignoreCase = true))
        assertFalse(serialized.contains("api_key", ignoreCase = true))
    }
}
