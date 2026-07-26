package dev.karan.koog.guardedtoolagent

data class GuardedAction(
    val action: String,
    val targetPath: String,
    val operationCategory: String? = null,
    val destructive: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
)

enum class ActionRisk {
    LOW,
    MEDIUM,
    HIGH,
    FORBIDDEN
}
