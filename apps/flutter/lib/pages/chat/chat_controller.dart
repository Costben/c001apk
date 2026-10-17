import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart' hide Response, FormData;
import 'package:image_picker/image_picker.dart';

import '../../logic/model/feed/datum.dart';
import '../../logic/network/api_failure.dart';
import '../../logic/network/network_repo.dart';
import '../../logic/state/loading_state.dart';
import '../../pages/common/common_controller.dart';
import '../../utils/global_data.dart';
import '../../utils/storage_util.dart';
import 'chat_image_uploader.dart';

enum DeliveryState { sending, sent, failed }

class ChatDelivery {
  ChatDelivery({required this.data, this.image, required this.text});
  Datum data;
  final String text;
  final XFile? image;
  String? uploadedPicture;
  DeliveryState state = DeliveryState.sending;
  String? error;
  bool unconfirmed = false;
}

class ChatController extends CommonController {
  ChatController({required this.ukey});
  final String ukey;
  final editingController = TextEditingController();
  final deliveries = <ChatDelivery>[].obs;
  final _resolvingImages = <String>{};
  final _generation = GlobalData().sessionGeneration;
  bool get _active =>
      !isClosed && _generation == GlobalData().sessionGeneration;

  List<Datum> get messages {
    final local = deliveries.map((item) => item.data).toList();
    final ids = local.map((data) => data.id.toString()).toSet();
    final history = loadingState.value;
    return [
      ...local,
      if (history is Success)
        ...(history.response as List<Datum>)
            .where((data) => !ids.contains(data.id.toString())),
    ];
  }

  ChatDelivery? deliveryFor(Datum data) =>
      deliveries.firstWhereOrNull((item) => identical(item.data, data));

  @override
  Future<LoadingState> customGetData() =>
      NetworkRepo.messageOperation('/v6/message/chat',
          ukey: ukey, page: page, firstItem: firstItem, lastItem: lastItem);

  @override
  void onInit() {
    super.onInit();
    onGetData();
  }

  @override
  List<Datum>? handleResponse(List<Datum> dataList) =>
      dataList.reversed.toList();

  @override
  List<Datum>? handleUnique(List<Datum> dataList) {
    final seen = <String>{};
    return dataList
        .where((data) =>
            seen.add('${data.entityType}:${data.id ?? data.entityId}'))
        .toList();
  }

  Future<void> onGetImageUrl(dynamic id) async {
    if (!_active || !_resolvingImages.add(id.toString())) return;
    try {
      await NetworkRepo.getImageUrl(id: id);
    } on DioException catch (error) {
      final uri = Uri.tryParse(error.response?.headers.value('location') ?? '');
      if (_active &&
          error.response?.statusCode == 302 &&
          uri != null &&
          ['https', 'http'].contains(uri.scheme) &&
          uri.host.isNotEmpty) {
        for (final data in messages.where((data) => data.id == id)) {
          data.messagePic = uri.toString();
        }
        loadingState.refresh();
        deliveries.refresh();
      }
    }
  }

  Future<void> retryImage(dynamic id) async {
    _resolvingImages.remove(id.toString());
    await onGetImageUrl(id);
  }

  Future<void> onDeleteMsg(dynamic id) async {
    try {
      final response =
          await NetworkRepo.deleteMsg('/v6/message/delete', ukey: ukey, id: id);
      NetworkRepo.requireMessageMutation(response);
      if (!_active) return;
      deliveries.removeWhere((item) => item.data.id == id);
      if (loadingState.value case Success(:final response)) {
        loadingState.value = LoadingState.success(
            (response as List<Datum>).where((data) => data.id != id).toList());
      }
      SmartDialog.showToast('已删除消息');
    } catch (error) {
      if (_active) SmartDialog.showToast(ApiFailure.fromError(error).message);
    }
  }

  Future<void> onSendMessage(dynamic uid,
      {String? pic, XFile? image, String? message}) async {
    if (!_active) return;
    final text =
        message ?? (image == null && pic == null ? editingController.text : '');
    if (text.trim().isEmpty && pic == null && image == null) return;
    final delivery = ChatDelivery(
      data: Datum(
        id: 'local-${DateTime.now().microsecondsSinceEpoch}-${deliveries.length}',
        entityType: 'message',
        fromuid: int.tryParse(GlobalData().uid),
        fromUserAvatar: GStorage.userAvatar,
        message: const HtmlEscape().convert(text),
        messagePic: pic,
        dateline: DateTime.now().millisecondsSinceEpoch ~/ 1000,
      ),
      image: image,
      text: text,
    )..uploadedPicture = pic;
    deliveries.insert(0, delivery);
    if (message == null && image == null && pic == null) {
      editingController.clear();
    }
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_active && scrollController?.hasClients == true) {
        scrollController!.animateTo(0,
            duration: const Duration(milliseconds: 200), curve: Curves.easeOut);
      }
    });
    await _send(uid, delivery);
  }

  Future<void> retry(dynamic uid, ChatDelivery delivery) async {
    if (!_active || delivery.state != DeliveryState.failed) return;
    delivery.state = DeliveryState.sending;
    delivery.error = null;
    deliveries.refresh();
    await _send(uid, delivery);
  }

  void discard(ChatDelivery delivery) {
    if (delivery.state == DeliveryState.sending) return;
    deliveries.remove(delivery);
  }

  Future<void> _send(dynamic uid, ChatDelivery delivery) async {
    bool requestStarted = false;
    try {
      if (delivery.image != null && delivery.uploadedPicture == null) {
        delivery.uploadedPicture = await uploadChatImage(
            delivery.image!, uid.toString(),
            isActive: () => _active);
      }
      if (!_active) return;
      requestStarted = true;
      final response = await NetworkRepo.sendMessage(
          uid: uid,
          data: FormData.fromMap({
            'message': delivery.text,
            'message_pic': delivery.uploadedPicture ?? '',
          }));
      final body = readApiObject(response)['data'];
      final rows = body is List
          ? body
          : body is Map<String, dynamic>
              ? [body]
              : [];
      final sent = rows
          .whereType<Map<String, dynamic>>()
          .map(Datum.fromJson)
          .where((data) => data.entityType == 'message' && data.id != null)
          .toList();
      if (sent.isEmpty) {
        throw const ApiFailure(
            ApiFailureKind.invalidResponse, '服务端未返回消息记录，发送结果未确认');
      }
      if (!_active) return;
      final acknowledged = sent.first;
      acknowledged.message ??= delivery.data.message;
      acknowledged.messagePic ??= delivery.uploadedPicture;
      acknowledged.fromuid ??= delivery.data.fromuid;
      acknowledged.fromUserAvatar ??= delivery.data.fromUserAvatar;
      delivery.data = acknowledged;
      delivery.state = DeliveryState.sent;
    } catch (error) {
      if (!_active) return;
      final failure = ApiFailure.fromError(error);
      delivery.error = failure.message;
      delivery.unconfirmed = requestStarted &&
          [
            ApiFailureKind.transport,
            ApiFailureKind.http,
            ApiFailureKind.invalidResponse,
            ApiFailureKind.parsing
          ].contains(failure.kind);
      delivery.state = DeliveryState.failed;
    }
    if (_active) deliveries.refresh();
  }
}
