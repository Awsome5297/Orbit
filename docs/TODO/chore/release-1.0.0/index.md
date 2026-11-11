---
fork: https://github.com/Awsome5297/Orbit
branch: main
status: built
---

# 1.0.0 发行基线

把发行版本从 `1.12.2+24 / 80` 重置为 `1.0.0 / 1`，作为后续增量更新的新基线。

## 版本线决策

- `versionName` 和 `versionCode` 都从 1.0.0 重新开始，后续 `1.0.0+n` 的 versionCode 从 2 继续递增。
- 旧的 `1.12.2+24` 不要求被 `1.0.0` 覆盖安装；安装 `1.0.0` 前需要先卸载旧包，因为 versionCode 从 80 降到 1 会被 Android 拒绝。
- `1.0.0` 是完整包基线，没有从旧版本线生成的增量包。
- 旧版本线 `v1.12.2+23`、`v1.12.2+24` 的版本名仍高于 `1.0.0`。开启 Beta 的 `1.0.0` 客户端可能把它们识别为更新；正式公开前需要把旧版本标记为 prerelease，或关闭 Beta 检查。

## 构建参数

使用 [Build 工作流](../../../../.github/workflows/build.yml)，引用为 `main`：

- `gradle_task=:app:verifyCiPreviewRelease`
- `publish_release=false`，只生成签名 APK 和报告，不创建标签或 Release
- 版本：`1.0.0` / versionCode 1
- 包名和固定签名不变：`com.ai.assistance.operit.orbit`，证书 SHA-256 `AF63C4BCE0AFAAE16956249DF4F695E388DE95D05C6A1AFAE98BA0AA5B062D35`

## 验证边界

- 本地未执行构建或测试，签名 APK 由 GitHub Actions 构建。
- 签名 APK 需要核对包名、versionCode 1、versionName 1.0.0 和固定证书。
- 旧 `+24` 设备安装前需要卸载旧包；本次不验证覆盖安装。

[DONE]

## 构建结果

- GitHub Actions `Build` 成功：[43051922251](https://github.com/Awsome5297/Orbit/actions/runs/43051922251)，产物 `operit-android-39`。
- 签名 APK：130,072,179 字节，SHA-256 `f6bba779b3911ad5b9eb7898985c14eb3ef09ff576429f0e88705579369d0596`。
- 包名 `com.ai.assistance.operit.orbit`，versionCode 1，versionName 1.0.0。
- 证书 SHA-256 `AF63C4BCE0AFAAE16956249DF4F695E388DE95D05C6A1AFAE98BA0AA5B062D35`，与既有固定签名一致。
- 本次未创建标签或 Release；旧版本线处理策略仍未执行。
