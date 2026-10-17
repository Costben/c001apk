import 'package:dio/dio.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';

import '../../constants/constants.dart';
import '../../utils/global_data.dart';
import '../../utils/storage_util.dart';
import '../../utils/token_util.dart';
import 'api_failure.dart';

class ApiInterceptor extends Interceptor {
  static String? _captchaCookie;
  static (int, bool, String, String, String)? _captchaSession;

  static (int, bool, String, String, String) get _session => (
        GlobalData().sessionGeneration,
        GlobalData().isLogin,
        GlobalData().uid,
        GlobalData().token,
        GStorage.xAppDevice,
      );

  // Read verification is a process-local Cookie, shared by the two API hosts.
  // It must never survive an account/device change or enter persistent storage.
  static void setCaptchaCookie(String proof) {
    _captchaCookie = Uri.encodeQueryComponent(proof);
    _captchaSession = _session;
  }

  static void clearCaptchaCookie() {
    _captchaCookie = null;
    _captchaSession = null;
  }

  static String? get _currentCaptchaCookie {
    if (_captchaSession != _session) clearCaptchaCookie();
    return _captchaCookie;
  }

  String get apiVersion => GStorage.apiVersion;
  String get versionCode => GStorage.versionCode;
  String get versionName => GStorage.versionName;
  String get sdkInt => GStorage.sdkInt;
  String get xAppDevice => GStorage.xAppDevice;
  String get userAgent => GStorage.userAgent;
  String get token =>
      TokenUtils.getTokenV3(xAppDevice, appCode: int.parse(versionCode));

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    if (options.extra['sessionGeneration'] != null &&
        options.extra['sessionGeneration'] != GlobalData().sessionGeneration) {
      handler.reject(DioException(
          requestOptions: options,
          type: DioExceptionType.cancel,
          error: '账号已切换'));
      return;
    }
    try {
      final uri = options.uri;
      final publicFeed = uri.scheme == 'https' &&
          uri.host == 'www.coolapk.com' &&
          options.method == 'GET' &&
          RegExp(r'^/feed/[0-9]+$').hasMatch(uri.path);
      if (uri.host == 'account.coolapk.com') {
        options.headers['sec-ch-ua'] =
            '"Android WebView";v="117", "Not;A=Brand";v="8", "Chromium";v="117"';
        options.headers['sec-ch-ua-mobile'] = '?1';
        options.headers['sec-ch-ua-platform'] = 'Android';
        options.headers['Upgrade-Insecure-Requests'] = '1';
        options.headers['User-Agent'] = userAgent;
        options.headers['Accept'] =
            'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7';
        options.headers['X-Requested-With'] = Constants.APP_ID;
        if (GlobalData().loginSession.isNotEmpty) {
          options.headers['Cookie'] =
              '${GlobalData().loginSession}; forward=https://www.coolapk.com';
        }
        if (options.method == 'POST') {
          options.headers['X-Requested-With'] = Constants.REQUEST_WITH;
        } else if (options.responseType == ResponseType.bytes) {
          options.headers['Accept'] = 'image/*,*/*;q=0.8';
          options.headers['Referer'] =
              'https://account.coolapk.com/auth/loginByCoolApk';
        } else {
          options.responseType = ResponseType.plain;
        }
      } else if (uri.host == 'api.coolapk.com' ||
          uri.host == 'api2.coolapk.com' ||
          publicFeed) {
        options.headers['User-Agent'] = userAgent;
        options.headers['X-Requested-With'] = Constants.REQUEST_WITH;
        options.headers['X-Sdk-Int'] = sdkInt;
        options.headers['X-Sdk-Locale'] = Constants.LOCALE;
        options.headers['X-App-Id'] = Constants.APP_ID;
        options.headers['X-App-Token'] = token;
        options.headers['X-App-Version'] = versionName;
        options.headers['X-App-Code'] = versionCode;
        options.headers['X-Api-Version'] = apiVersion;
        options.headers['X-App-Device'] = xAppDevice;
        options.headers['X-Dark-Mode'] = '0';
        options.headers['X-App-Channel'] = Constants.CHANNEL;
        options.headers['X-App-Mode'] = Constants.MODE;
        options.headers['X-App-Supported'] = versionCode;
        if (publicFeed) {
          // Official public feed JSON needs signed client headers, but no
          // account/session cookie. Restrict these headers to numeric feed IDs.
          options.headers
              .removeWhere((key, _) => key.toLowerCase() == 'cookie');
          options.responseType = ResponseType.plain;
        } else {
          options.headers['Cookie'] = GlobalData().isLogin
              ? 'uid=${GlobalData().uid}; username=${GlobalData().username}; token=${GlobalData().token}'
              : GlobalData().SESSID;
          final validation = _currentCaptchaCookie;
          if (uri.scheme == 'https' && validation != null) {
            final cookie = options.headers['Cookie'] as String;
            options.headers['Cookie'] =
                '${cookie.isEmpty ? '' : '$cookie; '}validate=$validation';
          }
        }
      }

      handler.next(options);
    } catch (error, stackTrace) {
      handler.reject(DioException(
        requestOptions: options,
        error:
            const ApiFailure(ApiFailureKind.business, '请求参数异常，请检查客户端协议设置和系统时间'),
        stackTrace: stackTrace,
      ));
    }
  }

  @override
  void onError(err, handler) async {
    String url = err.requestOptions.uri.toString();
    if (err.requestOptions.extra['silentError'] != true &&
        !url.contains('/v6/apk/download') &&
        !url.contains('/v6/message/showImage')) {
      SmartDialog.showToast(
        await dioError(err),
        displayType: SmartToastType.onlyRefresh,
      );
    }
    super.onError(err, handler);
  }

  static Future<String> dioError(DioException error) async {
    return ApiFailure.fromError(error).message;
  }
}
