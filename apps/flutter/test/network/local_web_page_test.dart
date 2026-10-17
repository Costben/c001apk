import 'dart:io';

import 'package:c001apk_flutter/utils/local_web_page.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
      'SDK HTML has a loopback HTTP origin and its server closes with the page',
      () async {
    final page = await LocalWebPage.open('<html lang="zh-CN">验证页面</html>');
    final url = page.url;
    final client = HttpClient()..findProxy = (_) => 'DIRECT';
    try {
      expect(page.url.scheme, 'http');
      expect(page.url.host, '127.0.0.1');
      final response = await (await client.getUrl(page.url)).close();
      expect(response.statusCode, 200);
      expect(response.headers.contentType!.mimeType, 'text/html');
      expect(
          response.headers.value(HttpHeaders.cacheControlHeader), 'no-store');
      await response.drain<void>();
      final missing =
          await (await client.getUrl(page.url.resolve('/unrelated'))).close();
      expect(missing.statusCode, 404);
      await missing.drain<void>();
      await page.close();
      await expectLater(() async => (await client.getUrl(url)).close(),
          throwsA(isA<IOException>()));
    } finally {
      client.close(force: true);
    }
  });
}
