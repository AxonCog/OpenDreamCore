"""M1.2 相机播放要用到的 MC API 跨版本验证。

房规：写代码前先用 javap 在**每个** MC 版本的 merged jar 上验证成员存在性，
不要靠"应该都有"。漂移一旦漏掉，就是某个目标编译期炸或者运行期 NoSuchMethod。
"""
import io
import os
import re
import subprocess

JAVAP = "C:/Users/24965/.jdks/jdk-25.0.4.1+1/bin/javap"
MM = "C:/Users/24965/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged"

VERSIONS = {
    "1.20.1": "1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2/minecraft-merged-1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2.jar",
    "1.21.1": "1.21.1-loom.mappings.1_21_1.layered+hash.2198-v2/minecraft-merged-1.21.1-loom.mappings.1_21_1.layered+hash.2198-v2.jar",
    "1.21.4": "1.21.4-loom.mappings.1_21_4.layered+hash.2198-v2/minecraft-merged-1.21.4-loom.mappings.1_21_4.layered+hash.2198-v2.jar",
    "1.21.8": "1.21.8-loom.mappings.1_21_8.layered+hash.2198-v2/minecraft-merged-1.21.8-loom.mappings.1_21_8.layered+hash.2198-v2.jar",
    "1.21.11": "1.21.11-loom.mappings.1_21_11.layered+hash.2198-v2/minecraft-merged-1.21.11-loom.mappings.1_21_11.layered+hash.2198-v2.jar",
    "26.1.2": "26.1.2-null.unspecified.26_1_2.1.0-v2/minecraft-merged-26.1.2-null.unspecified.26_1_2.1.0-v2.jar",
}

# (类, 要查的成员名列表, 是否是字段)
CHECKS = [
    ("net.minecraft.world.entity.Entity",
     ["setOldPosAndRot", "setInvisible", "setNoGravity", "setPos", "setYRot", "setXRot", "setYHeadRot"], False),
    ("net.minecraft.world.entity.Entity", ["xo", "yo", "zo", "yRotO", "xRotO"], True),
    ("net.minecraft.world.entity.Entity",
     ["getEyeHeight", "getEyeY", "getEyePosition", "getViewYRot", "getViewXRot", "setDeltaMovement", "isRemoved"], False),
    ("net.minecraft.client.Minecraft", ["setCameraEntity", "getCameraEntity"], False),
    ("net.minecraft.client.Minecraft", ["options"], True),
    ("net.minecraft.client.Options", ["getCameraType", "setCameraType"], False),
    ("net.minecraft.client.Options", ["hideGui"], True),
    ("net.minecraft.client.CameraType", ["FIRST_PERSON", "THIRD_PERSON_BACK", "THIRD_PERSON_FRONT"], True),
    ("net.minecraft.world.entity.EntityType", ["ARMOR_STAND"], True),
    ("net.minecraft.world.entity.EntityType", ["create"], False),
    ("net.minecraft.world.entity.decoration.ArmorStand", ["setInvisible"], False),
    ("net.minecraft.client.player.LocalPlayer", ["input"], True),
]


def javap(jar, cls):
    try:
        out = subprocess.run([JAVAP, "-p", "-classpath", jar, cls],
                             capture_output=True, timeout=180)
    except Exception as exc:  # noqa: BLE001
        return None, str(exc)
    text = out.stdout.decode("utf-8", "replace")
    if not text.strip():
        return None, out.stderr.decode("utf-8", "replace")[:120]
    return text, ""


rows = []
for ver, rel in VERSIONS.items():
    jar = os.path.join(MM, rel)
    if not os.path.isfile(jar):
        print("缺 jar:", ver, jar)
        continue
    cache = {}
    for cls, members, is_field in CHECKS:
        if cls not in cache:
            cache[cls] = javap(jar, cls)
        text, err = cache[cls]
        for m in members:
            if text is None:
                ok = None
            elif is_field:
                ok = re.search(r"\b%s\b\s*;" % re.escape(m), text) is not None
            else:
                ok = re.search(r"\b%s\s*\(" % re.escape(m), text) is not None
            rows.append((ver, cls.split(".")[-1] + "." + m, ok))

# 输出矩阵：成员 × 版本
vers = [v for v in VERSIONS]
width = max(len(r[1]) for r in rows) + 2
print("成员".ljust(width) + "".join(v.ljust(10) for v in vers))
print("-" * (width + 10 * len(vers)))
by_member = {}
for ver, member, ok in rows:
    by_member.setdefault(member, {})[ver] = ok
missing = []
for member, per in by_member.items():
    line = member.ljust(width)
    for v in vers:
        ok = per.get(v)
        line += ("OK" if ok else ("缺失" if ok is False else "?")).ljust(10)
        if ok is False:
            missing.append((member, v))
    print(line)

print()
if missing:
    print("!! 有缺失（需按版本兜底）:")
    for member, v in missing:
        print("   %-42s %s" % (member, v))
else:
    print("全部版本均存在 —— 可以直调，无需兜底。")
