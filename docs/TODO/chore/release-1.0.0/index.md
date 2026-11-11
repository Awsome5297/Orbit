---
fork: https://github.com/Awsome5297/Orbit
branch: main
status: prepared
---

# 1.0.0 发行基线

把发行版本从 `1.12.2+24 / 80` 重置为 `1.0.0 / 81`，作为后续增量更新的新基线。

## 待决事项

仓库里已经存在 `v1.12.2+23` 和 `v1.12.2+24`。更新器按版本名比较，`1.12.2+24` 大于 `1.0.0`，所以直接发布 `1.0.0` 会产生两个问题：

- 新装的 `1.0.0` 客户端开启 Beta 后会看到 `1.12.2+24` 更新提示，但旧包的 versionCode 是 80，低于 `1.0.0` 的 81，安装会失败。
- 已经在 `1.12.2+24` 的客户端不会把 `1.0.0` 当成更新，需要手动安装一次完整 APK 才能进入新版本线。

发布前需要选择旧版本线的处理方式，推荐把旧的 `v1.12.2+23`、`v1.12.2+24` 标记为 prerelease，让更新器忽略它们；旧用户手动安装一次 `1.0.0` 完整包后继续使用增量更新。

## 构建参数

- `gradle_task=:app:verifyCiPreviewRelease`
- `publish_release=false`，只生成签名 APK 和报告，不创建标签或 Release
- 版本：`1.0.0` / versionCode 81
- 包名和固定签名不变：`com.ai.assistance.operit.orbit`，证书 SHA-256 `AF63C4BCE0AFAAE16956249DF4F695E388DE95D05C6A1AFAE98BA0AA5B062D35`

[DONE] 待签名 APK 构建完成并确认发布策略后标记。
