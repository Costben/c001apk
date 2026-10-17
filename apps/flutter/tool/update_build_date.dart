import 'dart:io';

/// Run from apps/flutter. CI resolves the UTC+8 date once, then passes it to
/// every platform job so binaries and archive names use the same build number.
void main(List<String> arguments) {
  if (arguments.length > 1) {
    throw ArgumentError('Usage: dart tool/update_build_date.dart [YYYYMMDD]');
  }
  final now = DateTime.now().toUtc().add(const Duration(hours: 8));
  final date = arguments.isEmpty
      ? '${now.year}${now.month.toString().padLeft(2, '0')}'
          '${now.day.toString().padLeft(2, '0')}'
      : arguments.single;
  if (!RegExp(r'^\d{8}$').hasMatch(date)) {
    throw ArgumentError('Build date must use YYYYMMDD');
  }
  final parsed = DateTime.tryParse(
      '${date.substring(0, 4)}-${date.substring(4, 6)}-${date.substring(6, 8)}');
  if (parsed == null ||
      parsed.year < 2000 ||
      parsed.month != int.parse(date.substring(4, 6)) ||
      parsed.day != int.parse(date.substring(6, 8))) {
    throw ArgumentError('Invalid build date: $date');
  }

  final pubspec = File('pubspec.yaml');
  final source = pubspec.readAsStringSync();
  final pattern =
      RegExp(r'^version: (\d+\.\d+\.\d+)\+\d+\r?$', multiLine: true);
  final matches = pattern.allMatches(source).toList();
  if (matches.length != 1) {
    throw StateError(
        'Expected one version: major.minor.patch+build in pubspec');
  }
  final match = matches.single;
  final version = '${match[1]}+$date';
  final ending = match[0]!.endsWith('\r') ? '\r' : '';
  pubspec.writeAsStringSync(
      source.replaceRange(match.start, match.end, 'version: $version$ending'));

  final output = Platform.environment['GITHUB_OUTPUT'];
  if (output != null) {
    File(output).writeAsStringSync('date=$date\nversion=$version\n',
        mode: FileMode.append);
  }
  stdout.writeln('Build version: $version (UTC+8 date)');
}
