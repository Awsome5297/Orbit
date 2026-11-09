# 文档索引

本目录集中存放项目说明、长期维护文档、开发计划与图片资源。长期文档以 `doc-src` 为单一事实源，`TODO` 用于协作中的计划，根 README 的演示图片位于 `assets/readme`。

## 目录结构

```text
docs/
	README.md
	SCRIPT_DEV_GUIDE.md
	SCRIPT_DEV_SKILL.md
	TOOLPKG_FORMAT_GUIDE.md
	agent/
		a2ui.md
	assets/
		readme/
	doc-src/
		architecture/
		dev-core/
		feature-protocol/
		research/
		test-example/
		toolpkg_developer_documentation/
	TODO/
		feat/
		fix/
		ci/
		chore/
		docs/
	.META/
		Legacy/
```

## 常用入口

- [脚本开发指南](SCRIPT_DEV_GUIDE.md)：TypeScript 脚本与 ToolPkg 的开发、构建和发布流程
- [SandboxPackage_DEV](SCRIPT_DEV_SKILL.md)：应用内 ToolPkg 开发环境的安装、更新与调试说明
- [ToolPkg 格式说明](TOOLPKG_FORMAT_GUIDE.md)：`.toolpkg` 的清单、结构和运行时约定
- [原生 A2UI](agent/a2ui.md)：A2UI 的生成、校验、修订与卡片渲染
- [长期维护文档](doc-src/README.md)：架构、开发核心、功能协议、调研与 ToolPkg 参考
- [开发计划](TODO/README.md)：按 feat、fix、ci、chore、docs 分类的协作计划

## 目录职责

- `assets/readme/`：根 README 与语言版本使用的图片资源
- `doc-src/`：经维护的设计、开发与协议文档，方案变化时同步更新
- `TODO/`：正在进行或待接手的开发计划，命名与协作方式见 [TODO 说明](TODO/README.md)
- `.META/Legacy/`：历史归档区，已完成或过期的计划迁入此处，避免干扰现行索引

文档撰写规范见 [文档撰写指南](doc-src/before_docing.md)。
