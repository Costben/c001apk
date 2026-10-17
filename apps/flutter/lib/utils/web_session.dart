import 'global_data.dart';
import 'storage_util.dart';

Future<void> prepareWebSession(
    Future<void> Function(Uri origin, String domain, String name, String value)
        setCookie,
    {required Future<void> Function(String name) removeCookie,
    bool includeAccount = true}) async {
  // Clear only Coolapk authentication, including the previous account's DDI
  // session. Keep unrelated site cookies and normal browser preferences.
  for (final name in ['uid', 'username', 'token', 'SESSID', 'ddid']) {
    await removeCookie(name);
  }
  if (!includeAccount || !GlobalData().isLogin) return;
  final values = {
    'DID': GStorage.szlmId,
    'forward': 'https://www.coolapk.com',
    'displayVersion': 'v16',
    'uid': GlobalData().uid,
    'username': GlobalData().username,
    'token': GlobalData().token,
    if (GlobalData().SESSID.startsWith('SESSID='))
      'SESSID': GlobalData().SESSID.substring('SESSID='.length),
  };
  for (final entry in values.entries) {
    if (entry.value.isNotEmpty) {
      await setCookie(Uri.parse('https://www.coolapk.com/'), '.coolapk.com',
          entry.key, entry.value);
    }
  }
}
