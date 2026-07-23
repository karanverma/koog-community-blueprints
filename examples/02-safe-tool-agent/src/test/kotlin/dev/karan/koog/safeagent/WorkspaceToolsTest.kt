package dev.karan.koog.safeagent

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkspaceToolsTest {

    @Test
    fun `reads an authorized file`() {
        val root = Files.createTempDirectory("workspace-tools")
        Files.writeString(root.resolve("notes.txt"), "safe content")

        val tools = WorkspaceTools(WorkspacePolicy(root))

        assertEquals("safe content", tools.readFile("notes.txt"))
    }

    @Test
    fun `rejects reading a directory`() {
        val root = Files.createTempDirectory("workspace-tools")
        Files.createDirectory(root.resolve("docs"))

        val tools = WorkspaceTools(WorkspacePolicy(root))

        assertFailsWith<PolicyViolation> {
            tools.readFile("docs")
        }
    }

    @Test
    fun `lists authorized directory contents in sorted order`() {
        val root = Files.createTempDirectory("workspace-tools")
        Files.writeString(root.resolve("beta.txt"), "beta")
        Files.writeString(root.resolve("alpha.txt"), "alpha")

        val tools = WorkspaceTools(WorkspacePolicy(root))

        assertEquals(
            listOf("alpha.txt", "beta.txt"),
            tools.listFiles()
        )
    }

    @Test
    fun `rejects listing a regular file`() {
        val root = Files.createTempDirectory("workspace-tools")
        Files.writeString(root.resolve("notes.txt"), "content")

        val tools = WorkspaceTools(WorkspacePolicy(root))

        assertFailsWith<PolicyViolation> {
            tools.listFiles("notes.txt")
        }
    }

    @Test
    fun `deletion requires confirmation`() {
        val root = Files.createTempDirectory("workspace-tools")
        Files.writeString(root.resolve("temporary.txt"), "temporary")

        val tools = WorkspaceTools(WorkspacePolicy(root))

        assertFailsWith<PolicyViolation> {
            tools.delete("temporary.txt", confirmed = false)
        }

        assertTrue(Files.exists(root.resolve("temporary.txt")))
    }

    @Test
    fun `confirmed deletion removes file`() {
        val root = Files.createTempDirectory("workspace-tools")
        val file = root.resolve("temporary.txt")
        Files.writeString(file, "temporary")

        val tools = WorkspaceTools(WorkspacePolicy(root))

        assertTrue(tools.delete("temporary.txt", confirmed = true))
        assertFalse(Files.exists(file))
    }

    @Test
    fun `workspace root cannot be deleted`() {
        val root = Files.createTempDirectory("workspace-tools")
        val tools = WorkspaceTools(WorkspacePolicy(root))

        assertFailsWith<PolicyViolation> {
            tools.delete(".", confirmed = true)
        }

        assertTrue(Files.exists(root))
    }
}
