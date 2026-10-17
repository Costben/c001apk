import 'package:flutter/foundation.dart';

import '../../utils/global_data.dart';
import '../../utils/storage_util.dart';

/// Session credentials stay in the existing local settings store. Passwords
/// are never retained, and credentials must not be displayed or logged.
class SavedAccount {
  const SavedAccount(
      {required this.uid,
      required this.username,
      required String token,
      this.avatar = '',
      this.level = 0,
      this.exp = 0,
      this.nextExp = 1})
      : _token = token;

  final String uid;
  final String username;
  final String _token;
  final String avatar;
  final int level, exp, nextExp;
  bool get needsLogin => _token.isEmpty;
  String get displayName {
    try {
      return Uri.decodeComponent(username);
    } catch (_) {
      return username;
    }
  }

  Map<String, dynamic> _encode() => {
        'uid': uid,
        'username': username,
        'token': _token,
        'avatar': avatar,
        'level': level,
        'exp': exp,
        'nextExp': nextExp
      };

  static SavedAccount? _decode(dynamic value) {
    if (value is! Map ||
        value['uid'] is! String ||
        !RegExp(r'^\d+$').hasMatch(value['uid'])) return null;
    return SavedAccount(
        uid: value['uid'],
        username: value['username'] is String ? value['username'] : '',
        token: value['token'] is String ? value['token'] : '',
        avatar: value['avatar'] is String ? value['avatar'] : '',
        level: value['level'] is int ? value['level'] : 0,
        exp: value['exp'] is int ? value['exp'] : 0,
        nextExp: value['nextExp'] is int && value['nextExp'] > 0
            ? value['nextExp']
            : 1);
  }
}

class AccountStore {
  static const storageKey = 'accountVaultV1';
  static final changes = ValueNotifier<int>(0);
  static Future<void> _pending = Future.value();

  List<SavedAccount> get accounts {
    final state = GStorage.settings.get(storageKey);
    if (state is! Map || state['accounts'] is! List) return [];
    return (state['accounts'] as List)
        .map(SavedAccount._decode)
        .whereType<SavedAccount>()
        .toList(growable: false);
  }

  Future<void> _serialize(Future<void> Function() action) {
    final result = _pending.then((_) => action());
    _pending = result.catchError((Object _) {});
    return result;
  }

  SavedAccount? _current() =>
      GStorage.isLogin && GStorage.uid.isNotEmpty && GStorage.token.isNotEmpty
          ? SavedAccount(
              uid: GStorage.uid,
              username: GStorage.username,
              token: GStorage.token,
              avatar: GStorage.userAvatar,
              level: GStorage.level,
              exp: GStorage.exp,
              nextExp: GStorage.nextExp)
          : null;

  List<SavedAccount> _withCurrent() {
    final result = accounts.toList();
    final current = _current();
    if (current != null) {
      result.removeWhere((a) => a.uid == current.uid);
      result.add(current);
    }
    return result;
  }

  Future<void> initialize() => _serialize(() async {
        final state = GStorage.settings.get(storageKey);
        if (state is! Map) {
          await _persist(_withCurrent(), _current(), notify: false);
        } else {
          final active = accounts
              .where((a) => a.uid == state['activeUid'] && !a.needsLogin)
              .firstOrNull;
          await _persist(accounts, active, notify: false);
        }
      });

  Future<void> saveLogin(
          {required String uid,
          required String username,
          required String token}) =>
      _serialize(() async {
        if (!RegExp(r'^\d+$').hasMatch(uid) || token.isEmpty) {
          throw StateError('登录响应缺少有效账号信息');
        }
        final all = _withCurrent();
        final previous = all.where((a) => a.uid == uid).firstOrNull;
        final account = SavedAccount(
            uid: uid,
            username: username,
            token: token,
            avatar: previous?.avatar ?? '',
            level: previous?.level ?? 0,
            exp: previous?.exp ?? 0,
            nextExp: previous?.nextExp ?? 1);
        all.removeWhere((a) => a.uid == uid);
        all.add(account);
        await _persist(all, account);
      });

  Future<void> switchTo(String uid) => _serialize(() async {
        final all = _withCurrent();
        final account = all.where((a) => a.uid == uid).firstOrNull;
        if (account == null || account.needsLogin) {
          throw StateError('此账号需要重新登录');
        }
        if (GStorage.isLogin && GStorage.uid == uid) return;
        await _persist(all, account);
      });

  Future<void> useGuest() => _serialize(() => _persist(_withCurrent(), null));

  Future<void> remove(String uid) => _serialize(() async {
        final all = _withCurrent()..removeWhere((a) => a.uid == uid);
        await _persist(all, GStorage.uid == uid ? null : _current(),
            notify: GStorage.uid == uid && GStorage.isLogin);
      });

  Future<void> expireCurrent({int? generation}) => _serialize(() async {
        if (generation != null &&
            generation != GlobalData().sessionGeneration) {
          return;
        }
        final all = _withCurrent();
        final current = _current();
        if (current != null) {
          all.removeWhere((a) => a.uid == current.uid);
          all.add(SavedAccount(
              uid: current.uid,
              username: current.username,
              token: '',
              avatar: current.avatar,
              level: current.level));
        }
        await _persist(all, null);
      });

  Future<void> cacheCurrentProfile() =>
      _serialize(() => _persist(_withCurrent(), _current(), notify: false));

  Future<void> refreshCurrent(
          {required int generation,
          String? username,
          String? token,
          String? avatar,
          int? level,
          int? exp,
          int? nextExp}) =>
      _serialize(() async {
        if (generation != GlobalData().sessionGeneration) return;
        final current = _current();
        if (current == null) return;
        final updated = SavedAccount(
            uid: current.uid,
            username: username ?? current.username,
            token: token ?? current._token,
            avatar: avatar ?? current.avatar,
            level: level ?? current.level,
            exp: exp ?? current.exp,
            nextExp: nextExp ?? current.nextExp);
        final all = accounts.toList()..removeWhere((a) => a.uid == updated.uid);
        all.add(updated);
        await _persist(all, updated, notify: false);
      });

  Future<void> _persist(List<SavedAccount> all, SavedAccount? active,
      {bool notify = true}) async {
    // A single canonical record is committed first. Startup repairs legacy
    // mirrors from this record if interrupted during the compatibility writes.
    await GStorage.settings.put(storageKey, {
      'accounts': all.map((a) => a._encode()).toList(),
      'activeUid': active?.uid,
    });
    final global = GlobalData();
    if (notify) global.sessionGeneration++;
    global.isLogin = active != null;
    global.uid = active?.uid ?? '';
    global.username = active?.username ?? '';
    global.token = active?._token ?? '';
    if (notify || active == null) global.SESSID = '';
    if (notify) global.loginSession = '';
    await GStorage.settings.putAll({
      SettingsBoxKey.isLogin: active != null,
      SettingsBoxKey.uid: global.uid,
      SettingsBoxKey.username: global.username,
      SettingsBoxKey.token: global.token,
      SettingsBoxKey.userAvatar: active?.avatar ?? '',
      SettingsBoxKey.level: active?.level ?? 0,
      SettingsBoxKey.exp: active?.exp ?? 0,
      SettingsBoxKey.nextExp: active?.nextExp ?? 1,
    });
    await GStorage.settings.flush();
    if (notify) changes.value++;
  }
}
