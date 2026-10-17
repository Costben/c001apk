import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart' hide Response;

import '../../logic/model/check_count/check_count.dart';
import '../../logic/accounts/account_store.dart';
import '../../logic/model/check_count/datum.dart' as checkcountdata;
import '../../logic/model/feed/data_model.dart';
import '../../logic/model/feed/datum.dart';
import '../../logic/network/network_repo.dart';
import '../../logic/network/api_failure.dart';
import '../../utils/global_data.dart';

class MessageController extends GetxController {
  final Rx<Datum?> userInfo = Datum().obs;
  final RxList<int?> firstList = <int?>[].obs;
  final RxList<int?> thirdList = <int?>[].obs;
  final notificationCount = Rxn<int>();

  Future<void> checkCount() async {
    final generation = GlobalData().sessionGeneration;
    try {
      Response response = await NetworkRepo.checkCount();
      if (isClosed || generation != GlobalData().sessionGeneration) return;
      checkcountdata.Datum? data =
          CheckCount.fromJson(readApiObject(response)).data;
      if (data == null) return;
      notificationCount.value = data.notification;
      thirdList.value = [
        data.atme,
        data.atcommentme,
        data.feedlike,
        data.contactsFollow,
        if (data.message != null || data.notification != null)
          (data.message ?? 0) + (data.notification ?? 0)
        else
          null,
      ];
    } catch (e) {
      debugPrint('消息计数：${ApiFailure.fromError(e).message}');
    }
  }

  Future<void> getProfile() async {
    final generation = GlobalData().sessionGeneration;
    final uid = GlobalData().uid;
    try {
      Response response = await NetworkRepo.getProfile(GlobalData().uid);
      if (isClosed || generation != GlobalData().sessionGeneration) return;
      Datum? data = DataModel.fromJson(readApiObject(response)).data;
      if (data == null) return;
      if (data.uid.toString() != uid) return;
      String username = data.username ?? '';
      try {
        username = Uri.encodeComponent(username);
      } catch (e) {
        debugPrint(e.toString());
      }
      await AccountStore().refreshCurrent(
          generation: generation,
          avatar: data.userAvatar,
          username: username.isEmpty ? null : username,
          level: data.level,
          exp: data.experience,
          nextExp: data.nextLevelExperience);
      if (isClosed || generation != GlobalData().sessionGeneration) return;
      userInfo.value = data;
      firstList.value = [data.feed, data.follow, data.fans];
    } catch (e) {
      debugPrint('个人资料：${ApiFailure.fromError(e).message}');
    }
  }
}
