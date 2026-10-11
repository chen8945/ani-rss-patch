# ani-rss-patch

[wushuo894/ani-rss](https://github.com/wushuo894/ani-rss) 的补丁构建仓库。CI 定时跟踪上游新版本，自动应用补丁并构建，版本号与上游保持一致。

## 分支结构

| 分支 | 说明 | 维护方式 |
| --- | --- | --- |
| `main` | 补丁、CI、文档（本分支） | 人工维护 |
| `patch` | 上游代码 + 补丁应用后的快照 | CI 维护，每个上游版本一个提交，可随时重建，不手工改 |
| `ani-rss` | 纯净上游镜像 | 跟踪 upstream，用于对比和生成补丁 |

## 补丁约定

- 位于 `patches/`，以**适用起始版本**命名（如 `v3.2.47.patch`），与上游 tag 命名一致。
- 上游新版本导致补丁不兼容时，以新版本号另存一份新补丁；旧文件保留，文件夹列表即为各补丁的适用范围表。
- CI 选取逻辑：从新到旧尝试应用，取第一个通过 `git apply --check` 的补丁；全部失败则构建失败（收到通知后人工适配）。
- 补丁权威生成方式：patch 分支永远只比上游基点多一个"应用补丁"提交，`git diff ani-rss patch` 即补丁内容。
- 所有改动必须附带中文注释，说明改了什么、为什么，方便日后理解补丁。
- **失效适配看 [`PATCHES.md`](PATCHES.md)**：每个补丁组的语义契约——旧补丁只是证据，契约才是设计。
- `.agents/skills/maintaining-ani-rss-patches/SKILL.md`：补丁维护技能，上游新版本补丁失效时照此流程适配。

## 自动构建（sync-build.yaml）

每天一次（可手动触发）：

1. 查询上游 `releases/latest`，与本仓库最新 Release 对比，相同则跳过；
2. 检出上游该版本 tag，从新到旧应用第一个可用补丁，提交到 `patch` 分支并打同名 tag；
3. `package.sh` 构建 jar/exe，发布 Release 并附 `ani-rss.jar`（应用内更新的下载源）；
4. 构建并推送 Docker 镜像到 `ghcr.io/chen8945/ani-rss`（`latest` / `vX.Y.Z` 及 openj9 变体，amd64+arm64）。

## 应用内更新

更新源已从上游切换到本仓库（`UpdateService` 改为查询本仓库 Release）：定时自动更新与设置-关于-更新都从本仓库检测并下载，拉取到的即是打过补丁的构建，补丁不会被官方包覆盖。版本判定逻辑与上游一致：major.minor 不变时才允许自动更新，跨 minor 版本需手动确认更新。

## 本地工具

- `scripts/gen-patch.sh vX.Y.Z`：从 main 分支执行（脚本只存在于 main），把 patch 分支上**已提交**的改动生成为 `patches/vX.Y.Z.patch`（内置 ani-rss 基点一致性校验）。
- `scripts/try-apply.sh vX.Y.Z`：在临时 worktree 里测试现有补丁能否应用于指定上游版本，适配新版本前先跑它验证。

## 使用

```bash
docker pull ghcr.io/chen8945/ani-rss:latest
```
