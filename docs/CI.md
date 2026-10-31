# GitHub Actions 云端构建

[返回开发指南](DEVELOPMENT.md) · [查看 Actions](https://github.com/Costben/c001apk/actions/workflows/build.yml)

配置入口为 [Build workflow](../.github/workflows/build.yml)。构建使用 Temurin JDK 21、
Android SDK compileSdk 37 / build-tools 37.0.0，所有 Gradle 命令从 `apps/android/` 执行。
coui17 以 submodule 参与构建，检出时需递归拉取子模块；Action 引用固定到完整提交 SHA。

## 运行方式

- 推送 `main` 或提交 PR，且改动 `apps/android/`、`third_party/coui17/` 或 workflow 时，
  自动执行单元测试和 debug 构建。
- 在 Actions → Build → Run workflow 手动运行，默认 `release=true`，生成正式签名 APK。
- 没有正式签名 Secrets 的 fork 可将 `release` 取消勾选，只执行 debug 构建。

| 任务 | Runner | 产物 |
| --- | --- | --- |
| 单元测试 + debug | `ubuntu-24.04` | `c001apk-coui-<版本>-debug.apk`，保留 7 天 |
| 正式签名 release | `ubuntu-24.04` | `c001apk-coui-<版本>.apk` 与 SHA-256 清单，保留 14 天 |

版本号取自 `apps/android/app/build.gradle.kts` 的 `versionName` / `versionCode`，
发版时手动提升；云端构建不修改仓库内的版本文件。
从运行详情的 Artifacts 下载；此 workflow 不创建 GitHub Release。

## Android 正式签名

维护者在仓库 Settings → Secrets and variables → Actions 配置：

| Secret | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 正式 JKS 的 Base64，单行编码 |
| `REVIA_KS_PASS` | 同时用于 store/key 的密码 |

当前正式身份的 alias 为 `秋风のとおり道`，证书 SHA-256 为
`EA:38:5A:FC:82:E1:98:24:EE:A8:A8:86:8E:D3:65:DF:03:E9:88:6B:BB:A3:3E:47:E1:E0:4B:43:EC:65:CB:67`。
以上是公开证书元数据；其他 fork 如使用自己的密钥，须同时调整 alias 和期望指纹。

手动 release 任务先检查 Secrets，再将 JKS 恢复到 runner 临时目录，并用
`keytool -storepass:env` 核对公开证书，然后以 `KEYSTORE`、`KEY_ALIAS` 与密码环境变量
传给 Gradle。构建后用 `apksigner verify` 核对指纹，校验失败不会上传安装包。
清理步骤使用 `always()`，在成功或失败后移除临时 JKS 和证书；不缓存 Gradle 目录，
只上传指定的分发文件，JKS、密码和 Base64 不进入 Git 或 artifacts。
公共 PR 和自动 push 不读取正式签名 Secrets。Secrets 的二进制存储方式见
[官方文档](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)。

## 排查失败

先检查失败步骤日志，区分 SDK/依赖下载、子模块检出、单元测试、编译和签名错误。
runner 自带 Android Studio JBR 可能覆盖 JDK，构建统一使用 `actions/setup-java` 安装的
Temurin 21；SDK packages 在构建前显式安装，Gradle wrapper 随仓库提供。
缺少 Secrets 或证书不匹配时补齐正确材料，不用 debug 密钥替代 release。
一次云端构建成功只证明该提交的检查和编译，不代表全部线上 API 或真实界面已验收。
