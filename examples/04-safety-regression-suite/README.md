# Safety Regression Suite

This example turns the guarded workspace policy into a deterministic regression harness. It is designed to answer a practical question: when a tool-policy change silently weakens or over-restricts the safety boundary, can the regression be detected locally and in CI without any LLM or API key?

## What it checks

The suite exercises the production runtime policy engine and guarded workspace tools from Example 03 directly. Each scenario records:

- a policy decision: ALLOW, CONFIRM, or DENY
- an execution outcome: EXECUTED, REJECTED, DENIED, or NOT_APPLICABLE
- optional filesystem side-effect assertions

The goal is to catch regressions in workspace-boundary enforcement, traversal protection, hidden-path blocking, sensitive-name matching, symlink escape prevention, confirmation flows, and post-approval rechecks.

## How to run

```bash
./gradlew test --no-daemon
./gradlew run --no-daemon
```

## How to add a scenario

Add a new entry to the scenario list in the main runner source and provide the expected policy decision, execution outcome, and any side-effect assertion you care about.

## Notes

This is a regression-testing reference implementation, not a security certification framework. It remains deterministic, local-first, and intentionally small. Some filesystem behavior can still be affected by platform-specific symlink semantics or TOCTOU timing, so the suite documents those limitations explicitly.
