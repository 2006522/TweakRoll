#!/usr/bin/env bash
# 下载编译所需依赖到 deps/（一次性）
#  - client.jar     Minecraft 26.4-snapshot-5 官方客户端（未混淆）
#  - loader.jar     fabric-loader 0.19.5（只为编译期 API）
#  - brigadier.jar  Component.translatable 需要
#  - fapi/          fabric-api 0.164.1+26.4 拆出来的各模块 jar
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
DEPS="${DEPS:-$ROOT/deps}"
mkdir -p "$DEPS/fapi"

CURL="curl -L -sS --ssl-no-revoke --connect-timeout 10 --max-time 120"

# 本机启动器缓存（Axolotl 会把依赖放在 meta/libraries 下）
MC_LIBS="${MC_LIBS:-E:/Axolotl.Launcher/meta/libraries}"

# 先从本机缓存拿，拿不到再走网络。bmclapi 经常整条链路卡死，别死等。
from_local() {  # $1 = libraries 下的相对路径, $2 = 目标文件
  local src="$MC_LIBS/$1"
  [ -f "$src" ] || return 1
  echo "   用本机缓存：$src"
  cp -f "$src" "$2"
}

MC_URL="https://piston-data.mojang.com/v1/objects/dabe2b9759859995565e7d01c4a96d05043718ee/client.jar"
FAPI_URL="https://cdn.modrinth.com/data/P7dR8mSH/versions/ZsOLXwvh/fabric-api-0.164.1%2B26.4.jar"
LOADER_URL="https://bmclapi2.bangbang93.com/maven/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar"
MIXIN_URL="https://bmclapi2.bangbang93.com/maven/net/fabricmc/sponge-mixin/0.17.4+mixin.0.8.7/sponge-mixin-0.17.4+mixin.0.8.7.jar"
BRIG_URL="https://libraries.minecraft.net/com/mojang/brigadier/1.3.11/brigadier-1.3.11.jar"
DFU_URL="https://bmclapi2.bangbang93.com/maven/com/mojang/datafixerupper/11.0.22/datafixerupper-11.0.22.jar"

echo "== client.jar =="
[ -f "$DEPS/client.jar" ] || $CURL -o "$DEPS/client.jar" "$MC_URL"

echo "== loader.jar =="
[ -f "$DEPS/loader.jar" ] || $CURL -o "$DEPS/loader.jar" "$LOADER_URL"

echo "== brigadier.jar =="
[ -f "$DEPS/brigadier.jar" ] || $CURL -o "$DEPS/brigadier.jar" "$BRIG_URL"

echo "== fabric-api 模块 =="
if [ -z "$(ls -A "$DEPS/fapi" 2>/dev/null)" ]; then
  TMP="$DEPS/.fapi-tmp"
  rm -rf "$TMP"; mkdir -p "$TMP"
  $CURL -o "$TMP/fabric-api.jar" "$FAPI_URL"
  unzip -q -o "$TMP/fabric-api.jar" 'META-INF/jars/*' -d "$TMP"
  cp "$TMP"/META-INF/jars/*.jar "$DEPS/fapi/"
  rm -rf "$TMP"
fi

echo "完成：依赖在 $DEPS"
ls -1 "$DEPS"
echo "fabric-api 模块数：$(ls -1 "$DEPS/fapi" | wc -l)"

echo "== dfu.jar（com.mojang.serialization，编译 Registry 相关 API 需要）=="
# libraries.minecraft.net 直连比 bmclapi 稳，先试它
if [ ! -f "$DEPS/dfu.jar" ]; then
  $CURL -o "$DEPS/dfu.jar" \
    "https://libraries.minecraft.net/com/mojang/datafixerupper/11.0.22/datafixerupper-11.0.22.jar" \
    || $CURL -o "$DEPS/dfu.jar" "$DFU_URL"
fi

echo "== mixin.jar =="
if [ ! -f "$DEPS/mixin.jar" ]; then
  from_local "net/fabricmc/sponge-mixin/0.17.4+mixin.0.8.7/sponge-mixin-0.17.4+mixin.0.8.7.jar" \
    "$DEPS/mixin.jar" || $CURL -o "$DEPS/mixin.jar" "$MIXIN_URL"
fi

echo "完成：$(ls -1 "$DEPS"/*.jar | wc -l) 个 jar + fapi $(ls -1 "$DEPS/fapi" 2>/dev/null | wc -l) 个模块"
