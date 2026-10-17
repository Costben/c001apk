import 'package:flutter/material.dart';

import '../logic/app_update/app_release.dart';

enum UpdateAction { later, skip, download }

class AppUpdateDialog extends StatelessWidget {
  const AppUpdateDialog(
      {super.key,
      required this.release,
      required this.currentVersion,
      required this.onOpenRelease});
  final AppRelease release;
  final String currentVersion;
  final VoidCallback onOpenRelease;

  @override
  Widget build(BuildContext context) {
    final date = release.publishedAt?.toLocal();
    const labels = {
      UpdateAction.later: '稍后',
      UpdateAction.skip: '跳过',
      UpdateAction.download: '更新',
    };
    return AlertDialog(
      title: const Text('发现新版本'),
      content: SizedBox(
        width: 420,
        height: (MediaQuery.sizeOf(context).height * .35).clamp(100, 280),
        child: SingleChildScrollView(
            child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('$currentVersion → ${release.version}',
                style: Theme.of(context).textTheme.titleMedium),
            if (date != null)
              Text(
                  '发布于 ${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}'),
            const SizedBox(height: 12),
            Text(release.notes),
            TextButton(onPressed: onOpenRelease, child: const Text('查看发布页面')),
          ],
        )),
      ),
      actions: [
        Wrap(alignment: WrapAlignment.end, spacing: 4, children: [
          for (final action in UpdateAction.values)
            TextButton(
                onPressed: () => Navigator.of(context).pop(action),
                child: Text(labels[action]!)),
        ])
      ],
    );
  }
}
