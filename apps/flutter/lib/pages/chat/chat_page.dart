import 'dart:async';
import 'dart:io';
import 'dart:math';

import 'package:chat_bottom_container/chat_bottom_container.dart';
import 'package:flutter/material.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart';
import 'package:image_picker/image_picker.dart';

import '../../components/cards/chat_card.dart';
import '../../components/cards/chat_time_card.dart';
import '../../components/common_body.dart';
import '../../logic/state/loading_state.dart';
import '../../pages/feed/reply/emoji_panel.dart';
import '../../utils/device_util.dart';
import '../../utils/extensions.dart';
import '../../utils/global_data.dart';
import '../../utils/storage_util.dart';
import '../../utils/utils.dart';
import 'chat_controller.dart';
import 'chat_feed_picker.dart';

enum PanelType { none, keyboard, emoji }

enum ChatMenuType { profile, block, report }

class ChatPage extends StatefulWidget {
  const ChatPage({super.key});
  @override
  State<ChatPage> createState() => _ChatPageState();
}

class _ChatPageState extends State<ChatPage> {
  late final String _ukey = Get.parameters['ukey'] ?? '';
  late final String _uid = Get.parameters['uid'] ?? '';
  late final String _username = Get.parameters['username'] ?? '';
  late final String _tag = _ukey + DeviceUtil.randHexString(8);
  late final _chatController = Get.put(ChatController(ukey: _ukey), tag: _tag);
  final _focusNode = FocusNode();
  final _panel = ChatBottomPanelContainerController<PanelType>();
  final _readOnlyStream = StreamController<bool>();
  PanelType _currentPanelType = PanelType.none;
  bool _readOnly = false;
  bool _pickingImage = false;

  @override
  void initState() {
    super.initState();
    _chatController.scrollController = ScrollController();
  }

  @override
  void dispose() {
    Get.delete<ChatController>(tag: _tag);
    _readOnlyStream.close();
    _focusNode.dispose();
    _chatController.editingController.dispose();
    _chatController.scrollController?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        resizeToAvoidBottomInset: false,
        appBar: AppBar(title: Text(_username), actions: [
          PopupMenuButton<ChatMenuType>(
            onOpened: _focusNode.unfocus,
            onSelected: (value) {
              switch (value) {
                case ChatMenuType.profile:
                  Get.toNamed('/u/$_uid');
                case ChatMenuType.block:
                  GStorage.onBlock(_uid);
                case ChatMenuType.report:
                  if (Utils.isSupportWebview()) {
                    Utils.report(_uid, ReportType.User);
                  } else {
                    SmartDialog.showToast('当前平台暂不支持此功能');
                  }
              }
            },
            itemBuilder: (_) => const [
              PopupMenuItem(value: ChatMenuType.profile, child: Text('查看用户')),
              PopupMenuItem(value: ChatMenuType.block, child: Text('屏蔽')),
              PopupMenuItem(value: ChatMenuType.report, child: Text('举报')),
            ],
          ),
        ]),
        body: SafeArea(
            top: false,
            child: Column(children: [
              Expanded(
                  child: Listener(
                      onPointerDown: (_) => hidePanel(), child: _buildList())),
              _buildInputView(),
              _buildPanelContainer(),
            ])),
      );

  Widget _buildList() => Obx(() {
        final rows = _chatController.messages;
        if (rows.isEmpty) return Center(child: buildBody(_chatController));
        final historyError = _chatController.loadingState.value is Error;
        return ListView.builder(
          controller: _chatController.scrollController,
          reverse: true,
          padding: EdgeInsets.zero,
          itemCount: rows.length + (historyError ? 1 : 0),
          itemBuilder: (context, index) {
            if (index == rows.length) {
              return ListTile(
                title:
                    Text((_chatController.loadingState.value as Error).errMsg),
                trailing: TextButton(
                    onPressed: () {
                      _chatController.onReset();
                      _chatController.setLoadingState(LoadingState.loading());
                      _chatController.onGetData();
                    },
                    child: const Text('重新加载聊天记录')),
              );
            }
            if (index == rows.length - 1 &&
                !_chatController.isEnd &&
                !_chatController.isLoading &&
                _chatController.loadingState.value is Success) {
              _chatController.onGetData(false);
            }
            final data = rows[index];
            if (data.entityType == 'messageExtra') {
              return ChatTimeCard(text: data.title ?? '');
            }
            if (data.entityType != 'message') {
              return const ListTile(title: Text('暂不支持此消息类型'));
            }
            final delivery = _chatController.deliveryFor(data);
            return ChatCard(
              key: ValueKey(data.id),
              data: data,
              isLeft: data.fromuid.toString() != GlobalData().uid,
              localImage: delivery?.state == DeliveryState.sent
                  ? null
                  : delivery?.image,
              sending: delivery?.state == DeliveryState.sending,
              failed: delivery?.state == DeliveryState.failed,
              onRetry: delivery == null ? null : () => _showFailure(delivery),
              onGetImageUrl: () => _chatController.onGetImageUrl(data.id),
              onViewImage: () {
                if (delivery?.image != null &&
                    delivery?.state != DeliveryState.sent) {
                  showDialog<void>(
                      context: context,
                      builder: (context) => Dialog(
                              child: Column(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                Flexible(
                                    child: InteractiveViewer(
                                        child: Image.file(
                                            File(delivery!.image!.path)))),
                                TextButton(
                                    onPressed: () => Navigator.pop(context),
                                    child: const Text('关闭')),
                              ])));
                  return;
                }
                if (data.messagePic?.startsWith('http') == true) {
                  Get.toNamed('/imageview', arguments: {
                    'imgList': [data.messagePic!]
                  });
                } else {
                  _chatController.retryImage(data.id);
                }
              },
              onLongPress: () {
                if (delivery?.state == DeliveryState.sending) return;
                if (delivery?.state == DeliveryState.failed) {
                  _showFailure(delivery!);
                  return;
                }
                showDialog<void>(
                    context: context,
                    builder: (context) => SimpleDialog(
                          children: [
                            if (!data.message.isNullOrEmpty)
                              SimpleDialogOption(
                                  child: const Text('复制'),
                                  onPressed: () {
                                    Navigator.pop(context);
                                    Utils.copyText(
                                        Utils.parseHtmlString(data.message!));
                                  }),
                            SimpleDialogOption(
                                child: const Text('删除'),
                                onPressed: () async {
                                  Navigator.pop(context);
                                  final confirmed = await showDialog<bool>(
                                      context: this.context,
                                      builder: (context) => AlertDialog(
                                              title: const Text('删除消息？'),
                                              content: const Text('删除后无法恢复。'),
                                              actions: [
                                                TextButton(
                                                    onPressed: () =>
                                                        Navigator.pop(
                                                            context, false),
                                                    child: const Text('取消')),
                                                TextButton(
                                                    onPressed: () =>
                                                        Navigator.pop(
                                                            context, true),
                                                    child: const Text('删除')),
                                              ]));
                                  if (confirmed == true && mounted) {
                                    _chatController.onDeleteMsg(data.id);
                                  }
                                }),
                          ],
                        ));
              },
            );
          },
        );
      });

  Future<void> _showFailure(ChatDelivery delivery) async {
    final result = await showDialog<String>(
        context: context,
        builder: (context) => AlertDialog(
              title: Text(delivery.unconfirmed ? '发送结果未确认' : '消息发送失败'),
              content: Text(
                  '${delivery.error ?? '请稍后重试'}${delivery.unconfirmed ? '\n请先检查聊天记录，再次发送可能产生重复消息。' : ''}'),
              actions: [
                TextButton(
                    onPressed: () => Navigator.pop(context, 'discard'),
                    child: const Text('移除此条')),
                TextButton(
                    onPressed: () => Navigator.pop(context),
                    child: const Text('取消')),
                FilledButton(
                    onPressed: () => Navigator.pop(context, 'retry'),
                    child: const Text('重新发送')),
              ],
            ));
    if (!mounted) return;
    if (result == 'retry') _chatController.retry(_uid, delivery);
    if (result == 'discard') _chatController.discard(delivery);
  }

  Widget _buildInputView() => Container(
        padding: const EdgeInsets.fromLTRB(8, 6, 8, 0),
        color: Theme.of(context).colorScheme.onInverseSurface,
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Listener(
              onPointerUp: (_) {
                if (_readOnly) updatePanelType(PanelType.keyboard);
              },
              child: StreamBuilder<bool>(
                initialData: false,
                stream: _readOnlyStream.stream,
                builder: (_, snapshot) => TextField(
                  readOnly: snapshot.data ?? false,
                  focusNode: _focusNode,
                  controller: _chatController.editingController,
                  minLines: 1,
                  maxLines: 4,
                  textInputAction: TextInputAction.newline,
                  decoration: InputDecoration(
                      filled: true,
                      hintText: '写私信...',
                      fillColor: Theme.of(context).colorScheme.surface,
                      border: OutlineInputBorder(
                          borderSide: BorderSide.none,
                          borderRadius: BorderRadius.circular(8)),
                      contentPadding: const EdgeInsets.all(10)),
                ),
              )),
          Row(children: [
            IconButton(
                tooltip: '表情',
                icon: const Icon(Icons.emoji_emotions_outlined),
                onPressed: () => updatePanelType(
                    _currentPanelType == PanelType.emoji
                        ? PanelType.keyboard
                        : PanelType.emoji)),
            IconButton(
                tooltip: '图片',
                icon: const Icon(Icons.add_photo_alternate_outlined),
                onPressed: _pickingImage ? null : _pickImage),
            IconButton(
                tooltip: '动态',
                icon: const Icon(Icons.feed_outlined),
                onPressed: _pickFeed),
            const Spacer(),
            ValueListenableBuilder<TextEditingValue>(
                valueListenable: _chatController.editingController,
                builder: (_, value, __) => IconButton(
                    tooltip: '发送',
                    icon: const Icon(Icons.send),
                    onPressed: value.text.trim().isEmpty
                        ? null
                        : () => _chatController.onSendMessage(_uid))),
          ]),
        ]),
      );

  Future<void> _pickImage() async {
    if (_pickingImage) return;
    _pickingImage = true;
    final generation = GlobalData().sessionGeneration;
    try {
      await hidePanel();
      final file = await ImagePicker().pickImage(source: ImageSource.gallery);
      if (file == null ||
          !mounted ||
          generation != GlobalData().sessionGeneration) return;
      _chatController.onSendMessage(_uid, image: file);
    } catch (_) {
      if (mounted) SmartDialog.showToast('无法打开图片，请检查相册权限或稍后重试');
    } finally {
      _pickingImage = false;
    }
  }

  Future<void> _pickFeed() async {
    await hidePanel();
    if (!mounted) return;
    final generation = GlobalData().sessionGeneration;
    final url = await showDialog<String>(
        context: context, builder: (_) => const ChatFeedPicker());
    if (url != null &&
        mounted &&
        generation == GlobalData().sessionGeneration) {
      _chatController.onSendMessage(_uid, message: url);
    }
  }

  Widget _buildPanelContainer() => ChatBottomPanelContainer<PanelType>(
        controller: _panel,
        inputFocusNode: _focusNode,
        panelBgColor: Theme.of(context).colorScheme.onInverseSurface,
        otherPanelWidget: (type) => type == PanelType.emoji
            ? SizedBox(
                height: max(240,
                    _panel.keyboardHeight == 0 ? 300 : _panel.keyboardHeight),
                child: EmotePanel(index: 1, onClick: _onChooseEmote),
              )
            : const SizedBox.shrink(),
        onPanelTypeChange: (type, data) {
          _currentPanelType = type == ChatBottomPanelType.keyboard
              ? PanelType.keyboard
              : type == ChatBottomPanelType.other
                  ? data ?? PanelType.none
                  : PanelType.none;
        },
      );

  void updatePanelType(PanelType type) {
    final keyboard = type == PanelType.keyboard;
    final updated = updateInputView(isReadOnly: !keyboard);
    void update() {
      if (!mounted) return;
      _panel.updatePanelType(
          keyboard ? ChatBottomPanelType.keyboard : ChatBottomPanelType.other,
          data: type,
          forceHandleFocus: keyboard
              ? ChatBottomHandleFocus.none
              : ChatBottomHandleFocus.requestFocus);
    }

    if (updated) {
      WidgetsBinding.instance.addPostFrameCallback((_) => update());
    } else {
      update();
    }
  }

  Future<void> hidePanel() async {
    _focusNode.unfocus();
    updateInputView(isReadOnly: false);
    if (_panel.currentPanelType != ChatBottomPanelType.none) {
      _panel.updatePanelType(ChatBottomPanelType.none);
    }
  }

  bool updateInputView({required bool isReadOnly}) {
    if (_readOnly == isReadOnly) return false;
    _readOnly = isReadOnly;
    _readOnlyStream.add(isReadOnly);
    return true;
  }

  void _onChooseEmote(String emoji) {
    final editor = _chatController.editingController;
    final selection = editor.selection;
    final start = selection.isValid ? selection.start : editor.text.length;
    final end = selection.isValid ? selection.end : start;
    editor.value = TextEditingValue(
        text: editor.text.replaceRange(start, end, emoji),
        selection: TextSelection.collapsed(offset: start + emoji.length));
  }
}
