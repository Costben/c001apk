import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart';

import '../../logic/model/feed/datum.dart';
import '../../logic/network/api_failure.dart';
import '../../logic/network/network_repo.dart';
import '../../logic/state/loading_state.dart';
import '../../pages/common/common_controller.dart';
import '../../utils/global_data.dart';

class NotificationController extends CommonController {
  NotificationController({
    required this.url,
  });

  final String url;
  final busyConversations = <String>{}.obs;
  final _conversationUpdates = <String, bool?>{};

  Future<void> updateConversation(Datum conversation,
      {required bool delete}) async {
    final key = conversation.ukey;
    if (key == null || key.isEmpty || !busyConversations.add(key)) return;
    final generation = GlobalData().sessionGeneration;
    final shouldPin = conversation.isTop != 1;
    try {
      final response = await NetworkRepo.deleteMsg(
          delete
              ? '/v6/message/deleteChat'
              : '/v6/message/${shouldPin ? 'addTop' : 'removeTop'}',
          ukey: key);
      NetworkRepo.requireMessageMutation(response);
      if (isClosed || generation != GlobalData().sessionGeneration) return;
      if (isLoading) _conversationUpdates[key] = delete ? null : shouldPin;
      if (loadingState.value case Success(:final response)) {
        loadingState.value = LoadingState.success(_applyConversationUpdates(
            List<Datum>.from(response), {key: delete ? null : shouldPin}));
      }
      SmartDialog.showToast(delete
          ? '已删除会话'
          : shouldPin
              ? '已置顶会话'
              : '已取消置顶');
    } catch (error) {
      if (!isClosed && generation == GlobalData().sessionGeneration) {
        SmartDialog.showToast(ApiFailure.fromError(error).message);
      }
    } finally {
      busyConversations.remove(key);
    }
  }

  @override
  List<Datum>? handleUnique(List<Datum> dataList) {
    if (url != '/v6/message/list') return null;
    final result = _applyConversationUpdates(dataList);
    _conversationUpdates.clear();
    return result;
  }

  List<Datum> _applyConversationUpdates(List<Datum> list,
      [Map<String, bool?>? updates]) {
    final changes = updates ?? _conversationUpdates;
    final seen = <String>{};
    final result = list.where((item) {
      final key = item.ukey;
      if (key != null && changes.containsKey(key)) {
        final pinned = changes[key];
        if (pinned == null) return false;
        item.isTop = pinned ? 1 : 0;
      }
      return key == null || seen.add(key);
    }).toList();
    result.sort((a, b) {
      final pin = (b.isTop == 1 ? 1 : 0).compareTo(a.isTop == 1 ? 1 : 0);
      return pin != 0
          ? pin
          : (int.tryParse('${b.dateline}') ?? 0)
              .compareTo(int.tryParse('${a.dateline}') ?? 0);
    });
    return result;
  }

  @override
  Future<LoadingState> customGetData() {
    _conversationUpdates.clear();
    return NetworkRepo.getDataListFromUrl(
      url: url,
      data: {
        'page': page,
        if (firstItem != null) 'firstItem': firstItem,
        if (lastItem != null) 'lastItem': lastItem,
      },
    );
  }

  @override
  void onInit() {
    super.onInit();
    onGetData();
  }
}
