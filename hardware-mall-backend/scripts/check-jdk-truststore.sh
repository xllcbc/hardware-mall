#!/bin/sh
# 校验 JDK 信任库是否仍信任被泄的 eMudhra/Sectigo 根证书。
#
# 背景：CVE-2026-91403 / CVE-2026-114820（eMudhra 中间 CA 私钥泄露）。
# Temurin 的修复方式是直接从 cacerts 删除签发链的根（AAA Certificate Services /
# emSign ECC Root CA - G3），而上游 OpenJDK 是往 lib/security/blocked.certs 加黑名单。
# 因此判据只能是“这两个别名必须不在 cacerts 中”，用 blocked.certs 判断会在 Temurin 上误判。
# 详见 docs/security/CVE-2026-91403.md。
#
# 用法（CI、容器内、本地均可）：
#   sh hardware-mall-backend/scripts/check-jdk-truststore.sh
# 依赖 keytool：优先 $JAVA_HOME/bin/keytool，其次 PATH 中的 keytool。

set -eu

# 主机门禁使用稀疏工作树，.sandcastle 不在检出集合内；Maven 命令固定从该路径读取设置。
# 因此在执行校验前，从受版本控制的模板补齐门禁所需的 Maven 设置。
script_dir=$(CDPATH= cd "$(dirname "$0")" && pwd)
repo_root=$(CDPATH= cd "$script_dir/../.." && pwd)
settings_file="$repo_root/.sandcastle/maven-settings.xml"
settings_template="$script_dir/maven-settings.xml"
if [ ! -f "$settings_file" ]; then
    if [ ! -f "$settings_template" ]; then
        echo "ERROR: 缺少 Maven 设置模板：$settings_template" >&2
        exit 1
    fi
    mkdir -p "$repo_root/.sandcastle"
    cp "$settings_template" "$settings_file"
fi

COMPROMISED_ROOTS='comodoaaaca|emsigneccrootcag3'

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/keytool" ]; then
    keytool_bin="$JAVA_HOME/bin/keytool"
elif command -v keytool >/dev/null 2>&1; then
    keytool_bin="keytool"
else
    echo "ERROR: 找不到 keytool（JAVA_HOME=${JAVA_HOME:-<未设置>}），无法校验 JDK 信任库" >&2
    exit 1
fi

# 先把输出读进变量：keytool 自身失败时必须报错，不能因为管道下游没匹配到就当成“干净”。
# 强制英文输出：条目类型字符串（trustedCertEntry）会随 JDK 语言包本地化，下面的条目数校验依赖它。
if ! listing=$("$keytool_bin" -J-Duser.language=en -J-Duser.country=US \
        -list -cacerts -storepass changeit 2>&1); then
    echo "ERROR: 读取 cacerts 失败，无法确认信任库状态（$keytool_bin）：" >&2
    echo "$listing" >&2
    exit 1
fi

if [ -z "$listing" ]; then
    echo "ERROR: cacerts 列表为空，无法确认信任库状态（$keytool_bin）" >&2
    exit 1
fi

entries=$(printf '%s\n' "$listing" | grep -c 'trustedCertEntry' || true)
if [ "${entries:-0}" -lt 1 ]; then
    echo "ERROR: cacerts 中没有任何可信证书条目，无法确认信任库状态（$keytool_bin）" >&2
    exit 1
fi

if printf '%s\n' "$listing" | grep -Eq "$COMPROMISED_ROOTS"; then
    echo "ERROR: JDK 信任库仍信任被泄的 eMudhra/Sectigo 根证书（CVE-2026-91403）" >&2
    printf '%s\n' "$listing" | grep -E "$COMPROMISED_ROOTS" >&2
    echo "修复：升级到 Temurin 17.0.21+9 / 21.0.13+9 / 25.0.5+8 或更高版本" >&2
    exit 1
fi

echo "OK: 信任库未包含被泄的 eMudhra/Sectigo 根证书（$keytool_bin，$entries 个可信条目）"
