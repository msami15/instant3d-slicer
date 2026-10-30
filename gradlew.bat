@echo off
set DIR=%~dp0
if "%DIR%"=="" set DIR=.
set APP_BASE_NAME=%~n0
set CLASSPATH=%DIR%gradle\wrapper\gradle-wrapper.jar

java -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
