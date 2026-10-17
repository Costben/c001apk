import 'dart:io';
import 'dart:typed_data';

import 'package:c001apk_flutter/components/cards/user_info_card.dart';
import 'package:c001apk_flutter/logic/model/feed/datum.dart';
import 'package:c001apk_flutter/utils/global_data.dart';
import 'package:c001apk_flutter/utils/storage_util.dart';
import 'package:c001apk_flutter/logic/local_follow/local_follow_store.dart';
import 'package:c001apk_flutter/logic/network/request.dart';
import 'package:c001apk_flutter/logic/state/loading_state.dart';
import 'package:c001apk_flutter/pages/user/user_controller.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive/hive.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';

class FollowAdapter implements HttpClientAdapter {
  final calls = <RequestOptions>[];
  @override
  Future<ResponseBody> fetch(RequestOptions options,
      Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    calls.add(options);
    return ResponseBody.fromString('{"data":1}', 200, headers: {
      Headers.contentTypeHeader: ['application/json']
    });
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  late Directory directory;
  setUpAll(() async {
    directory = await Directory.systemTemp.createTemp('local-follow-ui-');
    Hive.init(directory.path);
    GStorage.settings = await Hive.openBox('settings');
    Request();
  });
  tearDownAll(() async {
    await Hive.close();
    await directory.delete(recursive: true);
  });
  testWidgets('guest can activate the follow button', (tester) async {
    GlobalData().isLogin = false;
    var calls = 0;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
          body: UserInfoCard(
        data: Datum(uid: 42, username: '合成用户', logintime: 0),
        onFollow: (_, __) => calls++,
      )),
    ));
    await tester.tap(find.byType(FilledButton));
    expect(calls, 1);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
      'guest saves locally with toast, signed-in long press never writes remotely',
      (tester) async {
    final adapter = FollowAdapter();
    final messages = <String>[];
    Request.dio.httpClientAdapter = adapter;
    final data = Datum(uid: 42, username: '合成用户', logintime: 0, isFollow: 0);
    final controller = UserController(uid: '42');
    controller.userState.value = LoadingState.success(data);
    GlobalData().isLogin = false;
    Future<void> mount() => tester.pumpWidget(MaterialApp(
          builder: FlutterSmartDialog.init(toastBuilder: (message) {
            messages.add(message);
            return Text(message);
          }),
          home: Scaffold(
              body: UserInfoCard(
                  data: data,
                  onFollow: controller.onFollow,
                  onLocalFollow: controller.onLocalFollow)),
        ));
    await mount();
    await tester.tap(find.byType(FilledButton));
    await tester.runAsync(
        () async => Future<void>.delayed(const Duration(milliseconds: 50)));
    await tester.pump();
    expect(LocalFollowStore().contains('42'), isTrue);
    expect(find.text('取消本地关注'), findsOneWidget);
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pump();
    expect(find.textContaining('已本地关注，仅保存在本机'), findsOneWidget);
    expect(adapter.calls, isEmpty);
    await tester
        .runAsync(() => SmartDialog.dismiss(status: SmartStatus.allToast));
    await tester.pump(const Duration(seconds: 1));

    GlobalData().isLogin = true;
    GlobalData().uid = '99';
    await tester.runAsync(() => GStorage.settings.putAll({
          LocalFollowStore.enabledKey: true,
          SettingsBoxKey.xAppDevice: 'synthetic-test-device',
          SettingsBoxKey.versionCode: '2604201',
        }));
    await mount();
    await tester.pump();
    await tester.longPress(find.byType(FilledButton));
    await tester.runAsync(
        () async => Future<void>.delayed(const Duration(milliseconds: 50)));
    await tester.pump();
    expect(LocalFollowStore().contains('42'), isFalse);
    expect(adapter.calls, isEmpty);
    expect(data.isFollow, 0);
    await tester
        .runAsync(() => SmartDialog.dismiss(status: SmartStatus.allToast));
    await tester.pump(const Duration(seconds: 1));
    await tester.tap(find.byType(FilledButton));
    await tester.runAsync(
        () async => Future<void>.delayed(const Duration(milliseconds: 100)));
    await tester.pump(const Duration(milliseconds: 300));
    await tester.runAsync(
        () async => Future<void>.delayed(const Duration(milliseconds: 50)));
    await tester.pump();
    expect(adapter.calls, isNotEmpty, reason: messages.join('\n'));
    expect(adapter.calls.single.uri.path, '/v6/user/follow');
    expect(LocalFollowStore().users, isEmpty);
    await tester
        .runAsync(() => SmartDialog.dismiss(status: SmartStatus.allToast));
    await tester.pump(const Duration(seconds: 1));

    await tester.runAsync(
        () => GStorage.settings.put(LocalFollowStore.enabledKey, false));
    await tester.pump();
    await tester.longPress(find.byType(FilledButton));
    await tester.pump();
    expect(adapter.calls.length, 1);
    expect(LocalFollowStore().users, isEmpty);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
