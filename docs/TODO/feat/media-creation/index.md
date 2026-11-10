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

## 验证

- `./gradlew :app:compileDebugKotlin`
- Room 迁移与设置序列化单测
- 手动检查工具菜单入口、会话创建/删除、返回栈
