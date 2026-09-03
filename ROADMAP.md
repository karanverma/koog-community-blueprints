# Roadmap

## Vision

This repository is an evolving community project exploring architectural patterns, engineering practices, and developer tooling for building AI agents with Koog.

The roadmap below outlines the intended direction of the project. It is not a fixed release plan, and priorities may evolve as the repository grows and community contributions are incorporated.

---

## Current Status

The repository currently provides:

- Architecture Overview
- Edge Agent
- Infrastructure Middleware
- Observability
- Threat Model
- Evaluation Checklist
- Runnable Kotlin examples for minimal, safe, guarded, regression-driven, and capability-diff agent patterns

The project now includes a production-style guarded tool example, a deterministic safety regression suite, and capability-diff evaluation for comparing policy behavior without requiring LLMs or API keys.

---

## Near-Term Goals

Planned areas of focus include:

- Expand existing blueprint documentation
- Improve architectural diagrams
- Add engineering examples
- Refine evaluation guidance
- Expand threat modelling scenarios
- Continue extending guarded execution patterns and approval workflows

## Completed Milestones

- A focused runtime policy engine for the guarded tool agent now exists as a reusable, deterministic example implementation with composable rules, explicit precedence, approval gating, and structured audit output.
- The v0.5 Safety Regression Suite milestone is now implemented through Example 04, which exercises the guarded-tool policy engine end to end with deterministic scenarios for allow, confirm, deny, traversal, hidden paths, sensitive files, symlink escapes, and post-approval rechecks.
- The v0.6 Agent Capability Diff milestone is now implemented through Example 05, which compares effective baseline and candidate policy decisions to surface capability expansions, tightenings, and unchanged behavior.

---

## Long-Term Direction

Potential future directions include:

- Minimal JVM reference implementation
- Graph-based agent workflow examples
- OpenTelemetry integration examples
- Local-first and edge deployment patterns
- Additional architectural blueprints
- Evaluation tooling
- Secure deployment examples
- Integration with common JVM frameworks
- Richer policy engines and distributed approval flows

---

## Community Contributions

Community participation may include:

- Architecture proposals
- Documentation improvements
- Example implementations
- Engineering discussions
- Bug reports
- Suggestions for future blueprints

Constructive feedback and contributions are welcome as the repository evolves.

---

## Out of Scope

This repository is not currently intended to provide:

- Production-ready frameworks
- Complete application templates
- Official Koog documentation
- Formal security or compliance guidance

Instead, it focuses on architectural exploration, engineering patterns, and community learning.
