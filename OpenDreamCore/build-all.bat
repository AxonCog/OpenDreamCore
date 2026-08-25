@echo off
REM OpenDreamCore 一键构建全部 target
REM 用法: 双击运行或在终端执行 build-all.bat

echo ============================================
echo   OpenDreamCore 全版本构建
echo ============================================
echo.

set SUCCESS=0
set FAIL=0

REM NeoForge 系列
for %%V in (1.21.1 1.21.4 1.21.8 26.1.2) do (
    echo [编译] neoforge-%%V ...
    cd /d "%~dp0"
    call gradlew.bat -p targets/neoforge-%%V build --console=plain >nul 2>&1
    if errorlevel 1 (
        echo   ❌ 失败
        set /a FAIL+=1
    ) else (
        echo   ✅ 成功
        set /a SUCCESS+=1
    )
)

REM Fabric 系列
for %%V in (1.21.1) do (
    echo [编译] fabric-%%V ...
    cd /d "%~dp0"
    call gradlew.bat -p targets/fabric-%%V build --console=plain >nul 2>&1
    if errorlevel 1 (
        echo   ❌ 失败
        set /a FAIL+=1
    ) else (
        echo   ✅ 成功
        set /a SUCCESS+=1
    )
)

REM 服务端插件
echo [编译] Plugin ...
cd /d "%~dp0\..\OpenDreamCore-Plugin"
call gradlew.bat build --console=plain >nul 2>&1
if errorlevel 1 (
    echo   ❌ 失败
    set /a FAIL+=1
) else (
    echo   ✅ 成功
    set /a SUCCESS+=1
)

echo.
echo ============================================
echo   构建完成: %SUCCESS% 成功, %FAIL% 失败
echo   产物目录: output\
echo ============================================
