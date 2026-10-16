@echo off
setlocal
if defined JAVA17_HOME set "JAVA_HOME=%JAVA17_HOME%"
pushd "%~dp0..\.."
call gradlew.bat :common-j8:jar --console=plain --no-daemon
set "ODC_RC=%ERRORLEVEL%"
popd
if not "%ODC_RC%"=="0" goto done
if defined JAVA8_HOME set "JAVA_HOME=%JAVA8_HOME%"
set "ODC_TASKS=%*"
if "%ODC_TASKS%"=="" set "ODC_TASKS=build"
pushd "%~dp0"
call gradlew.bat %ODC_TASKS% --console=plain --no-daemon
set "ODC_RC=%ERRORLEVEL%"
popd
:done
endlocal & exit /b %ODC_RC%