# doc-src

这里是项目设计、开发和功能相关文档的单一事实源，按主题分类维护，方便快速了解当前实现与方案。

## 目录结构

```text
doc-src/
	README.md
	before_docing.md
	architecture/
	dev-core/
	feature-protocol/
	research/
	test-example/
	toolpkg_developer_documentation/
```

## 架构

- [应用源码包职责](architecture/package_layout.md)
- [默认工具架构与参数变更清单](architecture/DEFAULT_TOOLS_ARCH.md)
- [流式 Markdown 渲染引擎](architecture/RENDERER_ARCH.md)
- [双 Session 聊天 Runtime 前台服务方案](architecture/chat_runtime_foreground_service_plan.md)
- [Tool Stream Reconcile 方案](architecture/tool_stream_reconcile_plan.md)
- [记忆候选评分公式](architecture/memory_candidate_scoring_formula.md)
- [语音服务独立档案](architecture/speech_service_profiles.md)
- [媒体创作草图编辑器](architecture/sketch_editor.md)

## 开发核心

- [贡献指南](dev-core/CONTRIBUTING.md)
- [构建指南](dev-core/BUILDING.md)
- [Java Bridge 接口](dev-core/JAVA_BRIDGE_INTERFACE.md)
- [数据救援与数据库检测](dev-core/DATA_RECOVERY_DATABASE_HEALTH.md)

## 功能与协议

- [Markdown 聊天记录导入](feature-protocol/chat_import_markdown.md)
- [DeepSeek Responses 与 Web Search](feature-protocol/deepseek_responses_web_search.md)
- [外部 A2A Server](feature-protocol/external_a2a_server.md)
- [外部 HTTP 对话接口](feature-protocol/external_http_chat.md)
- [外部 Intent 对话接口](feature-protocol/external_intent_chat.md)
- [主题制作手册](feature-protocol/theme_authoring_guide.md)
- [主题 JavaScript 外观接口](feature-protocol/theme_script_api.md)
- [主题设置参数目录](feature-protocol/theme_settings_reference.md)
- [主题工作室手机验收指南](feature-protocol/theme_acceptance_checklist.md)
- [思考片段的 token 边界](feature-protocol/thinking_token_boundary.md)
- [AI 工具权限](feature-protocol/tool_permissions.md)
- [Tool Call 与 XML 内部协议](feature-protocol/toolcall_xml_conversion.md)
- [Waifu 展示、语音和通知边界](feature-protocol/waifu_rendering_boundary.md)
- [web-chat 终端命令](feature-protocol/web_chat_cli.md)
- [Workflow Intent 触发](feature-protocol/workflow_intent_trigger.md)

## 调研记录

- [MNN Tool Call 调研](research/mnn_toolcall_research.md)
- [TTS/STT 配置方案对比](research/speech_service_config_comparison.md)

## 测试示例

- [Markdown 聊天记录导入示例](test-example/chat_import_markdown_example.md)
- [ToolPkg Probe 耗时拆解](test-example/toolpkg_probe_timing_breakdown.md)

## ToolPkg 开发者文档

- [文档入口](toolpkg_developer_documentation/index.md)
- [快速开始](toolpkg_developer_documentation/01_getting_started/quick_start.md)
- [包模型](toolpkg_developer_documentation/02_package_model/manifest.md)
- [运行时](toolpkg_developer_documentation/03_runtime/global_api.md)
- [模块参考](toolpkg_developer_documentation/04_modules/index.md)
- [Hook 参考](toolpkg_developer_documentation/05_hooks/index.md)
- [UI 与 Compose](toolpkg_developer_documentation/06_ui_and_compose/index.md)
- [类型与库](toolpkg_developer_documentation/07_types_and_libraries/index.md)
- [示例](toolpkg_developer_documentation/08_examples/index.md)
- [兼容性](toolpkg_developer_documentation/09_compatibility/api_versions.md)

## 文档维护

- 新增文档时放入最匹配的主题目录，文件名体现内容主题
- 方案发生较大变化时同步更新相关设计文档，避免文档与当前实现不一致
- 移动文件前用 `rg` 检查引用，修改依赖其相对位置的链接
- 发布版与 CDN 不应直接索引项目目录位置
- 撰写规范见 [文档撰写指南](before_docing.md)
