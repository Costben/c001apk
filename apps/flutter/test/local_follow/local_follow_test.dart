import 'dart:io';

import 'package:c001apk_flutter/logic/local_follow/local_follow_store.dart';
import 'package:c001apk_flutter/logic/local_follow/local_follow_pager.dart';
import 'package:c001apk_flutter/logic/model/feed/datum.dart';
import 'package:c001apk_flutter/logic/state/loading_state.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive/hive.dart';

Datum feed(int id, int uid, int time) =>
    Datum(id: id, entityId: id, entityType: 'feed', uid: uid, dateline: time);

void main() {
  test('local users persist independently of credentials and the switch',
      () async {
    final directory = await Directory.systemTemp.createTemp('local-follow-');
    Hive.init(directory.path);
    var box = await Hive.openBox('local-follow-test');
    var store = LocalFollowStore(box);
    await box.putAll({
      'token': 'synthetic',
      'history': [1, 2]
    });
    await store.follow('42', '测试用户', '');
    await store.follow('42', '更新昵称', '');
    await store.follow('43', '另一用户', '');
    await box.put(LocalFollowStore.enabledKey, false);
    await box.close();
    box = await Hive.openBox('local-follow-test');
    store = LocalFollowStore(box);
    expect(store.users.map((e) => e.uid), ['43', '42']);
    expect(store.users.last.name, '更新昵称');
    expect(store.contains('42'), isTrue);
    await store.unfollow('42');
    expect(store.contains('42'), isFalse);
    expect(box.get('token'), 'synthetic');
    expect(box.get('history'), [1, 2]);
    await Hive.close();
    await directory.delete(recursive: true);
  });

  test('merges user cursors by time, deduplicates, and exhausts independently',
      () async {
    final calls = <String>[];
    final pager =
        LocalFollowPager(['1', '2'], fetch: (uid, page, first, last) async {
      calls.add('$uid:$page:$last');
      if (uid == '1' && page == 1) {
        return Success([feed(11, 1, 100), feed(12, 1, 70)]);
      }
      if (uid == '1' && page == 2) {
        return Success([feed(12, 1, 70), feed(13, 1, 40)]);
      }
      if (uid == '2' && page == 1) {
        return Success([feed(21, 2, 90), feed(22, 2, 80)]);
      }
      return const Empty();
    });
    expect((await pager.next(limit: 3)).map((e) => e.id), [11, 21, 22]);
    expect((await pager.next(limit: 3)).map((e) => e.id), [12, 13]);
    expect(pager.isEnd, isTrue);
    expect(calls, contains('1:2:12'));
    expect(calls.where((c) => c.startsWith('2:2')).length, 1);
  });

  test('one failed user preserves other results and retries its same page',
      () async {
    var fail = true;
    final pages = <int>[];
    final pager =
        LocalFollowPager(['1', '2'], fetch: (uid, page, first, last) async {
      if (uid == '2') {
        pages.add(page);
        if (fail) return const Error('需要验证');
        if (page == 1) return Success([feed(21, 2, 90)]);
      }
      if (uid == '1' && page == 1) return Success([feed(11, 1, 100)]);
      return const Empty();
    });
    expect((await pager.next()).map((e) => e.id), [11]);
    expect(pager.warning, contains('需要验证'));
    expect(pager.isEnd, isFalse);
    fail = false;
    expect((await pager.next()).map((e) => e.id), [21]);
    expect(pages.take(2), [1, 1]);
  });

  test('bounded concurrent reads and no following means no requests', () async {
    var active = 0;
    var peak = 0;
    Future<LoadingState> fetch(
        String uid, int page, String? first, String? last) async {
      active++;
      if (active > peak) peak = active;
      await Future<void>.delayed(const Duration(milliseconds: 1));
      active--;
      return const Empty();
    }

    expect(await LocalFollowPager([], fetch: fetch).next(), isEmpty);
    await LocalFollowPager(List.generate(15, (i) => '$i'), fetch: fetch).next();
    expect(peak, lessThanOrEqualTo(4));
  });
}
