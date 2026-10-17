import 'dart:async';
import 'dart:io';
import 'package:c001apk_flutter/utils/global_data.dart';
import 'package:c001apk_flutter/utils/storage_util.dart';
import 'package:c001apk_flutter/utils/web_session.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive/hive.dart';

void main() {
  late Directory temp;
  setUpAll(() async {
    temp = await Directory.systemTemp.createTemp('c001apk-web-session-');
    Hive.init(temp.path);
    GStorage.settings = await Hive.openBox('settings');
  });
  tearDownAll(() async {
    await Hive.close();
    await temp.delete(recursive: true);
  });
  test(
      'session cookies use a valid HTTPS origin and await all writes before navigation',
      () async {
    GlobalData().isLogin = true;
    GlobalData().uid = '123';
    GlobalData().username = 'synthetic-user';
    GlobalData().token = 'synthetic-token';
    final writes = <String, String>{};
    final gate = Completer<void>();
    var ready = false;
    final future = prepareWebSession((origin, domain, name, value) async {
      expect(origin.scheme, 'https');
      expect(origin.host, 'www.coolapk.com');
      expect(domain, '.coolapk.com');
      await gate.future;
      writes[name] = value;
    }, removeCookie: (_) async {})
        .then((_) {
      ready = true;
    });
    await Future<void>.delayed(Duration.zero);
    expect(ready, isFalse);
    gate.complete();
    await future;
    expect(writes['uid'], '123');
    expect(writes['token'], 'synthetic-token');
    expect(ready, isTrue);
  });
  test('guest navigation never seeds account cookies', () async {
    GlobalData().isLogin = false;
    final writes = <String>[];
    await prepareWebSession((_, __, name, value) async {
      writes.add(name);
    }, removeCookie: (_) async {});
    expect(writes, isEmpty);
  });
  test('adding an account clears web auth without seeding the current login',
      () async {
    GlobalData().isLogin = true;
    GlobalData().uid = '123';
    GlobalData().token = 'synthetic-token';
    final writes = <String>[];
    final removed = <String>[];
    await prepareWebSession((_, __, name, value) async => writes.add(name),
        includeAccount: false, removeCookie: (name) async => removed.add(name));
    expect(writes, isEmpty);
    expect(removed, containsAll(['uid', 'token', 'SESSID']));
    expect(GlobalData().uid, '123');
    expect(GlobalData().isLogin, isTrue);
  });
  test(
      'guest web navigation clears retained authentication but preserves other cookies',
      () async {
    GlobalData().isLogin = false;
    final jar = {
      'uid': 'old-user',
      'username': 'old-name',
      'token': 'synthetic-old-token',
      'SESSID': 'synthetic-session',
      'ddid': 'synthetic-ddid',
      'unrelated': 'keep'
    };
    await prepareWebSession((_, __, name, value) async {
      jar[name] = value;
    }, removeCookie: (name) async {
      jar.remove(name);
    });
    expect(jar, {'unrelated': 'keep'});
  });
}
