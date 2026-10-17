// ignore_for_file: constant_identifier_names

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart';

import '../../constants/constants.dart';
import '../../logic/accounts/account_store.dart';
import '../../utils/cache_util.dart';
import '../../utils/extensions.dart';
import '../../utils/storage_util.dart';
import '../../utils/utils.dart';
import '../../utils/web_session.dart';
import '../../utils/web_runtime.dart';

enum WebviewMenuItem {
  Refresh,
  Copy,
  Open_In_Browser,
  Clear_Cache,
  Go_Back;

  String get label => switch (this) {
        WebviewMenuItem.Refresh => '刷新',
        WebviewMenuItem.Copy => '复制',
        WebviewMenuItem.Open_In_Browser => '在浏览器中打开',
        WebviewMenuItem.Clear_Cache => '清理缓存',
        WebviewMenuItem.Go_Back => '返回上一页',
      };
}

class WebviewPage extends StatefulWidget {
  const WebviewPage({super.key});

  @override
  State<WebviewPage> createState() => _WebviewPageState();
}

class _WebviewPageState extends State<WebviewPage> {
  final String _url = Get.parameters['url'] ?? '';
  final bool _isLogin = Get.parameters['isLogin'] == '1';
  final _titleStream = StreamController<String?>();
  final _progressStream = StreamController<double>();

  InAppWebViewController? _webViewController;
  late Future<void> _cookiesReady;
  bool _savingLogin = false;
  String? _pageError;

  @override
  void initState() {
    super.initState();
    _cookiesReady = _prepareSession();
  }

  Future<void> _prepareSession() async {
    await WebRuntime.ensureReady();
    await prepareWebSession(
        (origin, domain, name, value) async {
          await WebRuntime.cookies.setCookie(
              url: WebUri.uri(origin),
              domain: domain,
              name: name,
              value: value,
              isSecure: true);
        },
        includeAccount: !_isLogin,
        removeCookie: (name) async {
          await WebRuntime.cookies.deleteCookie(
              url: WebUri('https://www.coolapk.com/'),
              domain: '.coolapk.com',
              name: name);
          for (final host in [
            'www.coolapk.com',
            'account.coolapk.com',
            'coolapk.com'
          ]) {
            await WebRuntime.cookies.deleteCookie(
                url: WebUri('https://$host/'), domain: host, name: name);
          }
        });
  }

  Future<void> _completeLogin(WebUri? url) async {
    // Only the official top-level landing page can complete a login. Read
    // cookies after navigation as WebView2 commits redirect cookies then.
    if (!_isLogin ||
        _savingLogin ||
        !mounted ||
        url == null ||
        url.scheme != 'https' ||
        url.host != 'www.coolapk.com' ||
        (url.path.isNotEmpty && url.path != '/')) return;
    _savingLogin = true;
    try {
      final cookies = await WebRuntime.cookies
          .getCookies(url: WebUri(Constants.URL_COOLAPK));
      final values = {
        for (final cookie in cookies) cookie.name: cookie.value.toString()
      };
      if (!mounted ||
          ['uid', 'username', 'token'].any((key) => values[key].isNullOrEmpty)) {
        return;
      }
      await AccountStore().saveLogin(
          uid: values['uid']!,
          username: values['username']!,
          token: values['token']!);
      if (mounted) Get.back(result: true);
    } catch (_) {
      if (mounted) SmartDialog.showToast('无法保存网页登录状态，请重试');
    } finally {
      _savingLogin = false;
    }
  }

  Widget _error(String message) => Center(
          child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Text(message),
          const SizedBox(height: 16),
          FilledButton(
              onPressed: () => setState(() {
                    _pageError = null;
                    _cookiesReady = _prepareSession();
                  }),
              child: const Text('重新加载')),
        ]),
      ));

  @override
  void dispose() {
    _titleStream.close();
    _progressStream.close();
    _webViewController = null;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: StreamBuilder(
          initialData: null,
          stream: _titleStream.stream,
          builder: (_, snapshot) => snapshot.data != null
              ? Text(
                  snapshot.data!,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                )
              : const SizedBox.shrink(),
        ),
        bottom: PreferredSize(
          preferredSize: Size.zero,
          child: StreamBuilder(
            initialData: 0.0,
            stream: _progressStream.stream,
            builder: (_, snapshot) => snapshot.data as double < 1
                ? LinearProgressIndicator(
                    value: snapshot.data as double,
                  )
                : const SizedBox.shrink(),
          ),
        ),
        actions: [
          PopupMenuButton(
            onSelected: (item) async {
              switch (item) {
                case WebviewMenuItem.Refresh:
                  _webViewController?.reload();
                  break;
                case WebviewMenuItem.Copy:
                  WebUri? uri = await _webViewController?.getUrl();
                  if (uri != null) {
                    Utils.copyText(uri.toString());
                  }
                  break;
                case WebviewMenuItem.Open_In_Browser:
                  WebUri? uri = await _webViewController?.getUrl();
                  if (uri != null) {
                    Utils.launchURL(uri.toString());
                  }
                  break;
                case WebviewMenuItem.Clear_Cache:
                  try {
                    await InAppWebViewController.clearAllCache();
                    await _webViewController?.clearHistory();
                    SmartDialog.showToast('已清理');
                  } catch (e) {
                    SmartDialog.showToast('缓存清理失败，请稍后重试');
                  }
                  break;
                case WebviewMenuItem.Go_Back:
                  if (await _webViewController?.canGoBack() == true) {
                    _webViewController?.goBack();
                  }
                  break;
              }
            },
            itemBuilder: (context) => <PopupMenuEntry<WebviewMenuItem>>[
              ...WebviewMenuItem.values.sublist(0, 4).map((item) =>
                  PopupMenuItem(value: item, child: Text(item.label))),
              const PopupMenuDivider(),
              PopupMenuItem(
                  value: WebviewMenuItem.Go_Back,
                  child: Text(
                    WebviewMenuItem.Go_Back.label,
                    style:
                        TextStyle(color: Theme.of(context).colorScheme.error),
                  )),
            ],
          )
        ],
      ),
      body: FutureBuilder<void>(
          future: _cookiesReady,
          builder: (_, snapshot) {
            if (snapshot.connectionState != ConnectionState.done) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snapshot.hasError) {
              return _error(WebRuntime.errorMessage(snapshot.error));
            }
            if (_pageError != null) return _error(_pageError!);
            return InAppWebView(
              webViewEnvironment: WebRuntime.environment,
              initialSettings: InAppWebViewSettings(
                useHybridComposition: false,
                mixedContentMode: MixedContentMode.MIXED_CONTENT_ALWAYS_ALLOW,
                useShouldOverrideUrlLoading: true,
                useOnDownloadStart: true,
                userAgent: GStorage.userAgent,
                forceDark: ForceDark.AUTO,
                algorithmicDarkeningAllowed: true,
              ),
              initialUrlRequest:
                  URLRequest(url: WebUri.uri(Uri.parse(_url)), headers: {
                'X-Requested-With': Constants.APP_ID,
              }),
              onWebViewCreated: (InAppWebViewController controller) {
                _webViewController = controller;
              },
              onLoadStop: (_, url) => _completeLogin(url),
              onReceivedError: (_, request, error) {
                if (request.isForMainFrame == true && mounted) {
                  setState(() => _pageError = '网页加载失败，请检查网络后重新加载');
                }
              },
              onReceivedHttpError: (_, request, response) {
                if (request.isForMainFrame == true && mounted) {
                  setState(() => _pageError =
                      '网页暂时无法访问（HTTP ${response.statusCode}），请稍后重试');
                }
              },
              onProgressChanged: (controller, progress) {
                if (!_progressStream.isClosed) {
                  _progressStream.add(progress / 100);
                }
              },
              onTitleChanged: (controller, title) {
                if (!_titleStream.isClosed) _titleStream.add(title);
              },
              onCloseWindow: (controller) => Get.back(),
              shouldOverrideUrlLoading: (controller, navigationAction) async {
                var url = navigationAction.request.url!.toString();

                if (!url.startsWith('http')) {
                  var snackBar = SnackBar(
                    content: const Text('当前网页将要打开外部链接，是否打开'),
                    showCloseIcon: true,
                    action: SnackBarAction(
                      label: '打开',
                      onPressed: () => Utils.launchURL(url),
                    ),
                  );
                  ScaffoldMessenger.of(context).showSnackBar(snackBar);
                  return NavigationActionPolicy.CANCEL;
                }

                return NavigationActionPolicy.ALLOW;
              },
              onDownloadStartRequest: (controller, request) {
                showDialog(
                    context: context,
                    builder: (context) {
                      String suggestedFilename =
                          request.suggestedFilename.toString();
                      String fileSize = CacheManage.formatSize(
                          request.contentLength.toDouble());
                      try {
                        suggestedFilename =
                            Uri.decodeComponent(suggestedFilename);
                      } catch (e) {
                        debugPrint(e.toString());
                      }
                      return AlertDialog(
                        title: Text(
                          '下载文件 $suggestedFilename？',
                          style: const TextStyle(fontSize: 18),
                        ),
                        content: SelectableText(request.url.toString()),
                        actions: [
                          TextButton(
                              onPressed: () => Get.back(),
                              child: const Text('关闭')),
                          TextButton(
                              onPressed: () async {
                                Get.back();
                                Utils.onDownloadFile(
                                  request.url.toString(),
                                  suggestedFilename,
                                );
                              },
                              child: Text('下载（$fileSize）')),
                        ],
                      );
                    });
                _progressStream.add(1);
              },
            );
          }),
    );
  }
}
