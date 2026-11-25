import 'dart:convert';
import 'package:flutter/services.dart';

class PlanClient {
  static const _channel = MethodChannel('project1k/plan');
  static const _events = EventChannel('project1k/plan_changes');
  Stream<dynamic> get changes => _events.receiveBroadcastStream();
  Future<List<Map<String, dynamic>>> _read(String method,
      [Map<String, dynamic>? args]) async {
    final rows = await _channel.invokeListMethod<dynamic>(method, args) ?? [];
    return rows.map((r) => Map<String, dynamic>.from(r as Map)).toList();
  }
  Future<Map<String, dynamic>> schema() async {
    final rows = await _read('schema');
    return jsonDecode(rows.single['json'] as String) as Map<String, dynamic>;
  }
  Future<List<Map<String, dynamic>>> libraries() => _read('libraries');
  Future<List<Map<String, dynamic>>> records(String libraryId) =>
      _read('records', {'libraryId': libraryId});
  Future<List<Map<String, dynamic>>> record(String recordId) =>
      _read('record', {'recordId': recordId});
  Future<List<Map<String, dynamic>>> references(String recordId) =>
      _read('references', {'recordId': recordId});
}
