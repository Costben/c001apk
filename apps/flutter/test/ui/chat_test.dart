import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui' show instantiateImageCodec;

import 'package:c001apk_flutter/components/cards/message_card.dart';
import 'package:c001apk_flutter/components/cards/chat_card.dart';
import 'package:c001apk_flutter/logic/model/feed/datum.dart';
import 'package:c001apk_flutter/logic/network/request.dart';
import 'package:c001apk_flutter/pages/chat/chat_page.dart';
import 'package:c001apk_flutter/pages/noitfication/notification_page.dart';
import 'package:c001apk_flutter/pages/noitfication/notification_controller.dart';
import 'package:c001apk_flutter/logic/state/loading_state.dart';
import 'package:c001apk_flutter/utils/emoji_util.dart';
import 'package:c001apk_flutter/utils/global_data.dart';
import 'package:c001apk_flutter/utils/storage_util.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:get/get.dart' hide FormData, Response;
import 'package:hive/hive.dart';

class ChatAdapter implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  Completer<void>? sendGate;
  Completer<void>? mutationGate;
  Completer<void>? listGate;
  Completer<void>? feedGate;
  bool historyError = false;
  Object? sendResult = {
    'data': [
      {
        'id': 100,
        'entityId': 100,
        'entityType': 'message',
        'fromuid': 1,
        'message': '第一条',
        'dateline': 1760000000
      }
    ]
  };
  Object? mutationResult = {
    'data': {'count': '操作成功'}
  };
  bool failSend = false;
  final conversations = [
    {
      'id': 10,
      'entityId': 10,
      'entityType': 'message',
      'messageUid': 2,
      'messageUsername': '联系人甲',
      'message': '甲的消息',
      'ukey': 'chat-a',
      'is_top': 0,
      'dateline': 1760000000
    },
    {
      'id': 20,
      'entityId': 20,
      'entityType': 'message',
      'messageUid': 3,
      'messageUsername': '联系人乙',
      'message': '乙的消息',
      'ukey': 'chat-b',
      'is_top': 0,
      'dateline': 1750000000
    },
  ];
  @override
  Future<ResponseBody> fetch(RequestOptions options,
      Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    requests.add(options);
    Object? body = {'data': []};
    if (options.uri.path == '/v6/message/send') {
      await sendGate?.future;
      if (failSend) {
        throw DioException(
            requestOptions: options,
            type: DioExceptionType.receiveTimeout,
            message: 'synthetic-private-error');
      }
      body = sendResult;
    }
    if (options.uri.path == '/v6/message/list') {
      body = jsonDecode(jsonEncode(
          {'data': options.queryParameters['page'] == 1 ? conversations : []}));
      await listGate?.future;
    }
    if (options.uri.path == '/v6/message/chat' && historyError) {
      body = {'status': -10};
    }
    if (options.uri.path == '/v6/user/feedList') {
      if (options.queryParameters['page'] == 2) await feedGate?.future;
      final page = options.queryParameters['page'];
      body = {
        'data': [
          {
            'id': page,
            'entityId': page,
            'entityType': 'feed',
            'message': '第$page页动态'
          }
        ]
      };
    }
    if ([
      '/v6/message/addTop',
      '/v6/message/removeTop',
      '/v6/message/deleteChat'
    ].contains(options.uri.path)) {
      await mutationGate?.future;
      body = mutationResult;
    }
    if (options.uri.path == '/v6/upload/ossUploadPrepare') {
      body = {'status': -10, 'message': 'synthetic-private-upload-rejection'};
    }
    return ResponseBody.fromString(jsonEncode(body), 200, headers: {
      Headers.contentTypeHeader: ['application/json']
    });
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late Directory temp;
  late ChatAdapter adapter;
  setUpAll(() async {
    temp = await Directory.systemTemp.createTemp('c001apk-chat-');
    Hive.init(temp.path);
    GStorage.settings = await Hive.openBox('settings');
    GStorage.favFeed = await Hive.openBox('favorites');
    GStorage.blackList = await Hive.openBox('blacklist');
    await GStorage.settings.putAll({
      SettingsBoxKey.xAppDevice: 'synthetic-device',
      SettingsBoxKey.userAgent: 'SyntheticTest/1.0',
      SettingsBoxKey.versionName: '16.2.0',
      SettingsBoxKey.versionCode: '2604201',
      SettingsBoxKey.apiVersion: '16',
      SettingsBoxKey.sdkInt: '33',
    });
  });
  setUp(() {
    adapter = ChatAdapter();
    Request();
    Request.dio.httpClientAdapter = adapter;
    GlobalData().uid = '1';
    GlobalData().isLogin = true;
    GlobalData().token = 'synthetic-token';
  });
  tearDown(() {
    Get.reset();
    GlobalData().isLogin = false;
    GlobalData().token = '';
  });
  tearDownAll(() async {
    await Hive.close();
    await temp.delete(recursive: true);
  });
  Future<void> showChat(WidgetTester tester) async {
    await tester.pumpWidget(GetMaterialApp(
      builder: FlutterSmartDialog.init(),
      initialRoute: '/chat?ukey=synthetic-chat&uid=2&username=合成联系人',
      getPages: [GetPage(name: '/chat', page: () => const ChatPage())],
    ));
    await tester.pumpAndSettle();
  }

  Future<void> pumpTask(WidgetTester tester, Future<void> task) async {
    var done = false;
    task.then((_) => done = true);
    for (var i = 0; i < 100 && !done; i++) {
      await tester.pump(const Duration(milliseconds: 20));
    }
    expect(done, isTrue, reason: 'synthetic request should finish');
  }

  testWidgets('sending shows a bubble immediately and leaves input usable',
      (tester) async {
    adapter.sendGate = Completer<void>();
    await showChat(tester);
    await tester.enterText(find.byType(TextField), '第一条');
    await tester.pump();
    await tester.tap(find.byTooltip('发送'));
    await tester.pump(const Duration(milliseconds: 100));
    expect(find.byType(ChatCard), findsOneWidget);
    expect(
        find.byWidgetPredicate((w) =>
            w is CircularProgressIndicator && w.semanticsLabel == '消息发送中'),
        findsOneWidget);
    expect(find.text('正在发送'), findsNothing);
    expect(
        tester.widget<TextField>(find.byType(TextField)).controller!.text, '');
    await tester.enterText(find.byType(TextField), '正在写第二条');
    adapter.sendGate!.complete();
    await tester.pumpAndSettle();
    expect(tester.widget<TextField>(find.byType(TextField)).controller!.text,
        '正在写第二条');
    expect(find.text('第一条', findRichText: true), findsOneWidget);
    expect(adapter.requests.where((r) => r.uri.path == '/v6/message/send'),
        hasLength(1));
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('empty success keeps failed bubble for an explicit retry',
      (tester) async {
    adapter.sendResult = {'data': []};
    await showChat(tester);
    await tester.enterText(find.byType(TextField), '第一条');
    await tester.pump();
    await tester.tap(find.byTooltip('发送'));
    await tester.pumpAndSettle();
    expect(find.text('第一条', findRichText: true), findsOneWidget);
    expect(find.byTooltip('发送失败，点击查看'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('attachments remain available while writing text',
      (tester) async {
    await showChat(tester);
    await tester.enterText(find.byType(TextField), '草稿');
    await tester.pump();
    expect(find.byTooltip('图片'), findsOneWidget);
    expect(find.byTooltip('动态'), findsOneWidget);
    expect(find.byTooltip('发送'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('niuniu picker inserts at an initially invalid cursor',
      (tester) async {
    await showChat(tester);
    await tester.tap(find.byTooltip('表情'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('牛牛'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('[牛牛点赞]'));
    await tester.pumpAndSettle();
    expect(tester.widget<TextField>(find.byType(TextField)).controller!.text,
        '[牛牛点赞]');
    expect(find.byTooltip('发送'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('received shared content opens its actual feed route',
      (tester) async {
    await tester.pumpWidget(GetMaterialApp(
        builder: FlutterSmartDialog.init(),
        getPages: [
          GetPage(
              name: '/feed/:id',
              page: () => Scaffold(body: Text('已打开动态${Get.parameters['id']}')))
        ],
        home: Scaffold(
            body: ChatCard(
                data: Datum.fromJson({
                  'id': 7,
                  'entityType': 'message',
                  'fromuid': 2,
                  'extra_title': '合成分享标题',
                  'extra_url': '/feed/123'
                }),
                isLeft: true,
                onGetImageUrl: () {},
                onLongPress: () {},
                onViewImage: () {}))));
    await tester.pumpAndSettle();
    await tester.tap(find.text('合成分享标题'));
    await tester.pumpAndSettle();
    expect(find.text('已打开动态123'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('pending bubbles retain a history error retry control',
      (tester) async {
    adapter.historyError = true;
    await showChat(tester);
    await tester.enterText(find.byType(TextField), '第一条');
    await tester.pump();
    await tester.tap(find.byTooltip('发送'));
    await tester.pumpAndSettle();
    expect(find.text('重新加载聊天记录'), findsOneWidget);
    adapter.historyError = false;
    await tester.tap(find.text('重新加载聊天记录'));
    await tester.pumpAndSettle();
    expect(find.byType(ChatCard), findsOneWidget);
    expect(adapter.requests.where((r) => r.uri.path == '/v6/message/chat'),
        hasLength(2));
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('feed pagination cannot skip a waiting page', (tester) async {
    adapter.feedGate = Completer<void>();
    await showChat(tester);
    await tester.tap(find.byTooltip('动态'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('加载更多'));
    await tester.pump(const Duration(milliseconds: 50));
    expect(find.text('加载更多'), findsNothing);
    expect(
        adapter.requests
            .where((r) => r.uri.path == '/v6/user/feedList')
            .map((r) => r.queryParameters['page']),
        [1, 2]);
    adapter.feedGate!.complete();
    await tester.pumpAndSettle();
    expect(find.text('第2页动态'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('conversation acknowledgements survive overlapping refreshes',
      (tester) async {
    await tester.pumpWidget(GetMaterialApp(
        builder: FlutterSmartDialog.init(),
        home: Builder(
            builder: (context) => Scaffold(
                body: TextButton(
                    child: const Text('打开私信'),
                    onPressed: () => Get.to(() => const NotificationPage(),
                        arguments: {'type': NotificationType.MESSAGE}))))));
    await tester.pumpAndSettle();
    await tester.tap(find.text('打开私信'));
    await tester.pumpAndSettle();
    final controller = Get.find<NotificationController>(tag: 'MESSAGE');
    final row =
        tester.widgetList<MessageCard>(find.byType(MessageCard)).last.data;
    adapter.mutationGate = Completer<void>();
    final pin = controller.updateConversation(row, delete: false);
    await tester.pump();
    adapter.conversations.last['is_top'] = 1;
    controller.onReset();
    final refreshed = controller.onGetData();
    await tester.pump(const Duration(milliseconds: 50));
    await pumpTask(tester, refreshed);
    adapter.mutationGate!.complete();
    await pumpTask(tester, pin);
    await tester.pumpAndSettle();
    expect(
        (controller.loadingState.value as Success)
            .response
            .where((dynamic item) => item.ukey == 'chat-b')
            .single
            .isTop,
        1);
    adapter.mutationGate = null;
    adapter.listGate = Completer<void>();
    controller.onReset();
    final refresh = controller.onGetData();
    await tester.pump();
    final deleted = controller.updateConversation(row, delete: true);
    await pumpTask(tester, deleted);
    adapter.listGate!.complete();
    await pumpTask(tester, refresh);
    await tester.pumpAndSettle();
    expect(find.text('联系人乙'), findsNothing);
    await tester.pump(const Duration(seconds: 6));
    await tester.pumpAndSettle();
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
      'ambiguous send fails inline without replaying or erasing a draft',
      (tester) async {
    adapter.failSend = true;
    await showChat(tester);
    await tester.enterText(find.byType(TextField), '第一条');
    await tester.pump();
    await tester.tap(find.byTooltip('发送'));
    await tester.pumpAndSettle();
    expect(find.byType(ChatCard), findsOneWidget);
    await tester.enterText(find.byType(TextField), '第二条草稿');
    await tester.tap(find.byTooltip('发送失败，点击查看'));
    await tester.pumpAndSettle();
    expect(find.textContaining('可能产生重复消息'), findsOneWidget);
    expect(find.textContaining('synthetic-private'), findsNothing);
    await tester.tap(find.text('取消'));
    await tester.pumpAndSettle();
    expect(adapter.requests.where((r) => r.uri.path == '/v6/message/send'),
        hasLength(1));
    expect(tester.widget<TextField>(find.byType(TextField)).controller!.text,
        '第二条草稿');
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
      'server rejection is retained and a manual retry replaces one bubble',
      (tester) async {
    adapter.sendResult = {
      'status': -10,
      'message': 'synthetic-private-refusal'
    };
    await showChat(tester);
    await tester.enterText(find.byType(TextField), '第一条');
    await tester.pump();
    await tester.tap(find.byTooltip('发送'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('发送失败，点击查看'));
    await tester.pumpAndSettle();
    expect(find.text('消息发送失败'), findsOneWidget);
    expect(find.textContaining('synthetic-private'), findsNothing);
    adapter.sendResult = {
      'data': [
        {
          'id': 100,
          'entityId': 100,
          'entityType': 'message',
          'fromuid': 1,
          'message': '第一条'
        }
      ]
    };
    await tester.tap(find.text('重新发送'));
    await tester.pumpAndSettle();
    expect(find.byType(ChatCard), findsOneWidget);
    expect(find.byTooltip('发送失败，点击查看'), findsNothing);
    expect(adapter.requests.where((r) => r.uri.path == '/v6/message/send'),
        hasLength(2));
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('account changes discard late send completion', (tester) async {
    adapter.sendGate = Completer<void>();
    await showChat(tester);
    await tester.enterText(find.byType(TextField), '第一条');
    await tester.pump();
    await tester.tap(find.byTooltip('发送'));
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pumpWidget(const SizedBox.shrink());
    GlobalData().sessionGeneration++;
    adapter.sendGate!.complete();
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(adapter.requests.where((r) => r.uri.path == '/v6/message/send'),
        hasLength(1));
  });

  testWidgets(
      'image preparation happens inline for the target and preserves text draft',
      (tester) async {
    final path = '${temp.path}/synthetic.png';
    File(path).writeAsBytesSync(
        (await rootBundle.load('assets/emojis/coolapk_emotion_37_doge.png'))
            .buffer
            .asUint8List());
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(
        const MethodChannel('plugins.flutter.io/image_picker'),
        (call) async => path);
    await showChat(tester);
    await tester.enterText(find.byType(TextField), '保留的文字草稿');
    await tester.pump();
    await tester.tap(find.byTooltip('图片'));
    for (var i = 0; i < 30; i++) {
      await tester.runAsync(
          () => Future<void>.delayed(const Duration(milliseconds: 10)));
      await tester.pump(const Duration(milliseconds: 50));
      if (find.byTooltip('发送失败，点击查看').evaluate().isNotEmpty) break;
    }
    expect(find.byType(ChatCard), findsOneWidget);
    expect(find.byTooltip('发送失败，点击查看'), findsOneWidget);
    final request = adapter.requests
        .singleWhere((r) => r.uri.path == '/v6/upload/ossUploadPrepare');
    final fields = Map.fromEntries((request.data as FormData).fields);
    expect(fields['toUid'], '2');
    expect(fields['uploadBucket'], 'message');
    expect(jsonDecode(fields['uploadFileList']!).single['resolution'], '96x96');
    expect(adapter.requests.where((r) => r.uri.path == '/v6/message/send'),
        isEmpty);
    expect(tester.widget<TextField>(find.byType(TextField)).controller!.text,
        '保留的文字草稿');
    await tester.tap(find.byType(Image).last);
    await tester.pumpAndSettle();
    expect(find.byType(InteractiveViewer), findsOneWidget);
    await tester.tap(find.text('关闭'));
    await tester.pumpAndSettle();
    await tester.pumpWidget(const SizedBox.shrink());
    messenger.setMockMethodCallHandler(
        const MethodChannel('plugins.flutter.io/image_picker'), null);
  });

  testWidgets(
      'share validates links, removes tracking, and confirms before sending',
      (tester) async {
    await showChat(tester);
    await tester.tap(find.byTooltip('动态'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('链接'));
    await tester.pumpAndSettle();
    await tester.enterText(find.widgetWithText(TextField, '动态链接或编号'),
        'https://evil.example/feed/123');
    await tester.tap(find.text('下一步'));
    await tester.pumpAndSettle();
    expect(find.text('请输入有效的酷安动态链接或编号'), findsOneWidget);
    await tester.enterText(find.widgetWithText(TextField, '动态链接或编号'),
        'https://www.coolapk.com/feed/123?s=tracking#comments');
    await tester.tap(find.text('下一步'));
    await tester.pumpAndSettle();
    expect(adapter.requests.where((r) => r.uri.path == '/v6/message/send'),
        isEmpty);
    await tester.tap(find.widgetWithText(FilledButton, '发送'));
    await tester.pumpAndSettle();
    final request =
        adapter.requests.singleWhere((r) => r.uri.path == '/v6/message/send');
    expect(request.method, 'POST');
    expect(request.queryParameters, {'uid': '2'});
    expect(Map.fromEntries((request.data as FormData).fields),
        {'message': 'https://www.coolapk.com/feed/123', 'message_pic': ''});
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
      'conversation menu pins remotely and deletion requires confirmation',
      (tester) async {
    await tester.pumpWidget(GetMaterialApp(
        builder: FlutterSmartDialog.init(),
        home: Builder(
            builder: (context) => Scaffold(
                body: TextButton(
                    child: const Text('打开私信'),
                    onPressed: () => Get.to(() => const NotificationPage(),
                        arguments: {'type': NotificationType.MESSAGE}))))));
    await tester.pumpAndSettle();
    await tester.tap(find.text('打开私信'));
    await tester.pumpAndSettle();
    expect(find.text('联系人乙'), findsOneWidget,
        reason: adapter.requests.map((r) => r.uri.path).join(','));
    await tester.longPress(find.text('联系人乙'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('置顶会话'));
    await tester.pumpAndSettle();
    await tester.pump(const Duration(seconds: 3));
    await tester.pumpAndSettle();
    final cards =
        tester.widgetList<MessageCard>(find.byType(MessageCard)).toList();
    expect(cards.first.data.messageUsername, '联系人乙');
    expect(cards.first.data.isTop, 1);
    final pin =
        adapter.requests.singleWhere((r) => r.uri.path == '/v6/message/addTop');
    expect(pin.method, 'GET');
    expect(pin.queryParameters, {'ukey': 'chat-b'});
    await tester.longPress(find.text('联系人乙'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('删除会话'));
    await tester.pumpAndSettle();
    expect(
        adapter.requests.where((r) => r.uri.path == '/v6/message/deleteChat'),
        isEmpty);
    await tester.tap(find.text('取消'));
    await tester.pumpAndSettle();
    expect(find.text('联系人乙'), findsOneWidget);
    await tester.longPress(find.text('联系人乙'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('删除会话'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('删除'));
    await tester.pumpAndSettle();
    expect(find.text('联系人乙'), findsNothing);
    expect(
        adapter.requests
            .singleWhere((r) => r.uri.path == '/v6/message/deleteChat')
            .queryParameters,
        {'ukey': 'chat-b'});
    await tester.pump(const Duration(seconds: 3));
    await tester.pumpAndSettle();
    await tester.pumpWidget(const SizedBox.shrink());
  });

  test('all current niuniu emoji have decodable bundled images', () async {
    const names = [
      '阿巴',
      '财奴',
      '呲牙笑',
      '大喊',
      '戴耳机',
      '点赞',
      '恶心',
      '尴尬',
      '鬼脸',
      '害怕',
      '害羞',
      '挥手',
      '娇羞',
      '惊讶',
      '冷汗',
      '墨镜',
      '疲惫',
      '求饶',
      '认真脸',
      '伤心',
      '生病',
      '生气',
      '思考',
      '微笑',
      '委屈',
      '问号脸',
      '喜欢',
      '星星眼',
      '疑惑',
      '眨眼',
      '真香',
      '爆赞'
    ];
    for (final name in names) {
      final asset = EmojiUtil.emojiMap['[牛牛$name]'];
      expect(asset, isNotNull, reason: name);
      final image = await rootBundle.load('assets/emojis/$asset');
      final codec = await instantiateImageCodec(image.buffer.asUint8List());
      final frame = await codec.getNextFrame();
      expect(frame.image.width, 96, reason: name);
      frame.image.dispose();
      codec.dispose();
    }
  });
}
