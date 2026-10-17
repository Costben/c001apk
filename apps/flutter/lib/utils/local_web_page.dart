import 'dart:io';

/// WebView2's loadData uses about:blank (ignoring baseUrl), where browser
/// storage throws SecurityError. Serve local SDK pages on a real loopback origin.
class LocalWebPage {
  LocalWebPage._(HttpServer server)
      : _server = server,
        url = Uri.parse('http://127.0.0.1:${server.port}/captcha');
  final HttpServer _server;
  final Uri url;

  static Future<LocalWebPage> open(String html) async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    server.listen((request) async {
      if (request.method == 'GET' && request.uri.path == '/captcha') {
        request.response.headers.contentType = ContentType.html;
        request.response.headers
            .set(HttpHeaders.cacheControlHeader, 'no-store');
        request.response.write(html);
      } else {
        request.response.statusCode = HttpStatus.notFound;
      }
      await request.response.close();
    });
    return LocalWebPage._(server);
  }

  Future<void> close() async => _server.close(force: true);
}
