# EarBudsApp 构建环境 —— 安装包下载清单

> 生成时间：2026-11-10　｜　本清单只做「下载」，不含安装动作。
> 所有直链均已实测可访问（HTTP 200），文件大小取服务器返回的 Content-Length。

## 0. 本机现状（已确认）

| 项目 | 状态 |
|---|---|
| JDK | ❌ 未安装（`java` 命令不存在，无 `JAVA_HOME`） |
| Android SDK | ❌ 未安装（`ANDROID_HOME`/`ANDROID_SDK_ROOT` 为空，无 `%LOCALAPPDATA%\Android\Sdk`，无 Android Studio） |
| adb / Platform-Tools | ✅ 已有（v37.0.0，位于 `C:\Users\shuitiao\platform-tools`，已在 PATH） |
| Gradle | ❌ 未安装；且工程内**只有** `gradle/wrapper/gradle-wrapper.properties`，**缺 `gradlew.bat` 和 `gradle-wrapper.jar`**，所以必须单独装 Gradle 8.7（装好后可再生成 wrapper） |

结论：需要下载 **JDK 17、cmdline-tools、Platform 34、Build-Tools 34.0.0、Gradle 8.7** 五项；Platform-Tools 可选（本机 adb 已可用，仅建议补齐进 SDK 目录）。

---

## 1. JDK 17（Eclipse Temurin 17.0.21+9 LTS）

| 项 | 内容 |
|---|---|
| 推荐文件 | `OpenJDK17U-jdk_x64_windows_hotspot_17.0.21_9.msi` |
| 大小 | 168,714,240 字节（≈160.9 MB） |
| 官方直链 | https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_windows_hotspot_17.0.21_9.msi |
| 官方免安装 ZIP | `OpenJDK17U-jdk_x64_windows_hotspot_17.0.21_9.zip`（191,117,380 B ≈182.3 MB）<br>https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_windows_hotspot_17.0.21_9.zip |
| 国内镜像（清华 TUNA，已验证） | MSI：https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/windows/OpenJDK17U-jdk_x64_windows_hotspot_17.0.21_9.msi<br>ZIP：https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/windows/OpenJDK17U-jdk_x64_windows_hotspot_17.0.21_9.zip |
| 安装目标建议 | MSI 默认：`C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot`<br>若用 ZIP：解压到 `C:\Java\jdk-17` |
| 环境变量 | `JAVA_HOME` = 上面的目录；`PATH` 追加 `%JAVA_HOME%\bin` |

> 注：华为云 `mirrors.huaweicloud.com/adoptium/...` 返回的是 HTML 页面而非文件，别用；清华 TUNA 已验证文件真实（大小与 GitHub 一致）。
> 装 MSI 时请把 `Set JAVA_HOME variable` 设为 `Will be installed on local hard drive`，可省去手动配。

---

## 2. Android SDK 命令行工具（cmdline-tools）

| 项 | 内容 |
|---|---|
| 文件 | `commandlinetools-win-13114758_latest.zip` |
| 大小 | 143,040,480 字节（≈136.4 MB） |
| 官方直链 | https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip |
| 国内镜像（腾讯云，已验证） | https://mirrors.cloud.tencent.com/AndroidSDK/commandlinetools-win-13114758_latest.zip |
| 解压目标 | `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\`（zip 内层是 `cmdline-tools`，需重命名内层目录为 `latest`） |
| 说明 | 解压后应存在 `...\cmdline-tools\latest\bin\sdkmanager.bat` |

---

## 3. Android SDK Platform API 34

| 项 | 内容 |
|---|---|
| 文件 | `platform-34-ext7_r03.zip`（=`platforms;android-34` rev.3，Google 现行文件名，**不是** `platform-34_r03.zip`） |
| 大小 | 63,180,081 字节（≈60.3 MB） |
| 官方直链 | https://dl.google.com/android/repository/platform-34-ext7_r03.zip |
| 国内镜像（腾讯云，已验证） | https://mirrors.cloud.tencent.com/AndroidSDK/platform-34-ext7_r03.zip |
| 解压目标 | `%LOCALAPPDATA%\Android\Sdk\platforms\android-34\`（解压出的目录重命名为 `android-34`） |

---

## 4. Android SDK Build-Tools 34.0.0

| 项 | 内容 |
|---|---|
| 文件 | `build-tools_r34-windows.zip` |
| 大小 | 58,253,258 字节（≈55.6 MB） |
| 官方直链 | https://dl.google.com/android/repository/build-tools_r34-windows.zip |
| 国内镜像（腾讯云，已验证） | https://mirrors.cloud.tencent.com/AndroidSDK/build-tools_r34-windows.zip |
| 解压目标 | `%LOCALAPPDATA%\Android\Sdk\build-tools\34.0.0\`（解压出的目录重命名为 `34.0.0`） |

---

## 5. Gradle 8.7

| 项 | 内容 |
|---|---|
| 文件 | `gradle-8.7-bin.zip` |
| 大小 | 134,184,980 字节（≈128.0 MB） |
| 官方直链 | https://services.gradle.org/distributions/gradle-8.7-bin.zip |
| 国内镜像 1（腾讯云，已验证） | https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip |
| 国内镜像 2（华为云，已验证） | https://mirrors.huaweicloud.com/gradle/gradle-8.7-bin.zip |
| 国内镜像 3（阿里云，已验证） | https://mirrors.aliyun.com/gradle/distributions/v8.7.0/gradle-8.7-bin.zip |
| 解压目标 | `C:\Gradle\gradle-8.7`（即 `bin\gradle.bat` 所在目录的上级） |
| 环境变量 | 新增 `GRADLE_HOME=C:\Gradle\gradle-8.7`；`PATH` 追加 `%GRADLE_HOME%\bin` |

---

## 6.（可选）Android SDK Platform-Tools

本机 adb 已有（`C:\Users\shuitiao\platform-tools\adb.exe`，v37.0.0），若想让 SDK 目录结构完整可再下：

| 项 | 内容 |
|---|---|
| 文件 | `platform-tools-latest-windows.zip`（8,044,989 B ≈7.7 MB） |
| 官方 | https://dl.google.com/android/repository/platform-tools-latest-windows.zip |
| 腾讯云镜像 | https://mirrors.cloud.tencent.com/AndroidSDK/platform-tools-latest-windows.zip |
| 解压目标 | `%LOCALAPPDATA%\Android\Sdk\platform-tools\` |

---

## 7. 汇总：下载清单（勾选式）

| # | 文件名 | 大小 | 保存位置建议 |
|---|---|---|---|
| 1 | `OpenJDK17U-jdk_x64_windows_hotspot_17.0.21_9.msi` | 160.9 MB | `D:\dl\` |
| 2 | `commandlinetools-win-13114758_latest.zip` | 136.4 MB | `D:\dl\` |
| 3 | `platform-34-ext7_r03.zip` | 60.3 MB | `D:\dl\` |
| 4 | `build-tools_r34-windows.zip` | 55.6 MB | `D:\dl\` |
| 5 | `gradle-8.7-bin.zip` | 128.0 MB | `D:\dl\` |
| 6 | `platform-tools-latest-windows.zip`（可选） | 7.7 MB | `D:\dl\` |

合计约 **542 MB**（含可选项约 550 MB）。建议全部下到同一个目录（如 `D:\dl`），装的时候按文件名找。

---

## 8. 安装顺序（下载完成后执行）

1. **JDK 17**：运行 MSI（或用 ZIP 解压到 `C:\Java\jdk-17`）→ 设 `JAVA_HOME`、PATH → `java -version` 应显示 `17.0.21`。
2. **Android SDK 骨架**：解压 cmdline-tools 到 `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\`，设 `ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk`（与 [build.bat] 的自动探测路径一致），PATH 加 `%ANDROID_HOME%\cmdline-tools\latest\bin`、`%ANDROID_HOME%\platform-tools`。
3. **SDK 组件**（两种方式任选）：
   - 走 sdkmanager（联网）：
     ```bat
     sdkmanager --sdk_root="%ANDROID_HOME%" --licenses
     sdkmanager --sdk_root="%ANDROID_HOME%" "platform-tools" "platforms;android-34" "build-tools;34.0.0"
     ```
   - 走已下载的 zip（离线）：按第 3/4 节的解压目标放置，并在 `%ANDROID_HOME%\licenses\` 下接受许可（可直接运行一次 `sdkmanager --licenses` 生成）。
4. **Gradle 8.7**：解压到 `C:\Gradle\gradle-8.7`，设 `GRADLE_HOME` 与 PATH → `gradle -v` 确认。
5. **补 wrapper**（工程缺 `gradlew.bat` / `gradle-wrapper.jar`）：在 `EarBudsApp` 目录执行 `gradle wrapper --gradle-version 8.7`，之后 [build.bat] 即可用 `gradlew.bat`。
6. **验证**：`gradle :app:assembleDebug`（或 `build.bat`），输出 `app\build\outputs\apk\debug\app-debug.apk`。

### 需要注意的联网点（构建阶段，不是本次下载）

AGP 8.5.2 + androidx 依赖构建时仍要从 `dl.google.com/maven2` 和 Maven Central 拉约 200–300 MB。网络差可在 `%USERPROFILE%\.gradle\init.gradle` 里加阿里云镜像：

```groovy
allprojects {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/google' }
        maven { url 'https://maven.aliyun.com/repository/public' }
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
        google(); mavenCentral()
    }
}
```

装完这五项 + 依赖，工程即可离线/半离线构建。
