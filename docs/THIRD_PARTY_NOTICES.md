# 第三方声明

本项目保留原有 [LICENSE](../LICENSE) 与上游署名。

`apps/flutter/lib/utils/token_util.dart` 中的 Token V3 兼容实现及
`apps/flutter/lib/utils/token_v3_data.dart` 中的协议查找表（Compose 版移植为
`apps/android/app/src/main/kotlin/app/awavenue/c001apk/data/TokenUtils.kt` 与 `TokenV3Data.kt`）参考自
[daimiaopeng/coolapk-desktop](https://github.com/daimiaopeng/coolapk-desktop)，
来源提交 `3ef2ad4da6d171e118d2b29b0f18d7ffa6da9086`，文件
`src-tauri/src/coolapk/auth.rs` 与 `auth_blob.b64`，采用日期 2026-10-14。
原项目以 MIT 授权，其完整许可证及版权声明见
[coolapk-desktop MIT](licenses/coolapk-desktop-MIT.txt)。

以上许可适用于原项目原创代码；查找表用于非公开协议兼容，其第三方来源
与权利不由原项目 MIT 许可担保。酷安名称、商标及服务内容归相应权利人。
未引入参考项目的 Tauri/Vue 架构或在线测试集。

2026-10-14，游客动态详情的官方分享页 JSON 请求方式参考同一提交的
`src-tauri/src/coolapk/client.rs`（`fetch_external_page` 中的公开动态请求）。
本项目独立实现 Flutter 请求和回退逻辑，并实测补齐该页面需要的客户端
签名头；上述 MIT 许可证及版权声明同样适用于这一协议调研来源。

`apps/flutter/test/fixtures/token_v3.json` 为合成设备与固定时间的测试签名，使用独立
Node bcryptjs 实现生成，不含真实设备、账号凭据或用户 Token。

2026-10-14，`captcha_challenge.dart`、`captcha_page.dart` 的验证码配置解析、
`NEC` 验证凭证格式，以及点赞/回复的表单参数参考上述同一提交的
`src/utils/neteaseCaptcha.ts` 与 `src-tauri/src/coolapk/client.rs`，适用上述 MIT 声明。
Flutter 验证界面独立实现，运行时加载网易易盾官方 Web SDK，不把 SDK 打包到源码中。
SDK 接入回调另核对 [网易易盾官方示例](https://github.com/yidun/captcha-php-demo/blob/master/index.php)。

2026-10-14，Android 启动器图标的系统 Monet 亮暗配色参考
[bggRGjQaUbCoE/PiliPlus](https://github.com/bggRGjQaUbCoE/PiliPlus)，
来源提交 `bd5e04e5de5a523e85a8eeba30da748dcd68925a`，文件
`android/app/src/main/res/values-v31/colors.xml` 与
`android/app/src/main/res/values-night-v31/colors.xml`。
该项目采用 GPL-3.0，完整许可证见 [PiliPlus GPL-3.0](licenses/PiliPlus-GPL-3.0.txt)。
仅参考系统颜色资源搭配，保留 c001apk 原有无穷符号和单色层，未引入 PiliPlus 图形素材。

2026-10-15，Android Compose 应用的界面组件来自 [Costben/coui17](https://github.com/Costben/coui17)，
以 git submodule 形式位于 `third_party/coui17/`，其上游为
[suqi8/coui](https://github.com/suqi8/coui) 与 [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)，
均采用 Apache-2.0，许可证与 NOTICE 随子模块保存在 `third_party/coui17/LICENSE`、`third_party/coui17/NOTICE`。
`ui/common/Icons.kt` 中点赞、评论图标的路径数据取自 Material Icons（Apache-2.0）。
