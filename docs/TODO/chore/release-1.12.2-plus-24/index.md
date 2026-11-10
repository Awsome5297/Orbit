---
fork: https://github.com/Awsome5297/Orbit
branch: main
status: published
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

[DONE]

## 发行结果

Release：[v1.12.2+24](https://github.com/Awsome5297/Orbit/releases/tag/v1.12.2%2B24)

附件：

- `app_orbit.apk`：130,056,311 字节，SHA-256 `c2032b1274c4c40d34ed383f761bbf716685e6e737429c3c8e9b5f0ff69338e5`
- `apkrawpatch_1.12.2+23_to_1.12.2+24_230def9a1237_c2032b1274c4.zip`：57,679,711 字节，SHA-256 `6ee768c50ae506aece0aaf09ca763020ad0c3fb872ab224a9994f60dba4aa755`
- `patch_1.12.2+23_to_1.12.2+24_230def9a1237_c2032b1274c4.json`：512,464 字节
- `distribution-release.json`：2,832 字节

核对结果：

- APK 包名为 `com.ai.assistance.operit.orbit`，versionCode 80，versionName `1.12.2+24`。
- 证书 SHA-256 为 `AF63C4BCE0AFAAE16956249DF4F695E388DE95D05C6A1AFAE98BA0AA5B062D35`，与既有固定签名一致。
- 补丁基础 SHA-256 与 `v1.12.2+23` 的 `app_orbit.apk` 摘要一致。
- 补丁目标 SHA-256 与 `+24` APK 一致，补丁文件 SHA-256 与元数据一致。
- 发行工作流已在发布前完成增量包还原与目标 APK 哈希校验。
