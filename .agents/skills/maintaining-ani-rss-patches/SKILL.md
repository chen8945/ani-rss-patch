---
name: maintaining-ani-rss-patches
description: 维护 ani-rss-patch 仓库的版本命名补丁。Use when a patch in patches/ fails to apply to a new upstream wushuo894/ani-rss release (git apply --check 或 scripts/try-apply.sh 失败), when sync-build CI reports 所有补丁均无法应用, when adapting or regenerating a version-named patch for a new upstream version, or when adding a new patch group. 把旧补丁当证据, PATCHES.md 当契约。
---

# Maintaining ani-rss Patches

## Overview

本仓库用 git diff 补丁（非语义脚本）跟随上游 wushuo894/ani-rss。补丁失效时的正确动作是**按契约产出一个新的版本命名补丁**，而不是修补旧文件。

先读两样东西再动手：

1. `PATCHES.md`（仓库根）——每个补丁组的语义契约，是设计依据。
2. 失效的旧补丁对应 hunk——只是证据：上游可能已经等价实现了某一组，该组应从新补丁中**删除**而非照搬。

## Output Contract

- 唯一交付物：`patches/vX.Y.Z.patch`（X.Y.Z = 适配的目标上游版本，与 tag 同格式）。
- 不改 CI、脚本、README；不在 patch 分支堆额外提交（必须保持"上游 tag 提交 + 单个应用补丁提交"的结构，CI 的树哈希验证依赖它）。
- 全程在临时 worktree 或 patch 分支工作区操作，不碰用户未提交的改动。
- 补丁内每处改动带中文注释说明意图。

## 适配流程

1. **复现失败**：运行 `bash scripts/try-apply.sh vX.Y.Z`，脚本会获取指定上游版本并验证补丁，保留本地发布标签；确认哪些文件、哪些 hunk 失效。获取上游版本失败时先恢复连接。
2. **逐组分类**：对每个失效 hunk，回到 `PATCHES.md` 对应组，判定三类：
   - 上游已实现同等行为 → 该组从新补丁**删除**，并同步删除 PATCHES.md 中该组；
   - 上游结构变了但契约仍成立 → 按契约在新代码上重写该组；
   - 契约与上游新行为冲突、语义不明 → 停下来问用户，不要猜。
3. **重演**：执行 `git fetch --no-tags upstream refs/tags/vX.Y.Z`，成功后用 `BASE=$(git rev-parse 'FETCH_HEAD^{commit}')` 固定上游基点；执行 `git branch -f ani-rss "$BASE"` 同步镜像，再 `git checkout -B patch "$BASE"` 重建，按契约逐组实现。
4. **生成**：patch 分支提交"应用补丁"提交后切回 main，运行 `bash scripts/gen-patch.sh vX.Y.Z`。脚本会确认 `patch` 的父提交和 `ani-rss` 均为指定上游版本，再导出两者差异；生成成功才替换同名文件。
5. **过验证门**，全部通过才算完成。

## 验证门

- 干净检出已确认的上游基点，`git apply --check` 通过（`scripts/try-apply.sh` 的职责）。
- 树哈希往返：临时 worktree 检出上述上游基点 → 应用新补丁 → 提交后的树哈希必须与 `git rev-parse patch^{tree}` 完全一致。
- 逐组核对：`PATCHES.md` 每一条契约都能在结果树中指认（grep 关键符号/注释），包括"应被删除的组确实不存在"。
- patch 分支树中不含 `patches/` 目录（否则 CI 生成的补丁会自引用、树哈希对不上）。
- 任一门未过不得交付；语义不明的问题上报用户。

## Common Mistakes

- 把旧补丁当设计照搬到新版本：上游行为漂移后旧 hunk 可能多余甚至有害，契约才是唯一依据。
- 在 patch 分支上多提交或改动结构：`git diff ani-rss patch` 就不再等于补丁内容，树哈希验证失效。
- 补丁命名用日期或"当前版本"而非**目标版本**：CI 靠文件名版本号从新到旧选取，命名错则选取逻辑失效。
- 只修第一个报错：`git apply` 按文件顺序失败，后面的失效点被掩盖；逐组核对契约才是完备检查。
- 忘记同步 ani-rss 分支就生成补丁：`git diff ani-rss patch` 会把上游版本间的改动也混进补丁，gen-patch.sh 的基点校验会拦住，但要知道为什么。
