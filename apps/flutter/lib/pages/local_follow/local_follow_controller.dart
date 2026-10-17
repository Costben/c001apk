import 'dart:async';

import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../logic/local_follow/local_follow_pager.dart';
import '../../logic/local_follow/local_follow_store.dart';
import '../../logic/model/feed/datum.dart';
import '../../logic/network/network_repo.dart';
import '../../logic/state/loading_state.dart';
import '../../utils/storage_util.dart';
import '../common/common_controller.dart';
import '../home/return_top_controller.dart';

class LocalFollowController extends CommonController {
  LocalFollowController({UserFeedFetcher? fetch})
      : _fetch = fetch ?? _fetchUser;
  final UserFeedFetcher _fetch;
  LocalFollowPager? _pager;
  int _generation = 0;
  final warning = RxnString();
  StreamSubscription? _usersSubscription;
  StreamSubscription? _blockedSubscription;
  StreamSubscription<int>? _topSubscription;

  static Future<LoadingState> _fetchUser(
          String uid, int page, String? first, String? last) =>
      NetworkRepo.getUserFeed(
          uid: uid,
          page: page,
          firstItem: first,
          lastItem: last,
          includeTop: false);

  @override
  void onInit() {
    super.onInit();
    scrollController = ScrollController();
    refreshKey = GlobalKey<RefreshIndicatorState>();
    _usersSubscription = GStorage.settings
        .watch(key: LocalFollowStore.usersKey)
        .listen((_) => reload());
    _blockedSubscription = GStorage.blackList.watch().listen((_) => reload());
    if (Get.isRegistered<ReturnTopController>(tag: 'home')) {
      returnTopController = Get.find<ReturnTopController>(tag: 'home');
      _topSubscription = returnTopController!.index.listen((index) {
        if (index == 0) animateToTop();
      });
    }
    reload();
  }

  void reload() {
    loadingState.value = LoadingState.loading();
    onGetData();
  }

  @override
  void onReset() {
    super.onReset();
    _generation++;
    isLoading = false;
    warning.value = null;
    _pager = LocalFollowPager(
        LocalFollowStore()
            .users
            .where((user) => !GStorage.checkUser(user.uid))
            .map((user) => user.uid),
        fetch: _fetch);
  }

  @override
  Future<LoadingState> customGetData() async =>
      LoadingState.success(await _pager!.next());

  @override
  Future<void> onGetData([bool isRefresh = true]) async {
    if (isClosed) return;
    if (isRefresh) onReset();
    if (isLoading) return;
    isLoading = true;
    final generation = _generation;
    final pager = _pager!;
    try {
      final items = await pager.next();
      if (generation != _generation || isClosed) return;
      warning.value = pager.warning;
      isEnd = pager.isEnd;
      final previous = !isRefresh && loadingState.value is Success
          ? (loadingState.value as Success).response as List<Datum>
          : <Datum>[];
      final combined = [...previous, ...items];
      if (combined.isEmpty) {
        loadingState.value = pager.warning == null
            ? LoadingState.empty()
            : LoadingState.error(pager.warning!);
      } else {
        loadingState.value = LoadingState.success(combined);
      }
      footerState.value = pager.warning != null
          ? LoadingState.error(pager.warning!)
          : LoadingState.empty();
      // Avoid automatic repeated reads while a cursor requires manual retry.
      if (pager.warning != null) isEnd = true;
    } finally {
      if (generation == _generation) isLoading = false;
    }
  }

  @override
  void onClose() {
    _generation++;
    _usersSubscription?.cancel();
    _blockedSubscription?.cancel();
    _topSubscription?.cancel();
    scrollController?.dispose();
    super.onClose();
  }
}
