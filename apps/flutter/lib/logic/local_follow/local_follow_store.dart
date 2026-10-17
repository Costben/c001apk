import 'package:hive/hive.dart';

import '../../utils/storage_util.dart';

class LocalFollowUser {
  const LocalFollowUser(this.uid, this.name, this.avatar);
  final String uid;
  final String name;
  final String avatar;
}

/// Device-local data, deliberately independent of the active account.
class LocalFollowStore {
  LocalFollowStore([Box<dynamic>? box]) : box = box ?? GStorage.settings;
  final Box<dynamic> box;
  static const usersKey = 'localFollowUsers';
  static const enabledKey = 'secretFollowEnabled';

  bool get secretEnabled => box.get(enabledKey, defaultValue: false) == true;

  List<LocalFollowUser> get users {
    final raw = box.get(usersKey, defaultValue: <dynamic>[]);
    if (raw is! List) return [];
    return raw
        .whereType<Map>()
        .where((e) => _validUid('${e['uid']}'))
        .map((e) => LocalFollowUser('${e['uid']}', e['name']?.toString() ?? '',
            e['avatar']?.toString() ?? ''))
        .toList();
  }

  bool contains(String uid) => users.any((e) => e.uid == uid);

  Future<void> follow(String uid, String name, String avatar) {
    if (!_validUid(uid)) throw ArgumentError.value(uid, 'uid');
    final entries = users.where((e) => e.uid != uid).toList();
    entries.insert(0, LocalFollowUser(uid, name, avatar));
    return _save(entries);
  }

  Future<void> unfollow(String uid) =>
      _save(users.where((e) => e.uid != uid).toList());

  Future<void> _save(List<LocalFollowUser> entries) => box.put(usersKey, [
        for (final user in entries)
          {'uid': user.uid, 'name': user.name, 'avatar': user.avatar},
      ]);

  static bool _validUid(String uid) =>
      RegExp(r'^[0-9]+$').hasMatch(uid) && (int.tryParse(uid) ?? 0) > 0;
}
