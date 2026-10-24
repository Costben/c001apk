# 第三方声明

本项目保留原有 [LICENSE](../LICENSE) 与上游署名。

Markdown 解析、渲染与导出为项目内实现，没有引入新的第三方解析库。

2026-10-16，新增的 `assets/emojis/niuniu_emotion_*.png` 为酷安官方
Android 16.6.4（2609291）安装包中的 32 张牛牛表情，用于兼容官方表情名称。
名称以官方 `niuniu_emotion_array` 核对；素材版权归酷安及相应权利人所有，
不因本项目的 AGPL-3.0 许可证而改变。未引入官方应用代码、账号或用户数据。
Compose 端（`apps/android/app/src/main/assets/emojis/`）直接复用同一批 PNG，
文件名与名称表保持一致。

本轮会话置顶、取消置顶及删除的接口路径参考
[c001apk-compose](https://github.com/bggRGjQaUbCoE/c001apk-compose)
提交 `d44c471fb38fa600d28fa33b5dd7fcfd6abb7197` 的
`ui/notification/NoticeViewModel.kt`、`logic/network/ApiService.kt`，
该项目同样采用 AGPL-3.0（见本项目保留的根 LICENSE）。
Flutter 状态管理、失败重试及会话刷新协调在本项目实现。

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

2026-10-16，空 `captchaField` 对应 `validate` Cookie 的协议语义经官方
Android 16.6.4（2609291）客户端与游客只读响应对照核实。本项目独立实现
该分支及当前账号、设备范围内的内存复用，没有移植或分发官方客户端代码。
SDK 接入回调另核对 [网易易盾官方示例](https://github.com/yidun/captcha-php-demo/blob/master/index.php)。

2026-10-14，Android 启动器图标的系统 Monet 亮暗配色参考
[bggRGjQaUbCoE/PiliPlus](https://github.com/bggRGjQaUbCoE/PiliPlus)，
来源提交 `bd5e04e5de5a523e85a8eeba30da748dcd68925a`，文件
`android/app/src/main/res/values-v31/colors.xml` 与
`android/app/src/main/res/values-night-v31/colors.xml`。
该项目采用 GPL-3.0，完整许可证见 [PiliPlus GPL-3.0](licenses/PiliPlus-GPL-3.0.txt)。
仅参考系统颜色资源搭配，保留 c001apk 原有无穷符号和单色层，未引入 PiliPlus 图形素材。

2026-10-15，账号切换入口、设置分类与文字层级参考同一项目
提交 `868580c3edbe13f874583a3139b1f4d116d4cc0f` 的
`lib/pages/setting/view.dart`、`lib/pages/setting/widgets/normal_item.dart`、
`lib/utils/theme_utils.dart` 与账号管理相关实现。
Flutter 页面和酷安多账号会话逻辑在本项目独立实现，沿用既有依赖及存储结构；
参考项目的 GPL-3.0 许可证见上述链接。

2026-10-15，c001apk 自身更新弹窗的更新说明、关闭提醒与分平台下载入口
参考同一项目提交 `d529e0a2d90a2a3a04ef9847e6c14187852ea6ee` 的
`lib/utils/update.dart`。本项目独立实现语义版本比较、24 小时限频、跳过版本、
独立网络请求及设置持久化；没有采用参考实现按构建时间判断版本的方式。
参考项目的 GPL-3.0 许可证见上述链接。

2026-10-15，Android Compose 应用的界面组件来自 [Costben/coui17](https://github.com/Costben/coui17)，
以 git submodule 形式位于 `third_party/coui17/`，其上游为
[suqi8/coui](https://github.com/suqi8/coui) 与 [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)，
均采用 Apache-2.0，许可证与 NOTICE 随子模块保存在 `third_party/coui17/LICENSE`、`third_party/coui17/NOTICE`。
`ui/common/Icons.kt` 中点赞、评论图标的路径数据取自 Material Icons（Apache-2.0）。
