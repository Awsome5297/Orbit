---
fork: https://github.com/Awsome5297/Orbit
branch: main
status: prepared
---

# 1.12.2+24 正式发行

将媒体创作的草图编辑器重设计合入 `main`，版本从 `1.12.2+23 / 79` 升到 `1.12.2+24 / 80`，以 `v1.12.2+23` 为增量基线发布完整 APK 和补丁包。

## 发行范围

- 全屏草图编辑器、六种笔型、文字与选择工具、可撤销的擦除和清空、HSV 自定义颜色。
- 画纸比例和底图、草图导出与媒体生成分辨率联动。
- 只包含当前分支已经通过的 Debug 构建和 JVM 测试内容，不包含其他未合入分支。

## 发布参数

使用 [Build 工作流](../../../../.github/workflows/build.yml)，正式发布引用为 `main`：

- `gradle_task=:app:verifyCiPreviewRelease`
- `publish_release=true`
- `complete_repository_migration=false`
- `cleanup_merged_branches=false`
- `previous_tag=v1.12.2+23`
- `release_notes` 使用 [1.12.2+24 发行说明](../../../../ci/distribution/updates/releases/1.12.2+24.md)

固定签名 APK、基础包比对、增量包生成和逐字节还原由发行工作流校验。HTTP 204 只表示派发受理，不表示公开发行已经完成。

## 验证边界

- 当前分支的 `assembleDebug` 和 Android Tests 已通过。
- 尚未在真机安装签名发行包，也尚未验证 `+23 → +24` 增量包的实际安装。
- 发行完成后需要核对 Release 附件、版本号、证书摘要和增量包还原结果。

[DONE] 待发行完成后标记。
