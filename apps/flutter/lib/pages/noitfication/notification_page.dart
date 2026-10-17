import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../components/common_body.dart';
import '../../components/cards/message_card.dart';
import '../../pages/noitfication/notification_controller.dart';

// ignore: constant_identifier_names
enum NotificationType { AT, COMMENT, LIKE, FOLLOW, MESSAGE, SYSTEM }

class NotificationPage extends StatefulWidget {
  const NotificationPage({super.key});

  @override
  State<NotificationPage> createState() => _NotificationPageState();
}

class _NotificationPageState extends State<NotificationPage> {
  final NotificationType type = Get.arguments['type'];
  final VoidCallback? _onNotificationsRead =
      Get.arguments['onNotificationsRead'];
  late int _notificationCount = Get.arguments['notificationCount'] ?? 0;
  late final String _title = switch (type) {
    NotificationType.AT => '@我的动态',
    NotificationType.COMMENT => '@我的评论',
    NotificationType.LIKE => '我收到的赞',
    NotificationType.FOLLOW => '好友关注',
    NotificationType.MESSAGE => '私信与信息',
    NotificationType.SYSTEM => '酷安小秘书',
  };
  late final _notificationController = Get.put(
    NotificationController(
      url: switch (type) {
        NotificationType.AT => '/v6/notification/atMeList',
        NotificationType.COMMENT => '/v6/notification/atCommentMeList',
        NotificationType.LIKE => '/v6/notification/feedLikeList',
        NotificationType.FOLLOW => '/v6/notification/contactsFollowList',
        NotificationType.MESSAGE => '/v6/message/list',
        NotificationType.SYSTEM => '/v6/notification/list',
      },
    ),
    tag: type.name,
  );

  @override
  void dispose() {
    _notificationController.scrollController?.dispose();
    Get.delete<NotificationController>(
      tag: type.name,
    );
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_title),
        bottom: const PreferredSize(
          preferredSize: Size.zero,
          child: Divider(height: 1),
        ),
      ),
      body: commonBody(
        _notificationController,
        itemBuilder: type == NotificationType.MESSAGE
            ? (data) => Obx(() => MessageCard(
                  data: data,
                  onPM: _notificationController.onPM,
                  busy: _notificationController.busyConversations
                      .contains(data.ukey),
                  onPin: () => _notificationController.updateConversation(data,
                      delete: false),
                  onDelete: () => _notificationController
                      .updateConversation(data, delete: true),
                ))
            : null,
        leading: type == NotificationType.MESSAGE
            ? Material(
                color: Theme.of(context).colorScheme.onInverseSurface,
                borderRadius: BorderRadius.circular(12),
                clipBehavior: Clip.antiAlias,
                child: ListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 10),
                  leading: const CircleAvatar(child: Icon(Icons.support_agent)),
                  title: const Text('酷安小秘书'),
                  subtitle: const Text('系统通知与信息', maxLines: 1),
                  trailing: _notificationCount > 0
                      ? Badge.count(count: _notificationCount)
                      : null,
                  onTap: () {
                    _onNotificationsRead?.call();
                    Get.toNamed('/notification',
                        arguments: {
                          'type': NotificationType.SYSTEM,
                        },
                        preventDuplicates: false);
                    setState(() => _notificationCount = 0);
                  },
                ),
              )
            : null,
      ),
    );
  }
}
