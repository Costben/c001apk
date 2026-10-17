import 'package:flutter/material.dart';
import 'package:flutter_widget_from_html_core/flutter_widget_from_html_core.dart';
import 'package:get/get.dart';

import '../../components/network_image.dart';
import '../../logic/network/network_repo.dart';
import '../../logic/state/loading_state.dart';
import '../../utils/utils.dart';

class EventPage extends StatefulWidget {
  const EventPage({super.key});

  @override
  State<EventPage> createState() => _EventPageState();
}

class _EventPageState extends State<EventPage> {
  final _id = Get.parameters['id'] ?? '';
  late Future<LoadingState<Map<String, dynamic>>> _detail =
      NetworkRepo.getEventDetail(_id);

  String _text(Map<String, dynamic> data, String key) =>
      data[key] is String ? (data[key] as String).trim() : '';

  Widget _retry(String message) => Center(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Padding(padding: const EdgeInsets.all(24), child: Text(message)),
          FilledButton(
            onPressed: () => setState(() {
              _detail = NetworkRepo.getEventDetail(_id);
            }),
            child: const Text('重新加载'),
          ),
        ]),
      );

  Widget _content(Map<String, dynamic> data) {
    final action = _text(data, 'action_url');
    final actionUri = Uri.tryParse(action);
    final deadline = int.tryParse('${data['time_reg_end']}');
    final pic = _text(data, 'pic');
    final content = _text(data, 'content');
    return SingleChildScrollView(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 900),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (pic.isNotEmpty) ...[
                  networkImage(pic, fit: BoxFit.fitWidth),
                  const SizedBox(height: 16),
                ],
                Text(_text(data, 'title'),
                    style: Theme.of(context).textTheme.headlineSmall),
                if (deadline != null && deadline > 0) ...[
                  const SizedBox(height: 12),
                  Text(
                      '报名截止：${DateTime.fromMillisecondsSinceEpoch(deadline * 1000).toString().substring(0, 16)}'),
                ],
                for (final key in ['description', 'notice_rule'])
                  if (_text(data, key).isNotEmpty) ...[
                    const SizedBox(height: 16),
                    Text(_text(data, key), style: const TextStyle(height: 1.6)),
                  ],
                if (content.isNotEmpty) ...[
                  const SizedBox(height: 16),
                  HtmlWidget(content, onTapUrl: (url) {
                    Utils.onOpenLink(url);
                    return true;
                  }),
                ],
                if (actionUri != null &&
                    ['https', 'http'].contains(actionUri.scheme) &&
                    actionUri.host.isNotEmpty) ...[
                  const SizedBox(height: 24),
                  FilledButton(
                    onPressed: () => Utils.onOpenLink(action),
                    child: const Text('查看报名页面'),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('活动详情')),
        body: FutureBuilder<LoadingState<Map<String, dynamic>>>(
          future: _detail,
          builder: (context, snapshot) {
            final state = snapshot.data;
            if (state is Success<Map<String, dynamic>>) {
              return _content(state.response);
            }
            if (state is Error) return _retry(state.errMsg);
            if (state is Empty) return _retry('未找到活动，可能已下架');
            return const Center(child: CircularProgressIndicator());
          },
        ),
      );
}
