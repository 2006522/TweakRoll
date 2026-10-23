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

echo "== 提交并推送 =="
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

echo "== 建 Release =="
REL=$(curl -sS --ssl-no-revoke -X POST \
  -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" \
  -H "Content-Type: application/json; charset=utf-8" \
  -d "{\"tag_name\":\"$TAG\",\"target_commitish\":\"main\",\"name\":\"$TITLE\",\"body\":\"鼠标手势 (TweakRoll) $VER\",\"draft\":false,\"prerelease\":false}" \
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
echo "完成：https://github.com/$REPO/releases/tag/$TAG"
echo "直链：https://github.com/$REPO/releases/download/$TAG/$ASSET"
