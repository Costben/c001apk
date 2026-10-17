# 开发指南

[返回项目首页](../README.md)

## 项目结构

```text
c001apk/
├── apps/
│   ├── android/             # Kotlin / Compose 重写中的 Android 应用（coui17 界面）
│   └── flutter/             # 当前 Flutter 应用
│       ├── lib/             # 页面、组件、网络层、模型与工具
│       ├── assets/          # 应用资源
│       ├── test/            # 回归测试源码与合成夹具
│       ├── android/         # 现有 Flutter Android 工程
│       ├── windows/         # Windows 原生宿主
│       ├── ios/、linux/、macos/、web/
│       └── pubspec.yaml
├── docs/
│   ├── DEVELOPMENT.md       # 开发与验证说明
│   ├── media/               # README 横幅与界面截图
│   ├── THIRD_PARTY_NOTICES.md
│   └── licenses/            # 第三方许可原文
├── third_party/
│   └── coui17/              # coui17 组件库（git submodule，源码参与构建）
├── LICENSE
└── README.md
```

## 本地开发

当前维护使用 **Flutter 3.24.5 / Dart 3.5.4**。依赖版本以 `apps/flutter/pubspec.lock` 为准，建议先使用相同工具链复现。

Windows 构建需要 Visual Studio 的 C++ 桌面开发组件及 Windows SDK。现有 Android 工程使用 Gradle 7.6.3、AGP 7.3.0、Kotlin 1.7.10 和 compileSdk 34，本地构建使用标准 JDK 17；请准备相应 Android SDK。这些要求适用于当前 Flutter 工程。

```powershell
git clone https://github.com/TG-Twilight/c001apk.git
cd c001apk/apps/flutter

flutter doctor -v
flutter pub get
flutter analyze
flutter test
```

所有 Flutter 命令均在 `apps/flutter/` 下执行。现有静态分析仍有待处理的历史提示，提交修改时请留意是否引入新问题。

运行与调试构建：

```powershell
flutter run -d windows
flutter build windows --debug
flutter build apk --debug
```

Windows 产物位于 `apps/flutter/build/windows/x64/runner/Debug/`，Android 调试 APK 位于 `apps/flutter/build/app/outputs/flutter-apk/app-debug.apk`。调试构建用于开发验证；正式发布时应配置自己的签名。

### Release 构建与 Android 签名

从 `apps/flutter/` 执行：

```powershell
flutter build windows --release
flutter build apk --release --split-per-abi
```

Windows 完整运行目录为 `build/windows/x64/runner/Release/`，分发时一起打包
EXE、DLL 和 `data/`；Android 分别生成 `build/app/outputs/flutter-apk/` 下的
`app-arm64-v8a-release.apk`、`app-armeabi-v7a-release.apk`、`app-x86_64-release.apk`。

后续发布和 CI 产物按平台、架构分别命名及分发：

| 平台 | 目标架构 | 本项目验证状态 |
| --- | --- | --- |
| Windows | `x86_64` | 本地及云端 release 已构建，下载包已复核 |
| Linux | `x86_64` | 云端 release 已构建，下载包已复核；运行体验待验收 |
| Android | `arm64-v8a`、`armeabi-v7a`、`x86_64` | 云端独立 APK 已构建、下载验签；结果见 Actions |
| iOS | `arm64`（真机） | 云端无签名编译/架构检查已通过；Apple 签名与真机运行待实施 |

桌面 `x86_32` 和 ARM64 不列入本轮发布矩阵。
桌面 `x86_64` 通常也称 x64/amd64，`aarch64` 通常也称 ARM64；Android 使用其 ABI 命名。
当前固定 Flutter 3.24.5 的官方 Windows/Linux SDK 发布包仅有 x64，
Linux 还不支持 x64 到 ARM64 交叉构建，因此当前桌面 CI 使用 x86_64 runner。
工具链未来变化时重新核对这些限制，不据引擎支持声称本项目所有架构都能构建。
架构支持和 APK 拆分方式参见 [Flutter 平台支持](https://docs.flutter.dev/reference/supported-platforms)
及 [Android 发布指南](https://docs.flutter.dev/deployment/android#build-an-apk)。

Android release 必须配置正式签名，缺少 keystore、alias 或密码时构建失败。
debug 继续使用开发证书。以下环境变量在构建时读取：

| 环境变量 | 用途 |
| --- | --- |
| `KEYSTORE` | JKS 的路径，建议绝对路径 |
| `KEY_ALIAS` | 签名 alias |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_PASSWORD` | 私钥密码 |
| `REVIA_KS_PASS` | 两项专用密码变量未分别设置时的统一密码 |

本地也可以复制 [key.properties.example](../apps/flutter/android/key.properties.example)
为 `android/key.properties`，仅填写 `storeFile` 和 `keyAlias`；这个本地文件已被
Git 忽略。密码通过环境变量提供，勿写进该文件或提交到仓库。
含中文或日文的路径/alias 使用 Properties 的 `\uXXXX` 转义，或通过环境变量传入。
Windows 用户级变量需要注入当前进程，例如：

```powershell
$env:REVIA_KS_PASS = [Environment]::GetEnvironmentVariable('REVIA_KS_PASS', 'User')
flutter build apk --release --split-per-abi
```

GitHub Actions 配置见 [云端构建指南](CI.md) 与 [Build workflow](../.github/workflows/build.yml)。
普通 push/PR 执行离线检查、Windows/Linux x86_64 release 和 Android debug 构建；
手动运行可生成正式签名的 Android 三 ABI APK，并选择执行 iOS arm64 无签名编译检查。
Android 签名使用 Actions Secrets `ANDROID_KEYSTORE_BASE64` 和 `REVIA_KS_PASS`：
runner 临时恢复 JKS，并以 `KEYSTORE`、`KEY_ALIAS` 和密码环境变量传给构建。
JKS、密码及其 Base64 不进入 Git 历史、公开附件或构建 artifacts，临时 JKS 在结束时清理。
Base64 只负责二进制转文本，保密依靠 Secrets；具体方式见
[GitHub 官方 Secrets 文档](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)。
Apple 签名另外配置，Android 密钥不用于 iOS。

Android 当前 applicationId / namespace 为 `app.awavenue.c001apk`，仍使用 Flutter 宿主。
它与旧包 `com.example.c001apk.flutter` 独立安装；旧包的账号、历史、收藏和设置不会自动迁移，新包需要重新登录。
酷安协议中的 `X-App-Id: com.coolapk.market` 保持其协议用途。

模型发生变化后，在 Flutter 工程目录生成代码：

```powershell
dart run build_runner build --delete-conflicting-outputs
```

### 测试

普通 `flutter test` 使用离线回归与合成设备夹具，在线检查默认跳过。测试覆盖请求签名、头部隔离、存储迁移、响应错误、游客详情回退、中文界面及缓存清理等。

账号回归还覆盖不完整登录检查、失败资料读取、验证期间切换账号、验证码取消和单次重试、文本写请求编码、私密发布字段，以及网页登录 Cookie 清理。
登录后受限详情可读取公开正文，界面会说明账号点赞状态尚未取得；Android/iOS 提供手动人机验证入口。完成验证不保证服务端解除其他风控，Windows 应用内人机验证暂不支持。

需要主动检查游客只读接口时，可单独执行：

```powershell
$env:COOLAPK_LIVE_PROBE = '1'
flutter test test/live/api_probe_test.dart
Remove-Item Env:COOLAPK_LIVE_PROBE
```

游客动态、文章详情检查使用 `COOLAPK_GUEST_DETAIL_PROBE=1` 和 `test/live/guest_detail_test.dart`。在线检查的脱敏结构结果写入 Flutter 工程下忽略的 `.local/reports/`；验证码或业务拒绝会如实导致检查失败。

若代理配置影响本机测试连接，可在当前 PowerShell 会话设置 `$env:NO_PROXY = 'localhost,127.0.0.1,::1'` 后重试。

## Android Compose 应用

`apps/android/` 是 Android 端的 Kotlin / Jetpack Compose 重写，界面使用
[coui17](https://github.com/Costben/coui17)（ColorOS 风格的 Compose Multiplatform 组件库）。
coui17 以 git submodule 放在 `third_party/coui17/`，通过 Gradle 复合构建直接参与编译，
替换 `io.github.suqi8.coui.kmp` 的发布坐标，因此始终使用子模块里的 ColorOS 17 改动。

当前进度：Flutter 版的页面与交互已完成移植。首页 7 个分段（关注 / 应用 / 动态 / 热门 /
话题 / 数码 / 酷图）、详情与回复、搜索、登录与人机验证、消息与私信、用户 / 应用 / 话题 /
酷图 / 动态号、设置及各子页均已接通，信息流卡片的图片查看、点赞、屏蔽、删除、举报等
交互与站内链接分发同 Flutter 版一致。逐项进度与落点见
[移植清单](PORTING.md)，已知差异与真机验证记录也在该文件。
功能对齐前，Compose 版使用独立的 applicationId `app.awavenue.c001apk.coui`，
与 Flutter 包并存安装，数据互不迁移。

工具链要求与 coui17 一致：JDK 21、Android SDK compileSdk 37 / build-tools 37.0.0。
AGP（9.3.1）与 Kotlin（2.4.10）版本必须与 `third_party/coui17/gradle/libs.versions.toml`
保持一致，升级子模块时一并核对。

```bash
git clone --recurse-submodules https://github.com/TG-Twilight/c001apk.git
# 已有仓库：git submodule update --init

cd c001apk/apps/android
./gradlew :app:testDebugUnitTest   # 单元测试：请求签名与 Flutter 实现逐字节对照、响应错误分类、列表解析
./gradlew :app:assembleDebug       # 调试 APK：app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease     # 正式包，签名配置同上文 Flutter Android
```

正式签名读取与 Flutter Android 相同的环境变量；本地 `key.properties` 放在 `apps/android/` 下。
缺少签名时 release 构建直接失败。

## 参与维护

欢迎提交 [Issue](https://github.com/TG-Twilight/c001apk/issues) 或 Pull Request。反馈问题时请提供平台、应用版本、复现步骤与脱敏错误信息，避免附带密码、Cookie、用户 Token 或私信内容。

API 修改请区分网络错误、HTTP 状态、业务拒绝、验证码和模型解析错误，并保留离线回归。修改存储时保留用户历史、收藏与设置；修改模型时使用生成器更新 `*.g.dart`。
