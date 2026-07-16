@ECHO OFF
SETLOCAL

SET DIR=%~dp0
SET APP_HOME=%DIR%

SET CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

IF NOT EXIST "%CLASSPATH%" (
    ECHO gradle-wrapper.jar is missing. Run bootstrap-wrapper.ps1 first.
    EXIT /B 1
)

SET JAVA_EXE=java.exe
IF DEFINED JAVA_HOME (
    SET JAVA_EXE=%JAVA_HOME%\bin\java.exe
)

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=gradlew" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
EXIT /B %ERRORLEVEL%
