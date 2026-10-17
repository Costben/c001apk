import 'package:dio/dio.dart';
import 'package:package_info_plus/package_info_plus.dart';

import 'app_release.dart';
import 'update_preferences.dart';

enum UpdateStatus { available, upToDate, suppressed, busy, failed }

class UpdateResult {
  const UpdateResult(this.status,
      {this.release, this.currentVersion = '', this.message = ''});
  final UpdateStatus status;
  final AppRelease? release;
  final String currentVersion, message;
}

class UpdateService {
  UpdateService(
      {required this.preferences,
      Dio? dio,
      Future<String> Function()? currentVersion})
      : _dio = dio ??
            Dio(BaseOptions(
                connectTimeout: const Duration(seconds: 8),
                receiveTimeout: const Duration(seconds: 8))),
        _currentVersion = currentVersion ??
            (() async => (await PackageInfo.fromPlatform()).version);
  final UpdatePreferences preferences;
  final Dio _dio;
  final Future<String> Function() _currentVersion;
  bool _checking = false;
  bool _automaticChecked = false;

  Future<UpdateResult> check({bool automatic = false}) async {
    if (_checking) return const UpdateResult(UpdateStatus.busy);
    if (automatic && (!preferences.enabled || _automaticChecked)) {
      return const UpdateResult(UpdateStatus.suppressed);
    }
    _checking = true;
    // The app owns one service per process. Do not persist this guard: a new
    // startup must discover releases published after the previous check.
    if (automatic) _automaticChecked = true;
    try {
      final current = await _currentVersion();
      final response = await _dio.get<dynamic>(
          'https://api.github.com/repos/TG-Twilight/c001apk/releases/latest',
          options: Options(headers: {
            'Accept': 'application/vnd.github+json',
            'User-Agent': 'c001apk-update',
          }));
      if (response.data is! Map<String, dynamic>) {
        throw const FormatException('更新信息格式异常');
      }
      final release = AppRelease.fromJson(response.data);
      if (automatic &&
          (!preferences.enabled ||
              preferences.skipped(release.version.toString()))) {
        return const UpdateResult(UpdateStatus.suppressed);
      }
      return UpdateResult(
          release.version > AppVersion.parse(current)
              ? UpdateStatus.available
              : UpdateStatus.upToDate,
          release: release,
          currentVersion: current);
    } on DioException catch (error) {
      final status = error.response?.statusCode;
      return UpdateResult(UpdateStatus.failed,
          message: switch (status) {
            403 || 429 => '检查更新请求过于频繁，请稍后重试或查看发布页面',
            404 => '暂未找到正式版本，请稍后重试或查看发布页面',
            _ => '检查更新失败，请检查网络后重试',
          });
    } on FormatException {
      return const UpdateResult(UpdateStatus.failed,
          message: '更新信息格式异常，请稍后重试或查看发布页面');
    } catch (_) {
      return const UpdateResult(UpdateStatus.failed, message: '暂时无法检查更新，请稍后重试');
    } finally {
      _checking = false;
    }
  }
}
