import 'package:hive/hive.dart';

/// Separate from checkUpdate, which controls other installed Android apps.
class UpdatePreferences {
  UpdatePreferences(this.box);
  final Box<dynamic> box;
  static const enabledKey = 'autoCheckAppUpdate';
  static const _skipKey = 'skippedAppUpdateVersion';

  bool get enabled => box.get(enabledKey, defaultValue: true) == true;
  Future<void> setEnabled(bool value) => box.put(enabledKey, value);
  Future<void> skip(String version) => box.put(_skipKey, version);
  bool skipped(String version) => box.get(_skipKey) == version;
}
