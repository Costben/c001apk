import 'package:flutter/material.dart';

import '../../../utils/emoji_util.dart';

class EmotePanel extends StatelessWidget {
  const EmotePanel({
    super.key,
    required this.index,
    required this.onClick,
  });
  final int index;
  final Function(String emoji) onClick;

  @override
  Widget build(BuildContext context) {
    final entries = EmojiUtil.emojiMap.entries.skip(4).toList();
    final groups = [
      entries
          .where((item) =>
              !item.key.startsWith('[牛牛') &&
              !item.key.contains('酷币') &&
              item.key != '[新币1分]')
          .toList(),
      entries.where((item) => item.key.startsWith('[牛牛')).toList(),
      entries
          .where((item) => item.key.contains('酷币') || item.key == '[新币1分]')
          .toList(),
    ];
    return DefaultTabController(
        length: 3,
        child: Column(children: [
          const TabBar(
              tabs: [Tab(text: '常用'), Tab(text: '牛牛'), Tab(text: '酷币')]),
          Expanded(
              child: TabBarView(
                  children: groups
                      .map((items) => GridView.count(
                            padding: const EdgeInsets.only(left: 10, right: 10),
                            mainAxisSpacing: 10,
                            crossAxisSpacing: 10,
                            crossAxisCount: 7,
                            childAspectRatio: 1.0,
                            children: items
                                .map((entry) => entry.key)
                                .map(
                                  (emoji) => Material(
                                    color: Colors.transparent,
                                    child: InkWell(
                                      onTap: () => onClick(emoji),
                                      borderRadius: BorderRadius.circular(8),
                                      child: Tooltip(
                                          message: emoji,
                                          child: Padding(
                                            padding: const EdgeInsets.all(8),
                                            child: Image.asset(
                                                'assets/emojis/${EmojiUtil.emojiMap[emoji]}'),
                                          )),
                                    ),
                                  ),
                                )
                                .toList(),
                          ))
                      .toList())),
        ]));
  }
}
