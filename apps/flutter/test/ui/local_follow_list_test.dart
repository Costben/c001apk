import 'dart:io';
import 'dart:typed_data';

import 'package:c001apk_flutter/logic/local_follow/local_follow_store.dart';
import 'package:c001apk_flutter/logic/network/request.dart';
import 'package:c001apk_flutter/pages/local_follow/local_follow_page.dart';
import 'package:c001apk_flutter/router/app_pages.dart';
import 'package:c001apk_flutter/utils/storage_util.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:get/get.dart';
import 'package:hive/hive.dart';

class EmptyUserAdapter implements HttpClientAdapter {
  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? stream,
          Future<void>? cancelled) async =>
      ResponseBody.fromString('{"data":null}', 200, headers: {
        Headers.contentTypeHeader: ['application/json']
      });
  @override
  void close({bool force = false}) {}
}

void main() {
  late Directory directory;
  setUpAll(() async {
    directory = await Directory.systemTemp.createTemp('local-follow-list-');
    Hive.init(directory.path);
    GStorage.settings = await Hive.openBox('settings');
    Request();
    Request.dio.httpClientAdapter = EmptyUserAdapter();
    await LocalFollowStore().follow('42', '合成用户', '');
    await GStorage.settings.putAll({
      SettingsBoxKey.versionCode: '2604201',
      SettingsBoxKey.xAppDevice: 'synthetic-test-device',
    });
  });
  tearDownAll(() async {
    await Hive.close();
    await directory.delete(recursive: true);
  });

  testWidgets('managed user opens the existing application profile route',
      (tester) async {
    await tester.pumpWidget(GetMaterialApp(
      initialRoute: '/',
      getPages: [
        GetPage(name: '/', page: () => const LocalFollowListPage()),
        ...AppPages.getPages.where((page) => page.name == '/u/:uid'),
      ],
    ));
    await tester.pumpAndSettle();
    await tester.tap(find.text('合成用户'));
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();
    expect(Get.currentRoute, '/u/42');
    expect(Get.parameters['uid'], '42');
    await tester.pumpWidget(const SizedBox.shrink());
    Get.reset();
  });
}
