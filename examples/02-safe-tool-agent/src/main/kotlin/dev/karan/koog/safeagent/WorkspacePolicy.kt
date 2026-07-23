package dev.karan.koog.safeagent

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

class PolicyViolation(message: String) : IllegalArgumentException(message)

class WorkspacePolicy(root: Path) {

    val root: Path = root.toAbsolutePath().normalize()

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
        Files.createDirectories(this.root)
    }

    fun authorizeRead(userPath: String): Path = resolve(userPath)

    fun authorizeList(userPath: String): Path =
        resolve(userPath.ifBlank { "." })

    fun authorizeDelete(userPath: String, confirmed: Boolean): Path {
        if (!confirmed) {
            throw PolicyViolation("Deletion requires explicit confirmation")
        }

        return resolve(userPath)
    }

    fun resolve(userPath: String): Path {
        if (userPath.isBlank()) {
            throw PolicyViolation("Path must not be blank")
        }

        val suppliedPath = Path.of(userPath)

        if (suppliedPath.isAbsolute) {
            throw PolicyViolation("Absolute paths are not allowed")
        }

        val candidate = root.resolve(suppliedPath).normalize()

        if (!candidate.startsWith(root)) {
            throw PolicyViolation("Path escapes the permitted workspace")
        }

        root.relativize(candidate).forEach { segment ->
            val name = segment.toString()

            if (name.startsWith(".")) {
                throw PolicyViolation(
                    "Hidden files and directories are blocked"
                )
            }

            if (name.lowercase() in blockedNames) {
                throw PolicyViolation("Sensitive filename is blocked")
            }
        }

        rejectSymbolicLinkEscape(candidate)

        return candidate
    }

    private fun rejectSymbolicLinkEscape(candidate: Path) {
        var current = root

        for (segment in root.relativize(candidate)) {
            current = current.resolve(segment)

            if (
                Files.exists(current, LinkOption.NOFOLLOW_LINKS) &&
                Files.isSymbolicLink(current)
            ) {
                val target = current.toRealPath()

                if (!target.startsWith(root.toRealPath())) {
                    throw PolicyViolation(
                        "Symbolic link escapes the permitted workspace"
                    )
                }
            }
        }
    }
}
