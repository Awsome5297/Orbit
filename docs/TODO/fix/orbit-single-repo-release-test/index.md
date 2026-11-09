---
For_Agent: 修复 Orbit 单仓发行后 Debug 构建测试失败
---

# Orbit 单仓发行测试修复

## 目标

修复 Debug Build 在 `Check distribution patch publishing` 步骤失败的单元测试，使 `:app:assembleDebug` 能够继续执行。

## 现状

- Build run `42619197803`，提交 `78b4124f7`，失败步骤为 `Check distribution patch publishing`。
- 失败用例：`test_publish_release.DistributionPublishingTest.test_markdown_body_is_separate_from_uploaded_patch_metadata`。
- 报错：`json.decoder.JSONDecodeError: Expecting value: line 1 column 1`，位置 `publish_release.py` 的源码 Release 查询。
- 原因：单仓方案下 `SOURCE_REPOSITORY == UPDATE_REPOSITORY`，`destinations` 始终包含源码仓库，触发一次冗余的源码 Release 覆盖检查。测试 mock 只为正常的发布调用准备了返回值，没有为这次额外查询准备数据。

## 改动清单

- [x] 移除单仓下冗余的源码 Release 预检查。
- [x] 更新相关注释，说明版本只发布到 Orbit。
- [x] 运行 `test_publish_release` 验证失败用例。
- [x] 本地提交，不执行远程推送。

## 验证方式

- 本地运行 `python3 -m unittest discover -s ci/distribution/updates/tests -p 'test_publish_release.py' -v`。
- 推送后由 GitHub Actions 重新执行 `Build` 的 `Check distribution patch publishing`，确认 Debug 构建继续执行。
