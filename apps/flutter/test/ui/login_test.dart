import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:c001apk_flutter/logic/network/request.dart';
import 'package:c001apk_flutter/pages/login/login_page.dart';
import 'package:c001apk_flutter/utils/storage_util.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart';
import 'package:hive/hive.dart';

class LoginAdapter implements HttpClientAdapter {
  String message = '图形验证码不能为空';
  int captchaRequests = 0;
  Completer<ResponseBody>? pendingLogin;
  @override
  Future<ResponseBody> fetch(RequestOptions options,
      Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    if (options.method == 'POST' && pendingLogin != null) {
      return pendingLogin!.future;
    }
    if (options.uri.path == '/auth/showCaptchaImage') {
      captchaRequests++;
      return ResponseBody.fromBytes(
          base64Decode(
              'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a3ioAAAAASUVORK5CYII='),
          200,
          headers: {
            Headers.contentTypeHeader: ['image/png']
          });
    }
    return ResponseBody.fromString(
        options.method == 'POST'
            ? jsonEncode({'status': 0, 'message': message})
            : '<html><body data-request-hash="synthetic-hash"></body></html>',
        200,
        headers: {
          Headers.contentTypeHeader: ['text/html']
        });
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  late Directory temp;
  late LoginAdapter adapter;
  setUpAll(() async {
    temp = await Directory.systemTemp.createTemp('c001apk-login-');
    Hive.init(temp.path);
    GStorage.settings = await Hive.openBox('settings');
  });
  setUp(() {
    adapter = LoginAdapter();
    Request();
    Request.dio.httpClientAdapter = adapter;
    Get.testMode = true;
  });
  tearDown(() => Get.reset());
  tearDownAll(() async {
    await Hive.close();
    await temp.delete(recursive: true);
  });
  Future<void> openLogin(WidgetTester tester) async {
    Request();
    await tester.pumpWidget(GetMaterialApp(
        builder: FlutterSmartDialog.init(),
        home: const LoginPage(),
        getPages: [
          GetPage(
              name: '/webview',
              page: () => const Scaffold(
                    body: Text('合成网页登录页'),
                  ))
        ]));
    await tester.pumpAndSettle();
  }

  testWidgets('Windows login exposes the web login action', (tester) async {
    await openLogin(tester);
    expect(find.widgetWithText(TextButton, '网页登录'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  }, skip: !Platform.isWindows);
  testWidgets('pending direct login prevents competing web login',
      (tester) async {
    adapter.pendingLogin = Completer<ResponseBody>();
    await openLogin(tester);
    await tester.enterText(find.byType(TextField).at(0), 'synthetic-user');
    await tester.enterText(find.byType(TextField).at(1), 'synthetic-password');
    await tester.tap(find.widgetWithText(FilledButton, '登录'));
    await tester.pump();
    final action =
        tester.widget<TextButton>(find.widgetWithText(TextButton, '网页登录'));
    expect(action.onPressed, isNull);
    adapter.pendingLogin!
        .complete(ResponseBody.fromString('{"status":0}', 200));
    await tester.pumpAndSettle();
    expect(
        tester
            .widget<TextButton>(find.widgetWithText(TextButton, '网页登录'))
            .onPressed,
        isNotNull);
    await tester.pumpWidget(const SizedBox.shrink());
  }, skip: !Platform.isWindows);
  testWidgets(
      'human verification opens web login and cancellation keeps the form',
      (tester) async {
    adapter.message = '请先完成人机验证';
    await openLogin(tester);
    await tester.enterText(find.byType(TextField).at(0), 'synthetic-user');
    await tester.enterText(find.byType(TextField).at(1), 'synthetic-password');
    await tester.tap(find.widgetWithText(FilledButton, '登录'));
    await tester.pumpAndSettle();
    expect(find.text('合成网页登录页'), findsOneWidget);
    expect(Get.parameters['isLogin'], '1');
    Get.back();
    await tester.pumpAndSettle();
    expect(find.text('synthetic-user'), findsOneWidget);
    expect(find.byType(TextField), findsNWidgets(2));
    await tester.pump(const Duration(seconds: 3));
    await tester.pumpWidget(const SizedBox.shrink());
  }, skip: !Platform.isWindows);
  for (final message in ['图形验证码不能为空', '图形验证码错误']) {
    testWidgets('$message loads an editable image challenge', (tester) async {
      adapter.message = message;
      await openLogin(tester);
      await tester.enterText(find.byType(TextField).at(0), 'synthetic-user');
      await tester.enterText(
          find.byType(TextField).at(1), 'synthetic-password');
      await tester.tap(find.widgetWithText(FilledButton, '登录'));
      await tester.pumpAndSettle();
      expect(adapter.captchaRequests, 1);
      expect(find.byType(TextField), findsNWidgets(3));
      await tester.pump(const Duration(seconds: 3));
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }
}
