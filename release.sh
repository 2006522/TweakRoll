#!/usr/bin/env bash
# 一键发版：构建 -> 提交 -> 推送 -> 建 Release -> 上传 jar
# 命名规则固定（用户要求）：Release 标题与附件显示名 = 「鼠标手势 (TweakRoll) <版本号>」
# 附件实际文件名只能是 ASCII：TweakRoll-<版本号>.jar
set -euo pipefail
cd "$(dirname "$0")"

REPO="2006522/TweakRoll"
VER=$(grep -o '"version": "[^"]*"' src/main/resources/fabric.mod.json | head -1 | sed 's/.*: "//;s/"//')
TAG="v$VER"
TITLE="鼠标手势 (TweakRoll) $VER"
JAR="dist/tweakroll-$VER+mc26.4-snapshot-5.jar"
ASSET="TweakRoll-$VER.jar"

echo "== 版本 $VER (tag $TAG) =="

echo "== 构建 =="
bash build.sh
[ -f "$JAR" ] || { echo "构建产物不存在: $JAR" >&2; exit 1; }

echo "== 同步 README 与 release/ 里的版本号 =="
sed -i -E "s|version-[0-9]+\.[0-9]+\.[0-9]+-blue|version-$VER-blue|g; s|releases/download/v[0-9.]+/TweakRoll-[0-9.]+\.jar|releases/download/$TAG/TweakRoll-$VER.jar|g; s|鼠标手势 \(TweakRoll\) [0-9]+\.[0-9]+\.[0-9]+|鼠标手势 (TweakRoll) $VER|g; s|TweakRoll-[0-9]+\.[0-9]+\.[0-9]+\.jar|TweakRoll-$VER.jar|g" README.md
mkdir -p release
find release -maxdepth 1 -name '*.jar' ! -name "*$VER*" -delete
cp -f "dist/tweakroll-$VER+mc26.4-snapshot-5.jar" "release/鼠标手势 (TweakRoll) $VER.jar"

echo "== 提交并推送 =="
export SKIP_HOOK_DEPLOY=1
git add -A
if git diff --cached --quiet; then
  echo "没有改动需要提交"
else
  git commit -m "release: $TITLE"
fi
git push origin main

echo "== 取 GitHub 凭据 =="
TOKEN=$(printf "protocol=https\nhost=github.com\n\n" | git credential fill | sed -n 's/^password=//p')
[ -n "$TOKEN" ] || { echo "拿不到 GitHub 凭据，先在 git 里登录一次" >&2; exit 1; }

echo "== 生成发布说明 =="
SHA=$(sha256sum "$JAR" | cut -d' ' -f1)
if [ -f build/changes.md ]; then
  # 可以提前把面向用户的说明写进 build/changes.md
  CHANGES=$(cat build/changes.md)
else
  CHANGES=$(awk -v ver="## v$VER" 'index($0, ver) == 1 {f=1; next} f && /^## / {f=0} f' CHANGELOG.md)
fi
[ -n "$CHANGES" ] || CHANGES="- 见 CHANGELOG.md"
mkdir -p build
{
  echo '按住 **Shift** + 按住**左键**，鼠标扫过哪个格子，那格物品就快速转移：'
  echo
  echo '- 物品栏（快捷栏）→ 背包'
  echo '- 背包 → 物品栏（容器界面里则与容器互换）'
  echo
  echo '按下的第一格由原版处理，之后扫过的格子由模组补上，不会重复转移；中途松开 Shift 立刻停止。'
  echo
  echo '### 本版变更'
  echo
  printf '%s\n' "$CHANGES"
  echo
  echo '### 环境'
  echo
  echo '| Minecraft | Fabric Loader | Fabric API | Java |'
  echo '| --- | --- | --- | --- |'
  echo '| 26.4-snapshot-5 | ≥ 0.19.5 | 0.164.1+26.4 或更高 | 25 |'
  echo
  echo '### 安装'
  echo
  echo '1. 装好 Fabric Loader 和 Fabric API'
  echo "2. 下载 \`$ASSET\` 放进 \`.minecraft/mods/\`"
  echo
  echo '### 校验'
  echo
  echo "SHA-256：\`$SHA\`"
} > build/release-body.md
BODY=$(sed ':a;N;$!ba;s/\\/\\\\/g;s/"/\\"/g;s/\n/\\n/g' build/release-body.md)

echo "== 建 Release =="
REL=$(curl -sS --ssl-no-revoke -X POST \
  -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" \
  -H "Content-Type: application/json; charset=utf-8" \
  -d "{\"tag_name\":\"$TAG\",\"target_commitish\":\"main\",\"name\":\"$TITLE\",\"body\":\"$BODY\",\"draft\":false,\"prerelease\":false}" \
  "https://api.github.com/repos/$REPO/releases")
REL_ID=$(printf '%s' "$REL" | grep -o '"id": *[0-9]*' | head -1 | grep -o '[0-9]*')
[ -n "$REL_ID" ] || { echo "创建 Release 失败：$REL" >&2; exit 1; }
echo "release id = $REL_ID"

echo "== 上传 jar =="
UPLOADED=$(curl -sS --ssl-no-revoke -X POST \
  -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" \
  -H "Content-Type: application/java-archive" \
  --data-binary @"$JAR" \
  "https://uploads.github.com/repos/$REPO/releases/$REL_ID/assets?name=$ASSET")
ASSET_ID=$(printf '%s' "$UPLOADED" | grep -o '"id": *[0-9]*' | head -1 | grep -o '[0-9]*')
[ -n "$ASSET_ID" ] || { echo "上传失败：$UPLOADED" >&2; exit 1; }

echo "== 设置中文显示名 =="
curl -sS --ssl-no-revoke -X PATCH \
  -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" \
  -H "Content-Type: application/json; charset=utf-8" \
  -d "{\"label\":\"$TITLE\"}" \
  "https://api.github.com/repos/$REPO/releases/assets/$ASSET_ID" > /dev/null

echo
echo "== 部署到游戏 mods =="
bash deploy.sh

echo
echo "完成：https://github.com/$REPO/releases/tag/$TAG"
echo "直链：https://github.com/$REPO/releases/download/$TAG/$ASSET"
