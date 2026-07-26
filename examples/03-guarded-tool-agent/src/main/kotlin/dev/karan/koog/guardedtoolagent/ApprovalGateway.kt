package dev.karan.koog.guardedtoolagent

interface ApprovalGateway {
    fun request(action: GuardedAction, risk: ActionRisk, reason: String): Boolean
}

class ConsoleApprovalGateway : ApprovalGateway {
    override fun request(action: GuardedAction, risk: ActionRisk, reason: String): Boolean {
        println("Approval required")
        println("- action: ${action.action}")
        println("- target: ${action.targetPath}")
        println("- risk: $risk")
        println("- reason: $reason")
        print("Approve execution? [yes/no]: ")
        val response = readLine()?.trim()?.lowercase()
        return response == "yes"
    }
}

class FakeApprovalGateway(private val approved: Boolean) : ApprovalGateway {
    override fun request(action: GuardedAction, risk: ActionRisk, reason: String): Boolean = approved
}
