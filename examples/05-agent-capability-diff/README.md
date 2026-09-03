# Agent Capability Diff

This example checks the blast radius of authorization policy changes. It runs the same deterministic guarded workspace actions through a baseline and candidate policy, then compares the effective decisions returned by the real `RuntimePolicyEngine`.

Ordinary rule unit tests can pass while a refactor changes the agent's combined permissions. Capability diff tests expose that effective change instead of comparing configuration objects or expected values alone.

Decision levels increase in autonomy:

- `DENY < CONFIRM < ALLOW`
- Expansion: `DENY -> CONFIRM`, `DENY -> ALLOW`, or `CONFIRM -> ALLOW`
- Tightening: `ALLOW -> CONFIRM`, `ALLOW -> DENY`, or `CONFIRM -> DENY`
- Equal decisions are unchanged.

## Run tests

```bash
./gradlew test
```

## Run the demo

```bash
./gradlew run
```

The CLI reports each effective transition and exits non-zero when any capability expansion is found. The reusable runner and report do not terminate the process.

## Limitations

The scenarios are deterministic and cover only the supplied actions and policy profiles. They do not prove completeness of a production policy, account for external state, or replace authorization review. This is a reference/evaluation pattern, not a production authorization framework.