#!/bin/bash
# 测试现有补丁能否应用于指定上游版本
# 用法: scripts/try-apply.sh v3.2.48
set -euo pipefail
VERSION="${1:?用法: try-apply.sh v3.2.48}"
if [[ $# -ne 1 || ! "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "版本格式必须为 vX.Y.Z，且只接受一个版本参数" >&2
  exit 1
fi
cd "$(git rev-parse --show-toplevel)"
ROOT=$PWD

# 只读取指定上游标签，保留本地发布标签；获取失败立即停止，避免验证错误基点
git fetch --no-tags upstream "refs/tags/$VERSION"
BASE=$(git rev-parse --verify 'FETCH_HEAD^{commit}')

WT=$(mktemp -d)
# worktree 创建失败时也清理 mktemp 留下的空目录
trap 'git worktree remove --force "$WT" 2>/dev/null || rmdir "$WT"' EXIT
git worktree add -q --detach "$WT" "$BASE"

for p in $(ls patches/v*.patch | sort -rV); do
  if git -C "$WT" apply --check "$ROOT/$p"; then
    echo "✓ $p 可应用于 $VERSION"
    exit 0
  fi
  echo "✗ $p 不适用于 $VERSION"
done

echo "所有补丁均不适用于 $VERSION 需人工适配" >&2
exit 1
