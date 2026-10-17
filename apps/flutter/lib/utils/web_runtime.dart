import 'dart:io';

import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:path_provider/path_provider.dart';

/// All WebViews and their cookie manager must use the same WebView2 profile.
class WebRuntime {
  static WebViewEnvironment? environment;
  static Future<void>? _ready;

  static CookieManager get cookies =>
      CookieManager.instance(webViewEnvironment: environment);

  static Future<void> ensureReady() async {
    if (!Platform.isWindows) return;
    try {
      await (_ready ??= _initialize());
    } catch (_) {
      _ready = null;
      rethrow;
    }
  }

  static Future<void> _initialize() async {
    if (await WebViewEnvironment.getAvailableVersion() == null) {
      throw StateError('请安装 Microsoft Edge WebView2 运行时后重试');
    }
    final support = await getApplicationSupportDirectory();
    environment = await WebViewEnvironment.create(
        settings: WebViewEnvironmentSettings(
            userDataFolder:
                '${support.path}${Platform.pathSeparator}webview2'));
  }

  static String errorMessage(Object? error) => error is StateError
      ? error.message.toString()
      : '网页组件初始化失败，请检查 Microsoft Edge WebView2 运行时后重试';
}
