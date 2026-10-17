import 'dart:io';
import 'dart:async';

import 'package:c001apk_flutter/components/update_dialog.dart';
import 'package:c001apk_flutter/components/update_check_tile.dart';
import 'package:c001apk_flutter/logic/app_update/app_release.dart';
import 'package:c001apk_flutter/logic/app_update/update_preferences.dart';
import 'package:c001apk_flutter/logic/app_update/update_service.dart';
import 'package:c001apk_flutter/pages/settings/settings_page.dart';
import 'package:c001apk_flutter/utils/storage_util.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:get/get.dart';
import 'package:hive/hive.dart';
import 'package:dio/dio.dart';

import 'app_update_test.dart' show releaseJson, ReleaseAdapter;

// Dio streams and Hive writes use real async work, while animations use the
// widget test clock. Advance both until the observable state is ready.
Future<void> waitForUpdateUi(WidgetTester tester, Finder finder) async {
  for (var attempt = 0; attempt < 200; attempt++) {
    await tester.pump(const Duration(milliseconds: 20));
    await tester
        .runAsync(() => Future<void>.delayed(const Duration(milliseconds: 10)));
    if (finder.evaluate().isNotEmpty) {
      await tester.pump(const Duration(milliseconds: 500));
      return;
    }
  }
  expect(finder, findsWidgets,
      reason: 'Update UI did not reach the expected state');
}

void main() {
  late Directory directory;
  setUpAll(() async {
    directory = await Directory.systemTemp.createTemp('update-ui-');
    Hive.init(directory.path);
    GStorage.settings = await Hive.openBox('settings');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
            const MethodChannel('plugins.flutter.io/path_provider'),
            (_) async => directory.path);
  });

  for (final label in ['稍后', '跳过']) {
    testWidgets('manual check applies $label and can still check again',
        (tester) async {
      final preferences = UpdatePreferences(GStorage.settings);
      final adapter = ReleaseAdapter()..pending = Completer<void>();
      final service = UpdateService(
          preferences: preferences,
          dio: Dio()..httpClientAdapter = adapter,
          currentVersion: () async => '1.1.5');
      await tester.pumpWidget(
          MaterialApp(home: Scaffold(body: UpdateCheckTile(service: service))));
      await tester.runAsync(() async {
        Timer(const Duration(milliseconds: 250),
            () => adapter.pending!.complete());
        await tester.tap(find.text('检查更新'));
      });
      // The tile's loading indicator remains mounted behind the modal.
      await waitForUpdateUi(tester, find.text('发现新版本'));
      expect(find.text('发现新版本'), findsOneWidget);
      await tester.runAsync(() async {
        await tester.tap(find.text(label));
        await GStorage.settings.flush();
      });
      await waitForUpdateUi(tester, find.text('检查更新'));
      await tester.pumpAndSettle();
      expect(GStorage.settings.get('appUpdateSnoozeUntil'), isNull);
      expect(preferences.skipped('1.2.0'), label == '跳过');
      expect(preferences.enabled, true);
      await tester.runAsync(() async {
        await tester.tap(find.text('检查更新'));
      });
      await waitForUpdateUi(tester, find.text('发现新版本'));
      expect(find.text('发现新版本'), findsOneWidget);
      await tester.runAsync(() async {
        await tester.tap(find.text('稍后'));
        await GStorage.settings.flush();
      });
      await waitForUpdateUi(tester, find.text('检查更新'));
      await tester.pumpAndSettle();
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }

  testWidgets('manual failures show feedback and allow retry', (tester) async {
    final adapter = ReleaseAdapter()..status = 403;
    final service = UpdateService(
        preferences: UpdatePreferences(GStorage.settings),
        dio: Dio()..httpClientAdapter = adapter,
        currentVersion: () async => '1.2.0');
    await tester.pumpWidget(
        MaterialApp(home: Scaffold(body: UpdateCheckTile(service: service))));
    await tester.runAsync(() async {
      await tester.tap(find.text('检查更新'));
    });
    await waitForUpdateUi(tester, find.textContaining('请求过于频繁'));
    await tester.pumpAndSettle();
    expect(find.textContaining('请求过于频繁'), findsOneWidget);
    adapter.status = 200;
    await tester.runAsync(() async {
      await tester.tap(find.text('检查更新'));
    });
    await waitForUpdateUi(tester, find.text('检查更新'));
    await tester.pumpAndSettle();
    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();
    expect(find.text('已是最新版本'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });
  setUp(() async => GStorage.settings.clear());
  tearDown(() => Get.reset());
  tearDownAll(() async {
    await Hive.close();
    await directory.delete(recursive: true);
  });

  testWidgets('manual intent survives disabling a pending startup check',
      (tester) async {
    final adapter = ReleaseAdapter()..pending = Completer<void>();
    final preferences = UpdatePreferences(GStorage.settings);
    final service = UpdateService(
        preferences: preferences,
        dio: Dio()..httpClientAdapter = adapter,
        currentVersion: () async => '1.1.5');
    late BuildContext context;
    await tester.pumpWidget(GetMaterialApp(home: Builder(builder: (value) {
      context = value;
      return const Scaffold(body: Text('首页'));
    })));
    late Future<void> automatic;
    late Future<void> manual;
    await tester.runAsync(() async {
      automatic =
          AppUpdates.check(context, automatic: true, updateService: service);
      await Future<void>.delayed(const Duration(milliseconds: 30));
      await preferences.setEnabled(false);
      manual = AppUpdates.check(context, updateService: service);
      adapter.pending!.complete();
      await Future<void>.delayed(const Duration(milliseconds: 30));
    });
    await waitForUpdateUi(tester, find.text('发现新版本'));
    expect(find.text('发现新版本'), findsOneWidget);
    expect(adapter.requests, hasLength(2));
    await tester.tap(find.text('稍后'));
    await tester.pumpAndSettle();
    await tester.runAsync(() async => Future.wait([automatic, manual]));
    await tester.pumpWidget(const SizedBox.shrink());
  });

  for (final throwsError in [false, true]) {
    testWidgets('download failure ($throwsError) opens the release page',
        (tester) async {
      final opened = <String>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
              const MethodChannel('plugins.flutter.io/url_launcher'),
              (call) async {
        if (call.method != 'launch') return true;
        opened.add((call.arguments as Map)['url'] as String);
        if (opened.length == 1 && throwsError) {
          throw PlatformException(code: 'synthetic-launch-failure');
        }
        return opened.length > 1;
      });
      final service = UpdateService(
          preferences: UpdatePreferences(GStorage.settings),
          dio: Dio()..httpClientAdapter = ReleaseAdapter(),
          currentVersion: () async => '1.1.5');
      await tester.pumpWidget(
          MaterialApp(home: Scaffold(body: UpdateCheckTile(service: service))));
      await tester.runAsync(() async {
        await tester.tap(find.text('检查更新'));
      });
      await waitForUpdateUi(tester, find.text('发现新版本'));
      await tester.tap(find.text('更新'));
      await tester.pump(const Duration(milliseconds: 500));
      await waitForUpdateUi(tester, find.text('检查更新'));
      await tester.pumpAndSettle();
      expect(opened, hasLength(2));
      expect(opened.last,
          'https://github.com/TG-Twilight/c001apk/releases/tag/1.2.0-release');
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }

  testWidgets('queued manual check follows the currently mounted page',
      (tester) async {
    final adapter = ReleaseAdapter()..pending = Completer<void>();
    final preferences = UpdatePreferences(GStorage.settings);
    final service = UpdateService(
        preferences: preferences,
        dio: Dio()..httpClientAdapter = adapter,
        currentVersion: () async => '1.1.5');
    late BuildContext firstContext;
    await tester.pumpWidget(MaterialApp(
        key: UniqueKey(),
        home: Builder(builder: (value) {
          firstContext = value;
          return const Scaffold(body: Text('存储与更新'));
        })));
    late Future<void> automatic, firstManual, secondManual;
    await tester.runAsync(() async {
      automatic = AppUpdates.check(firstContext,
          automatic: true, updateService: service);
      await Future<void>.delayed(const Duration(milliseconds: 30));
      firstManual = AppUpdates.check(firstContext, updateService: service);
    });
    late BuildContext secondContext;
    await tester.pumpWidget(MaterialApp(
        key: UniqueKey(),
        home: Builder(builder: (value) {
          secondContext = value;
          return const Scaffold(body: Text('关于'));
        })));
    expect(firstContext.mounted, false);
    await tester.runAsync(() async {
      secondManual = AppUpdates.check(secondContext, updateService: service);
      adapter.pending!.complete();
      await Future<void>.delayed(const Duration(milliseconds: 30));
    });
    await waitForUpdateUi(tester, find.text('发现新版本'));
    expect(find.text('发现新版本'), findsOneWidget);
    await tester.tap(find.text('稍后'));
    await tester.pumpAndSettle();
    await tester.runAsync(() async {
      await Future.wait([automatic, firstManual, secondManual]);
    });
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('automatic update switch works on desktop independently',
      (tester) async {
    await tester.pumpWidget(const GetMaterialApp(
        home: SettingsDetailPage(section: SettingsSection.storage)));
    await tester.pumpAndSettle();
    expect(find.text('自动检查 c001apk 更新'), findsOneWidget);
    await tester.runAsync(() async {
      await tester.tap(find.text('自动检查 c001apk 更新'));
      await GStorage.settings.flush();
    });
    await tester.pumpAndSettle();
    expect(UpdatePreferences(GStorage.settings).enabled, false);
    expect(GStorage.checkUpdate, true);
    expect(find.text('检查更新'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  for (final action in UpdateAction.values) {
    testWidgets('update dialog returns ${action.name} on a narrow enlarged UI',
        (tester) async {
      tester.view.physicalSize = const Size(360, 740);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      UpdateAction? selected;
      await tester.pumpWidget(MaterialApp(
          home: MediaQuery(
        data: const MediaQueryData(
            size: Size(360, 740), textScaler: TextScaler.linear(1.3)),
        child: Builder(builder: (context) {
          return Scaffold(
              body: TextButton(
                  onPressed: () async {
                    selected = await showDialog<UpdateAction>(
                        context: context,
                        builder: (_) => AppUpdateDialog(
                            release: AppRelease.fromJson({
                              ...releaseJson(),
                              'body': List.filled(80, '新增与修复说明').join('\n'),
                            }),
                            currentVersion: '1.1.5',
                            onOpenRelease: () {}));
                  },
                  child: const Text('打开')));
        }),
      )));
      await tester.tap(find.text('打开'));
      await tester.pumpAndSettle();
      const labels = {
        UpdateAction.later: '稍后',
        UpdateAction.skip: '跳过',
        UpdateAction.download: '更新',
      };
      expect(find.text('发现新版本'), findsOneWidget);
      await tester.tap(find.text(labels[action]!));
      await tester.pumpAndSettle();
      expect(selected, action);
      expect(tester.takeException(), isNull);
    });
  }
}
