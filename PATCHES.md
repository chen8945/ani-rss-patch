# 补丁契约

每个补丁组的**语义规格**。上游新版本导致补丁失效时，按本契约重新适配，而不是机械修补旧 diff——旧补丁只是证据，契约才是设计。
维护流程见 `.agents/skills/maintaining-ani-rss-patches/SKILL.md`。

## 通用不变量

- 补丁位于 `patches/`，以**适用起始版本**命名（`vX.Y.Z.patch`），与上游 tag 同格式；某组失效需要适配时，产出以**目标版本**命名的新文件，旧文件保留（文件夹列表即适用范围表）。
- patch 分支永远 = 上游 tag 提交 + 单个"应用补丁"提交；补丁内容恒等于 `git diff ani-rss patch`；patch 分支树中不得出现 `patches/` 目录。
- 每处改动必须带中文注释说明意图（补丁是写给人看的）。
- CI 从新到旧应用第一个 `git apply --check` 通过的补丁；全部失败即构建失败并通知。

## 组 1：bangumi.ini 双层生成

涉及：`ScrapeService.saveBangumiIni`、`DownloadService` 两处调用、`ScrapeView.vue`

1. 开启 `bangumiIniEnabled` 时，生成不依赖自动刮削：订阅开始下载时、单集下载完成后各触发一次（`DownloadService` 内，try/catch 包裹，失败仅记日志，不影响下载流程）。
2. 每部番剧只生成一次：标记文件 `配置目录/bangumi_ini/{条目id}`，全部写入成功后才 touch；删除标记与对应 ini 可重新生成。
3. 双层内容：季目录 `[Bangumi] id + offset`（offset 空取 0）；顶层（父目录）只含 `[Bangumi] id`（整剧条目）。
4. 剧场版（`ani.getOva()`）下载位置即影片目录，不写顶层。
5. 下载目录不存在时静默跳过且**不落标记**（下载器重建目录后自然补生成，不会出现"移走后永远不生成"）。
6. 方法签名保留上游 `saveBangumiIni(Ani ani, Boolean force)`，`force` 无实际作用——刮削调用点不动，降低补丁冲突面。
7. UI：开关置于刮削设置页"自动刮削"**上方**，附说明"下载时自动生成 无需开启自动刮削"。

## 组 2：更新源指向本仓库

涉及：`UpdateService.about()`、`AboutView.vue` 版本号链接

1. `githubService.getUpdateInfo("chen8945", "ani-rss-patch", filename, version)`——只改 owner/repo 两个字符串字面量并附注释，其余在线更新逻辑保持上游原样。
2. 自动更新（`CronConfig` 定时任务）与手动更新（设置-关于）都经由 `about()`，改一处即全覆盖。
3. 关于页更新弹窗"版本号"链接指向本仓库 Release：`https://github.com/chen8945/ani-rss-patch/releases/tag/v${about.latest}`。
4. 前提不变量：CI 每个版本发布同名 tag 的 Release 并附 `ani-rss.jar` / `ani-rss.exe` 资产。

## 组 3：qBittorrent 双模式认证

涉及：`qBittorrent.login/getApi/postApi/addAuth`、`DownloadView.vue` qB 分支

1. 模式判据：密码字段以 `qbt_` 开头 = **ApiKey 模式**（`Authorization: Bearer`，上游现行行为）；否则 = **账号密码模式**（还原上游 660c375f 移除的实现：`POST /api/v2/auth/login` + 会话 Cookie，兼容 5.2.0+ 空 body 响应）。
2. 账密模式要求用户名非空，否则告警返回 false；ApiKey 模式忽略用户名。
3. 所有 API 调用统一经 `getApi/postApi` → `addAuth()`：仅 ApiKey 模式附加 Bearer 头；账密模式依赖全局 CookieManager 自动携带会话，不附加请求头。
4. UI：qB 分支恢复"用户名"输入框（placeholder 提示留空使用 ApiKey）；密码框标签与占位符随是否填写用户名动态切换"密码/password"与"ApiKey/qbt_xxxx"；ApiKey 格式告警仅在未填用户名时显示。

## 组 4：bgm 标题年份规则

涉及：`BgmUtil`、`TmdbUtils`

1. 使用 bgm 标题命名时，年份取 **bgm 条目自身播出年份**，不使用 tmdb 整剧首播年份。
2. tmdb 只负责追加 id 后缀：`TmdbUtils.appendTmdbId(title, tmdb)`（自 `getFinalName` 抽出的公开方法；`tmdb` 为 null 时原样返回；`getFinalName` 其余行为不变）。
3. `tmdbId` / `tmdbIdPlexMode` 后缀格式沿用上游配置逻辑。

## 组 5：RSS 间隔最小值

涉及：`RssView.vue`

`rssSleepMinutes` 输入框 `:min` 由 10 改为 1。

## 组 6：通知标题与 Bangumi ID 变量

涉及：`BaseNotification.replaceNotificationTemplate`

1. 通知变量 `${title}`、`${themoviedbName}` 仅剥离 `StringEnum.TMDB_ID_REG` 匹配的 `[tmdbid=数字]` / `{tmdb-数字}`，保留年份及其他标题内容，不受重命名剔除选项影响。
2. 只处理变量替换值，不修改 `Ani` 数据、文件命名或实际下载路径；`${text}`、`${downloadPath}`、`${tmdbid}` 维持原有语义，邮件主题等独立标题不在本组范围内。
3. 新增小写 `${bgmid}`：仅从已保存的 `Ani.bgmUrl` 提取 Bangumi 条目 ID，不触发额外网络请求、不修改订阅；支持任意域名或主机的 HTTP(S) `/subject/数字` 链接，可带端口、尾斜杠、查询参数和片段。缺失或不匹配时替换为空字符串。
4. 全局模板、自定义模板及 `${notification}` 嵌套模板统一生效；Shell/WebHook 共用此替换器，依赖旧标题拼路径的模板需相应调整。
5. 变量说明仅记录于本文档，不改通知设置界面和默认模板；捐赠验证不变。

复用边界：TMDB 清理复用上游 `StringEnum.TMDB_ID_REG`，不调用同时处理年份和重命名设置的 `RenameUtil.renameDel`。Bangumi ID 不直接复用 `BgmUtil.getSubjectId`：字符串重载对缺失或不支持的链接抛异常，订阅重载还可能联网补全并修改订阅；通知变量保持本地提取、缺失留空。
