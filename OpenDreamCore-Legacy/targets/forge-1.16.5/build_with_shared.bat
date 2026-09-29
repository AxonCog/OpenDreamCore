@echo off
REM Bake the common-j8 shared jar first: it is built by the root Gradle 7.6 with JDK 17.
REM
REM Same reasoning as the 1.12.2 target: common-j8 is embedded as a finished jar, so a
REM target-only build silently keeps a stale shared layer. Baking it here keeps a direct
REM invocation from producing a jar that quietly behaves like an older revision.
set JAVA_HOME=C:\Program Files\Java\jdk-17.0.1
pushd "%~dp0..\.."
call gradlew.bat :common-j8:jar --console=plain -q
set ODC_J8_RC=%ERRORLEVEL%
popd
if not "%ODC_J8_RC%"=="0" exit /b %ODC_J8_RC%
echo Building with the target's own Gradle...
gradlew.bat %*
