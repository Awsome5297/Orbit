# 独立发行的 Release 与增量更新

客户端统一查询 `Awsome5297/Orbit` 的 GitHub Releases。源码、正式版、补丁版、增量包和固定离线资源都在这个仓库中，不再拆分独立发行仓库。

当前签名版本为 `1.12.2+23`、versionCode 79。Orbit 使用新的固定签名密钥，证书 SHA-256 为 `AF63C4BCE0AFAAE16956249DF4F695E388DE95D05C6A1AFAE98BA0AA5B062D35`。首次发布前需要运行一次 `Bootstrap resources` 工作流，把上游 `resources-v1`、`resources-v2-d16730007f8d94095264` 以及历史安装记录引用的两个 `resources-v2-*` 标签原字节镜像到 Orbit。

## 渠道与安装

自动检查与手动检查共用 Beta 计划开关：关闭时仅选择 `x.y.z` 正式版，开启后也选择 `x.y.z+n` 补丁版。先过滤渠道，再比较数字版本。退出 Beta 不降级已有补丁版，仍可安装更高基础版本的正式版。开关变化后清除旧检查结果。

首次切换通过完整 APK 安装。包名保持 `com.ai.assistance.operit.kong`，沿用已有固定签名，versionCode 递增。仅创建标签或上传 Actions artifact 不会形成可发现的应用更新。完整 APK 必须随 Release 保存，供新用户安装和后续生成补丁。

## 发布方法

首次发行前，在 Actions 中手动运行 `Bootstrap resources`。该工作流只做一次性镜像，把 `3316891527/Operit-Kong-resources` 的 `resources-v1`、`resources-v2-d16730007f8d94095264` 以及历史安装记录引用的两个 `resources-v2-*` 标签全部资产复制到 Orbit，文件字节和 SHA-256 不变。

之后从 [Build 工作流](../../../.github/workflows/build.yml)选择 `main`、任务 `:app:verifyCiPreviewRelease`，勾选 `publish_release`，填写非空 Markdown 更新说明和可选 `previous_tag`。留空时选择 Orbit 中同一基础版本且低于目标的最新已公开 APK。普通构建保持 `publish_release=false`。

发布使用 Actions 内置 `GITHUB_TOKEN`，工作流已声明 `contents: write` 和 `actions: read`，不需要额外创建 `OPERIT_DISTRIBUTION_TOKEN` 或跨仓库 PAT。权限预检在签名构建前执行；缺少写入权限时明确失败。`complete_repository_migration` 与 `cleanup_merged_branches` 保持关闭，它们只用于上游历史迁移。

发布脚本读取 APK 的真实版本，检查 Kong 包名、固定证书和递增内部版本号。应用版本先以草稿上传并核验，再公开为 Release；已有版本不覆盖。

Build 发行步骤直接向 Orbit 发布，不创建额外的源码发行副本。源码提交和源码归档地址仍记录在正文与摘要中。Release tag 继续承载应用版本，已有标签不删除或移动。直接调用未变换的 `publish_release.py` 仍保留历史源码标签逻辑，正式发行应通过当前工作流。

## 不公开版本的准备

可以继续使用当前版本号构建内部验证 APK，不需要为验证提前递增公开版本。发布脚本的 `--prepare-only` 允许准备已有版本，但不会创建任何标签、草稿或公开 Release；实际发布仍拒绝已有版本。

```bash
python3 ci/distribution/updates/publish_release.py \
  --apk app/build/outputs/apk/release/app-kong.apk \
  --build-tools "$ANDROID_HOME/build-tools/35.0.0" \
  --repository Awsome5297/Orbit \
  --commit "$GITHUB_SHA" \
  --notes '内部发行准备' \
  --prepare-only
```

此模式执行 APK 校验、基础包下载、补丁生成和逐字节还原，输出 `release-plan.json`、完整 APK 对应摘要及可用增量包。只准备模式的产物不代表已经发布的版本。

## 补丁协议与校验

继续使用 `tools/hotbuild/build_patch.py` 和 `apply_patch.py` 的 `apkraw-1` 协议，复制未变化 ZIP 原始记录，保留 APK 签名区和完整文件布局。发布前还原并比对目标 APK 的 SHA-256，客户端也核对基础 APK、补丁和目标 APK 摘要。

补丁仅连接同一基础版本的递增版本，基础 Release 标签必须与其 APK 内版本一致。跨基础版本使用完整 APK。客户端从补丁及元数据附件地址解析仓库，直接增量和跨版本链自动使用新仓库，不增加旧仓库查询分支。

发行附件包括完整包 `app-kong.apk`、存在基线时的 `apkrawpatch_*.zip` 和配对 `patch_*.json`，以及 `distribution-release.json`。摘要保留版本、哈希、更新说明、Orbit 发行仓库和源码身份。正文使用非空可读文本或 Markdown，JSON 仅作为附件；历史 JSON 正文继续由既有客户端解析逻辑读取。

安装器最多扫描最近 100 个 Release，补丁链最多连接 64 步。保留链上全部版本和配套文件，历史迁移时 APK 和增量 ZIP 字节保持不变；重新签名或打包的 APK 不适用原链。

## 验证状态

原有发布回归继续覆盖版本、基线、包名、签名、原始记录复用及逐字节还原。新增回归覆盖发行分流、草稿上传顺序、只准备模式、源码标签保护、迁移完整性、资源清单改址及产物检查。客户端 JVM 回归继续覆盖完整包、增量、渠道、数字排序、草稿过滤和文案提取。

本次未在本地执行编译、构建或测试。工作分支派发、不公开版本的构建结果及真实迁移收据分别记录；准备好的回归用例不等于通过结果。早期更新实现的已通过验证属于历史证据：[Android Build](https://github.com/3316891527/Operit/actions/runs/40199253010) 与 [Android Tests](https://github.com/3316891527/Operit/actions/runs/40198980173)，不覆盖本次单仓迁移。


## 发布后清理本轮工作分支

历史 `1.12.2+22` 发布曾使用 branch-cleanup-plan.json。本次 `1.12.2+23` 保持 `cleanup_merged_branches=false`，保留功能分支和联合测试分支；历史清理名单不用于本轮发行。该选项默认关闭。

清理先核对公开 Release 与本次 release-plan.json 的版本、源码提交、附件大小和 SHA-256；仍有开放 PR 或包含本次发行未合入提交的分支会保留。实际删除使用观察到的分支头作为租约，构建期间新增的提交不会被误删。结果写入 branch-cleanup-report.json 并随发行报告上传，不需要持续轮询工作流。
