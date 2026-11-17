@rem Minimal Gradle wrapper launcher for Windows (see the note in gradlew).
@echo off
setlocal
set APP_HOME=%~dp0
if defined JAVA_HOME (set JAVACMD=%JAVA_HOME%\bin\java.exe) else (set JAVACMD=java.exe)
if not exist "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" (
  echo gradlew.bat: gradle\wrapper\gradle-wrapper.jar is missing. Run "gradle wrapper --gradle-version 8.9" once. 1>&2
  exit /b 1
)
"%JAVACMD%" %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=gradlew" -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
endlocal
