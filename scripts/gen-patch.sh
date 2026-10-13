#!/bin/bash
# 生成补丁到 patches/
# 用法: 在 main 分支执行 scripts/gen-patch.sh vX.Y.Z
# 前提: patch 分支上的修改已提交 (脚本比较的是两个分支的已提交树, 未提交的改动不会进补丁)
set -euo pipefail
VERSION="${1:?用法: gen-patch.sh vX.Y.Z}"
if [[ $# -ne 1 || ! "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "版本格式必须为 vX.Y.Z，且只接受一个版本参数" >&2
  exit 1
fi
cd "$(git rev-parse --show-toplevel)"

# 补丁文件只写入 main，避免在 patch 分支生成自引用的 patches/ 目录
if [ "$(git branch --show-current)" != main ]; then
  echo "请切回 main 分支后生成补丁" >&2
  exit 1
fi

# 版本名必须对应真实上游基点，本地同名标签可能已经指向补丁提交
git fetch --no-tags upstream "refs/tags/$VERSION"
BASE=$(git rev-parse --verify 'FETCH_HEAD^{commit}')
PATCH_COMMIT=$(git rev-parse --verify 'refs/heads/patch^{commit}')
if [ "$(git rev-parse --verify "$PATCH_COMMIT^")" != "$BASE" ] ||
   [ "$(git rev-parse --verify 'refs/heads/ani-rss^{commit}')" != "$BASE" ]; then
  echo "基点不一致: patch 的父提交和 ani-rss 必须均为上游 $VERSION ($BASE)" >&2
  echo "请基于 $BASE 重建 patch 的单个补丁提交，并同步镜像: git branch -f ani-rss $BASE" >&2
  exit 1
fi

# 未提交改动提醒: patch 分支上没提交的修改不会包含在 diff 里
if [ -n "$(git status --porcelain -- . ':!patches')" ]; then
  echo "警告: 工作区存在 patches/ 之外的未提交改动, 补丁只包含两个分支已提交的内容" >&2
fi

mkdir -p patches
# 在同一目录完整生成后再替换，diff 失败时保留旧补丁；禁用会影响补丁内容的展示配置
TMP_PATCH=$(mktemp "patches/.${VERSION}.patch.XXXXXX")
trap 'rm -f -- "$TMP_PATCH"' EXIT
git diff --no-ext-diff --no-textconv --no-color "$BASE" "$PATCH_COMMIT" -- > "$TMP_PATCH"
mv -fT -- "$TMP_PATCH" "patches/${VERSION}.patch"
echo "已生成 patches/${VERSION}.patch ($(grep -c '^diff --git' "patches/${VERSION}.patch") 个文件)"
echo "别忘了提交到 main 分支"
