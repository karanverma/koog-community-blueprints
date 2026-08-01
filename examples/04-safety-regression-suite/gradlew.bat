@echo off
setlocal
set JAVA_EXE=java.exe
if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
)
if not exist "%JAVA_EXE%" (
  echo JAVA_HOME is not set and no 'java' command could be found in your PATH.
  exit /b 1
)

"%JAVA_EXE%" -classpath "%~dp0..\03-guarded-tool-agent\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
