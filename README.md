# Phi Tracker

一款可以用于读取 Phigros TapTap 云存档并查分的小工具。一次导入，随时查分。

## 关于本分支

本分支是我们将项目迁移至 Kotlin Multiplatform 的开发分支，我们当前正在将 Phi Tracker 重写至 Compose Multiplatform 架构以期实现 iOS 端跨平台适配，并可以在未来扩展至桌面端和 WebAssembly 驱动的 Web 端。

重写过程将可能持续一段时间，我们当前规划的开发周期大概持续 1~2 个月，在重写完成后，原本的 `main` 分支将标记为弃用，不再开发，本分支将作为新的 `main` 分支持续维护。

## 如何参与测试

我们在 KMP 重写分支中实现了 GitHub CI 的自动打包流程，在每次推送后，对应的编译和打包任务将推送至 GitHub Actions 对应的 Workflows 中，若要实时跟进开发进度，只需在每次构建完成后下载 Artifacts 即可。

> 请注意：我们不保证 CI 编译版本在任何意义上的可用性，使用 CI 编译产物所导致的一切后果，由使用者自行承担。

## 如何参与贡献

由于重写工作尚未完成且整体任务复杂度较高，目前我们不接受任何形式的贡献，当前 Pull Request 仅向 Kasumi's IT Infrastructure 内部成员开放，敬请谅解。

## 版权与许可协议表述

请参阅 `main` 分支的 [`README.md`](https://github.com/Kasumi-Ushio/Phi-Tracker/blob/main/README.md#版权与许可协议表述), [`LICENSE.md`](https://github.com/Kasumi-Ushio/Phi-Tracker/blob/main/LICENSE.md)。