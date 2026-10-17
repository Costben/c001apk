import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../constants/constants.dart';
import '../../components/app_logo.dart';
import '../../components/update_check_tile.dart';
import '../../utils/utils.dart';

class AboutPage extends StatelessWidget {
  const AboutPage({super.key});

  @override
  Widget build(BuildContext context) {
    final version = Get.parameters['version'] ?? '';
    return Scaffold(
      appBar: AppBar(
        title: const Text('关于'),
        leading: const BackButton(),
      ),
      body: ListView(
        physics: const AlwaysScrollableScrollPhysics(
          parent: BouncingScrollPhysics(),
        ),
        children: [
          const Padding(
            padding: EdgeInsets.all(24),
            child: Center(child: AppLogo(size: 96)),
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.all_inclusive),
            title: const Text(Constants.APP_NAME),
            subtitle: const Text('一个轻量的非官方酷安客户端'),
            onTap: () {},
          ),
          ListTile(
            leading: const Icon(Icons.error_outline),
            title: const Text('版本'),
            subtitle: Text(version),
            onTap: () {},
          ),
          ListTile(
            leading: const Icon(Icons.code),
            title: const Text('项目地址'),
            subtitle: const Text(Constants.URL_SOURCE_CODE),
            onTap: () => Utils.launchURL(Constants.URL_SOURCE_CODE),
          ),
          const UpdateCheckTile(),
          ListTile(
            leading: const Icon(Icons.source_outlined),
            title: const Text('开源许可证'),
            onTap: () => showLicensePage(
              context: context,
              applicationName: Constants.APP_NAME,
              applicationVersion: version,
              applicationLegalese: '基于 Flutter 构建',
              applicationIcon: const AppLogo(),
            ),
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.favorite_outline),
            title: const Text('致谢'),
            subtitle: const Text('c001apk-flutter'),
            onTap: () => Utils.launchURL(Constants.URL_UPSTREAM),
          ),
        ],
      ),
    );
  }
}
