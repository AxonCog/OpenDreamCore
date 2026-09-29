@echo off
REM Bake the common-j8 shared jar first: it is built by the root Gradle 7.6 with JDK 17.
REM
REM This target embeds common-j8 as a whole jar (not as a source dir), so a target-only
REM build happily packs whatever shared-layer jar happens to be lying around. That is
REM exactly how this target once shipped without visual/FontRules at all: its jar was
REM built before the shared layer and nobody noticed, because the build still went green.
REM Baking the shared layer here makes a direct invocation safe.
set JAVA_HOME=C:\Program Files\Java\jdk-17.0.1
pushd "%~dp0..\.."
call gradlew.bat :common-j8:jar --console=plain -q
set ODC_J8_RC=%ERRORLEVEL%
popd
if not "%ODC_J8_RC%"=="0" exit /b %ODC_J8_RC%
echo Building with the target's own Gradle...
gradlew.bat %*
