@echo off
setlocal
if defined JAVA21_HOME set "JAVA_HOME=%JAVA21_HOME%"
pushd "%~dp0"

REM Usage: build-all.bat [target ...]
REM Root Gradle selects child JDKs from JAVA8_HOME/JAVA17_HOME/JAVA21_HOME/JAVA25_HOME.
REM JAVA_HOME is the fallback; no arguments builds and collects the complete release.
if "%~1"=="" goto full

:target
call gradlew.bat build -Ptarget="%~1" --console=plain --no-daemon
set "ODC_RC=%ERRORLEVEL%"
if not "%ODC_RC%"=="0" goto done
shift
if not "%~1"=="" goto target
goto done

:full
call gradlew.bat buildFull --console=plain --no-daemon
set "ODC_RC=%ERRORLEVEL%"

:done
popd
endlocal & exit /b %ODC_RC%
