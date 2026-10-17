<div align="center">

<img src="docs/media/banner.png" alt="c001apk — 让酷安，轻一点。" width="100%">

# c001apk

**轻量、简体中文的非官方酷安客户端**

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-607D8B?style=flat-square)](LICENSE)
[![Flutter](https://img.shields.io/badge/Flutter-3.24.5-02569B?style=flat-square&logo=flutter&logoColor=white)](docs/DEVELOPMENT.md)
[![Build](https://github.com/TG-Twilight/c001apk/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/TG-Twilight/c001apk/actions/workflows/build.yml)
![Platforms](https://img.shields.io/badge/平台-Android%20%7C%20Windows-486B78?style=flat-square)
![Status](https://img.shields.io/badge/状态-持续维护中-6E8B74?style=flat-square)

[下载](https://github.com/TG-Twilight/c001apk/releases) · [反馈问题](https://github.com/TG-Twilight/c001apk/issues) · [参与开发](docs/DEVELOPMENT.md)

</div>

## 关于

基于 [c001apk-flutter](https://github.com/bggRGjQaUbCoE/c001apk-flutter) 继续维护，修复 API、完善中文体验，让日常浏览保持轻巧。

<table align="center">
  <thead>
    <tr>
      <th align="center">浏览内容</th>
      <th align="center">找到兴趣</th>
      <th align="center">按你的习惯</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td align="center">动态、文章、评论、用户空间</td>
      <td align="center">动态、应用、用户、话题与内容搜索</td>
      <td align="center">明暗主题、字体大小</td>
    </tr>
    <tr>
      <td align="center">图文详情与图片查看</td>
      <td align="center">应用、话题及产品详情</td>
      <td align="center">本地收藏与屏蔽、浏览历史</td>
    </tr>
  </tbody>
</table>

## 界面预览

<p align="center">
  <img src="docs/media/screenshots/android-home.avif" width="31%" alt="Android 首页：动态列表与底部导航">
  <img src="docs/media/screenshots/android-article.avif" width="31%" alt="Android 图文详情：文章封面与正文">
  <img src="docs/media/screenshots/android-settings.avif" width="31%" alt="Android 中文设置：字体、历史记录与缓存管理">
</p>
<p align="center"><sub>Android · 首页 / 图文详情 / 中文设置</sub></p>

<p align="center">
  <img src="docs/media/screenshots/windows-home.avif" width="96%" alt="Windows 桌面端首页：侧边导航与宽屏内容区域">
</p>
<p align="center"><sub>Windows · 桌面端首页</sub></p>

以上为当前 Flutter 调试版的真实截图，界面随主题设置变化。

## 下载与进度

正式安装包可从 [Releases](https://github.com/TG-Twilight/c001apk/releases) 获取，也可以参照 [开发指南](docs/DEVELOPMENT.md) 自行构建。

云端构建包可从 [Actions](https://github.com/TG-Twilight/c001apk/actions/workflows/build.yml) 的 Artifacts 下载，平台、签名与手动构建方式见 [云端构建指南](docs/CI.md)。

- [x] 更新公共 API 请求协议、签名与错误处理。
- [x] 完成现有 Flutter 界面的一轮简体中文汉化。
- [x] 恢复主要游客浏览流程，为部分受限动态、文章提供公开详情回退。
- [x] 基本修复账号登录与状态管理，补齐验证码交互。
- [ ] 继续完善验证码放行与部分账号操作。
- [ ] Android 迁移到 Kotlin / Jetpack Compose，采用 [coui17](https://github.com/Costben/coui17) ColorOS 风格组件库：已完成主导航与首页列表，见 [开发指南](docs/DEVELOPMENT.md#android-compose-应用)。
- [ ] 完善 Flutter 桌面端随窗口尺寸变化的自适应布局与图片保存。

当前重点维护 **Android 与 Windows**，代码仍为 Flutter。Android 包名为 `app.awavenue.c001apk`；Linux 已通过云端 release 构建，iOS 已通过无签名编译检查，运行体验仍待验收。新包与旧包独立安装，登录及本地数据不会自动迁移。

> [!NOTE]
> 部分内容仍受登录、验证码和服务端接口限制，验证码放行与部分账号操作仍需继续完善。Windows 暂不支持应用内直接保存图片，可在浏览器中打开后保存。

## 参与与致谢

欢迎提交 [Issue](https://github.com/TG-Twilight/c001apk/issues) 或 Pull Request。反馈请附平台、版本、复现步骤与脱敏错误信息；开发环境、工程目录及检查命令见 [开发指南](docs/DEVELOPMENT.md)。

感谢 [bggRGjQaUbCoE/c001apk-flutter](https://github.com/bggRGjQaUbCoE/c001apk-flutter) 的开源，以及 [daimiaopeng/coolapk-desktop](https://github.com/daimiaopeng/coolapk-desktop) 的协议调研参考。

本项目沿用 [GNU AGPL v3](LICENSE)，第三方来源与 MIT 许可见 [第三方声明](docs/THIRD_PARTY_NOTICES.md)。本项目与酷安官方无隶属关系，酷安名称、商标及服务内容归相应权利人所有。
