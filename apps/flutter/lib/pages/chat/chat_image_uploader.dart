import 'dart:convert';
import 'dart:ui' as ui;

import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';
import 'package:image_picker/image_picker.dart';
import 'package:mime/mime.dart';
import 'package:uuid/uuid.dart';

import '../../logic/model/oss_upload/datum.dart';
import '../../logic/model/oss_upload/oss_upload_model.dart';
import '../../logic/network/api_failure.dart';
import '../../logic/network/network_repo.dart';
import '../../utils/oss/aliyunoss_client.dart';
import '../../utils/oss/aliyunoss_config.dart';

/// The owning message carries upload state; this never opens a loading dialog.
Future<String> uploadChatImage(XFile file, String uid,
    {required bool Function() isActive}) async {
  final bytes = await file.readAsBytes();
  final mime = lookupMimeType(file.name, headerBytes: bytes);
  if (mime == null || !mime.startsWith('image/')) {
    throw const ApiFailure(ApiFailureKind.business, '请选择有效的图片文件');
  }
  final codec = await ui.instantiateImageCodec(bytes);
  final frame = await codec.getNextFrame();
  final resolution = '${frame.image.width}x${frame.image.height}';
  frame.image.dispose();
  codec.dispose();
  if (!isActive()) {
    throw const ApiFailure(ApiFailureKind.authentication, '账号已切换');
  }
  final name = '${const Uuid().v4()}.${mime.split('/').last}';
  final response = await NetworkRepo.postOSSUploadPrepare(
      data: FormData.fromMap({
    'uploadBucket': 'message',
    'uploadDir': 'message',
    'is_anonymous': '0',
    'uploadFileList': jsonEncode([
      OssUploadModel(
              name: name,
              resolution: resolution,
              md5: md5.convert(bytes).toString())
          .toJson()
    ]),
    'toUid': uid,
  }));
  final body = readApiObject(response)['data'];
  if (body is! Map<String, dynamic>) {
    throw const ApiFailure(ApiFailureKind.invalidResponse, '无法准备图片上传，请重试');
  }
  final prepared = OssDatum.fromJson(body);
  final info = prepared.uploadPrepareInfo;
  final object = prepared.fileInfo?.firstOrNull?.uploadFileName;
  if (info == null ||
      object == null ||
      !object.startsWith('message/') ||
      [
        info.accessKeyId,
        info.accessKeySecret,
        info.securityToken,
        info.endPoint,
        info.bucket
      ].any((value) => value == null || value.isEmpty)) {
    throw const ApiFailure(ApiFailureKind.invalidResponse, '图片上传配置不完整，请重试');
  }
  if (!isActive()) {
    throw const ApiFailure(ApiFailureKind.authentication, '账号已切换');
  }
  final result = await AliyunOssClient(
          accessKeyId: info.accessKeyId!,
          accessKeySecret: info.accessKeySecret!,
          securityToken: info.securityToken!)
      .upload(
          id: name,
          config: AliyunOssConfig(
              endpoint: info.endPoint!,
              bucket: info.bucket!,
              directory: 'message'),
          ossFileName: object.substring('message'.length),
          filePath: file.path);
  if (result.state != AliyunOssResultState.success) {
    throw const ApiFailure(ApiFailureKind.transport, '图片上传失败，请检查网络后重试');
  }
  return '/$object';
}
