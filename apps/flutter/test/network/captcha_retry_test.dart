import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:c001apk_flutter/logic/network/request.dart';
import 'package:c001apk_flutter/logic/network/interceptor.dart';
import 'package:c001apk_flutter/logic/network/captcha_challenge.dart';
import 'package:c001apk_flutter/utils/global_data.dart';
import 'package:c001apk_flutter/utils/storage_util.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive/hive.dart';

class CaptchaAdapter implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  final bodies = <String>[];
  bool alwaysChallenge = false;
  void Function()? beforeResponse;
  dynamic extra = jsonEncode({
    'captchaId': '0123456789abcdef0123456789abcdef',
    'captchaField': '_custom_token'
  });
  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? stream,
      Future<void>? cancelFuture) async {
    requests.add(options);
    bodies.add(stream == null
        ? ''
        : utf8.decode(await stream.expand((x) => x).toList()));
    beforeResponse?.call();
    return ResponseBody.fromString(
        jsonEncode(requests.length == 1 || alwaysChallenge
            ? {
                'code': 403,
                'messageStatus': 'err_request_captcha_v2',
                'messageExtra': extra,
                'data': null
              }
            : {
                'data': {'id': 123}
              }),
        200,
        headers: {
          Headers.contentTypeHeader: ['application/json']
        });
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  late Directory temp;
  late CaptchaAdapter adapter;
  setUpAll(() async {
    temp = await Directory.systemTemp.createTemp('c001apk-captcha-test-');
    Hive.init(temp.path);
    GStorage.settings = await Hive.openBox('settings');
    await GStorage.settings.putAll({
      SettingsBoxKey.xAppDevice: 'synthetic-device',
      SettingsBoxKey.versionCode: '2604201',
      SettingsBoxKey.versionName: '16.2.0',
      SettingsBoxKey.apiVersion: '16',
      SettingsBoxKey.sdkInt: '33',
      SettingsBoxKey.userAgent: 'SyntheticTest/1.0',
    });
    Request();
    Request.dio.interceptors.clear();
  });
  setUp(() {
    ApiInterceptor.clearCaptchaCookie();
    adapter = CaptchaAdapter();
    Request.dio.httpClientAdapter = adapter;
    Request.dio.interceptors
      ..clear()
      ..add(ApiInterceptor());
  });
  tearDown(() {
    (Request() as dynamic).captchaVerifier = null;
    ApiInterceptor.clearCaptchaCookie();
    GlobalData().isLogin = false;
    GlobalData().uid = '';
    GlobalData().token = '';
  });
  tearDownAll(() async {
    await Hive.close();
    await temp.delete(recursive: true);
  });
  test('verified GET retains original query and uses server challenge field',
      () async {
    (Request() as dynamic).captchaVerifier = (dynamic c) async {
      expect(c.captchaId, '0123456789abcdef0123456789abcdef');
      return 'NEC:01234567:synthetic-proof';
    };
    final response = await Request().get(
        'https://api.coolapk.com/v6/feed/detail',
        queryParameters: {'id': '123'});
    expect(response.data['data']['id'], 123);
    expect(adapter.requests.last.queryParameters,
        {'id': '123', '_custom_token': 'NEC:01234567:synthetic-proof'});
    expect(adapter.requests, hasLength(2));
  });
  test('blank captchaField preserves the cookie verification protocol', () {
    final challenge = CaptchaChallenge.fromBody({
      'messageStatus': 'err_request_captcha_v2',
      'messageExtra': {
        'captchaType': 'NEC',
        'captchaId': '0123456789abcdef0123456789abcdef',
        'captchaField': ''
      },
    })!;
    expect(challenge.field, isEmpty);
  });
  test(
      'comment verification sends encoded validate cookie and reuses it for pagination',
      () async {
    adapter.extra = {
      'captchaType': 'NEC',
      'captchaId': '0123456789abcdef0123456789abcdef',
      'captchaField': ''
    };
    Request().captchaVerifier = (c) async => c.proof('synthetic+proof/value');
    GlobalData().isLogin = true;
    GlobalData().uid = 'synthetic-uid';
    GlobalData().token = 'synthetic-account-token';
    await Request().get('https://api2.coolapk.com/v6/feed/replyList',
        queryParameters: {'id': '123', 'page': 1});
    final retried = adapter.requests.last;
    expect(retried.queryParameters, {'id': '123', 'page': 1});
    expect(retried.headers['Cookie'], contains('uid=synthetic-uid'));
    expect(retried.headers['Cookie'],
        contains('validate=NEC%3A01234567%3Asynthetic%2Bproof%2Fvalue'));
    await Request().get('https://api.coolapk.com/v6/feed/replyList',
        queryParameters: {'id': '123', 'page': 2});
    expect(adapter.requests.last.headers['Cookie'], contains('validate='));
    await Request().get('https://www.coolapk.com/feed/123');
    expect(adapter.requests.last.headers, isNot(contains('Cookie')));
    await Request().get('https://example.invalid/public');
    expect(adapter.requests.last.headers, isNot(contains('Cookie')));
    GlobalData().sessionGeneration++;
    await Request().get('https://api.coolapk.com/v6/feed/replyList');
    expect(
        adapter.requests.last.headers['Cookie'], isNot(contains('validate=')));
  });
  test('verified multipart POST clones form and retains method and payload',
      () async {
    (Request() as dynamic).captchaVerifier =
        (dynamic c) async => 'NEC:01234567:synthetic-proof';
    final form = FormData.fromMap({'message': '合成评论', 'replyAndForward': 0});
    final response = await Request().post(
        'https://api.coolapk.com/v6/feed/reply',
        queryParameters: {'id': '123', 'type': 'feed'},
        data: form);
    expect(response.data['data']['id'], 123);
    expect(adapter.requests.map((r) => r.method), ['POST', 'POST']);
    expect(
        adapter.requests.last.queryParameters, {'id': '123', 'type': 'feed'});
    expect(adapter.bodies.last, contains('合成评论'));
    expect(adapter.bodies.last, contains('name="_custom_token"'));
    expect(adapter.bodies.last, contains('NEC:01234567:synthetic-proof'));
    expect(form.fields.where((f) => f.key == '_custom_token'), isEmpty);
  });
  test('blank-field POST uses the cookie without adding proof to its body',
      () async {
    adapter.extra = {
      'captchaId': '0123456789abcdef0123456789abcdef',
      'captchaField': ''
    };
    Request().captchaVerifier = (c) async => c.proof('synthetic-proof');
    await Request().post('https://api.coolapk.com/v6/feed/reply',
        data: {'message': '合成评论'},
        queryParameters: {'id': '123', 'type': 'feed'},
        options: Options(contentType: Headers.formUrlEncodedContentType));
    expect(
        adapter.requests.last.queryParameters, {'id': '123', 'type': 'feed'});
    expect(Uri.splitQueryString(adapter.bodies.last), {'message': '合成评论'});
    expect(adapter.requests.last.headers['Cookie'], contains('validate='));
  });
  test('cookie is discarded after a repeated challenge and never loops',
      () async {
    adapter.extra = {
      'captchaId': '0123456789abcdef0123456789abcdef',
      'captchaField': ''
    };
    adapter.alwaysChallenge = true;
    var prompts = 0;
    Request().captchaVerifier = (c) async {
      prompts++;
      return c.proof('synthetic-proof');
    };
    await Request().get('https://api.coolapk.com/v6/feed/replyList');
    expect(prompts, 1);
    expect(adapter.requests, hasLength(2));
    await Request().get('https://api.coolapk.com/v6/feed/replyList',
        options: Options(extra: {'skipCaptcha': true}));
    expect(
        adapter.requests.last.headers['Cookie'], isNot(contains('validate=')));
  });
  test('cached validation cannot follow a device change', () async {
    ApiInterceptor.setCaptchaCookie('NEC:01234567:synthetic-proof');
    final device = GStorage.xAppDevice;
    await GStorage.settings
        .put(SettingsBoxKey.xAppDevice, 'another-synthetic-device');
    try {
      await Request().get('https://api.coolapk.com/v6/feed/replyList',
          options: Options(extra: {'skipCaptcha': true}));
      expect(adapter.requests.last.headers['Cookie'],
          isNot(contains('validate=')));
    } finally {
      await GStorage.settings.put(SettingsBoxKey.xAppDevice, device);
    }
  });
  test('device change while verifying prevents replay', () async {
    adapter.extra = {
      'captchaId': '0123456789abcdef0123456789abcdef',
      'captchaField': ''
    };
    final device = GStorage.xAppDevice;
    Request().captchaVerifier = (c) async {
      await GStorage.settings
          .put(SettingsBoxKey.xAppDevice, 'another-synthetic-device');
      return c.proof('synthetic-proof');
    };
    try {
      await Request().get('https://api.coolapk.com/v6/feed/replyList');
      expect(adapter.requests, hasLength(1));
    } finally {
      await GStorage.settings.put(SettingsBoxKey.xAppDevice, device);
    }
  });
  test('cancelled verification never repeats a write', () async {
    (Request() as dynamic).captchaVerifier = (dynamic c) async => null;
    await Request().post('https://api.coolapk.com/v6/feed/like',
        queryParameters: {'id': '123'});
    expect(adapter.requests, hasLength(1));
  });
  test('persistent challenge is retried once without a verification loop',
      () async {
    adapter.alwaysChallenge = true;
    var prompts = 0;
    (Request() as dynamic).captchaVerifier = (dynamic c) async {
      prompts++;
      return 'NEC:01234567:synthetic-proof';
    };
    await Request().get('https://api.coolapk.com/v6/feed/detail');
    expect(prompts, 1);
    expect(adapter.requests, hasLength(2));
  });
  test('server cannot replace business parameters with a captcha field',
      () async {
    adapter.extra = {
      'captchaId': '0123456789abcdef0123456789abcdef',
      'captchaField': 'id'
    };
    (Request() as dynamic).captchaVerifier =
        (dynamic c) async => 'NEC:01234567:synthetic-proof';
    await Request().get('https://api.coolapk.com/v6/feed/detail',
        queryParameters: {'id': '123'});
    expect(adapter.requests.last.queryParameters['id'], '123');
    expect(adapter.requests.last.queryParameters['_v2_post_token'],
        'NEC:01234567:synthetic-proof');
  });
  test('external hosts never invoke account verification', () async {
    var prompts = 0;
    (Request() as dynamic).captchaVerifier = (dynamic c) async {
      prompts++;
      return null;
    };
    await Request().get('https://example.invalid/v6/feed/detail');
    expect(prompts, 0);
    expect(adapter.requests, hasLength(1));
  });
  test(
      'session change during verification prevents replay under another account',
      () async {
    GlobalData().token = 'synthetic-account-a';
    (Request() as dynamic).captchaVerifier = (dynamic c) async {
      GlobalData().token = 'synthetic-account-b';
      return 'NEC:01234567:synthetic-proof';
    };
    await Request().post('https://api.coolapk.com/v6/feed/like');
    expect(adapter.requests, hasLength(1));
  });
  test(
      'session change while awaiting the original response prevents verification and replay',
      () async {
    GlobalData().token = 'synthetic-account-a';
    adapter.beforeResponse = () {
      GlobalData().token = 'synthetic-account-b';
    };
    var prompts = 0;
    (Request() as dynamic).captchaVerifier = (dynamic c) async {
      prompts++;
      return 'NEC:01234567:synthetic-proof';
    };
    await Request().post('https://api.coolapk.com/v6/feed/like');
    expect(prompts, 0);
    expect(adapter.requests, hasLength(1));
  });
  test('A to B to A while verifying never replays the old write', () async {
    (Request() as dynamic).captchaVerifier = (dynamic c) async {
      GlobalData().sessionGeneration += 2;
      return 'NEC:01234567:synthetic-proof';
    };
    await expectLater(
        Request().post('https://api.coolapk.com/v6/feed/like'),
        throwsA(isA<DioException>()
            .having((e) => e.type, 'type', DioExceptionType.cancel)));
    expect(adapter.requests, hasLength(1));
  });
}
