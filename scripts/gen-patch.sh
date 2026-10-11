#!/bin/bash
# 生成补丁到 patches/
# 用法: 在 main 分支执行 scripts/gen-patch.sh vX.Y.Z
# 前提: patch 分支上的修改已提交 (脚本比较的是两个分支的已提交树, 未提交的改动不会进补丁)
set -euo pipefail
VERSION="${1:?用法: gen-patch.sh vX.Y.Z}"
cd "$(git rev-parse --show-toplevel)"

# 基点校验: patch 分支的父提交必须是 ani-rss 指向的上游提交
# 否则 diff 会混入上游版本间的改动 (适配新版本前先 git branch -f ani-rss upstream/main)
if [ "$(git rev-parse patch^)" != "$(git rev-parse ani-rss)" ]; then
  echo "基点不一致: patch^ ($(git rev-parse --short patch^)) != ani-rss ($(git rev-parse --short ani-rss))" >&2
  echo "先同步 ani-rss 到目标 tag: git branch -f ani-rss upstream/main" >&2
  exit 1
fi

# 未提交改动提醒: patch 分支上没提交的修改不会包含在 diff 里
if [ -n "$(git status --porcelain -- . ':!patches')" ]; then
  echo "警告: 工作区存在 patches/ 之外的未提交改动, 补丁只包含两个分支已提交的内容" >&2
fi

mkdir -p patches
git diff ani-rss patch > "patches/${VERSION}.patch"
echo "已生成 patches/${VERSION}.patch ($(grep -c '^diff --git' "patches/${VERSION}.patch") 个文件)"
echo "别忘了提交到 main 分支"
