#!/usr/bin/env bash
# 把构建好的 mod 自动放进游戏 mods 目录
# 用法：./deploy.sh            （默认实例）
#      MODS=/path/to/mods ./deploy.sh
set -euo pipefail
cd "$(dirname "$0")"

MODS="${MODS:-E:/Axolotl.Launcher/profiles/26.4-snapshot-5-Fabric 0.19.5/mods}"
VER=$(grep -o '"version": "[^"]*"' src/main/resources/fabric.mod.json | head -1 | sed 's/.*: "//;s/"//')
JAR="dist/tweakroll-$VER+mc26.4-snapshot-5.jar"
TARGET="$MODS/TweakRoll.jar"
BACKUP="$(dirname "$MODS")/_mods_backup"

[ -d "$MODS" ] || { echo "mods 目录不存在: $MODS" >&2; exit 1; }
[ -f "$JAR" ] || { echo "构建产物不存在: $JAR（先跑 ./build.sh）" >&2; exit 1; }

mkdir -p "$BACKUP"

echo "== 清掉 mods 里其它版本的同 id jar =="
shopt -s nullglob
for f in "$MODS"/*.jar; do
  name=$(basename "$f")
  case "$name" in
    *tweakroll*|*TweakRoll*|*鼠标手势*)
      if [ "$f" != "$TARGET" ]; then
        echo "   移出: $name"
        mv -f "$f" "$BACKUP/$name"
      fi
      ;;
  esac
done

# mods/_backup 里的旧 jar 也挪到 mods 外面，避免被 Loader 扫到
if [ -d "$MODS/_backup" ]; then
  echo "== 把 mods/_backup 挪到 $BACKUP =="
  mv -f "$MODS/_backup" "$BACKUP/_backup_$(date +%H%M%S)"
fi

echo "== 复制 $VER =="
cp -f "$JAR" "$TARGET"
echo "已安装: $TARGET"
unzip -p "$TARGET" fabric.mod.json | grep -E '"version"|"name"'
ls -lh "$TARGET" | awk '{print "大小:", $5}'
