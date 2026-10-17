import 'dart:convert';
import 'dart:io';

import 'package:c001apk_flutter/components/cards/image_text_scroll_card.dart';
import 'package:c001apk_flutter/logic/model/feed/datum.dart';
import 'package:c001apk_flutter/logic/network/request.dart';
import 'package:c001apk_flutter/router/app_pages.dart';
import 'package:c001apk_flutter/utils/storage_util.dart';
import 'package:c001apk_flutter/utils/utils.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:get/get.dart';
import 'package:hive/hive.dart';

class EventAdapter implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  Object? data = {
    'id': 1086,
    'title': '酷安众测：合成设备',
    'description': '',
    'content': '',
    'notice_rule': '产品数量：8\n等级要求：3 级以上',
    'time_reg_end': 1792116000,
    'action_url': 'https://example.com/synthetic-registration',
    'tabList': [],
  };
  bool fail = false;
  @override
  Future<ResponseBody> fetch(RequestOptions options,
      Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    requests.add(options);
    return ResponseBody.fromString(
        jsonEncode(fail
            ? {'code': 403, 'message': 'synthetic-private-server-error'}
            : {'data': data}),
        200,
        headers: {
          Headers.contentTypeHeader: ['application/json']
        });
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late Directory temp;
  late EventAdapter adapter;
  final opened = <String>[];
  const title = '【新品抢鲜】酷安众测：荣耀WIN2 系列';
  setUpAll(() async {
    temp = await Directory.systemTemp.createTemp('c001apk-event-link-');
    Hive.init(temp.path);
    GStorage.settings = await Hive.openBox('settings');
  });
  setUp(() async {
    opened.clear();
    adapter = EventAdapter();
    Request();
    Request.dio.httpClientAdapter = adapter;
    await GStorage.settings.putAll({
      SettingsBoxKey.openInBrowser: true,
      SettingsBoxKey.xAppDevice: 'synthetic-device',
      SettingsBoxKey.userAgent: 'SyntheticTest/1.0',
      SettingsBoxKey.versionName: '16.2.0',
      SettingsBoxKey.versionCode: '2604201',
      SettingsBoxKey.apiVersion: '16',
      SettingsBoxKey.sdkInt: '33',
    });
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
            const MethodChannel('plugins.flutter.io/url_launcher'),
            (call) async {
      if (call.method == 'launch') {
        opened.add((call.arguments as Map)['url'] as String);
      }
      return true;
    });
  });
  tearDown(() {
    Get.reset();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
            const MethodChannel('plugins.flutter.io/url_launcher'), null);
  });
  tearDownAll(() async {
    await Hive.close();
    await temp.delete(recursive: true);
  });
  Future<void> showCard(WidgetTester tester) async {
    await tester.pumpWidget(GetMaterialApp(
      builder: FlutterSmartDialog.init(),
      home: Scaffold(
          body: ImageTextScrollCard(
              data: Datum.fromJson({
        'entityType': 'card',
        'entityTemplate': 'imageTextScrollCard',
        'title': '新品众测与优质图文',
        'entities': [
          {'entityType': 'imageText', 'title': title, 'url': '/event/1086'}
        ],
      }))),
      getPages: AppPages.getPages.where((page) => page.name != '/').toList(),
    ));
    await tester.pumpAndSettle();
  }

  testWidgets(
      'trial card reads event details without the official app landing page',
      (tester) async {
    await showCard(tester);
    await tester.tap(find.text('$title\n'));
    await tester.pumpAndSettle();
    expect(find.text('酷安众测：合成设备'), findsOneWidget);
    expect(find.textContaining('产品数量：8'), findsOneWidget);
    expect(adapter.requests.single.method, 'GET');
    expect(adapter.requests.single.uri.path, '/v6/event/detail');
    expect(adapter.requests.single.queryParameters, {'id': '1086'});
    expect(opened, isEmpty);
    await tester.tap(find.text('查看报名页面'));
    await tester.pumpAndSettle();
    expect(opened, ['https://example.com/synthetic-registration']);
    Get.back();
    await tester.pumpAndSettle();
    expect(find.text('新品众测与优质图文'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });
  testWidgets('event deep links strip share parameters from the requested ID',
      (tester) async {
    await showCard(tester);
    for (final url in [
      '/event/1086?from=share#details',
      'coolmarket://event/1086?from=share#details',
      'https://www.coolapk.com/event/1086?from=share#details',
    ]) {
      Utils.onOpenLink(url);
      await tester.pumpAndSettle();
      expect(find.textContaining('产品数量：8'), findsOneWidget, reason: url);
      expect(adapter.requests.last.queryParameters, {'id': '1086'},
          reason: url);
      Get.back();
      await tester.pumpAndSettle();
    }
    expect(opened, isEmpty);
    await tester.pumpWidget(const SizedBox.shrink());
  });
  testWidgets('event business errors remain errors and can be retried',
      (tester) async {
    adapter.fail = true;
    await showCard(tester);
    await tester.tap(find.text('$title\n'));
    await tester.pumpAndSettle();
    expect(find.text('服务器未能完成请求，请稍后重试'), findsOneWidget);
    expect(find.textContaining('synthetic-private'), findsNothing);
    adapter.fail = false;
    await tester.tap(find.text('重新加载'));
    await tester.pumpAndSettle();
    expect(find.textContaining('产品数量：8'), findsOneWidget);
    expect(adapter.requests, hasLength(2));
    await tester.pumpWidget(const SizedBox.shrink());
  });
  testWidgets('missing event is an empty state with no registration action',
      (tester) async {
    adapter.data = null;
    await showCard(tester);
    await tester.tap(find.text('$title\n'));
    await tester.pumpAndSettle();
    expect(find.text('未找到活动，可能已下架'), findsOneWidget);
    expect(find.text('查看报名页面'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('a deadline today keeps the exact cutoff time', (tester) async {
    final today = DateTime.now();
    (adapter.data as Map)['time_reg_end'] =
        DateTime(today.year, today.month, today.day, 12, 34)
                .millisecondsSinceEpoch ~/
            1000;
    await showCard(tester);
    await tester.tap(find.text('$title\n'));
    await tester.pumpAndSettle();
    expect(find.textContaining('12:34'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
