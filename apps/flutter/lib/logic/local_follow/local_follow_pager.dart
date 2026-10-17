import '../model/feed/datum.dart';
import '../state/loading_state.dart';

typedef UserFeedFetcher = Future<LoadingState> Function(
    String uid, int page, String? first, String? last);

class _UserCursor {
  _UserCursor(this.uid);
  final String uid;
  int page = 1;
  String? first;
  String? last;
  bool end = false;
  final buffer = <Datum>[];
  final seen = <String>{};
}

/// Merge public user timelines, retaining each user's independent pagination.
/// Reads run at most four at a time; failed cursors remain retryable.
class LocalFollowPager {
  LocalFollowPager(Iterable<String> uids, {required this.fetch})
      : _cursors = uids.toSet().map(_UserCursor.new).toList();
  final UserFeedFetcher fetch;
  final List<_UserCursor> _cursors;
  final _seen = <String>{};
  String? warning;
  bool get isEnd => _cursors.every((e) => e.end && e.buffer.isEmpty);

  Future<List<Datum>> next({int limit = 20}) async {
    final output = <Datum>[];
    final failures = <_UserCursor, String>{};
    final deferred = <_UserCursor>{};
    warning = null;
    while (output.length < limit) {
      final pending = _cursors
          .where((e) =>
              !e.end &&
              e.buffer.isEmpty &&
              !failures.containsKey(e) &&
              !deferred.contains(e))
          .toList();
      for (var start = 0; start < pending.length; start += 4) {
        await Future.wait(pending.skip(start).take(4).map((cursor) async {
          // Bounded work even when all pages contain blocked/duplicate cards.
          for (var attempt = 0; attempt < 3; attempt++) {
            LoadingState result;
            try {
              result = await fetch(
                  cursor.uid, cursor.page, cursor.first, cursor.last);
            } catch (_) {
              result = const Error('网络请求失败，请稍后重试');
            }
            if (result is Error) {
              failures[cursor] = result.errMsg;
              return;
            }
            if (result is Empty) {
              cursor.end = true;
              return;
            }
            if (result is! Success || result.response is! List<Datum>) {
              failures[cursor] = '动态响应异常，请稍后重试';
              return;
            }
            final items = result.response as List<Datum>;
            cursor.end = items.any((e) => e.entityTemplate == 'noMoreDataCard');
            final feeds = items
                .where((e) =>
                    e.entityType == 'feed' &&
                    e.id != null &&
                    e.uid.toString() == cursor.uid)
                .toList();
            cursor.first ??= feeds.firstOrNull?.id.toString();
            cursor.last = feeds.lastOrNull?.id.toString() ?? cursor.last;
            cursor.page++;
            cursor.buffer
                .addAll(feeds.where((e) => cursor.seen.add('${e.id}')));
            cursor.buffer.sort(_newestFirst);
            if (cursor.buffer.isNotEmpty || cursor.end) return;
          }
          deferred.add(cursor);
        }));
      }
      final available = _cursors.where((e) => e.buffer.isNotEmpty).toList()
        ..sort((a, b) => _newestFirst(a.buffer.first, b.buffer.first));
      if (available.isEmpty) break;
      final item = available.first.buffer.removeAt(0);
      if (_seen.add('${item.id}')) output.add(item);
    }
    if (failures.isNotEmpty) {
      warning = '${failures.length} 位用户的动态加载失败：${failures.values.first}';
    } else if (deferred.isNotEmpty) {
      warning = '部分动态已过滤，请继续加载';
    }
    return output;
  }

  static int _time(Datum item) => int.tryParse('${item.dateline}') ?? 0;
  static int _newestFirst(Datum a, Datum b) => _time(b).compareTo(_time(a));
}
