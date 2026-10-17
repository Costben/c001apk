import 'dart:async';
import 'dart:io';

import 'package:c001apk_flutter/logic/local_follow/local_follow_store.dart';
import 'package:c001apk_flutter/logic/model/feed/datum.dart';
import 'package:c001apk_flutter/logic/state/loading_state.dart';
import 'package:c001apk_flutter/pages/local_follow/local_follow_controller.dart';
import 'package:c001apk_flutter/utils/storage_util.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive/hive.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late Directory directory;
  setUpAll(() async {
    directory =
        await Directory.systemTemp.createTemp('local-follow-controller-');
    Hive.init(directory.path);
    GStorage.settings = await Hive.openBox('settings');
    GStorage.blackList = await Hive.openBox('blackList');
    await LocalFollowStore().follow('1', '合成用户', '');
  });
  tearDownAll(() async {
    await Hive.close();
    await directory.delete(recursive: true);
  });

  test('refresh after an empty timeline reads new posts', () async {
    var published = false;
    final controller =
        LocalFollowController(fetch: (uid, page, first, last) async {
      if (published && page == 1) {
        return Success(<Datum>[
          Datum(id: 10, uid: 1, entityType: 'feed', dateline: 100),
        ]);
      }
      return const Empty();
    });
    controller.onReset();
    await controller.onGetData();
    expect(controller.loadingState.value, isA<Empty>());
    published = true;
    // Same entry point as the common empty-state retry, no external reset.
    await controller.onGetData(true);
    expect(controller.loadingState.value, isA<Success>());
    expect((controller.loadingState.value as Success).response.single.id, 10);
    controller.onClose();
  });

  test('a response from before refresh cannot replace the refreshed result',
      () async {
    final delayed = Completer<LoadingState>();
    var firstRequest = true;
    final controller =
        LocalFollowController(fetch: (uid, page, first, last) async {
      if (firstRequest) {
        firstRequest = false;
        return delayed.future;
      }
      return const Empty();
    });
    controller.onReset();
    final old = controller.onGetData();
    controller.onReset();
    await controller.onGetData();
    delayed.complete(Success(<Datum>[
      Datum(id: 10, uid: 1, entityType: 'feed', dateline: 100),
    ]));
    await old;
    expect(controller.loadingState.value, isA<Empty>());
    controller.onClose();
  });
}
