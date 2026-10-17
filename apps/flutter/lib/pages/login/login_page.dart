import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart' hide Response;
import 'package:html/dom.dart' as dom;
import 'package:html/parser.dart';

import '../../constants/constants.dart';
import '../../logic/accounts/account_store.dart';
import '../../logic/model/login/login_response.dart';
import '../../logic/network/network_repo.dart';
import '../../logic/network/api_failure.dart';
import '../../utils/extensions.dart';
import '../../utils/global_data.dart';
import '../../utils/utils.dart';

class LoginPage extends StatefulWidget {
  const LoginPage({super.key});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  bool _showPassword = false;
  bool _submitting = false;
  bool _webLoginOpen = false;
  final _showPasswordStream = StreamController<bool>();
  final _showClearAccount = StreamController<bool>();
  final _showClearCaptcha = StreamController<bool>();
  final _enableLogin = StreamController<bool>();
  final TextEditingController _accountController = TextEditingController();
  final TextEditingController _pwdController = TextEditingController();
  final TextEditingController _captchaController = TextEditingController();
  final FocusNode _pwdFocusNode = FocusNode();
  final FocusNode _captchaFocusNode = FocusNode();
  String? _requestHash;
  bool _showCaptcha = false;
  final _captchaImg = StreamController<Uint8List?>();

  String urlPreGetParam = '/auth/login?type=mobile';
  String urlGetParam = '/auth/loginByCoolApk';

  @override
  void initState() {
    super.initState();
    GlobalData().loginSession = '';
    _onGetLoginParam(urlPreGetParam);
  }

  String? _getParam(List<String>? cookies, String param) {
    return cookies
        ?.where((cookie) => cookie.contains('$param='))
        .toList()
        .lastOrNull
        ?.split(';')
        .firstOrNull
        ?.replaceFirst('$param=', '')
        .trim();
  }

  Future<void> _onGetCaptcha() async {
    try {
      Response response = await NetworkRepo.getLoginParam(
          '/auth/showCaptchaImage?${DateTime.now().microsecondsSinceEpoch ~/ 1000}',
          Options(responseType: ResponseType.bytes));
      if (!mounted) return;
      setState(() => _showCaptcha = true);
      _captchaController.clear();
      _captchaImg.add(response.data);
    } catch (e) {
      SmartDialog.showToast('无法获取验证码：${ApiFailure.fromError(e).message}');
    }
  }

  void _beforeLogin() {
    if (_submitting || _webLoginOpen || _requestHash.isNullOrEmpty) return;
    if (_accountController.text.isEmpty || _pwdController.text.isEmpty) {
      SmartDialog.showToast('账号或密码为空');
    } else {
      _onLogin();
    }
  }

  Future<void> _onLogin() async {
    if (_submitting) return;
    setState(() => _submitting = true);
    try {
      Response response = await NetworkRepo.onLogin(
          _requestHash!,
          _accountController.text,
          _pwdController.text,
          _captchaController.text);
      if (!mounted) return;
      LoginResponse loginResponse =
          LoginResponse.fromJson(jsonDecode(response.data));
      if (loginResponse.status == 1) {
        List<String>? cookies = response.headers['Set-Cookie'];
        String? uid = _getParam(cookies, 'uid');
        String? username = _getParam(cookies, 'username');
        String? token = _getParam(cookies, 'token');
        if (!uid.isNullOrEmpty &&
            !username.isNullOrEmpty &&
            !token.isNullOrEmpty) {
          if (!mounted) return;
          await AccountStore()
              .saveLogin(uid: uid!, username: username!, token: token!);
          SmartDialog.showToast('登录成功');
          if (mounted) Get.back(result: true);
        }
      } else {
        if (!loginResponse.message.isNullOrEmpty) {
          SmartDialog.showToast(loginResponse.message!);
        }
        if (['图形验证码不能为空', '图形验证码错误'].contains(loginResponse.message) ||
            (_showCaptcha && loginResponse.message == '密码错误')) {
          await _onGetCaptcha();
        } else if (Utils.isSupportWebview() &&
            (loginResponse.message?.contains('人机') == true ||
                loginResponse.messageStatus == 'err_request_captcha_v2')) {
          await _openWebLogin();
        }
      }
    } catch (e) {
      SmartDialog.showToast('登录失败：${ApiFailure.fromError(e).message}');
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Future<void> _openWebLogin() async {
    if (_webLoginOpen) return;
    _webLoginOpen = true;
    try {
      final result = await Get.toNamed('/webview', parameters: {
        'url': Constants.URL_LOGIN,
        'isLogin': '1',
      });
      if (!mounted) return;
      if (result == true) {
        SmartDialog.showToast('登录成功');
        Get.back(result: true);
      }
    } finally {
      _webLoginOpen = false;
    }
  }

  Future<void> _onGetLoginParam(String url) async {
    try {
      Response response = await NetworkRepo.getLoginParam(url);
      if (!mounted) return;
      if (url == urlGetParam) {
        try {
          dom.Document document = parse(response.data);
          _requestHash = document
              .getElementsByTagName('Body')[0]
              .attributes['data-request-hash'];
          _enableLogin.add(!_requestHash.isNullOrEmpty);
        } catch (e) {
          SmartDialog.showToast('登录页面缺少验证参数，请稍后重试');
        }
      }
      try {
        String? sessionCookie = response.headers['Set-Cookie']
            ?.where((c) => c.startsWith('SESSID='))
            .firstOrNull;
        if (sessionCookie != null) {
          GlobalData().loginSession = sessionCookie.split(';').first;
        }
      } catch (e) {
        SmartDialog.showToast('无法获取登录会话，请稍后重试');
      }
      if (url == urlPreGetParam) {
        _onGetLoginParam(urlGetParam);
      }
    } catch (e) {
      SmartDialog.showToast('无法获取登录参数：${ApiFailure.fromError(e).message}');
    }
  }

  @override
  void dispose() {
    GlobalData().loginSession = '';
    _enableLogin.close();
    _showPasswordStream.close();
    _showClearAccount.close();
    _showClearCaptcha.close();
    _captchaImg.close();
    _accountController.dispose();
    _pwdController.dispose();
    _captchaController.dispose();
    _pwdFocusNode.dispose();
    _captchaFocusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('登录'),
        bottom: const PreferredSize(
          preferredSize: Size.zero,
          child: Divider(height: 1),
        ),
        actions: [
          if (Utils.isSupportWebview())
            TextButton(
              onPressed: _submitting ? null : _openWebLogin,
              child: const Text('网页登录'),
            ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (Utils.isSupportWebview())
              const Padding(
                padding: EdgeInsets.only(bottom: 16),
                child: Text('遇到人机验证或无法直接登录时，请使用右上角的“网页登录”，在酷安网页中完成验证与登录。'),
              ),
            TextField(
              controller: _accountController,
              autofocus: true,
              onChanged: (value) => _showClearAccount.add(value.isNotEmpty),
              textInputAction: TextInputAction.next,
              onSubmitted: (value) => _pwdFocusNode.requestFocus(),
              decoration: InputDecoration(
                border: const UnderlineInputBorder(),
                filled: true,
                labelText: '账号',
                suffixIcon: StreamBuilder(
                  stream: _showClearAccount.stream,
                  builder: (_, snapshot) => snapshot.data == true
                      ? IconButton(
                          icon: const Icon(Icons.cancel),
                          onPressed: () {
                            _accountController.clear();
                            _showClearAccount.add(false);
                          },
                        )
                      : const SizedBox.shrink(),
                ),
              ),
            ),
            const SizedBox(height: 10),
            StreamBuilder(
              initialData: false,
              stream: _showPasswordStream.stream,
              builder: (_, snapshot) => TextField(
                focusNode: _pwdFocusNode,
                controller: _pwdController,
                obscureText: !snapshot.data!,
                textInputAction:
                    _showCaptcha ? TextInputAction.next : TextInputAction.done,
                onSubmitted: (value) {
                  if (_showCaptcha) {
                    _captchaFocusNode.requestFocus();
                  } else {
                    _beforeLogin();
                  }
                },
                decoration: InputDecoration(
                  border: const UnderlineInputBorder(),
                  filled: true,
                  labelText: '密码',
                  suffixIcon: IconButton(
                    icon: Icon(
                      snapshot.data == true
                          ? Icons.visibility_off
                          : Icons.visibility,
                    ),
                    onPressed: () {
                      _showPassword = !_showPassword;
                      _showPasswordStream.add(_showPassword);
                    },
                  ),
                ),
              ),
            ),
            StreamBuilder(
              stream: _captchaImg.stream,
              builder: (_, snapshot) => snapshot.data != null
                  ? Padding(
                      padding: const EdgeInsets.only(top: 10),
                      child: Row(
                        children: [
                          Expanded(
                            flex: 1,
                            child: GestureDetector(
                              onTap: () => _onGetCaptcha(),
                              child: Image.memory(snapshot.data!),
                            ),
                          ),
                          Expanded(
                            flex: 1,
                            child: TextField(
                              controller: _captchaController,
                              focusNode: _captchaFocusNode,
                              inputFormatters: [
                                LengthLimitingTextInputFormatter(4),
                                FilteringTextInputFormatter.allow(
                                    RegExp("[0-9a-zA-Z]")),
                              ],
                              textInputAction: TextInputAction.done,
                              onSubmitted: (value) => _beforeLogin(),
                              onChanged: (value) =>
                                  _showClearCaptcha.add(value.isNotEmpty),
                              decoration: InputDecoration(
                                border: const UnderlineInputBorder(),
                                filled: true,
                                labelText: '验证码',
                                suffixIcon: StreamBuilder(
                                  stream: _showClearCaptcha.stream,
                                  builder: (_, snapshot) =>
                                      snapshot.data == true
                                          ? IconButton(
                                              icon: const Icon(Icons.cancel),
                                              onPressed: () {
                                                _showClearCaptcha.add(false);
                                                _captchaController.clear();
                                              },
                                            )
                                          : const SizedBox.shrink(),
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    )
                  : const SizedBox.shrink(),
            ),
            const SizedBox(height: 10),
            StreamBuilder(
              stream: _enableLogin.stream,
              builder: (_, snapshot) => FilledButton.tonal(
                onPressed: snapshot.data == true
                    ? () {
                        _beforeLogin();
                      }
                    : null,
                child: const Text('登录'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
