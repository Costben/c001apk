# 开发指南

[返回项目首页](../README.md)

## 项目结构

```text
c001apk/
├── apps/
│   └── android/                 # Kotlin / Jetpack Compose 应用（coui17 界面）
│       ├── app/src/main/kotlin/app/awavenue/c001apk/
│       │   ├── data/            # 账号、设置、收藏与历史、协议、更新与下载
│       │   ├── network/         # 请求签名、错误分类、验证码与 Cookie
│       │   ├── model/           # 接口模型
│       │   ├── media/           # 图片、动图与视频处理
│       │   ├── platform/        # 平台相关适配
│       │   └── ui/              # 页面、导航、组件与主题
│       ├── app/src/test/kotlin/ # 单元测试
│       ├── app/build.gradle.kts
│       └── gradlew
├── docs/
│   ├── DEVELOPMENT.md           # 开发与验证说明
│   ├── CI.md                    # 云端构建与签名
│   ├── UI-MOTION-AND-PERSISTENCE.md  # 过渡动画与内容持久化实现
│   ├── media/                   # README 横幅与界面截图
│   ├── THIRD_PARTY_NOTICES.md
│   └── licenses/                # 第三方许可原文
├── third_party/
│   └── coui17/                  # coui17 组件库（git submodule，源码参与构建）
├── LICENSE
└── README.md
```

## 工具链

界面组件来自 [coui17](https://github.com/Costben/coui17)（ColorOS 风格的 Compose Multiplatform
组件库），以 git submodule 放在 `third_party/coui17/`，通过 Gradle 复合构建直接参与编译，
替换 `io.github.suqi8.coui.kmp` 的发布坐标，因此始终使用子模块里的 ColorOS 17 改动。

工具链要求与 coui17 一致：JDK 21、Android SDK compileSdk 37 / build-tools 37.0.0，
Gradle 9.8.2 由 wrapper 提供。AGP（9.3.1）与 Kotlin（2.4.10）版本必须与
`third_party/coui17/gradle/libs.versions.toml` 保持一致，升级子模块时一并核对。

## 本地开发

```bash
git clone --recurse-submodules https://github.com/Costben/c001apk.git
# 已有仓库：git submodule update --init

cd c001apk/apps/android
./gradlew :app:testDebugUnitTest   # 单元测试：请求签名、错误分类、Markdown、更新解析等
./gradlew :app:assembleDebug       # 调试 APK：app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease     # 正式包，需要签名配置，见下文
```

所有 Gradle 命令都在 `apps/android/` 下执行。若本机存在与仓库同级的 `coui` 检出，
`settings.gradle.kts` 会优先使用该目录而不是子模块，方便同时调试组件库。

真机验证请安装 release 包：debug 包在高刷设备上信息流滚动明显掉帧，与用户实际体验不一致。
保持设备上既有安装的签名一致即可覆盖安装，不要为了换签名而卸载应用。

### 应用更新检测

设置 → 存储与更新提供“自动检查 c001apk 更新”，默认开启，启动后后台检查，
每次冷启动检测一次，同次运行不重复自动请求；关闭后不再自动请求。存储与更新、关于页
均可手动检查，手动操作不受自动开关、跳过版本或稍后提醒影响。自动检查失败不打扰，
手动失败显示中文原因。

更新来源为本仓库的 GitHub 正式 Release，按语义版本比较，预发布标签排在稳定版之前；
同一语义版本重新构建不触发更新提示，发布修订版时应提升语义版本。
弹窗展示版本、发布日期、完整可滚动说明和发布页入口，底部提供“稍后”“跳过”“更新”。
检测按设备支持的 ABI 匹配 `c001apk-coui-<版本>[-<ABI>].apk` 资源，找不到匹配资产时
打开该 Release 页面。应用内更新在设置中手动触发下载并拉起系统安装器。

实现位于 `data/AppUpdate.kt`（版本比较、Release 解析）与 `data/InAppUpdater.kt`
（下载与安装），弹窗在 `ui/settings/AppUpdateDialog.kt`，独立页在 `ui/app/AppUpdateScreen.kt`。
检测使用独立 OkHttp 客户端，不加入酷安拦截器、Cookie、账号 Token 或设备签名。
提醒状态保存在 `SharedPreferences` 的设置键中，保留原账号、历史及收藏。
`AppUpdateTest` 使用合成 Release 与固定时间验证解析和比较逻辑，不访问 GitHub，
也不修改真实用户设置。

## 签名与 Release 构建

release 构建必须配置正式签名，缺少 keystore、alias 或密码时构建直接失败，不会产出
debug 签名的正式包。以下环境变量在构建时读取：

| 环境变量 | 用途 |
| --- | --- |
| `KEYSTORE` | JKS 的路径，建议绝对路径 |
| `KEY_ALIAS` | 签名 alias |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_PASSWORD` | 私钥密码 |
| `REVIA_KS_PASS` | 两项专用密码变量未分别设置时的统一密码 |

也可以在 `apps/android/` 下放 `key.properties`（已被 Git 忽略），填写 `storeFile` 与
`keyAlias`；密码仍建议通过环境变量提供，勿写进该文件或提交到仓库。
含中文或日文的路径/alias 使用 Properties 的 `\uXXXX` 转义，或通过环境变量传入。
macOS / Linux 下可直接前缀环境变量执行：

```bash
KEYSTORE="$HOME/keys/release.jks" KEY_ALIAS=release \
  KEYSTORE_PASSWORD=*** KEY_PASSWORD=*** ./gradlew :app:assembleRelease
```

GitHub Actions 配置见 [云端构建指南](CI.md) 与 [Build workflow](../.github/workflows/build.yml)。

应用包名为 `app.awavenue.c001apk.coui`，酷安协议中的 `X-App-Id: com.coolapk.market`
保持其协议用途。升级时保留原有 applicationId 与签名，用户数据不会迁移或丢失。

## 测试

`./gradlew :app:testDebugUnitTest` 运行离线单元测试，覆盖请求签名与 Token V3、
响应错误分类、验证码 Cookie 协议、Markdown 解析与导出、本地关注分页、更新版本比较等。
测试不访问真实账号，也不写入用户数据。

服务端下发空 `captchaField` 时，验证结果使用 `validate` Cookie；非空的合法字段继续按原请求
参数传递。Cookie 只在当前进程的同一账号、设备会话内复用，不落盘，不发送到公开分享页或
外部主机。重新启动应用后服务端仍可能要求验证；验证后的原请求最多重试一次，持续拒绝不会
循环弹出验证。

若代理配置影响本机连接，可先设置 `NO_PROXY=localhost,127.0.0.1,::1` 再重试。

## 账号管理与设置

主导航为“主页、关注、我的”，宽窗口侧栏同步显示。“关注”展示关注流，原主页顶部不再保留
“关注”。主页与关注右上角仅保留搜索，独立设置从“我的”右上角进入。设置按
“外观与字号、关注与内容、浏览与隐私、存储与更新、协议与设备”分类；窄窗口进入分类子页，
宽窗口并排显示分类与内容。

“我的”通过“私信与信息”进入会话列表，酷安小秘书作为独立会话与普通私信并列，点击后查看
原系统通知流；个人页底部不再直接展开通知。

长按普通会话可置顶、取消置顶或确认删除。聊天的图片、动态入口始终保留；发送文字或选择
图片后立即显示气泡，上传和发送进度显示在消息旁，失败保留在当前聊天页并支持手动重试。
网络中断等无法确认结果时会提示先检查记录，避免重复发送；离开聊天页不会持久保存待发或
失败消息。

动态可从“我的动态、本地收藏、链接”选择，确认后发送规范的酷安动态链接；本客户端将独立
动态链接显示为可点击卡片。表情面板分“常用、牛牛、酷币”，正文与输入面板均支持官方 32 个
牛牛表情。

发布动态时，输入框工具行的“谁可以看”可选择“所有人”或“仅自己”；仅自己可见的动态
（服务端 `publish_status=1`）在信息流卡片和详情页的时间旁带锁标识。回复他人的动态仍使用
同一位置的“回复并转发”开关。

“我的”和设置页均提供“账号管理”。首次启动自动保存已有有效登录；添加账号成功后保留此前
登录，点击已保存账号即可切换。“使用游客模式”保留已保存的账号，方便恢复；移除账号会清除
该账号在本机保存的登录，移除当前账号后进入游客模式。登录失效的账号会提示重新登录。

账号切换会关闭旧账号的详情等页面，重新加载主页、关注和我的，并丢弃旧请求的晚到响应。
历史、本地收藏、屏蔽设置和本地关注继续由本机共用。账号与会话信息保存在
`SharedPreferences`，账号密码不保存。

界面标题、设置标题与说明参考 PiliPlus 的文字层级；“外观与字号 → 字体大小”可调节应用字号，
并与系统文字缩放叠加。

## 本地关注与悄悄关注

游客可在用户主页点击“本地关注”，在底栏“关注”查看名单中用户的动态。登录后，在
“设置 → 关注与内容”中开启“悄悄关注”，长按用户主页的关注按钮添加或取消；短按仍操作
账号关注。“关注”页左上角标题为下拉按钮，可选择账号的用户、话题、数码、应用、全部关注，
或切换到本机悄悄关注。默认显示用户关注，明确区分关注作者与关注话题；选择保存在本机，
切换分类时重新从第一页加载。设置中不再提供重复的账号关注筛选入口。

设置提供名单管理入口。名单只保存在当前设备，不会自动同步到账号；登录、退出或关闭悄悄
关注开关不会清空名单。当前支持用户，暂不包含话题、数码和应用。本地关注不需要登录，
读取动态仍需要网络，并受接口权限、验证码或风控限制。

## 界面实现参考

过渡动画、底栏、悬浮按钮与内容持久化的实现细节见
[过渡与持久化实现](UI-MOTION-AND-PERSISTENCE.md)，其中逐项标注了对应文件与行号，
改动相关代码前建议先读该文档，避免破坏已验证的过渡时序。

## 参与维护

欢迎提交 [Issue](https://github.com/Costben/c001apk/issues) 或 Pull Request。反馈问题时请提供
应用版本、复现步骤与脱敏错误信息，避免附带密码、Cookie、用户 Token 或私信内容。

API 修改请区分网络错误、HTTP 状态、业务拒绝、验证码和模型解析错误，并保留离线回归。
修改存储时保留用户历史、收藏与设置；改动界面时序时同步更新
[过渡与持久化实现](UI-MOTION-AND-PERSISTENCE.md)。
