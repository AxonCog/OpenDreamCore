"""验证各 Minecraft 版本的相机 API 是否存在。"""
import os
import re
import subprocess

JAVAP = os.environ.get("JAVAP", "javap")
MM = os.environ.get(
    "MINECRAFT_MERGED_DIR",
    os.path.expanduser("~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged"),
)

VERSIONS = {
    "1.20.1": "1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2/minecraft-merged-1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2.jar",
    "1.21.1": "1.21.1-loom.mappings.1_21_1.layered+hash.2198-v2/minecraft-merged-1.21.1-loom.mappings.1_21_1.layered+hash.2198-v2.jar",
    "1.21.4": "1.21.4-loom.mappings.1_21_4.layered+hash.2198-v2/minecraft-merged-1.21.4-loom.mappings.1_21_4.layered+hash.2198-v2.jar",
    "1.21.8": "1.21.8-loom.mappings.1_21_8.layered+hash.2198-v2/minecraft-merged-1.21.8-loom.mappings.1_21_8.layered+hash.2198-v2.jar",
    "1.21.11": "1.21.11-loom.mappings.1_21_11.layered+hash.2198-v2/minecraft-merged-1.21.11-loom.mappings.1_21_11.layered+hash.2198-v2.jar",
    "26.1.2": "26.1.2-null.unspecified.26_1_2.1.0-v2/minecraft-merged-26.1.2-null.unspecified.26_1_2.1.0-v2.jar",
}

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
        out = subprocess.run([JAVAP, "-p", "-classpath", jar, cls], capture_output=True, timeout=180)
    except Exception as exc:
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
        for member in members:
            if text is None:
                ok = None
            elif is_field:
                ok = re.search(r"\b%s\b\s*;" % re.escape(member), text) is not None
            else:
                ok = re.search(r"\b%s\s*\(" % re.escape(member), text) is not None
            rows.append((ver, cls.split(".")[-1] + "." + member, ok))

vers = list(VERSIONS)
width = max((len(row[1]) for row in rows), default=4) + 2
print("成员".ljust(width) + "".join(ver.ljust(10) for ver in vers))
print("-" * (width + 10 * len(vers)))
by_member = {}
for ver, member, ok in rows:
    by_member.setdefault(member, {})[ver] = ok
missing = []
for member, per_version in by_member.items():
    line = member.ljust(width)
    for ver in vers:
        ok = per_version.get(ver)
        line += ("OK" if ok else ("缺失" if ok is False else "?")).ljust(10)
        if ok is False:
            missing.append((member, ver))
    print(line)

print()
if missing:
    print("发现缺失成员：")
    for member, ver in missing:
        print("  ", ver, member)
else:
    print("全部已发现的版本成员均存在。")
