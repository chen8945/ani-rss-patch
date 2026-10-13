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
2. 检出上游该版本 tag，从新到旧应用第一个可用补丁，在本地提交到 `patch` 分支并打同名 tag；
3. `package.sh` 构建 jar/exe；
4. 构建并推送 Docker 镜像到 `ghcr.io/chen8945/ani-rss`（`latest` / `vX.Y.Z` 及 openj9 变体，amd64+arm64）；
5. 全部构建成功后推送分支和版本 tag，最后发布 Release 并附 `ani-rss.jar` / `ani-rss.exe`。

Release 作为版本检查的完成标志；构建或镜像发布失败时，不发布新 Release，重跑可继续构建该版本。
CI 只处理上游最新 Release，不补发中间版本。例如本仓库为 `v3.2.47`、上游最新为 `v3.2.49` 时，会在 `v3.2.49` 源码上应用兼容补丁，成功后直接发布 `v3.2.49` 的 tag 和 Release。

## 应用内更新

更新源已从上游切换到本仓库（`UpdateService` 改为查询本仓库 Release）：定时自动更新与设置-关于-更新都从本仓库检测并下载，拉取到的即是打过补丁的构建，补丁不会被官方包覆盖。版本判定逻辑与上游一致：major.minor 不变时才允许自动更新，跨 minor 版本需手动确认更新。

## 本地工具

- `scripts/gen-patch.sh vX.Y.Z`：从 main 分支执行，把 patch 分支上**已提交**的改动生成为 `patches/vX.Y.Z.patch`。从 upstream 获取指定版本，确认 `patch` 的父提交和 `ani-rss` 均为该版本后生成；成功才替换同名文件。
- `scripts/try-apply.sh vX.Y.Z`：从 upstream 获取指定版本，在临时 worktree 里测试现有补丁能否应用；适配新版本前先跑它验证。

两个脚本都保留本地发布标签；获取上游版本失败时会停止，需要能连接 upstream。

## 使用

```bash
docker pull ghcr.io/chen8945/ani-rss:latest
```
