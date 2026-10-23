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

CURL="curl -L -sS --ssl-no-revoke"

MC_URL="https://piston-data.mojang.com/v1/objects/dabe2b9759859995565e7d01c4a96d05043718ee/client.jar"
FAPI_URL="https://cdn.modrinth.com/data/P7dR8mSH/versions/ZsOLXwvh/fabric-api-0.164.1%2B26.4.jar"
LOADER_URL="https://bmclapi2.bangbang93.com/maven/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar"
BRIG_URL="https://libraries.minecraft.net/com/mojang/brigadier/1.3.11/brigadier-1.3.11.jar"

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
