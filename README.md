![Phi-Tracker](https://socialify.git.ci/Kasumi-Ushio/Phi-Tracker/image?custom_description=%E4%B8%80%E6%AC%BE%E5%8F%AF%E4%BB%A5%E7%94%A8%E4%BA%8E%E8%AF%BB%E5%8F%96+Phigros+TapTap+%E4%BA%91%E5%AD%98%E6%A1%A3%E5%B9%B6%E6%9F%A5%E5%88%86%E7%9A%84%E5%B0%8F%E5%B7%A5%E5%85%B7&description=1&logo=https%3A%2F%2Ffiles.seeusercontent.com%2F2026%2F04%2F04%2F0fsN%2Fphi_search.svg&name=1&pattern=Plus&theme=Dark)

# Phi Tracker

一款可以用于读取 Phigros TapTap 云存档并查分的小工具。一次导入，随时查分。

## 开发暂停

从现在开始，Phi Tracker 的开发工作暂停，4.10 恢复开发，下一阶段计划如下：

1. 实现向统一查分 API 上传数据的功能
2. 调整最近同步机制，使其在没有数据变动时默认显示最后一次有效同步历史
3. 增加自动检查更新功能
4. 进一步扩充 B30 Overflow 上限
5. 体验优化和 Bug 修复
6. 为 Compose MultiPlatform 做准备

## 项目简述

Phi Tracker 是一个基于 Android 平台的 Phigros 查分工具，通过读取 TapTap 云存档来获取玩家的游玩数据，并提供查分、统计等功能。

Phi Tracker 的目标是成为一款功能全面、易用且美观的 Phigros 查分工具，让玩家能够方便地查看自己的游戏数据，并在现有的 QQ Bot 查分解决方案之外，提供另一种选择。

目前，Phi Tracker 已经支持以下功能：

- 通过 TapTap 扫码登录或 sessionToken 两种方式登录
- B30 成绩展示与详细的单曲曲目详情
- 全部曲目搜索，支持筛选条件和通配符
- 包括等效 RKS 计算、RKS 变化趋势显示和导出 sessionToken 等多项工具功能
- 基本的 B30 图片展示
- 通过统一查分 API 获取更多统计数据，并解锁更多工具功能
- 高清曲绘预览和保存功能
- 主页可显示最近一次同步历史
- 分离式曲目信息更新，无需在每次游戏本体更新后都全量更新程序


## 构建

本项目使用 Gradle 构建系统，因此在构建时，您可能需要确认您当前的系统是否安装了任意 JDK 17+ 发行版。

<details>
<summary>在 Ubuntu 24.04.x LTS 系统下，构建流程如下：</summary>

1. 安装 JDK 17+（推荐使用 JDK 21）和安装 SDK 必要的依赖：
```bash
sudo apt update && sudo apt full-upgrade
sudo apt install openjdk-21-jdk unzip wget
```
2. 安装 Android SDK：
    1. 创建 SDK 目录：
    ```bash
    mkdir -p ~/Android/Sdk/cmdline-tools
    ```
    2. 下载 Android SDK Command-line Tools：
    ```bash
    cd ~/Android/Sdk/cmdline-tools
    wget https://dl.google.com/android/repository/commandlinetools-linux-14742923_latest.zip
    ```
    3. 解压：
    ```bash
    unzip commandlinetools-linux-14742923_latest.zip -d .
    mv cmdline-tools latest
    ```
    4. （可选）清理已下载的压缩包：
    ```bash
    rm commandlinetools-linux-14742923_latest.zip
    ```
    5. 配置环境变量：
    ```bash
    echo 'export ANDROID_HOME="~/Android/Sdk"' >> ~/.bashrc
    echo 'export ANDROID_HOME=$ANDROID_SDK_ROOT' >> ~/.bashrc
    echo 'export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"' >> ~/.bashrc

    # 可选，如果您需要 adb 和 fastboot：

    echo 'export PATH="$PATH:$ANDROID_HOME/platform-tools"' >> ~/.bashrc
    source ~/.bashrc
    ```
    6. 阅读并接受 SDK 许可协议：
    ```bash
    sdkmanager --licenses

    # 输入 y 接受所有许可协议
    ```
    7. 安装构建工具：
    ```bash
    # 我们构建的 Target SDK 版本是 35，推荐您在构建时以 36 为 Target SDK
    # 我们将在后续版本中更新 Target SDK

    sdkmanager "build-tools;35.0.0" "platforms;android-35" "ndk;27.0.12077973"

    # 如果您需要 adb 和 fastboot：

    sdkmanager "platform-tools"
    ```
    至此，Android SDK 应该已经被安装完毕。

3. 配置 Gradle：
    1. 为了生成 Release 构建，您需要预先配置您自己的 KeyStore：
    ```bash
    # 如果您此时还在 cmdline-tools 目录中，请先返回至本项目的根目录

    cd /path/to/Ushio-Prober-Phigros
    keytool -genkeypair -v -keystore release.keystore -alias phitacker -keyalg RSA -keysize 2048 -validity 10000
    ```
    命令运行时，会要求您输入 KeyStore 的密码、别名、姓名、组织等信息，请根据提示输入即可，如果您只想构建 Debug 版本，则可以跳过这一步，但请先确认您现在在项目的根目录。

    2. 创建一个空的 local.properties 文件，无需添加任何内容，后续如果编译中报错，再根据报错情况填充内容即可。

4. 构建 Phi Tracker：

    若要构建 Debug 版本，请运行：
    ```bash
    ./gradlew assembleDebug
    ```
    若要构建 Release 版本，请运行：
    ```bash
    ./gradlew assembleRelease
    ```

构建完成后，您可以在 `app/build/outputs/apk/debug` 或 `app/build/outputs/apk/release` 目录下找到生成的 APK 文件。
</details>


在其它 GNU/Linux 发行版上的构建流程与 Ubuntu 24.04.x LTS 基本相同，但请根据您系统的实际情况安装对应的软件包。

理论上说，在 Windows 和 macOS 上的构建流程也大致相似，只要有了 Android SDK 和 JDK，就可以完成构建。

## 版权与许可协议表述

版权所有 © 2026 Kasumi's IT Infrastructure

版权所有 © 2026 铃萤-RinLin a.k.a. 朝比奈ほたる 及所有 Phi Tracker 贡献者

Phi Tracker 是自由软件，请参阅 [LICENSE.md](https://github.com/Kasumi-Ushio/Ushio-Prober-Phigros/blob/main/LICENSE.md) 获取详细的许可协议内容。