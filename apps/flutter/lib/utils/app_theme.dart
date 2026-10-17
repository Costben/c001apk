import 'package:flutter/material.dart';

/// Typography follows the reference app's compact page titles and setting
/// descriptions while retaining Material's normal body sizes and touch targets.
ThemeData applyAppTypography(ThemeData theme) => theme.copyWith(
      appBarTheme: theme.appBarTheme.copyWith(
        centerTitle: false,
        scrolledUnderElevation: 0,
        titleTextStyle:
            TextStyle(fontSize: 16, color: theme.colorScheme.onSurface),
      ),
      listTileTheme: theme.listTileTheme.copyWith(
        titleTextStyle:
            TextStyle(fontSize: 16, color: theme.colorScheme.onSurface),
        subtitleTextStyle: TextStyle(
            fontSize: 12,
            color: theme.colorScheme.outline,
            fontWeight: FontWeight.normal),
      ),
      dialogTheme: theme.dialogTheme.copyWith(
        titleTextStyle:
            TextStyle(fontSize: 18, color: theme.colorScheme.onSurface),
      ),
    );

class AppTextScaler extends TextScaler {
  const AppTextScaler(this.system, this.factor);
  final TextScaler system;
  final double factor;
  @override
  double scale(double fontSize) => system.scale(fontSize) * factor;
  @override
  double get textScaleFactor => scale(14) / 14;
  @override
  bool operator ==(Object other) =>
      other is AppTextScaler &&
      other.system == system &&
      other.factor == factor;
  @override
  int get hashCode => Object.hash(system, factor);
}
