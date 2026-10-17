import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../components/footer.dart';
import '../components/item_card.dart';
import '../logic/model/feed/datum.dart';
import '../logic/state/loading_state.dart';
import '../pages/common/common_controller.dart';

Widget _bodyState(String text, Function() onTap) {
  return GestureDetector(
    onTap: onTap,
    child: Container(
      alignment: Alignment.center,
      padding: const EdgeInsets.all(10.0),
      child: Text(text),
    ),
  );
}

Widget commonBody(
  CommonController commonController, {
  Widget? leading,
  Widget Function(Datum)? itemBuilder,
  bool isHomeCard = false,
  bool isReply2Reply = false,
  dynamic uid,
}) {
  return Obx(() {
    final body = buildBody(
      commonController,
      leading: leading,
      itemBuilder: itemBuilder,
      isHomeCard: isHomeCard,
      isReply2Reply: isReply2Reply,
      uid: uid,
    );
    if (commonController.loadingState.value is Success) {
      return RefreshIndicator(
        key: commonController.refreshKey,
        onRefresh: () async {
          commonController.onReset();
          await commonController.onGetData();
        },
        child: body,
      );
    }
    if (leading == null) return Center(child: body);
    // Keep system messages reachable even if private messages fail or are empty.
    return Column(children: [
      Padding(padding: const EdgeInsets.all(10), child: leading),
      Expanded(child: Center(child: body)),
    ]);
  });
}

Widget buildBody(
  CommonController commonController, {
  Widget? leading,
  Widget Function(Datum)? itemBuilder,
  bool isHomeCard = false,
  bool isReply2Reply = false,
  dynamic uid,
  Function(
    dynamic id,
    dynamic uname,
    dynamic fid,
  )? onReply,
}) {
  switch (commonController.loadingState.value) {
    case Empty():
      return _bodyState(
        '暂无内容',
        () {
          commonController.isEnd = false;
          commonController.setLoadingState(LoadingState.loading());
          commonController.onGetData(true);
        },
      );
    case Error():
      return _bodyState(
        (commonController.loadingState.value as Error).errMsg,
        () {
          commonController.isEnd = false;
          commonController.setLoadingState(LoadingState.loading());
          commonController.onGetData(true);
        },
      );
    case Success():
      var dataList = (commonController.loadingState.value as Success).response
          as List<Datum>;
      return ListView.separated(
        controller: commonController.scrollController,
        physics: AlwaysScrollableScrollPhysics(
          parent: isReply2Reply
              ? const ClampingScrollPhysics()
              : const BouncingScrollPhysics(),
        ),
        padding: EdgeInsets.only(
            left: isReply2Reply ? 0 : 10,
            top: isReply2Reply ? 0 : 10,
            right: isReply2Reply ? 0 : 10,
            bottom: (isReply2Reply ? 0 : 10) +
                MediaQuery.of(Get.context!).padding.bottom),
        itemCount: dataList.length + 1 + (leading == null ? 0 : 1),
        itemBuilder: (_, index) {
          if (leading != null) {
            if (index == 0) return leading;
            index--;
          }
          if (index == dataList.length) {
            if (!commonController.isEnd && !commonController.isLoading) {
              commonController.setFooterState(LoadingState.loading());
              commonController.onGetData(false);
            }
            return Obx(
                () => footerWidget(commonController.footerState.value, () {
                      commonController.isEnd = false;
                      commonController.setFooterState(LoadingState.loading());
                      commonController.onGetData(false);
                    }));
          } else {
            if (itemBuilder != null) return itemBuilder(dataList[index]);
            return itemCard(
              dataList[index],
              isHomeCard: isHomeCard,
              isReply2Reply: isReply2Reply,
              isTopReply: isReply2Reply && index == 0,
              uid: uid,
              onBlock: commonController.onBlock,
              onReply: onReply,
              onDelete: commonController.postLikeDeleteFollow,
              onDeleteNoti: (id) => commonController.postLikeDeleteFollow(
                id,
                null,
                isNoti: true,
              ),
              onLike: commonController.onLike,
              onPM: commonController.onPM,
            );
          }
        },
        separatorBuilder: (_, index) => isReply2Reply
            ? const Divider(height: 1)
            : const SizedBox(height: 10),
      );
  }
  return Container(
    height: 80,
    alignment: Alignment.center,
    padding: const EdgeInsets.all(10.0),
    child: const CircularProgressIndicator(),
  );
}
