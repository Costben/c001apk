import 'dart:io';

import 'package:c001apk_flutter/utils/storage_util.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive/hive.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test('startup upgrades old protocol while preserving device and local data',
      () async {
    final directory =
        await Directory.systemTemp.createTemp('c001apk-migration-');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
            const MethodChannel('plugins.flutter.io/path_provider'),
            (_) async => directory.path);
    Hive.init('${directory.path}/hive');
    final settings = await Hive.openBox('settings');
    await settings.putAll({
      SettingsBoxKey.installTime: '1700000000000',
      SettingsBoxKey.versionName: '13.4.1',
      SettingsBoxKey.versionCode: '2312121',
      SettingsBoxKey.apiVersion: '13',
      SettingsBoxKey.xAppDevice: 'synthetic-preserved-device',
      SettingsBoxKey.androidVersion: '13',
      SettingsBoxKey.model: 'Pixel 7',
      SettingsBoxKey.brand: 'google',
      SettingsBoxKey.buildNumber: 'Build/TQ3A.230805.001',
      SettingsBoxKey.userAgent: 'old +CoolMarket/13.4.1-2312121-universal',
      SettingsBoxKey.selectedTheme: 2,
      SettingsBoxKey.token: 'synthetic-account-value',
      SettingsBoxKey.uid: '123',
      SettingsBoxKey.username: 'synthetic-user',
      SettingsBoxKey.isLogin: true,
    });
    for (final name in [
      'favFeed',
      'historyFeed',
      'searchHistory',
      'blackList'
    ]) {
      final box = await Hive.openBox(name);
      await box.put('preserve-test', 'synthetic-local-data');
    }
    await Hive.close();
    try {
      await GStorage.init();
      expect(GStorage.versionName, '16.2.0');
      expect(GStorage.versionCode, '2604201');
      expect(GStorage.apiVersion, '16');
      expect(
          GStorage.userAgent, contains('+CoolMarket/16.2.0-2604201-universal'));
      expect(GStorage.xAppDevice, 'synthetic-preserved-device');
      expect(GStorage.installTime, '1700000000000');
      expect(GStorage.token, 'synthetic-account-value');
      expect(GStorage.selectedTheme, 2);
      for (final box in [
        GStorage.favFeed,
        GStorage.historyFeed,
        GStorage.searchHistory,
        GStorage.blackList
      ]) {
        expect(box.get('preserve-test'), 'synthetic-local-data');
      }
      await GStorage.settings
          .put(SettingsBoxKey.versionName, 'custom-after-upgrade');
      await GStorage.migrateProtocolParams();
      expect(GStorage.versionName, 'custom-after-upgrade');
    } finally {
      await Hive.close();
      await directory.delete(recursive: true);
    }
  });
}
