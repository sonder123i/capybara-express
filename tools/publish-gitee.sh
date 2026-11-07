#!/usr/bin/env bash
# 把已经签好名的发布包传到 Gitee Release。
#
# 用法：
#   GITEE_TOKEN=xxxx tools/publish-gitee.sh v0.3.0 showcode-0.3.0.apk SHA256SUMS.txt
#
# 令牌在 Gitee「设置 → 私人令牌」创建，勾 projects 权限即可。
# 这个脚本只新增发行版/附件，不删除任何已有内容；同名附件会跳过。
set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "usage: GITEE_TOKEN=... $0 <tag> <files...>" >&2
  exit 2
fi

TAG="$1"
shift
: "${GITEE_TOKEN:?GITEE_TOKEN is required}"
OWNER="${GITEE_OWNER:-sonder123i}"
REPO="${GITEE_REPO:-capybara-express}"
API="https://gitee.com/api/v5/repos/$OWNER/$REPO"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

existing=$(curl -sf --max-time 20 "$API/releases/tags/$TAG?access_token=$GITEE_TOKEN" || true)
id=$(printf '%s' "$existing" | jq -r 'if type=="object" then (.id // empty) else empty end')
if [ -z "$id" ]; then
  TITLE="卡皮巴拉快递助手 ${TAG#v}"
  CODE=$(grep -m1 'versionCode = ' app/build.gradle.kts | grep -o '[0-9]*' | head -1)
  NOTES="fastlane/metadata/android/zh-CN/changelogs/$CODE.txt"
  BODY=""
  [ -f "$NOTES" ] && BODY=$(cat "$NOTES")
  created=$(curl -sf --max-time 20 -X POST "$API/releases" \
    -H "Content-Type: application/json" \
    -d "$(jq -n --arg token "$GITEE_TOKEN" --arg tag "$TAG" --arg name "$TITLE" --arg body "$BODY" \
      '{access_token:$token, tag_name:$tag, name:$name, body:$body, target_commitish:"main"}')")
  id=$(printf '%s' "$created" | jq -r '.id // empty')
fi
if [ -z "$id" ]; then
  echo "无法在 Gitee 创建发行版（镜像仓库是否已推 main？）" >&2
  exit 1
fi

assets=$(curl -sf --max-time 20 "$API/releases/$id?access_token=$GITEE_TOKEN" | jq -r '.assets[]?.name' || true)
for f in "$@"; do
  name=$(basename "$f")
  if printf '%s\n' "$assets" | grep -Fxq "$name"; then
    echo "skip existing $name"
    continue
  fi
  curl -sf --max-time 300 -X POST "$API/releases/$id/attach_files" \
    -F "access_token=$GITEE_TOKEN" \
    -F "file=@$f" >/dev/null
  echo "uploaded $name"
done

echo "https://gitee.com/$OWNER/$REPO/releases/tag/$TAG"
