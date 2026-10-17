// ignore_for_file: non_constant_identifier_names

import 'dart:io';

import 'package:dio/dio.dart';
import 'package:get/get.dart' hide Response;
import "package:flutter/foundation.dart";

import '../../logic/network/network_repo.dart';
import '../../logic/accounts/account_store.dart';
import '../../logic/network/api_failure.dart';
import '../../logic/model/check_info/check.dart';
import '../../utils/extensions.dart';
import '../../utils/global_data.dart';

class MainController extends GetxController {
  Future<void> checkLoginInfo() async {
    if (!GlobalData().isLogin) return;
    final generation = GlobalData().sessionGeneration;
    final uid = GlobalData().uid;
    try {
      Response response = await NetworkRepo.checkLoginInfo();
      if (response.statusCode == HttpStatus.ok) {
        // A challenge or incomplete response must not erase a working session.
        if (response.data is Map && response.data['message'] == '登录信息有误') {
          await AccountStore().expireCurrent(generation: generation);
          return;
        }
        readApiObject(response);
        CheckInfo responseData = CheckInfo.fromJson(response.data);
        if (responseData.data != null) {
          final data = responseData.data!;
          if (data.uid == uid && !data.token.isNullOrEmpty) {
            await AccountStore().refreshCurrent(
                generation: generation,
                username: data.username.isNullOrEmpty
                    ? null
                    : Uri.encodeComponent(data.username!),
                token: data.token);
          }
        }
      } else {
        debugPrint('statusCode: ${response.statusCode}');
      }
      for (final cookie in response.headers['Set-Cookie'] ?? <String>[]) {
        final pair = cookie.split(';').first;
        if (generation == GlobalData().sessionGeneration &&
            pair.startsWith('SESSID=')) GlobalData().SESSID = pair;
      }
    } catch (e) {
      debugPrint('登录检查：${ApiFailure.fromError(e).message}');
    }
  }
}
