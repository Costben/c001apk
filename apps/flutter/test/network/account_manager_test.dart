import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:c001apk_flutter/logic/accounts/account_store.dart';
import 'package:c001apk_flutter/logic/network/request.dart';
import 'package:c001apk_flutter/utils/global_data.dart';
import 'package:c001apk_flutter/utils/storage_util.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive/hive.dart';

class DelayedAccountAdapter implements HttpClientAdapter {
  final started = Completer<void>();
  final finish = Completer<void>();
  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? stream,
      Future<void>? cancelFuture) async {
    started.complete();
    await finish.future;
    return ResponseBody.fromString('{"data":{"uid":"1"}}', 200);
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  late Directory directory;
  final store = AccountStore();
  setUpAll(() async {
    directory = await Directory.systemTemp.createTemp('account-manager-');
    Hive.init(directory.path);
    GStorage.settings = await Hive.openBox('settings');
    Request();
    Request.dio.interceptors.clear();
  });
  setUp(() async {
    await GStorage.settings.clear();
    GStorage.setUid('1');
    GStorage.setUsername('old-user');
    GStorage.setToken('synthetic-one');
    GStorage.setIsLogin(true);
    GStorage.setUserAvatar('https://example.invalid/one');
    GStorage.setLevel(8);
    await GStorage.settings.put('localFollowUsers', [
      {'uid': '42'}
    ]);
  });
  tearDownAll(() async {
    await Hive.close();
    await directory.delete(recursive: true);
  });
  test('migration preserves existing login and is idempotent', () async {
    await store.initialize();
    await store.initialize();
    expect(store.accounts.single.uid, '1');
    expect(store.accounts.single.avatar, 'https://example.invalid/one');
    expect(GStorage.token, 'synthetic-one');
    expect(GlobalData().isLogin, isTrue);
  });
  test('switch, guest and restart keep independent account profiles', () async {
    await store.initialize();
    await store.saveLogin(uid: '2', username: 'second', token: 'synthetic-two');
    expect(store.accounts.length, 2);
    expect(GStorage.userAvatar, '');
    expect(GStorage.level, 0);
    await store.switchTo('1');
    expect(GStorage.token, 'synthetic-one');
    expect(GStorage.level, 8);
    GlobalData().SESSID = 'synthetic-session';
    await store.useGuest();
    expect(GStorage.token, '');
    expect(GStorage.uid, '');
    expect(GlobalData().SESSID, '');
    await store.initialize();
    expect(GlobalData().isLogin, isFalse);
    expect(store.accounts.length, 2);
    await store.switchTo('2');
    expect(GlobalData().uid, '2');
    expect(GlobalData().token, 'synthetic-two');
    expect(GStorage.settings.get('localFollowUsers'), [
      {'uid': '42'}
    ]);
  });
  test(
      'removing inactive account leaves active login; active removal uses guest',
      () async {
    await store.initialize();
    await store.saveLogin(uid: '2', username: 'second', token: 'synthetic-two');
    await store.remove('1');
    expect(GStorage.uid, '2');
    await store.remove('2');
    expect(store.accounts, isEmpty);
    expect(GStorage.isLogin, isFalse);
    expect(GStorage.userAvatar, '');
  });
  test('expired session retains account identity without reusable credentials',
      () async {
    await store.initialize();
    await store.expireCurrent();
    expect(store.accounts.single.needsLogin, isTrue);
    expect(GStorage.isLogin, isFalse);
    await expectLater(store.switchTo('1'), throwsStateError);
    await store.saveLogin(
        uid: '1', username: 'renewed', token: 'synthetic-new');
    expect(store.accounts.length, 1);
    expect(store.accounts.single.needsLogin, isFalse);
    await store.useGuest();
    await store.switchTo('1');
    expect(GStorage.token, 'synthetic-new');
  });
  test('response from previous account is discarded even after A to B to A',
      () async {
    await store.initialize();
    final adapter = DelayedAccountAdapter();
    Request.dio.httpClientAdapter = adapter;
    final response = Request().get('https://api.coolapk.com/v6/test');
    final expectation = expectLater(
        response,
        throwsA(isA<DioException>()
            .having((e) => e.type, 'type', DioExceptionType.cancel)));
    await adapter.started.future;
    await store.saveLogin(uid: '2', username: 'second', token: 'synthetic-two');
    await store.switchTo('1');
    adapter.finish.complete();
    await expectation;
  });
}
