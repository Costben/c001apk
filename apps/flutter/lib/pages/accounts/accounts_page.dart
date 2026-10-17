import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:hive_flutter/hive_flutter.dart';

import '../../components/network_image.dart';
import '../../logic/accounts/account_store.dart';
import '../../utils/storage_util.dart';

class AccountsPage extends StatefulWidget {
  const AccountsPage({super.key});
  @override
  State<AccountsPage> createState() => _AccountsPageState();
}

class _AccountsPageState extends State<AccountsPage> {
  final _store = AccountStore();
  bool _busy = false;
  String? _error;

  Future<void> _run(Future<void> Function() action) async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await action();
    } catch (_) {
      if (mounted) setState(() => _error = '账号操作未完成，请重试');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _remove(SavedAccount account) async {
    final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
              title: const Text('移除本机登录？'),
              content: Text('将移除“${account.displayName}”在本机保存的登录。再次使用需要重新登录。'
                  '${GStorage.uid == account.uid ? '\n当前账号将切换为游客。' : ''}'),
              actions: [
                TextButton(
                    onPressed: () => Navigator.pop(context, false),
                    child: const Text('取消')),
                TextButton(
                    onPressed: () => Navigator.pop(context, true),
                    child: const Text('移除')),
              ],
            ));
    if (confirmed == true && mounted) {
      await _run(() => _store.remove(account.uid));
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('账号管理')),
        body: SafeArea(
            top: false,
            child: Align(
              alignment: Alignment.topCenter,
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 720),
                child: ValueListenableBuilder(
                  valueListenable: GStorage.settings
                      .listenable(keys: [AccountStore.storageKey]),
                  builder: (context, _, __) => ListView(
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    children: [
                      const Padding(
                          padding: EdgeInsets.fromLTRB(20, 4, 20, 16),
                          child: Text('点击账号即可切换。登录仅保存在本机；游客模式会保留已保存的账号。')),
                      if (_busy) const LinearProgressIndicator(),
                      if (_error != null)
                        Padding(
                            padding: const EdgeInsets.all(16),
                            child: Text(_error!)),
                      if (_store.accounts.isEmpty)
                        const ListTile(
                            leading: Icon(Icons.person_outline),
                            title: Text('暂无已保存账号'),
                            subtitle: Text('添加账号后可在这里快速切换')),
                      for (final account in _store.accounts)
                        ListTile(
                          enabled: !_busy,
                          leading: account.avatar.isEmpty
                              ? const CircleAvatar(
                                  child: Icon(Icons.person_outline))
                              : clipNetworkImage(account.avatar,
                                  isAvatar: true, width: 40, height: 40),
                          title: Text(
                              account.displayName.isEmpty
                                  ? account.uid
                                  : account.displayName,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis),
                          subtitle: Text(account.needsLogin
                              ? '登录已失效 · 点击重新登录'
                              : GStorage.isLogin && GStorage.uid == account.uid
                                  ? '当前使用'
                                  : 'UID ${account.uid}'),
                          trailing: IconButton(
                              tooltip: '移除${account.displayName}',
                              onPressed: _busy ? null : () => _remove(account),
                              icon: const Icon(Icons.delete_outline)),
                          onTap: () {
                            if (account.needsLogin) {
                              Get.toNamed('/login');
                            } else {
                              _run(() => _store.switchTo(account.uid));
                            }
                          },
                        ),
                      const Divider(),
                      ListTile(
                          enabled: !_busy,
                          leading: const Icon(Icons.person_add_alt),
                          title: const Text('添加账号'),
                          subtitle: const Text('登录新的酷安账号，已有登录会保留'),
                          onTap: () => Get.toNamed('/login')),
                      ListTile(
                          enabled: !_busy,
                          leading: const Icon(Icons.no_accounts_outlined),
                          title: const Text('使用游客模式'),
                          subtitle: Text(GStorage.isLogin
                              ? '暂时不使用账号浏览，保留所有本机数据'
                              : '当前为游客模式'),
                          onTap: GStorage.isLogin
                              ? () => _run(_store.useGuest)
                              : null),
                    ],
                  ),
                ),
              ),
            )),
      );
}
