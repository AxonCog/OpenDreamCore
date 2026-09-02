#!/bin/bash
# OpenDreamCore 一键构建全部 target（Linux/macOS/CI）
set -e

echo "============================================"
echo "  OpenDreamCore 全版本构建"
echo "============================================"

for V in 1.21.1 1.21.4 1.21.8 26.1.2; do
    echo "[编译] neoforge-$V ..."
    ./gradlew -p "targets/neoforge-$V" build --no-daemon --console=plain > /dev/null 2>&1
    echo "  ✅ 成功"
done

for V in 1.21.1; do
    echo "[编译] fabric-$V ..."
    ./gradlew -p "targets/fabric-$V" build --no-daemon --console=plain > /dev/null 2>&1
    echo "  ✅ 成功"
done

echo "[编译] Plugin ..."
cd ../OpenDreamCore-Plugin
./gradlew build --no-daemon --console=plain > /dev/null 2>&1
echo "  ✅ 成功"
cd ../OpenDreamCore

echo ""
echo "============================================"
echo "  全部构建完成"
echo "============================================"
