"""验证跨版本直调 API 的参数和返回类型。"""
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

CALLS = [
    ("net.minecraft.client.Minecraft", "setCameraEntity", ["Entity"], "void"),
    ("net.minecraft.client.Minecraft", "getCameraEntity", [], "Entity"),
    ("net.minecraft.client.Minecraft", "options", [], "Options"),
    ("net.minecraft.client.Options", "getCameraType", [], "CameraType"),
    ("net.minecraft.client.Options", "setCameraType", ["CameraType"], "void"),
    ("net.minecraft.client.Options", "hideGui", [], "boolean"),
    ("net.minecraft.world.entity.Entity", "setOldPosAndRot", [], "void"),
    ("net.minecraft.world.entity.Entity", "setPos", ["double", "double", "double"], "void"),
    ("net.minecraft.world.entity.Entity", "setYRot", ["float"], "void"),
    ("net.minecraft.world.entity.Entity", "setXRot", ["float"], "void"),
    ("net.minecraft.world.entity.Entity", "setYHeadRot", ["float"], "void"),
    ("net.minecraft.world.entity.Entity", "setInvisible", ["boolean"], "void"),
    ("net.minecraft.world.entity.Entity", "setNoGravity", ["boolean"], "void"),
    ("net.minecraft.world.entity.Entity", "setDeltaMovement", ["double", "double", "double"], "void"),
    ("net.minecraft.world.entity.Entity", "getYRot", [], "float"),
    ("net.minecraft.world.entity.Entity", "getXRot", [], "float"),
    ("net.minecraft.world.entity.Entity", "getX", [], "double"),
    ("net.minecraft.world.entity.Entity", "getY", [], "double"),
    ("net.minecraft.world.entity.Entity", "getZ", [], "double"),
    ("net.minecraft.world.entity.Entity", "getEyeHeight", [], "float"),
    ("net.minecraft.world.entity.Entity", "isInvisible", [], "boolean"),
    ("net.minecraft.world.entity.EntityType", "create", ["Level"], "Entity"),
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


def simple(type_name):
    type_name = type_name.strip()
    if type_name.endswith("..."):
        type_name = type_name[:-3] + "[]"
    return type_name.split(".")[-1]


def find_member(text, name, wanted_args):
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith(("public", "protected")):
            field = re.match(r"^(?:public|protected)\s+(?:static\s+|final\s+)*([\w.$<>\[\], ?]+?)\s+(\w+)\s*;$", stripped)
            if field and field.group(2) == name:
                return [], simple(field.group(1))
    pattern = re.compile(
        r"^(?:public|protected)\s+(?:static\s+|final\s+|abstract\s+|native\s+)*"
        + re.escape(name)
        + r"\(([^)]*)\)\s*;"
    )
    for line in text.splitlines():
        match = pattern.match(line.strip())
        if not match:
            continue
        args = [] if not match.group(1).strip() else [simple(value.strip()) for value in match.group(1).split(",")]
        if args == wanted_args:
            prefix = line.strip().split(name, 1)[0].strip().split()
            return args, simple(prefix[-1]) if prefix else "?"
    return None


rows = []
for version, relative in VERSIONS.items():
    jar = os.path.join(MM, relative)
    if not os.path.isfile(jar):
        print("缺 jar:", version)
        continue
    cache = {}
    for cls, member, wanted_args, wanted_return in CALLS:
        if cls not in cache:
            cache[cls] = javap(jar, cls)[0]
        text = cache[cls]
        got = None if text is None else find_member(text, member, wanted_args)
        rows.append((version, member, got, wanted_return))

for version, member, got, wanted_return in rows:
    if got is None:
        result = "缺失"
    else:
        result = "OK" if got[1] == wanted_return else "返回 " + got[1]
    print(version, member, result)
