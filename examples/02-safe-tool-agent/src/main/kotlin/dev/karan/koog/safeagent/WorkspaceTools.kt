package dev.karan.koog.safeagent

import java.nio.file.Files

class WorkspaceTools(
    private val policy: WorkspacePolicy
) {

    fun readFile(path: String): String {
        val authorizedPath = policy.authorizeRead(path)

        if (!Files.isRegularFile(authorizedPath)) {
            throw PolicyViolation("Path is not a regular file")
        }

        return Files.readString(authorizedPath)
    }

    fun listFiles(path: String = ""): List<String> {
        val authorizedPath = policy.authorizeList(path)

        if (!Files.isDirectory(authorizedPath)) {
            throw PolicyViolation("Path is not a directory")
        }

        return Files.list(authorizedPath).use { entries ->
            entries
                .iterator()
                .asSequence()
                .map { policy.root.relativize(it).toString() }
                .sorted()
                .toList()
        }
    }

    fun delete(path: String, confirmed: Boolean): Boolean {
        val authorizedPath = policy.authorizeDelete(path, confirmed)

        if (authorizedPath == policy.root) {
            throw PolicyViolation("Workspace root cannot be deleted")
        }

        return Files.deleteIfExists(authorizedPath)
    }
}
