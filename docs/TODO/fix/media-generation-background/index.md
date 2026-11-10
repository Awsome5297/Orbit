---
For_Agent: 修复媒体生成协程绑定页面导致退出即失败的问题
---

# 媒体生成脱离页面生命周期

## 现象

媒体创作开始生成后，退出创作页面或让应用进入后台，记录会被写成失败，错误信息是 `rememberCoroutineScope left the composition`。

## 原因

生成任务由页面里的 `rememberCoroutineScope` 启动。页面离开 composition 时这个作用域被取消，取消异常又被当成普通生成异常写回记录，于是后台仍在进行的请求被标记为失败。

## 修改

- 新增 `MediaGenerationRunner`，用应用级 `SupervisorJob` 作用域执行记录创建、供应商轮询、结果下载和落库。
- 新增 `MediaGenerationService` 前台服务，在生成期间保活并在结束后停止；任务本身不依赖 Service 生命周期。
- 页面只观察 `activeSessions` 和数据库记录，不再持有生成协程；退出后重新进入仍能看到运行状态。
- 生成完成事件用于清空输入框，页面不在时事件丢弃，不影响任务。
- 前台服务启动失败只记录日志，生成任务仍在应用进程内继续。
- 进程被系统杀死后，下一次启动会把本轮之前遗留的活动记录标记为“生成已中断”，避免永久显示运行中。
- 取消异常不再写回失败记录。

## 验证

- 静态检查生成调用、服务声明、通知资源和数据库状态更新。
- 本地未执行构建或测试。
- GitHub Actions `Build`（`assembleDebug`）通过：[42842530158](https://github.com/Awsome5297/Orbit/actions/runs/42842530158)
- GitHub Actions `Android Tests` 通过：[42842540239](https://github.com/Awsome5297/Orbit/actions/runs/42842540239)
- 仍需真机验证：生成中退出创作页、切后台、重新进入页面，以及强制停止后的“生成已中断”记录。

[DONE]
