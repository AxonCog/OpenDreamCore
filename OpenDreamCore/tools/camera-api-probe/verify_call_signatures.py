"""M1.2 直调点的「精确签名」校验：不只是名字存在，参数/返回类型也要跨 6 个版本一致。

为什么单独跑这一遍：javap 按名字匹配只能证明「有这么个成员」，
真正会让某个目标编译失败的是参数类型漂移（例如 Options.getCameraType()
在某版返回 CameraType、在另一版返回 int；setCameraEntity 在某版收 Entity、
在另一版收 Entity 的子接口）。这里把我要写的每一处直调都钉死到签名上。
"""
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

# 我要写的调用 → (类, 成员名, 期望的参数类型列表（去空格的简单名）, 期望返回类型或 None)
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
    except Exception as exc:  # noqa: BLE001
        return None, str(exc)
    text = out.stdout.decode("utf-8", "replace")
    if not text.strip():
        return None, out.stderr.decode("utf-8", "replace")[:120]
    return text, ""


def simple(t):
    """net.minecraft.world.entity.Entity -> Entity；double[] -> double[]"""
    t = t.strip()
    if t.endswith("..."):
        t = t[:-3] + "[]"
    return t.split(".")[-1]


def find_member(text, name, want_args):
    """在 javap 输出里找某成员，返回 (参数类型简单名列表, 返回类型简单名) 或 None。"""
    # 字段形态：  public net.minecraft.client.Options options;
    for line in text.splitlines():
        s = line.strip()
        if s.startswith("public") or s.startswith("protected"):
            m = re.match(r"^(?:public|protected)\s+(?:static\s+|final\s+)*([\w.$<>\[\], ?]+?)\s+(\w+)\s*;$", s)
            if m and m.group(2) == name:
                return [], simple(m.group(1))
    # 方法形态
    pattern = re.compile(r"^(?:public|protected)\s+(?:static\s+|final\s+|abstract\s+|native\s+)*"
                         r"([\w.$<>\[\], ?]+?)\s+" + re.escape(name) + r"\(([^)]*)\)\s*;")
    for line in text.splitlines():
        m = pattern.match(line.strip())
        if not m:
            continue
        ret = simple(m.group(1))
        args = [] if not m.group(2).strip() else [simple(a.strip()) for a in m.group(2).split(",")]
        if args == want_args:
            return args, ret
    return None


rows = []
unresolved = []
for ver, rel in VERSIONS.items():
    jar = os.path.join(MM, rel)
    if not os.path.isfile(jar):
        print("缺 jar:", ver)
        continue
    cache = {}
    for cls, member, want_args, want_ret in CALLS:
        if cls not in cache:
            cache[cls] = javap(jar, cls)[0]
        text = cache[cls]
        got = find_member(text, member, want_args) if text else None
        if got is None:
            rows.append((ver, cls.split(".")[-1] + "." + member + "(" + ",".join(want_args) + ")",
                         "缺失", want_ret))
        else:
            ret_ok = want_ret is None or got[1] == want_ret
            rows.append((ver, cls.split(".")[-1] + "." + member + "(" + ",".join(want_args) + ")",
                         "OK" if ret_ok else "返回 " + got[1], want_ret))

by_member = {}
for ver, member, status, want_ret in rows:
    by_member.setdefault(member, {})[ver] = status

width = max(len(m) for m in by_member) + 2
print("直调点".ljust(width) + "".join(v.ljust(11) for v in VERSIONS))
print("-" * (width + 11 * len(VERSIONS)))
bad = []
for member, per in by_member.items():
    line = member.ljust(width)
    for v in VERSIONS:
        st = per.get(v, "?")
        line += st.ljust(11)
        if st != "OK":
            bad.append((member, v, st))
    print(line)

print()
if bad:
    print("!! 签名不一致（需按版本兜底或改写法）:")
    for member, v, st in bad:
        print("   %-52s %-9s %s" % (member, v, st))
else:
    print("全部直调点的签名跨 6 个版本完全一致 —— 可放心直调。")
