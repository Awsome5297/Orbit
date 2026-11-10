---
For_Agent: 媒体创作完整对齐的分阶段实施记录
---

# 媒体创作

## 目标

在工具菜单新增“媒体创作”，对齐 rikkahub 的图片/视频生成、会话时间线、历史、供应商配置和后台任务能力。按功能重写，不复制 AGPL 源码。

## 阶段

- [ ] 阶段 1：`:mediagen` 模块、供应商配置、设置持久化、Room 实体与迁移、工具菜单入口。
- [ ] 阶段 2：OpenAI 图片生成/编辑、会话时间线、结果落盘、历史、删除、重试。
- [ ] 阶段 3：OpenRouter 图片/视频、异步轮询、视频封面。
- [ ] 阶段 4：阿里云百炼图片/视频与 workspaceId。
- [ ] 阶段 5：火山方舟、MiniMax、首尾帧与参考素材。
- [ ] 阶段 6：远程素材上传、前台服务恢复、批量保存/删除、备份、测试与文档。

## 阶段 1 清单

- [x] `settings.gradle.kts` 与 `app/build.gradle.kts` 接入 `:mediagen`。
- [x] 新增媒体生成公共模型、供应商配置和能力描述。
- [x] 新增 `MediaGenerationPreferences`，持久化供应商配置。
- [x] 新增 Room 实体、DAO，数据库版本 29→30 与迁移。
- [x] 新增 `Screen.MediaCreationSessions` 与工具菜单入口。
- [x] 新增会话列表页与空状态，能创建/删除空会话并进入详情占位。
- [x] 静态检查通过后本地提交；本地无 Android SDK，编译由 CI 验证。
- [x] 更新集成数据库迁移测试到版本 30。

## 阶段 2 进度

- [x] OpenAI 图片生成/编辑适配器。
- [x] 媒体供应商配置页（含 OpenAI、Aliyun、Volcengine、MiniMax、OpenRouter 配置）。
- [x] 会话详情生成、结果落盘、历史展示与删除。
- [x] 修正 CI 编译：MediaKind 导入与 OkHttp Headers 扩展。
- [ ] 参考图编辑 UI。
- [ ] 失败重试与中断恢复。

## 阶段 3 进度

- [x] OpenRouter 图片生成与视频任务适配。
- [x] 视频异步轮询与结果下载。
- [x] 视频本地播放（ExoPlayer）。
- [ ] 参考视频、参考音频与首尾帧素材 UI。

## 阶段 4 进度

- [x] 阿里云百炼图片同步生成（万相/千问）。
- [x] 阿里云视频异步任务与轮询。
- [x] workspaceId 占位符解析。
- [ ] 参考视频、参考音频与首尾帧素材 UI。

## 阶段 5 进度

- [x] 火山方舟 Seedream 图片生成。
- [x] 火山方舟 Seedance 视频任务与轮询。
- [x] MiniMax 视频任务与轮询。
- [ ] 首尾帧、参考视频、参考音频素材选择 UI。
- [ ] 失败重试与中断恢复。

## 阶段 6 进度

- [x] 参考图、首帧、尾帧本地选择与角色切换。
- [x] 按供应商能力阻止需要公网素材但未配置上传的请求。
- [ ] S3 兼容素材上传配置。
- [ ] 前台服务与中断恢复。
- [ ] 批量保存/删除。
- [ ] 生成结果备份与恢复。
- [ ] 供应商请求映射单测与完整文档。

草图画板已由 [草图编辑器重设计](../sketch-editor-redesign/index.md) 接管，本计划不再维护旧画板实现。

## 阶段 7：界面重构

参照 rikkahub 的 Material 3 Expressive 结构重做媒体创作三页，同时修正外层与页面内顶栏重复的问题。

- [x] 媒体路由隐藏外层 `AppContent` 顶栏，页面只保留一条自带顶栏。
- [x] 会话列表改为大标题、异形封面、生成中状态、改名/删除菜单。
- [x] 创作页改为时间线结果卡与悬浮输入区，补齐素材缩略图、状态、保存/分享/重试。
- [x] 服务设置改为供应商列表与详情页，模型改为结构化编辑。
- [x] 数据层刷新会话封面与活动任务数。
- [x] 单元测试、`assembleDebug` 与分支推送。

GitHub Actions 结果：Android Tests 通过，Build（`assembleDebug`）通过，产物 `operit-android-21`。

以下交互作为后续增量，不在本次界面重构内：版本切换、多选批量保存/删除、供应商拖动排序、连接测试与素材存储分页。

### 设计约束

- 当前 Material3 版本为 1.4.0，Flexible 顶栏与 Expressive 注解为 internal，沿用公开的 `LargeTopAppBar`；`MaterialShapes`、`ContainedLoadingIndicator`、`HorizontalFloatingToolbar` 用自绘组件替代。
- 结果缩略图沿用紧凑上限，点击后全屏预览。
- 只参考 rikkahub 的布局与交互，不复制 AGPL 源码。

## 验证

- GitHub Actions `Android Tests` 通过。
- GitHub Actions `Build`（`assembleDebug`）通过，产物 `operit-android-21`。
- 本地不执行 Gradle 构建，构建与测试统一由 GitHub Actions 完成。
