---
For_Agent: 修复 Orbit 单仓发布使用 GITHUB_TOKEN 时的权限预检误判
---

# Orbit 发布权限预检修复

## 目标

让单仓库发布在使用 Actions 内置 `GITHUB_TOKEN` 时通过权限预检，同时保留跨仓库发布时的写入权限检查。

## 现状

- 发布 run `42629542681`，提交 `4e3b11a9`，失败步骤为 `Validate cross-repository release access`。
- 报错：`ValueError: 凭据缺少仓库写入权限：Awsome5297/Orbit`。
- `GITHUB_TOKEN` 在仓库 API 的 `permissions.push` 字段返回 `false`，但工作流已声明 `contents: write`，实际具备同仓库写入能力。
- 单仓库方案下，源码、更新和资源仓库都是 `Awsome5297/Orbit`，不需要跨仓库 PAT。

## 改动清单

- [x] 同仓库预检只核对仓库可访问和默认分支，不依赖 `permissions.push`。
- [x] 跨仓库目标继续要求 `permissions.push`。
- [x] 补充单元测试覆盖同仓库与跨仓库两种情况。
- [x] 提交并推送，重新触发正式发布。

## 验证方式

- 运行 `ci/distribution/updates/tests` 中的仓库迁移测试。
- 推送后重新派发 Build，确认权限预检通过并继续签名构建与发布。
