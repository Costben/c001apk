/// Only stable semantic versions participate in public release checks.
class AppVersion implements Comparable<AppVersion> {
  const AppVersion(this.major, this.minor, this.patch);
  final int major, minor, patch;

  factory AppVersion.parse(String value) {
    final match = RegExp(
            r'^v?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-release)?(?:\+\d+)?$')
        .firstMatch(value);
    if (match == null) throw const FormatException('无法识别正式版本');
    return AppVersion(
        int.parse(match[1]!), int.parse(match[2]!), int.parse(match[3]!));
  }

  @override
  int compareTo(AppVersion other) {
    for (final pair in [
      (major, other.major),
      (minor, other.minor),
      (patch, other.patch)
    ]) {
      final order = pair.$1.compareTo(pair.$2);
      if (order != 0) return order;
    }
    return 0;
  }

  bool operator >(AppVersion other) => compareTo(other) > 0;
  @override
  String toString() => '$major.$minor.$patch';
}

class AppRelease {
  AppRelease._(
      this.version, this.page, this.notes, this.publishedAt, this._assets);
  final AppVersion version;
  final Uri page;
  final String notes;
  final DateTime? publishedAt;
  final Map<String, Uri> _assets;
  static const _repository = '/TG-Twilight/c001apk/releases/';

  static bool _trusted(Uri uri, String prefix) =>
      uri.scheme == 'https' &&
      uri.host == 'github.com' &&
      uri.userInfo.isEmpty &&
      !uri.hasPort &&
      uri.path.startsWith('$_repository$prefix');

  factory AppRelease.fromJson(Map<String, dynamic> json) {
    if (json['draft'] != false ||
        json['prerelease'] != false ||
        json['tag_name'] is! String ||
        json['html_url'] is! String ||
        json['assets'] is! List) {
      throw const FormatException('更新信息格式异常');
    }
    final tag = json['tag_name'] as String;
    final version = AppVersion.parse(tag);
    final page = Uri.parse(json['html_url'] as String);
    if (!_trusted(page, 'tag/') || page.pathSegments.last != tag) {
      throw const FormatException('更新来源异常');
    }
    final assets = <String, Uri>{};
    for (final asset in json['assets'] as List) {
      if (asset is! Map ||
          asset['name'] is! String ||
          asset['browser_download_url'] is! String ||
          asset['state'] != 'uploaded' ||
          asset['size'] is! num ||
          (asset['size'] as num) <= 0) continue;
      final uri = Uri.tryParse(asset['browser_download_url'] as String);
      if (uri != null &&
          _trusted(uri, 'download/') &&
          uri.pathSegments.length == 6 &&
          uri.pathSegments[4] == tag &&
          uri.pathSegments.last == asset['name']) {
        assets[asset['name'] as String] = uri;
      }
    }
    return AppRelease._(
        version,
        page,
        json['body'] is String && (json['body'] as String).trim().isNotEmpty
            ? json['body'] as String
            : '此版本未提供更新说明，请查看发布页面。',
        DateTime.tryParse(json['published_at']?.toString() ?? ''),
        assets);
  }

  Uri downloadFor(String platform, {List<String> abis = const []}) {
    final suffixes = switch (platform) {
      'android' => abis
          .where(['arm64-v8a', 'armeabi-v7a', 'x86_64'].contains)
          .map((abi) => 'android-$abi.apk')
          .toList(),
      'windows' => ['windows-x86_64.zip'],
      'linux' => ['linux-x86_64.tar.gz'],
      _ => <String>[],
    };
    for (final suffix in suffixes) {
      final pattern = RegExp('^c001apk-${RegExp.escape(version.toString())}'
          '(?:\\+\\d+)?-${RegExp.escape(suffix)}\$');
      for (final asset in _assets.entries) {
        if (pattern.hasMatch(asset.key)) return asset.value;
      }
    }
    return page;
  }
}
