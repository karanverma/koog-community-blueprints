package dev.karan.koog.guardedtoolagent

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimePolicyEngineTest {

    @Test
    fun `single allow rule returns allow`() {
        val engine = RuntimePolicyEngine(listOf(object : PolicyRule {
            override val id = "allow"
            override val description = "Allow"
            override fun evaluate(context: PolicyContext): PolicyEvaluation? {
                return PolicyEvaluation(GuardDecision.ALLOW, "Allowed", id)
            }
        }))

        val result = engine.evaluate(PolicyContext(GuardedAction("read", "notes.txt")))
        assertEquals(GuardDecision.ALLOW, result.decision)
        assertEquals(listOf("allow"), result.matchedRuleIds)
    }

    @Test
    fun `single confirm rule returns confirm`() {
        val engine = RuntimePolicyEngine(listOf(object : PolicyRule {
            override val id = "confirm"
            override val description = "Confirm"
            override fun evaluate(context: PolicyContext): PolicyEvaluation? {
                return PolicyEvaluation(GuardDecision.CONFIRM, "Confirm", id)
            }
        }))

        val result = engine.evaluate(PolicyContext(GuardedAction("write", "draft.txt")))
        assertEquals(GuardDecision.CONFIRM, result.decision)
        assertEquals("Confirm", result.reason)
    }

    @Test
    fun `single deny rule returns deny`() {
        val engine = RuntimePolicyEngine(listOf(object : PolicyRule {
            override val id = "deny"
            override val description = "Deny"
            override fun evaluate(context: PolicyContext): PolicyEvaluation? {
                return PolicyEvaluation(GuardDecision.DENY, "Denied", id)
            }
        }))

        val result = engine.evaluate(PolicyContext(GuardedAction("read", "notes.txt")))
        assertEquals(GuardDecision.DENY, result.decision)
    }

    @Test
    fun `deny overrides allow`() {
        val engine = RuntimePolicyEngine(listOf(
            object : PolicyRule {
                override val id = "allow"
                override val description = "Allow"
                override fun evaluate(context: PolicyContext): PolicyEvaluation? = PolicyEvaluation(GuardDecision.ALLOW, "Allow", id)
            },
            object : PolicyRule {
                override val id = "deny"
                override val description = "Deny"
                override fun evaluate(context: PolicyContext): PolicyEvaluation? = PolicyEvaluation(GuardDecision.DENY, "Deny", id)
            }
        ))

        val result = engine.evaluate(PolicyContext(GuardedAction("read", "notes.txt")))
        assertEquals(GuardDecision.DENY, result.decision)
        assertEquals(listOf("allow", "deny"), result.matchedRuleIds)
        assertEquals("deny", result.ruleId)
    }

    @Test
    fun `deny overrides confirm`() {
        val engine = RuntimePolicyEngine(listOf(
            object : PolicyRule {
                override val id = "confirm"
                override val description = "Confirm"
                override fun evaluate(context: PolicyContext): PolicyEvaluation? = PolicyEvaluation(GuardDecision.CONFIRM, "Confirm", id)
            },
            object : PolicyRule {
                override val id = "deny"
                override val description = "Deny"
                override fun evaluate(context: PolicyContext): PolicyEvaluation? = PolicyEvaluation(GuardDecision.DENY, "Deny", id)
            }
        ))

        val result = engine.evaluate(PolicyContext(GuardedAction("delete", "notes.txt")))
        assertEquals(GuardDecision.DENY, result.decision)
        assertEquals("deny", result.ruleId)
    }

    @Test
    fun `confirm overrides allow`() {
        val engine = RuntimePolicyEngine(listOf(
            object : PolicyRule {
                override val id = "allow"
                override val description = "Allow"
                override fun evaluate(context: PolicyContext): PolicyEvaluation? = PolicyEvaluation(GuardDecision.ALLOW, "Allow", id)
            },
            object : PolicyRule {
                override val id = "confirm"
                override val description = "Confirm"
                override fun evaluate(context: PolicyContext): PolicyEvaluation? = PolicyEvaluation(GuardDecision.CONFIRM, "Confirm", id)
            }
        ))

        val result = engine.evaluate(PolicyContext(GuardedAction("write", "draft.txt")))
        assertEquals(GuardDecision.CONFIRM, result.decision)
        assertEquals("confirm", result.ruleId)
    }

    @Test
    fun `results remain stable regardless of rule ordering`() {
        val allowThenDeny = RuntimePolicyEngine(listOf(
            object : PolicyRule {
                override val id = "allow"
                override val description = "Allow"
                override fun evaluate(context: PolicyContext): PolicyEvaluation? = PolicyEvaluation(GuardDecision.ALLOW, "Allow", id)
            },
            object : PolicyRule {
                override val id = "deny"
                override val description = "Deny"
                override fun evaluate(context: PolicyContext): PolicyEvaluation? = PolicyEvaluation(GuardDecision.DENY, "Deny", id)
            }
        ))
        val denyThenAllow = RuntimePolicyEngine(listOf(
            object : PolicyRule {
                override val id = "deny"
                override val description = "Deny"
                override fun evaluate(context: PolicyContext): PolicyEvaluation? = PolicyEvaluation(GuardDecision.DENY, "Deny", id)
            },
            object : PolicyRule {
                override val id = "allow"
                override val description = "Allow"
                override fun evaluate(context: PolicyContext): PolicyEvaluation? = PolicyEvaluation(GuardDecision.ALLOW, "Allow", id)
            }
        ))

        assertEquals(allowThenDeny.evaluate(PolicyContext(GuardedAction("read", "notes.txt"))).decision, denyThenAllow.evaluate(PolicyContext(GuardedAction("read", "notes.txt"))).decision)
        assertEquals("deny", allowThenDeny.evaluate(PolicyContext(GuardedAction("read", "notes.txt"))).ruleId)
    }

    @Test
    fun `tool policy blocks traversal and hidden paths`() {
        val root = Files.createTempDirectory("policy-engine-test")
        val policy = ToolPolicy(root)

        val traversal = policy.evaluate(GuardedAction("read", "../outside.txt"))
        assertEquals(GuardDecision.DENY, traversal.decision)
        assertTrue(traversal.reason.contains("traversal", ignoreCase = true) || traversal.reason.contains("workspace", ignoreCase = true) || traversal.reason.contains("escapes", ignoreCase = true))

        val hidden = policy.evaluate(GuardedAction("read", ".env"))
        assertEquals(GuardDecision.DENY, hidden.decision)
        assertTrue(hidden.reason.contains("hidden", ignoreCase = true) || hidden.reason.contains("sensitive", ignoreCase = true))
    }

    @Test
    fun `tool policy blocks sensitive filenames and symlink escapes`() {
        val root = Files.createTempDirectory("policy-engine-test-2")
        val external = Files.createTempDirectory("policy-engine-external")
        val link = root.resolve("linked")
        Files.createSymbolicLink(link, external.toAbsolutePath())
        val policy = ToolPolicy(root)

        val sensitive = policy.evaluate(GuardedAction("read", "credentials.json"))
        assertEquals(GuardDecision.DENY, sensitive.decision)

        val nestedSensitive = policy.evaluate(GuardedAction("read", "folder/credentials.json"))
        assertEquals(GuardDecision.DENY, nestedSensitive.decision)

        val sensitiveParent = policy.evaluate(GuardedAction("read", "credentials.json/notes.txt"))
        assertEquals(GuardDecision.DENY, sensitiveParent.decision)

        val mixedCaseSensitive = policy.evaluate(GuardedAction("read", "safe/SECRETS.TXT/data.txt"))
        assertEquals(GuardDecision.DENY, mixedCaseSensitive.decision)

        val ordinaryNested = policy.evaluate(GuardedAction("read", "safe/docs/notes.txt"))
        assertEquals(GuardDecision.ALLOW, ordinaryNested.decision)

        val escaped = policy.evaluate(GuardedAction("read", "linked"))
        assertEquals(GuardDecision.DENY, escaped.decision)
    }

    @Test
    fun `deletion confirmation is enforced and approved writes remain allowed`() {
        val root = Files.createTempDirectory("policy-engine-test-3")
        val policy = ToolPolicy(root)

        val deleteDecision = policy.evaluate(GuardedAction("delete", "notes.txt"))
        assertEquals(GuardDecision.CONFIRM, deleteDecision.decision)
        assertTrue(deleteDecision.requiresConfirmation)

        val writeDecision = policy.evaluate(GuardedAction("write", "notes.txt"))
        assertEquals(GuardDecision.CONFIRM, writeDecision.decision)
        assertTrue(writeDecision.requiresConfirmation)

        val readDecision = policy.evaluate(GuardedAction("read", "notes.txt"))
        assertEquals(GuardDecision.ALLOW, readDecision.decision)
        assertFalse(readDecision.requiresConfirmation)
    }

    @Test
    fun `audit metadata is preserved by guarded workspace tools`() {
        val root = Files.createTempDirectory("policy-engine-test-4")
        Files.writeString(root.resolve("notes.txt"), "safe")
        val sink = InMemoryAuditSink()
        val tools = GuardedWorkspaceTools(root, approvalGateway = FakeApprovalGateway(true), auditSink = sink)

        tools.read("notes.txt")
        val event = sink.events.single()
        assertEquals("read", event.action)
        assertEquals("notes.txt", event.target)
        assertEquals(GuardDecision.ALLOW, event.policyDecision)
        assertTrue(event.reason.contains("permitted", ignoreCase = true))
        assertFalse(event.approvalRequested)
        assertFalse(event.approvalGranted == false)
    }
}
