@echo off
REM OpenDreamCore modern-target full build (Windows).
REM
REM NOTE: keep this file ASCII-only. cmd.exe reads a .bat using the OEM codepage, so
REM non-ASCII comments get mis-decoded and can split a line in the middle -- a stray
REM token then gets executed as a command and the script dies before building anything
REM (observed symptom: "'abledelayedexpansion' is not recognized"). For the same reason
REM do not add non-ASCII to echo strings.
REM
REM The target list is not hardcoded here: it is read from Gradle's listTargetsPlain.
REM Previously each build script kept its own copy of the list, and when a version was
REM added some copies were missed, so that target silently stopped being built -- from
REM the outside it looked like the version did not exist. The list now has exactly one
REM source of truth (the root build.gradle); this script only decides how to build.
REM
REM Usage: build-all.bat                 build everything
REM        build-all.bat fabric-1.21.8   build one target

setlocal enabledelayedexpansion
cd /d "%~dp0"

set "J17=C:\Program Files\Java\jdk-17.0.1"
set "J21=C:\Program Files\Java\jdk-21"
set "J25=C:\Users\24965\.jdks\jdk-25.0.4.1+1"

echo ============================================
echo   OpenDreamCore build (all modern targets)
echo ============================================
echo.

set "TARGETS=%*"
if "%TARGETS%"=="" (
    for /f "usebackq delims=" %%T in (`call gradlew.bat listTargetsPlain -q --no-daemon 2^>nul`) do (
        set "LINE=%%T"
        set "LINE=!LINE: =!"
        if not "!LINE!"=="" set "TARGETS=!TARGETS! !LINE!"
    )
)

if "%TARGETS%"=="" (
    echo ERROR: empty target list ^(listTargetsPlain produced nothing^)
    exit /b 1
)

if not exist "build-logs" mkdir "build-logs"

set /a SUCCESS=0
set /a FAIL=0

for %%T in (%TARGETS%) do (
    REM Pick the JDK per MC version: 1.20.1 needs 17, 26.1.2 needs 25, the rest 21.
    set "JDK=%J21%"
    echo %%T | findstr /c:"-1.20.1" >nul && set "JDK=%J17%"
    echo %%T | findstr /c:"-26.1.2" >nul && set "JDK=%J25%"

    echo [build] %%T ...
    cd /d "%~dp0"
    if not exist "targets\%%T" (
        echo   FAILED: target directory missing
        set /a FAIL+=1
    ) else (
        cd /d "%~dp0targets\%%T"
        set "JAVA_HOME=!JDK!"
        REM Do not use --offline: it turns a missing dependency into a confusing
        REM "no cached version" failure instead of just downloading it.
        REM Do not keep a daemon either: each target runs on a different JDK, so a daemon
        REM started for one target can never be reused by the next ("incompatible Daemon
        REM could not be reused") and the buildup of idle daemons has hung the build
        REM indefinitely. One JVM per target, then gone.
        call gradlew.bat build --console=plain --no-daemon --max-workers=2 > "%~dp0build-logs\%%T.log" 2>&1
        if errorlevel 1 (
            echo   FAILED  ^(log: build-logs\%%T.log^)
            set /a FAIL+=1
        ) else (
            echo   ok
            set /a SUCCESS+=1
        )
    )
)

cd /d "%~dp0"
echo.
echo ============================================
echo   done: %SUCCESS% ok, %FAIL% failed
echo ============================================
echo.
echo Products are collected into output/all by:  gradlew.bat collectAll
if not "%FAIL%"=="0" exit /b 1
endlocal
