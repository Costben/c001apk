import 'package:flutter/material.dart';

import '../../logic/model/fav_history/fav_history.dart';
import '../../logic/model/feed/datum.dart';
import '../../logic/network/network_repo.dart';
import '../../logic/state/loading_state.dart';
import '../../utils/global_data.dart';
import '../../utils/storage_util.dart';
import '../../utils/utils.dart';

/// Shares a canonical feed URL through the existing text-message protocol.
/// Both official and third-party clients can open it without private card fields.
class ChatFeedPicker extends StatefulWidget {
  const ChatFeedPicker({super.key});
  @override
  State<ChatFeedPicker> createState() => _ChatFeedPickerState();
}

class _ChatFeedPickerState extends State<ChatFeedPicker> {
  final _link = TextEditingController();
  String? _error;
  late Future<LoadingState> _mine = _load();
  int _page = 1;
  bool _more = false;
  final _feeds = <Datum>[];
  Future<LoadingState> _load() =>
      NetworkRepo.getDataListFromUrl(url: '/v6/user/feedList', data: {
        'uid': GlobalData().uid,
        'page': _page,
        'showAnonymous': 0,
        'isIncludeTop': 0
      });

  @override
  void dispose() {
    _link.dispose();
    super.dispose();
  }

  void _choose(String id) async {
    final yes = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
                title: const Text('发送动态？'),
                content: Text('https://www.coolapk.com/feed/$id'),
                actions: [
                  TextButton(
                      onPressed: () => Navigator.pop(context, false),
                      child: const Text('取消')),
                  FilledButton(
                      onPressed: () => Navigator.pop(context, true),
                      child: const Text('发送'))
                ]));
    if (yes == true && mounted) {
      Navigator.pop(context, 'https://www.coolapk.com/feed/$id');
    }
  }

  Widget _tile(String id, String text) => ListTile(
      leading: const Icon(Icons.feed_outlined),
      title: Text(Utils.parseHtmlString(text),
          maxLines: 2, overflow: TextOverflow.ellipsis),
      subtitle: Text('动态 $id'),
      onTap: () => _choose(id));

  @override
  Widget build(BuildContext context) => Dialog(
          child: SizedBox(
        width: 520,
        height: MediaQuery.sizeOf(context).height * .7,
        child: DefaultTabController(
            length: 3,
            child: Column(children: [
              Padding(
                  padding: const EdgeInsets.only(left: 20, top: 8),
                  child: Row(children: [
                    const Expanded(
                        child: Text('发送动态', style: TextStyle(fontSize: 18))),
                    IconButton(
                        tooltip: '关闭',
                        onPressed: () => Navigator.pop(context),
                        icon: const Icon(Icons.close)),
                  ])),
              const TabBar(tabs: [
                Tab(text: '我的动态'),
                Tab(text: '本地收藏'),
                Tab(text: '链接')
              ]),
              Expanded(
                  child: TabBarView(children: [
                FutureBuilder<LoadingState>(
                    future: _mine,
                    builder: (context, snapshot) {
                      if (snapshot.connectionState != ConnectionState.done ||
                          !snapshot.hasData) {
                        return const Center(child: CircularProgressIndicator());
                      }
                      final state = snapshot.data!;
                      if (state is Success) {
                        for (final feed in state.response as List<Datum>) {
                          if (feed.entityType == 'feed' &&
                              !_feeds.any((item) => item.id == feed.id)) {
                            _feeds.add(feed);
                          }
                        }
                      }
                      return ListView(children: [
                        for (final feed in _feeds)
                          _tile('${feed.id}',
                              feed.messageTitle ?? feed.message ?? '动态'),
                        if (state is Error)
                          ListTile(
                              title: Text(state.errMsg),
                              subtitle: const Text('点击重试'),
                              onTap: () => setState(() => _mine = _load()))
                        else if (state is Empty && _feeds.isEmpty)
                          const ListTile(title: Text('暂无动态，可粘贴动态链接'))
                        else if (state is Success)
                          TextButton(
                              onPressed: () => setState(() {
                                    _page++;
                                    _more = true;
                                    _mine = _load();
                                  }),
                              child: const Text('加载更多'))
                        else if (_more)
                          const ListTile(title: Text('没有更多动态')),
                      ]);
                    }),
                Builder(builder: (_) {
                  final items = GStorage.favFeed.values
                      .whereType<FavHistoryItem>()
                      .toList()
                      .reversed
                      .toList();
                  if (items.isEmpty) {
                    return const Center(child: Text('暂无本地收藏，可粘贴动态链接'));
                  }
                  return ListView(children: [
                    for (final item in items)
                      if (RegExp(r'^[0-9]+$').hasMatch(item.id ?? ''))
                        _tile(item.id!, item.message ?? '动态')
                  ]);
                }),
                Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(children: [
                      TextField(
                          controller: _link,
                          decoration: InputDecoration(
                              labelText: '动态链接或编号',
                              hintText: 'https://www.coolapk.com/feed/…',
                              errorText: _error)),
                      const SizedBox(height: 12),
                      FilledButton(
                          onPressed: () {
                            final input = _link.text.trim();
                            String? id;
                            if (RegExp(r'^[0-9]+$').hasMatch(input)) {
                              id = input;
                            } else {
                              final uri = Uri.tryParse(input);
                              if (uri != null &&
                                  ((['http', 'https'].contains(uri.scheme) &&
                                          ['www.coolapk.com', 'coolapk.com']
                                              .contains(uri.host)) ||
                                      uri.scheme == 'coolmarket')) {
                                final path = uri.scheme == 'coolmarket'
                                    ? '/${uri.host}${uri.path}'
                                    : uri.path;
                                id = RegExp(r'^/feed/([0-9]+)/?$')
                                    .firstMatch(path)
                                    ?.group(1);
                              }
                            }
                            if (id == null) {
                              setState(() => _error = '请输入有效的酷安动态链接或编号');
                            } else {
                              _choose(id);
                            }
                          },
                          child: const Text('下一步')),
                    ])),
              ])),
            ])),
      ));
}
