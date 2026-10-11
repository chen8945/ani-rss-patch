#!/bin/bash
# 测试现有补丁能否应用于指定上游版本
# 用法: scripts/try-apply.sh v3.2.48
set -euo pipefail
VERSION="${1:?用法: try-apply.sh v3.2.48}"
cd "$(git rev-parse --show-toplevel)"
ROOT=$PWD

git fetch upstream --tags || echo "警告: fetch 失败(代理未开?), 使用本地已有的 tag 数据"

WT=$(mktemp -d)
git worktree add -q --detach "$WT" "$VERSION"
trap 'git worktree remove --force "$WT"' EXIT

for p in $(ls patches/v*.patch | sort -rV); do
  if git -C "$WT" apply --check "$ROOT/$p"; then
    echo "✓ $p 可应用于 $VERSION"
    exit 0
  fi
  echo "✗ $p 不适用于 $VERSION"
done

echo "所有补丁均不适用于 $VERSION 需人工适配" >&2
exit 1
