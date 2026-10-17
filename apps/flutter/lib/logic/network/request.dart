import 'dart:async';
import 'package:dio/dio.dart';

import '../../logic/network/interceptor.dart';
import '../../utils/global_data.dart';
import '../../utils/storage_util.dart';
import 'captcha_challenge.dart';

class Request {
  static final Request _instance = Request._internal();
  static late final Dio dio;
  factory Request() => _instance;
  Future<String?> Function(CaptchaChallenge)? captchaVerifier;
  bool _verifying = false;

  Request._internal() {
    dio = Dio()
      ..options.connectTimeout = const Duration(milliseconds: 5000)
      ..options.receiveTimeout = const Duration(milliseconds: 5000)
      ..interceptors.add(ApiInterceptor());
  }

  Future<Response<dynamic>> get(
    url, {
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
    extra,
  }) async {
    final generation = GlobalData().sessionGeneration;
    options = (options ?? Options()).copyWith(extra: {
      ...?options?.extra,
      'sessionGeneration': generation,
    });
    final session =
        (GlobalData().isLogin, GlobalData().uid, GlobalData().token);
    final response = await dio.get(
      url,
      queryParameters: queryParameters,
      options: options,
      cancelToken: cancelToken,
    );
    _checkSession(generation, response);
    final verified = await _verifyAndRetry(
        response,
        session,
        generation,
        options.extra?['skipCaptcha'] == true,
        (proof, challenge) => dio.get(url,
            queryParameters: challenge.field.isEmpty
                ? queryParameters
                : {...?queryParameters, challenge.field: proof},
            options: options,
            cancelToken: cancelToken));
    _checkSession(generation, verified);
    return verified;
  }

  Future<Response<dynamic>> post(
    url, {
    Object? data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
    extra,
  }) async {
    final generation = GlobalData().sessionGeneration;
    options = (options ?? Options()).copyWith(extra: {
      ...?options?.extra,
      'sessionGeneration': generation,
    });
    final session =
        (GlobalData().isLogin, GlobalData().uid, GlobalData().token);
    // A multipart body becomes finalized during its first send; retain a copy
    // before sending so a server-rejected write can be retried exactly once.
    final retryData = data is FormData ? data.clone() : data;
    final response = await dio.post(
      url,
      data: data,
      queryParameters: queryParameters,
      options: options,
      cancelToken: cancelToken,
    );
    _checkSession(generation, response);
    final verified = await _verifyAndRetry(
        response, session, generation, options.extra?['skipCaptcha'] == true,
        (proof, challenge) {
      Object? verifiedData = retryData;
      if (challenge.field.isNotEmpty) {
        if (retryData is FormData) {
          retryData.fields.removeWhere((e) => e.key == challenge.field);
          retryData.fields.add(MapEntry(challenge.field, proof));
        } else if (retryData is Map<String, dynamic>) {
          verifiedData = {...retryData, challenge.field: proof};
        }
      }
      return dio.post(url,
          data: verifiedData,
          queryParameters: challenge.field.isEmpty ||
                  verifiedData is FormData ||
                  verifiedData is Map
              ? queryParameters
              : {...?queryParameters, challenge.field: proof},
          options: options,
          cancelToken: cancelToken);
    });
    _checkSession(generation, verified);
    return verified;
  }

  void _checkSession(int generation, Response response) {
    if (generation != GlobalData().sessionGeneration) {
      throw DioException(
          requestOptions: response.requestOptions,
          type: DioExceptionType.cancel,
          error: '账号已切换，已忽略旧请求');
    }
  }

  Future<Response<dynamic>> _verifyAndRetry(
      Response<dynamic> response,
      (bool, String, String) session,
      int generation,
      bool skip,
      Future<Response<dynamic>> Function(String, CaptchaChallenge)
          retry) async {
    final uri = response.requestOptions.uri;
    if (skip ||
        generation != GlobalData().sessionGeneration ||
        _verifying ||
        captchaVerifier == null ||
        session !=
            (GlobalData().isLogin, GlobalData().uid, GlobalData().token) ||
        uri.scheme != 'https' ||
        !['api.coolapk.com', 'api2.coolapk.com'].contains(uri.host)) {
      return response;
    }
    final challenge = CaptchaChallenge.fromBody(response.data);
    if (challenge == null) return response;
    final device = response.requestOptions.headers['X-App-Device'];
    if (challenge.field.isEmpty) ApiInterceptor.clearCaptchaCookie();
    String? proof;
    _verifying = true;
    try {
      proof = await captchaVerifier!(challenge);
    } finally {
      _verifying = false;
    }
    if (proof == null ||
        generation != GlobalData().sessionGeneration ||
        (device != null && device != GStorage.xAppDevice) ||
        !challenge.acceptsProof(proof) ||
        response.requestOptions.cancelToken?.isCancelled == true ||
        session !=
            (GlobalData().isLogin, GlobalData().uid, GlobalData().token)) {
      response.extra['captchaVerification'] =
          proof == null ? 'cancelled' : 'incomplete';
      return response;
    }
    // Direct Dio call, deliberately outside this helper: no verification loop,
    // network retries or replay after ambiguous transport errors.
    if (challenge.field.isEmpty) ApiInterceptor.setCaptchaCookie(proof);
    final verified = await retry(proof, challenge);
    if (challenge.field.isEmpty &&
        CaptchaChallenge.fromBody(verified.data) != null) {
      ApiInterceptor.clearCaptchaCookie();
    }
    verified.extra['captchaVerification'] = 'submitted';
    return verified;
  }
}
