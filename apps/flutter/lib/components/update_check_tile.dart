import 'dart:async';
import 'dart:io';

import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:url_launcher/url_launcher.dart';

import '../logic/app_update/update_preferences.dart';
import '../logic/app_update/update_service.dart';
import '../utils/storage_util.dart';
import 'update_dialog.dart';

class AppUpdates {
  static final service =
      UpdateService(preferences: UpdatePreferences(GStorage.settings));
  static Future<void>? _active;
  static Future<void>? _manualQueued;
  static (BuildContext, UpdateService?)? _manualRequest;

  static Future<void> _open(BuildContext context, Uri uri,
      {Uri? fallback}) async {
    for (final target in {uri, if (fallback != null) fallback}) {
      try {
        if (await launchUrl(target, mode: LaunchMode.externalApplication)) {
          return;
        }
      } catch (_) {
        // Try the release page before reporting a browser failure.
      }
    }
    if (context.mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('无法打开下载链接，请检查系统浏览器设置')));
    }
  }

  static Future<void> check(BuildContext context,
      {bool automatic = false, UpdateService? updateService}) async {
    final pending = _active;
    if (pending != null) {
      if (automatic) return;
      // Preserve one manual request while startup checking or another dialog
      // is active. Multiple clicks join that request rather than stack modals.
      _manualRequest = (context, updateService);
      final queued = _manualQueued ??= _checkAfter(pending);
      try {
        await queued;
      } finally {
        if (identical(_manualQueued, queued)) _manualQueued = null;
      }
      return;
    }
    final completed = Completer<void>();
    _active = completed.future;
    try {
      final updater = updateService ?? service;
      final result = await updater.check(automatic: automatic);
      if (!context.mounted) return;
      // An automatic prompt must not interrupt login, verification or a dialog.
      if (automatic &&
          (Get.currentRoute != '/' ||
              Get.isDialogOpen == true ||
              Get.isBottomSheetOpen == true ||
              !updater.preferences.enabled)) return;
      if (result.status == UpdateStatus.available) {
        final release = result.release!;
        final action = await showDialog<UpdateAction>(
            context: context,
            builder: (_) => AppUpdateDialog(
                release: release,
                currentVersion: result.currentVersion,
                onOpenRelease: () => _open(context, release.page)));
        switch (action) {
          case UpdateAction.skip:
            await updater.preferences.skip(release.version.toString());
          case UpdateAction.download:
            List<String> abis = const [];
            if (Platform.isAndroid) {
              try {
                abis = (await DeviceInfoPlugin().androidInfo).supportedAbis;
              } catch (_) {
                // Fall back to the release page if device information fails.
              }
            }
            if (context.mounted) {
              await _open(context,
                  release.downloadFor(Platform.operatingSystem, abis: abis),
                  fallback: release.page);
            }
          case UpdateAction.later:
          case null:
            break;
        }
      } else if (!automatic &&
          (result.status == UpdateStatus.failed ||
              result.status == UpdateStatus.upToDate)) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text(result.status == UpdateStatus.upToDate
                ? '已是最新版本'
                : result.message)));
      }
    } finally {
      _active = null;
      completed.complete();
    }
  }

  static Future<void> _checkAfter(Future<void> pending) async {
    await pending;
    final request = _manualRequest;
    _manualRequest = null;
    _manualQueued = null;
    if (request != null && request.$1.mounted) {
      await check(request.$1, updateService: request.$2);
    }
  }
}

class UpdateCheckTile extends StatefulWidget {
  const UpdateCheckTile({super.key, this.service});
  final UpdateService? service;
  @override
  State<UpdateCheckTile> createState() => _UpdateCheckTileState();
}

class _UpdateCheckTileState extends State<UpdateCheckTile> {
  bool _checking = false;
  @override
  Widget build(BuildContext context) => ListTile(
      leading: const Icon(Icons.system_update_alt),
      title: Text(_checking ? '正在检查更新…' : '检查更新'),
      subtitle: const Text('查看 c001apk 的最新正式版本'),
      trailing: _checking
          ? const SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(strokeWidth: 2))
          : null,
      enabled: !_checking,
      onTap: () async {
        setState(() => _checking = true);
        try {
          await AppUpdates.check(context, updateService: widget.service);
        } finally {
          if (mounted) setState(() => _checking = false);
        }
      });
}
