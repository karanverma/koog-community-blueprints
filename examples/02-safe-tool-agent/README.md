# Safe Workspace Tools

A runnable Kotlin example demonstrating a policy-enforced filesystem layer for safe file operations.

## Features

- Workspace boundary enforcement
- Path traversal prevention
- Absolute path rejection
- Hidden file blocking
- Sensitive filename blocking
- Symbolic-link escape protection
- Explicit confirmation before deletion
- Unit tests
- Runnable demo

## Run

```bash
gradle clean test
gradle run
```

## Scope

This example demonstrates a secure filesystem policy and tool layer. It is intended as an educational reference and does not yet register these operations as Koog tools.
