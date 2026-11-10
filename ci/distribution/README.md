# 独立发行签名

本目录为独立发行分支准备固定签名。私钥和密码不得加入 Git 仓库，也不得作为构建产物上传。

## GitHub Actions 配置

- `OPERIT_DISTRIBUTION_KEYSTORE_BASE64`：PKCS12 密钥库内容的单行 Base64
- `OPERIT_DISTRIBUTION_STORE_PASSWORD`：密钥库密码，同时作为私钥密码

密钥别名固定为 `operit-distribution`。`prepare_signing.py` 将密钥写入被 Git 忽略的 `.ci-signing/operit-distribution.p12`，缺少或无效的密钥会使准备步骤失败。工作流只在发行任务中准备密钥，Debug JVM 测试不需要发行签名 Secret。

## 发行与覆盖安装

Android Build 默认任务为 `:app:verifyCiPreviewRelease`，只构建并上传 `app_orbit.apk`。校验任务检查签名有效性、证书指纹、包名 `com.ai.assistance.operit.orbit` 和非调试属性。公开证书 SHA-256 为 `AF63C4BCE0AFAAE16956249DF4F695E388DE95D05C6A1AFAE98BA0AA5B062D35`。

Orbit 使用单仓库发行：源码、应用 Release、补丁和离线资源都位于 `Awsome5297/Orbit`。发布使用 Actions 内置 `GITHUB_TOKEN`，不再依赖额外的跨仓库 PAT。

首次发行顺序：先手动运行一次 `Bootstrap resources`，把上游 `resources-v1`、当前 `resources-v2` 与历史安装记录引用的两个 `resources-v2-*` 标签镜像到 Orbit；之后运行 `Build` 并勾选 `publish_release`。资源引导只做一次，后续资源版本由正式发布流程生成。

首个固定签名版本为 `1.12.2-personal.1`，版本号 52。后续发行须保留此密钥和包名，并提高版本号。重新生成密钥会改变应用安装身份；旧 CI 临时调试签名包无法仅凭此新密钥直接覆盖。密钥文件与密码由发行维护者另行备份，GitHub Secrets 不能代替可恢复的本地密钥备份。

本目录只记录已经接入的构建和校验逻辑；实际构建及设备覆盖安装结果以对应运行和设备验证为准。

本轮准备的发行版本为 `1.12.2+24`，版本号 80；上一发行版本为 `1.12.2+23`，版本号 79。应用版本名必须遵守现有 `x.y.z` 或 `x.y.z+n` 格式；`personal` 后缀不被插件市场和 ToolPkg 的版本解析器接受。首个固定签名版本的签名和包名继续沿用，新版通过递增 versionCode 满足 Android 更新条件。

## 独立更新源与发布

[Release 与增量更新](updates/README.md) 说明个人仓库更新源、首次完整安装、工作流发布参数、补丁生成校验和版本递增要求。
