import 'dart:io';

import 'package:c001apk_flutter/components/network_image.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:image_picker/image_picker.dart';

import '../../components/html_text.dart';
import '../../components/no_splash_factory.dart';
import '../../logic/model/feed/datum.dart';
import '../../utils/extensions.dart';
import '../../utils/utils.dart';

class ChatCard extends StatelessWidget {
  const ChatCard({
    super.key,
    required this.data,
    required this.isLeft,
    required this.onGetImageUrl,
    required this.onLongPress,
    required this.onViewImage,
    this.localImage,
    this.sending = false,
    this.failed = false,
    this.onRetry,
  });

  final Datum data;
  final bool isLeft;
  final Function() onGetImageUrl;
  final Function() onLongPress;
  final Function() onViewImage;
  final XFile? localImage;
  final bool sending;
  final bool failed;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(builder: (context, constraints) {
      double maxWidth = (constraints.maxWidth - 176).clamp(80, 520);
      final plainText = Utils.parseHtmlString(data.message ?? '').trim();
      final feedLink = RegExp(r'^https://www\.coolapk\.com/feed/([0-9]+)$')
          .firstMatch(plainText);
      return InkWell(
        highlightColor: Colors.transparent,
        splashColor: Colors.transparent,
        splashFactory: NoSplashFactory(),
        onLongPress: onLongPress,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          child: Row(
            mainAxisAlignment:
                isLeft ? MainAxisAlignment.start : MainAxisAlignment.end,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (!isLeft && sending)
                const Padding(
                    padding: EdgeInsets.only(right: 8, top: 10),
                    child: SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, semanticsLabel: '消息发送中'))),
              if (!isLeft && failed)
                IconButton(
                    tooltip: '发送失败，点击查看',
                    onPressed: onRetry,
                    icon: Icon(Icons.error_outline,
                        color: Theme.of(context).colorScheme.error)),
              if (isLeft) ...[
                GestureDetector(
                  onTap: () {
                    Get.toNamed('/u/${data.messageUid}');
                  },
                  child: clipNetworkImage(
                    data.fromUserAvatar ?? '',
                    isAvatar: true,
                    width: 40,
                    height: 40,
                  ),
                ),
                const SizedBox(width: 10),
              ],
              ConstrainedBox(
                  constraints: BoxConstraints(maxWidth: maxWidth),
                  child: Column(
                      crossAxisAlignment: isLeft
                          ? CrossAxisAlignment.start
                          : CrossAxisAlignment.end,
                      children: [
                        if (!data.message.isNullOrEmpty)
                          ConstrainedBox(
                            constraints: BoxConstraints(maxWidth: maxWidth),
                            child: Container(
                              decoration: BoxDecoration(
                                color: isLeft
                                    ? Theme.of(context)
                                        .colorScheme
                                        .onInverseSurface
                                    : Theme.of(context)
                                        .colorScheme
                                        .primaryContainer,
                                borderRadius: BorderRadius.only(
                                  topLeft: isLeft
                                      ? const Radius.circular(4)
                                      : const Radius.circular(12),
                                  topRight: isLeft
                                      ? const Radius.circular(12)
                                      : const Radius.circular(4),
                                  bottomLeft: const Radius.circular(12),
                                  bottomRight: const Radius.circular(12),
                                ),
                              ),
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 10, vertical: 10),
                              child: feedLink == null
                                  ? htmlText(data.message!)
                                  : InkWell(
                                      onTap: () => Utils.onOpenLink(plainText),
                                      child: Row(
                                          mainAxisSize: MainAxisSize.min,
                                          children: [
                                            const Icon(Icons.feed_outlined),
                                            const SizedBox(width: 8),
                                            Flexible(
                                                child: Text(
                                                    '动态 ${feedLink.group(1)}\n点击查看',
                                                    style: const TextStyle(
                                                        fontSize: 14))),
                                          ]),
                                    ),
                            ),
                          ),
                        if (localImage != null)
                          GestureDetector(
                              onTap: onViewImage,
                              child: ClipRRect(
                                  borderRadius: BorderRadius.circular(12),
                                  child: Image.file(File(localImage!.path),
                                      width: maxWidth,
                                      height: 140,
                                      fit: BoxFit.contain,
                                      errorBuilder: (_, __, ___) =>
                                          const SizedBox(
                                              width: 100,
                                              height: 100,
                                              child: Center(
                                                  child: Text('图片预览不可用')))))),
                        if (localImage == null &&
                            !data.messagePic.isNullOrEmpty)
                          _chatImage(context, maxWidth / 2),
                        if (!data.extraUrl.isNullOrEmpty)
                          Card(
                              child: InkWell(
                                  onTap: () => Utils.onOpenLink(data.extraUrl!),
                                  child: Padding(
                                      padding: const EdgeInsets.all(10),
                                      child: Column(
                                          crossAxisAlignment:
                                              CrossAxisAlignment.start,
                                          children: [
                                            const Icon(Icons.feed_outlined),
                                            Text(data.extraTitle ?? '分享的内容',
                                                maxLines: 3,
                                                overflow:
                                                    TextOverflow.ellipsis),
                                            const Text('点击查看',
                                                style: TextStyle(fontSize: 12)),
                                          ])))),
                      ])),
              if (!isLeft) ...[
                const SizedBox(width: 10),
                GestureDetector(
                  onTap: () {
                    Get.toNamed('/u/${data.fromuid}');
                  },
                  child: clipNetworkImage(
                    data.fromUserAvatar ?? '',
                    isAvatar: true,
                    width: 40,
                    height: 40,
                  ),
                ),
              ],
            ],
          ),
        ),
      );
    });
  }

  Widget _chatImage(BuildContext context, double width) {
    if (data.messagePic!.startsWith('http')) {
      List<double> imageLp =
          Utils.getImageLp(data.messagePic!.split('?').first);
      return Container(
        decoration: BoxDecoration(
          border: Border.all(
              color: Theme.of(context).colorScheme.outline.withOpacity(0.5)),
          borderRadius: BorderRadius.circular(12),
        ),
        width: width,
        height: width * imageLp[1] / imageLp[0],
        child: GestureDetector(
          onTap: onViewImage,
          child: clipNetworkImage(
            data.messagePic!,
            radius: 12,
            fit: BoxFit.fill,
          ),
        ),
      );
    } else {
      onGetImageUrl();
      List<double> imageLp = Utils.getImageLp(data.messagePic!);
      return Container(
        width: width,
        height: width * imageLp[1] / imageLp[0],
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(12),
          color: Theme.of(context).colorScheme.onInverseSurface,
          border: Border.all(
              color: Theme.of(context).colorScheme.outline.withOpacity(0.5)),
        ),
        child: IconButton(
            tooltip: '加载图片',
            onPressed: onViewImage,
            icon: const Icon(Icons.image_outlined)),
      );
    }
  }
}
