// ignore_for_file: constant_identifier_names

import 'dart:io';
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../pages/home/app/app_list_page.dart';
import '../../pages/home/feed/home_feed_page.dart';
import '../../pages/home/return_top_controller.dart';
import '../../pages/home/topic/home_topic_page.dart';

enum TabType {
  FOLLOW,
  APP,
  FEED,
  HOT,
  TOPIC,
  PRODUCT,
  COOLPIC,
  NONE;

  String get label => switch (this) {
        TabType.FOLLOW => '关注',
        TabType.APP => '应用',
        TabType.FEED => '动态',
        TabType.HOT => '热门',
        TabType.TOPIC => '话题',
        TabType.PRODUCT => '数码',
        TabType.COOLPIC => '酷图',
        TabType.NONE => '其他',
      };
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> with TickerProviderStateMixin {
  late TabController _tabController;
  StreamSubscription<int>? _returnTopSubscription;
  final ReturnTopController _pageScrollController =
      Get.find<ReturnTopController>(tag: 'home');
  // late final _config = Provider.of<AppConfigProvider>(context, listen: false);
  // late bool _showFab = true; //_config.isLogin;

  final _tabTypes = [
    if (Platform.isAndroid) TabType.APP,
    TabType.FEED,
    TabType.HOT,
    TabType.TOPIC,
    TabType.PRODUCT,
    TabType.COOLPIC,
  ];

  final _pages = [
    if (Platform.isAndroid) const AppListPage(),
    const HomeFeedPage(tabType: TabType.FEED),
    const HomeFeedPage(tabType: TabType.HOT),
    const HomeTopicPage(tabType: TabType.TOPIC),
    const HomeTopicPage(tabType: TabType.PRODUCT),
    const HomeFeedPage(tabType: TabType.COOLPIC),
  ];

  void scrollToTop(int index) {
    _pageScrollController.setIndex(_tabTypes[index].index);
  }

  @override
  void initState() {
    super.initState();

    _tabController = TabController(
      vsync: this,
      initialIndex: _tabTypes.indexOf(TabType.FEED),
      length: _tabTypes.length,
    );

    _returnTopSubscription = _pageScrollController.index.listen((index) {
      if (index == 998) {
        scrollToTop(_tabController.index);
      }
    });
  }

  @override
  void dispose() {
    _returnTopSubscription?.cancel();
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: TabBar(
          controller: _tabController,
          tabs: _tabTypes.map((type) => Tab(text: type.label)).toList(),
          isScrollable: true,
          onTap: (index) {
            if (!_tabController.indexIsChanging) {
              scrollToTop(index);
            }
          },
        ),
        actions: [
          IconButton(
            onPressed: () => Get.toNamed('/search'),
            icon: const Icon(Icons.search),
            tooltip: '搜索',
          )
        ],
      ),
      body: TabBarView(controller: _tabController, children: _pages),
    );
  }
}
