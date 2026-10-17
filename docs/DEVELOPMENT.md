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

### 应用更新检测

设置 → 存储与更新提供“自动检查 c001apk 更新”，默认开启，启动后后台检查，
每次冷启动检测一次，同次运行不重复自动请求；关闭后不再自动请求。该开关与 Android 的
“检查已安装应用更新”独立。存储与更新、关于页均可手动检查，手动操作不受
自动开关、跳过版本或稍后提醒影响。自动检查失败不打扰，手动失败显示中文原因。

更新来源为 `Costben/c001apk` 的 GitHub Latest 正式 Release；
不检测 Actions artifacts、草稿或预发布版本。按语义版本比较，兼容
`1.1.0-release` / `v1.2.0` 标签，不比较 Release 时间或 Android ABI 版本码。
同一语义版本重新构建不触发更新提示，发布修订版时应提升语义版本。

弹窗展示版本、发布日期、完整可滚动说明和发布页入口，底部只提供“稍后”
（结束本次提醒，下次冷启动继续检测）、“跳过”（当前版本）、“更新”。自动检测仅在设置中关闭。
关闭弹窗等同稍后，旧版24小时检测/稍后时间戳不再拦截启动检测。Android 按设备支持 ABI 匹配 APK，Windows/Linux
匹配现有 x86_64 ZIP/tar.gz；没有匹配资产时打开该 Release 页面。
下载通过系统浏览器进行，资产链接打开失败会尝试发布页，不自动覆盖安装。
iOS 当前没有可安装正式包，只打开发布页面。

实现位于 `lib/logic/app_update/` 和 `lib/components/update_*.dart`，
使用独立 Dio，不加入酷安拦截器、Cookie、账号 Token 或设备签名。
提醒状态保存在既有 Hive settings 中的独立键，保留原账号、历史及收藏。
`test/update/` 使用合成 Release、可控时间、临时 Hive 和浏览器通道验证，
普通测试不访问 GitHub，也不修改真实用户设置。

当前维护使用 **Flutter 3.24.5 / Dart 3.5.4**。依赖版本以 `apps/flutter/pubspec.lock` 为准，建议先使用相同工具链复现。

Windows 构建需要 Visual Studio 的 C++ 桌面开发组件及 Windows SDK。现有 Android 工程使用 Gradle 7.6.3、AGP 7.3.0、Kotlin 1.7.10 和 compileSdk 34，本地构建使用标准 JDK 17；请准备相应 Android SDK。这些要求适用于当前 Flutter 工程。

Windows 网页登录和人机验证使用 WebView2。构建前需将 `nuget.exe` 所在目录加入当前进程的 PATH；运行机器需安装 [Microsoft Edge WebView2 Runtime](https://developer.microsoft.com/microsoft-edge/webview2/)。WebView2 数据保存在应用支持目录的 `webview2/` 中，与 Hive 账号和设置分开。使用方式为登录页右上角“网页登录”，在酷安官方网页完成验证及登录后自动返回并保存账号；取消保留原账号。参考 [插件 Windows 配置](https://inappwebview.dev/docs/webview/in-app-webview/)。

`windows/cmake/webview_lifetime_fix.cmake` 修正 Windows WebView 插件0.6.0的退出资源释放顺序：配置时生成原生源文件的补丁副本，在平台消息队列仍运行时释放图形合成对象，避免退出时 `Unknown Hard Error`。不会改动全局 Pub 缓存；升级 WebView 插件时需复查并移除或更新此补丁。Windows 改动还应验证关闭窗口后进程正常结束：

```powershell
powershell -NoProfile -File tool/windows_close_smoke.ps1 `
  -ExePath build/windows/x64/runner/Release/c001apk.exe `
  -ReportPath windows-close-report.json
```

此脚本会实际启动及关闭桌面窗口，检查退出码和系统错误弹窗事件。运行前关闭已有实例；还需手动复核打开网页登录、取消后关闭，以及直接在网页登录页关闭窗口。

```powershell
git clone https://github.com/Costben/c001apk.git
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
| iOS | `arm64`（真机） | 云端未签名 IPA 构建、架构与校验值检查已通过；Apple 签名与真机运行待实施 |

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
手动运行可生成正式签名的 Android 三 ABI APK，并选择构建 iOS arm64 未签名 IPA（安装前需自行签名）。
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
登录后受限详情可读取公开正文，界面会说明账号点赞状态尚未取得；Android/iOS 提供手动人机验证入口，Windows 通过 WebView2 提供网页登录与人机验证页面。完成验证不保证服务端解除其他风控。

服务端下发空 `captchaField` 时，验证结果使用 `validate` Cookie；非空的合法字段继续按原请求参数传递。
Cookie 只在当前进程的同一账号、设备会话内复用，不落盘，不发送到公开分享页或外部主机。
重新启动应用后服务端仍可能要求验证；验证后的原请求最多重试一次，持续拒绝不会循环弹出验证。

需要主动检查游客只读接口时，可单独执行：

```powershell
$env:COOLAPK_LIVE_PROBE = '1'
flutter test test/live/api_probe_test.dart
Remove-Item Env:COOLAPK_LIVE_PROBE
```

游客动态、文章详情检查使用 `COOLAPK_GUEST_DETAIL_PROBE=1` 和 `test/live/guest_detail_test.dart`。在线检查的脱敏结构结果写入 Flutter 工程下忽略的 `.local/reports/`；验证码或业务拒绝会如实导致检查失败。

若代理配置影响本机测试连接，可在当前 PowerShell 会话设置 `$env:NO_PROXY = 'localhost,127.0.0.1,::1'` 后重试。

## 账号管理与设置

主导航为“主页、关注、我的”，宽窗口侧栏同步显示。“关注”展示关注流，原主页顶部不再保留“关注”。主页与关注右上角仅保留搜索，独立设置从“我的”右上角进入。设置按“外观与字号、关注与内容、浏览与隐私、存储与更新、协议与设备”分类；窄窗口进入分类子页，宽窗口并排显示分类与内容。

“我的”通过“私信与信息”进入会话列表，酷安小秘书作为独立会话与普通私信并列，点击后查看原系统通知流；个人页底部不再直接展开通知。

长按普通会话可置顶、取消置顶或确认删除。聊天的图片、动态入口始终保留；发送文字或选择图片后立即显示气泡，上传和发送进度显示在消息旁，失败保留在当前聊天页并支持手动重试。网络中断等无法确认结果时会提示先检查记录，避免重复发送；离开聊天页不会持久保存待发或失败消息。

动态可从“我的动态、本地收藏、链接”选择，确认后发送规范的酷安动态链接；本客户端将独立动态链接显示为可点击卡片。当前未使用官方专用动态卡片的扩展发送字段。表情面板分“常用、牛牛、酷币”，正文与输入面板均支持官方 32 个牛牛表情。

“我的”和设置页均提供“账号管理”。首次升级自动保存已有有效登录；添加账号成功后保留此前登录，点击已保存账号即可切换。“使用游客模式”保留已保存的账号，方便恢复；移除账号会清除该账号在本机保存的登录，移除当前账号后进入游客模式。登录失效的账号会提示重新登录。

账号切换会关闭旧账号的详情等页面，重新加载主页、关注和我的，并丢弃旧请求的晚到响应。历史、本地收藏、屏蔽设置和本地关注继续由本机共用。会话信息沿用本机 Hive 存储，账号密码不保存。

界面标题、设置标题与说明参考 PiliPlus 的文字层级；“外观与字号 → 字体大小”可调节应用字号，并与系统文字缩放叠加。

## 本地关注与悄悄关注

游客可在用户主页点击“本地关注”，在底栏“关注”查看名单中用户的动态。登录后，在“设置 → 关注与内容”中开启“悄悄关注”，长按用户主页的关注按钮添加或取消；短按仍操作账号关注。“关注”页左上角标题为下拉按钮，可选择账号的用户、话题、数码、应用、全部关注，或切换到本机悄悄关注。默认显示用户关注，明确区分关注作者与关注话题；选择保存在本机，切换分类时重新从第一页加载。设置中不再提供重复的账号关注筛选入口。

设置提供名单管理入口。名单只保存在当前设备，不会自动同步到账号；登录、退出或关闭悄悄关注开关不会清空名单。当前支持用户，暂不包含话题、数码和应用。本地关注不需要登录，读取动态仍需要网络，并受接口权限、验证码或风控限制。

## Android Compose 应用

`apps/android/` 是 Android 端的 Kotlin / Jetpack Compose 重写，界面使用
[coui17](https://github.com/Costben/coui17)（ColorOS 风格的 Compose Multiplatform 组件库）。
coui17 以 git submodule 放在 `third_party/coui17/`，通过 Gradle 复合构建直接参与编译，
替换 `io.github.suqi8.coui.kmp` 的发布坐标，因此始终使用子模块里的 ColorOS 17 改动。

当前进度：Flutter 1.1.0 的页面与交互已完成移植，上游 1.2.x 已补上主页 / 关注 / 我的导航、
本地关注与悄悄关注、多账号与游客模式和自身更新检查（来源为本仓库 Release，资源名
`c001apk-coui-<版本>[-<ABI>].apk`）。详情与回复、搜索、登录与人机验证、私信与信息、用户 / 应用 / 话题 /
酷图 / 动态号、设置及各子页均已接通，信息流卡片的图片查看、点赞、屏蔽、删除、举报等
交互与站内链接分发同 Flutter 版一致。逐项进度与落点见
[移植清单](PORTING.md)，已知差异与真机验证记录也在该文件。
功能对齐前，Compose 版使用独立的 applicationId `app.awavenue.c001apk.coui`，
与 Flutter 包并存安装，数据互不迁移。

工具链要求与 coui17 一致：JDK 21、Android SDK compileSdk 37 / build-tools 37.0.0。
AGP（9.3.1）与 Kotlin（2.4.10）版本必须与 `third_party/coui17/gradle/libs.versions.toml`
保持一致，升级子模块时一并核对。

```bash
git clone --recurse-submodules https://github.com/Costben/c001apk.git
# 已有仓库：git submodule update --init

cd c001apk/apps/android
./gradlew :app:testDebugUnitTest   # 单元测试：请求签名与 Flutter 实现逐字节对照、响应错误分类、列表解析
./gradlew :app:assembleDebug       # 调试 APK：app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease     # 正式包，签名配置同上文 Flutter Android
```

正式签名读取与 Flutter Android 相同的环境变量；本地 `key.properties` 放在 `apps/android/` 下。
缺少签名时 release 构建直接失败。

## 参与维护

欢迎提交 [Issue](https://github.com/Costben/c001apk/issues) 或 Pull Request。反馈问题时请提供平台、应用版本、复现步骤与脱敏错误信息，避免附带密码、Cookie、用户 Token 或私信内容。

API 修改请区分网络错误、HTTP 状态、业务拒绝、验证码和模型解析错误，并保留离线回归。修改存储时保留用户历史、收藏与设置；修改模型时使用生成器更新 `*.g.dart`。
