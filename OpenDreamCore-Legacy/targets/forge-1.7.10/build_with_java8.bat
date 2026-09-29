@echo off
REM Bake the common-j8 shared jar first: it is built by the root Gradle 7.6 with JDK 17.
set JAVA_HOME=C:\Program Files\Java\jdk-17.0.1
pushd "%~dp0..\.."
call gradlew.bat :common-j8:jar --console=plain -q
set ODC_J8_RC=%ERRORLEVEL%
popd
if not "%ODC_J8_RC%"=="0" exit /b %ODC_J8_RC%
set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_181
set PATH=%JAVA_HOME%\bin;%PATH%
echo Using Java:
java -version
echo.
echo Building with Gradle 2.14...
REM Default to "build" when no task is given. Bare "gradlew.bat" with no args only prints
REM help and exits with code 0 -- looks like success but builds no jar, so the artifact
REM silently stays at its previous timestamp. Keeping this file ASCII-only matters: a
REM batch file is read as the OEM codepage, so non-ASCII comments corrupt the parse.
set ODC_TASKS=%*
if "%ODC_TASKS%"=="" set ODC_TASKS=build
gradlew.bat %ODC_TASKS%
