import 'package:c001apk_flutter/components/network_image.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../logic/model/feed/datum.dart';
import '../../utils/date_util.dart';
import '../../utils/utils.dart';

class MessageCard extends StatelessWidget {
  const MessageCard({
    super.key,
    required this.data,
    required this.onPM,
    this.onPin,
    this.onDelete,
    this.busy = false,
  });

  final Datum data;
  final Function(dynamic id)? onPM;
  final VoidCallback? onPin;
  final VoidCallback? onDelete;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    final thisContext = context;
    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onLongPress: busy || (onPin == null && onDelete == null)
          ? null
          : () {
              showModalBottomSheet<void>(
                  context: context,
                  builder: (context) => SafeArea(
                          child:
                              Column(mainAxisSize: MainAxisSize.min, children: [
                        ListTile(
                            leading: const Icon(Icons.push_pin_outlined),
                            title: Text(data.isTop == 1 ? '取消置顶' : '置顶会话'),
                            onTap: () {
                              Navigator.pop(context);
                              onPin?.call();
                            }),
                        ListTile(
                            leading: const Icon(Icons.delete_outline),
                            title: const Text('删除会话'),
                            onTap: () async {
                              Navigator.pop(context);
                              final confirmed = await showDialog<bool>(
                                  context: thisContext,
                                  builder: (context) => AlertDialog(
                                          title: const Text('删除此会话？'),
                                          content: const Text(
                                              '会话将从私信列表中删除，此操作无法撤销。'),
                                          actions: [
                                            TextButton(
                                                onPressed: () => Navigator.pop(
                                                    context, false),
                                                child: const Text('取消')),
                                            TextButton(
                                                onPressed: () => Navigator.pop(
                                                    context, true),
                                                child: const Text('删除'))
                                          ]));
                              if (confirmed == true) onDelete?.call();
                            }),
                      ])));
            },
      onTap: () {
        Get.toNamed('/chat', parameters: {
          'ukey': data.ukey ?? '',
          'uid': data.messageUid.toString(),
          'username': data.messageUsername ?? '',
        });
        if (onPM != null) {
          onPM!(data.id);
        }
      },
      child: Ink(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(12),
          color: Theme.of(context).colorScheme.onInverseSurface,
        ),
        padding: const EdgeInsets.all(10),
        child: Row(
          children: [
            GestureDetector(
              onTap: () => Get.toNamed('/u/${data.messageUid}'),
              child: clipNetworkImage(
                data.messageUserAvatar ?? '',
                isAvatar: true,
                width: 40,
                height: 40,
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              flex: 1,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Row(
                    children: [
                      Expanded(
                        flex: 1,
                        child: Text(
                          data.messageUsername ?? '',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      if (data.isTop == 1)
                        const Padding(
                            padding: EdgeInsets.only(left: 6),
                            child: Icon(Icons.push_pin,
                                size: 14, semanticLabel: '置顶会话')),
                      if (busy)
                        const Padding(
                            padding: EdgeInsets.only(left: 6),
                            child: SizedBox(
                                width: 14,
                                height: 14,
                                child:
                                    CircularProgressIndicator(strokeWidth: 2))),
                      const SizedBox(width: 10),
                      Text(
                        DateUtil.fromToday(data.dateline),
                        style: TextStyle(
                          fontSize: 13,
                          color: Theme.of(context).colorScheme.outline,
                        ),
                      ),
                    ],
                  ),
                  Row(
                    children: [
                      Expanded(
                        flex: 1,
                        child: Text(
                          Utils.parseHtmlString(data.message ?? ''),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      if (data.unreadNum != null && data.unreadNum != 0)
                        Badge.count(count: data.unreadNum!),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
