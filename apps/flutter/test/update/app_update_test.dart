import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:c001apk_flutter/logic/app_update/app_release.dart';
import 'package:c001apk_flutter/logic/app_update/update_preferences.dart';
import 'package:c001apk_flutter/logic/app_update/update_service.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive/hive.dart';

Map<String, dynamic> releaseJson({String tag = '1.2.0-release'}) => {
      'tag_name': tag,
      'html_url': 'https://github.com/TG-Twilight/c001apk/releases/tag/$tag',
      'draft': false,
      'prerelease': false,
      'body': '修复问题\n新增功能',
      'published_at': '2026-10-15T00:00:00Z',
      'assets': [
        for (final name in [
          'android-arm64-v8a.apk',
          'android-armeabi-v7a.apk',
          'android-x86_64.apk',
          'windows-x86_64.zip',
          'linux-x86_64.tar.gz',
        ])
          {
            'name': 'c001apk-1.2.0+20261015-$name',
            'state': 'uploaded',
            'size': 1024,
            'browser_download_url':
                'https://github.com/TG-Twilight/c001apk/releases/download/$tag/c001apk-1.2.0%2B20261015-$name',
          }
      ],
    };

class ReleaseAdapter implements HttpClientAdapter {
  Object body = releaseJson();
  int status = 200;
  final requests = <RequestOptions>[];
  Completer<void>? pending;
  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? stream,
      Future<void>? cancelFuture) async {
    requests.add(options);
    await pending?.future;
    return ResponseBody.fromString(jsonEncode(body), status, headers: {
      Headers.contentTypeHeader: ['application/json']
    });
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  late Directory temp;
  late Box<dynamic> box;
  late UpdatePreferences preferences;
  late ReleaseAdapter adapter;
  late UpdateService service;
  var now = DateTime.utc(2026, 10, 15, 12);
  setUpAll(() async {
    temp = await Directory.systemTemp.createTemp('c001apk-update-');
    Hive.init(temp.path);
    box = await Hive.openBox('updates');
  });
  setUp(() async {
    await box.clear();
    now = DateTime.utc(2026, 10, 15, 12);
    preferences = UpdatePreferences(box);
    adapter = ReleaseAdapter();
    final dio = Dio()..httpClientAdapter = adapter;
    service = UpdateService(
        preferences: preferences,
        dio: dio,
        currentVersion: () async => '1.1.5');
  });
  tearDownAll(() async {
    await Hive.close();
    await temp.delete(recursive: true);
  });

  test('numeric versions handle tags and do not offer downgrades', () {
    expect(
        AppVersion.parse('v1.10.0-release') > AppVersion.parse('1.9.9'), true);
    expect(
        AppVersion.parse('1.2.0+20261015') > AppVersion.parse('1.2.0'), false);
    expect(
        AppVersion.parse('1.1.0-release') > AppVersion.parse('1.2.0'), false);
    expect(() => AppVersion.parse('1.3.0-beta'), throwsFormatException);
  });
  test('release selects an exact platform and compatible Android ABI', () {
    final release = AppRelease.fromJson(releaseJson());
    expect(
        release.downloadFor('android', abis: ['unknown', 'armeabi-v7a']).path,
        endsWith('android-armeabi-v7a.apk'));
    expect(release.downloadFor('windows').path, endsWith('windows-x86_64.zip'));
    expect(release.downloadFor('linux').path, endsWith('linux-x86_64.tar.gz'));
    expect(release.downloadFor('ios'), release.page);
    expect(release.downloadFor('android', abis: ['mips']), release.page);
  });
  test('drafts, prereleases and malformed payloads are rejected', () {
    for (final payload in [
      {...releaseJson(), 'draft': true},
      {...releaseJson(), 'prerelease': true},
      {...releaseJson(), 'tag_name': 'nightly'},
      {...releaseJson(), 'html_url': 'https://example.com/fake'},
      {'message': 'API rate limit exceeded'},
    ]) {
      expect(() => AppRelease.fromJson(payload), throwsFormatException);
    }
  });
  test('untrusted and mismatched asset links fall back to the release page',
      () {
    final payload = releaseJson();
    (payload['assets'] as List).first['browser_download_url'] =
        'https://example.com/installer.apk';
    final release = AppRelease.fromJson(payload);
    expect(release.downloadFor('android', abis: ['arm64-v8a']), release.page);
    final noAssets = AppRelease.fromJson({...payload, 'assets': []});
    expect(noAssets.downloadFor('windows'), noAssets.page);
  });
  test('disabling automatic checks does not alter the installed-app preference',
      () async {
    await box.put('checkUpdate', false);
    await preferences.setEnabled(false);
    expect(
        (await service.check(automatic: true)).status, UpdateStatus.suppressed);
    expect(adapter.requests, isEmpty);
    expect((await service.check()).status, UpdateStatus.available);
    expect(box.get('checkUpdate'), false);
  });
  test('every startup checks again within 24 hours, once per running service',
      () async {
    expect(
        (await service.check(automatic: true)).status, UpdateStatus.available);
    expect(
        (await service.check(automatic: true)).status, UpdateStatus.suppressed);
    final restarted = UpdateService(
        preferences: UpdatePreferences(box),
        dio: Dio()..httpClientAdapter = adapter,
        currentVersion: () async => '1.1.5');
    expect((await restarted.check(automatic: true)).status,
        UpdateStatus.available);
    expect(adapter.requests, hasLength(2));
  });
  test('legacy check and snooze timestamps cannot suppress a new startup',
      () async {
    await box.put('lastAppUpdateCheck', now.millisecondsSinceEpoch);
    await box.put('appUpdateSnoozeUntil',
        now.add(const Duration(hours: 24)).millisecondsSinceEpoch);
    expect(
        (await service.check(automatic: true)).status, UpdateStatus.available);
    expect(adapter.requests, hasLength(1));
  });
  test('manual checks remain available after the startup check', () async {
    expect(
        (await service.check(automatic: true)).status, UpdateStatus.available);
    expect((await service.check()).status, UpdateStatus.available);
    expect(
        (await service.check(automatic: true)).status, UpdateStatus.suppressed);
    expect(adapter.requests, hasLength(2));
  });
  test('skip affects only that version and manual checks bypass it', () async {
    await preferences.skip('1.2.0');
    expect(
        (await service.check(automatic: true)).status, UpdateStatus.suppressed);
    expect((await service.check()).status, UpdateStatus.available);
    adapter.body = releaseJson(tag: '1.3.0-release');
    final restarted = UpdateService(
        preferences: UpdatePreferences(box),
        dio: Dio()..httpClientAdapter = adapter,
        currentVersion: () async => '1.1.5');
    expect((await restarted.check(automatic: true)).status,
        UpdateStatus.available);
  });
  test('same version and older releases never trigger an update', () async {
    adapter.body = releaseJson(tag: '1.1.5-release');
    expect((await service.check()).status, UpdateStatus.upToDate);
    adapter.body = releaseJson(tag: '1.1.0-release');
    expect((await service.check()).status, UpdateStatus.upToDate);
  });
  test('failed startup checks can retry manually or on the next startup',
      () async {
    adapter.status = 403;
    expect((await service.check(automatic: true)).status, UpdateStatus.failed);
    expect(
        (await service.check(automatic: true)).status, UpdateStatus.suppressed);
    expect((await service.check()).message, contains('请求过于频繁'));
    adapter.status = 404;
    expect((await service.check()).message, contains('正式版本'));
    adapter.status = 200;
    adapter.body = '<html>error</html>';
    expect((await service.check()).status, UpdateStatus.failed);
    adapter.body = releaseJson();
    final restarted = UpdateService(
        preferences: UpdatePreferences(box),
        dio: Dio()..httpClientAdapter = adapter,
        currentVersion: () async => '1.1.5');
    expect((await restarted.check(automatic: true)).status,
        UpdateStatus.available);
  });
  test(
      'concurrent checks share the guard and disabling during fetch suppresses UI',
      () async {
    adapter.pending = Completer<void>();
    final first = service.check(automatic: true);
    await Future<void>.delayed(Duration.zero);
    expect((await service.check()).status, UpdateStatus.busy);
    await preferences.setEnabled(false);
    adapter.pending!.complete();
    expect((await first).status, UpdateStatus.suppressed);
  });
  test('release queries use only public GitHub headers and no account data',
      () async {
    await service.check();
    final request = adapter.requests.single;
    expect(request.uri.toString(),
        'https://api.github.com/repos/TG-Twilight/c001apk/releases/latest');
    expect(request.method, 'GET');
    expect(
        request.headers.keys.map((key) => key.toLowerCase()),
        isNot(anyOf(contains('cookie'), contains('authorization'),
            contains('x-app-token'), contains('x-app-device'))));
  });
}
