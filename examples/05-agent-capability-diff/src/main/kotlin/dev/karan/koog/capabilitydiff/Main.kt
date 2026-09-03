package dev.karan.koog.capabilitydiff

fun main() {
    val report = CapabilityDiffRunner().run(referenceScenarios())
    println("Agent Capability Diff")
    println()
    report.results.forEach { result ->
        println("${result.change}  ${result.scenario.id}")
        println("           ${result.baseline.decision} -> ${result.candidate.decision}")
        println("           ${result.scenario.description}")
        println()
    }
    println("Summary")
    println("${report.totalScenarios} scenarios evaluated")
    println("${report.expansionCount} expansions")
    println("${report.tighteningCount} tightening")
    println("${report.unchangedCount} unchanged")
    println()
    println("Capability blast radius: ${if (report.hasExpansions) "FAIL" else "PASS"}")
    if (report.hasExpansions) kotlin.system.exitProcess(1)
}