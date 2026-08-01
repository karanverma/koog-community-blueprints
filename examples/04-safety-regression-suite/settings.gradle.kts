rootProject.name = "04-safety-regression-suite"

include("guarded-tool-agent")
project(":guarded-tool-agent").projectDir = File(settingsDir, "../03-guarded-tool-agent")
