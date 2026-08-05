import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/expense.dart';
import '../services/api_service.dart';

class ExpenseRepository {
  final ApiService _api = ApiService();

  Future<List<Map<String, int>>> getTrackedMonths() async {
    try {
      final res = await _api.getTrackedMonths();
      return List<Map<String, int>>.from(res.map((e) => {
        'month': e['month'] as int,
        'year': e['year'] as int,
      }));
    } catch (_) {
      final now = DateTime.now();
      return [{'month': now.month, 'year': now.year}];
    }
  }

  Future<List<Expense>> getExpenses(DateTime startDate, DateTime endDate) async {
    final startStr = startDate.toUtc().toIso8601String();
    final endStr = endDate.toUtc().toIso8601String();
    return _api.getExpenses(startDate: startStr, endDate: endStr, limit: 1000);
  }

  Future<Expense> addExpense(Expense e) async {
    try {
      return await _api.createExpense(e);
    } on Exception catch (ex) {
      final msg = parseError(ex);
      if (msg.contains('network') || msg.contains('reach server') || msg.contains('timed out')) {
        await _queueOfflineExpense(e);
        return e.copyWith(id: 'offline_${DateTime.now().millisecondsSinceEpoch}');
      }
      throw Exception(msg);
    }
  }

  Future<Expense> updateExpense(String id, Map<String, dynamic> data) async {
    try {
      return await _api.updateExpense(id, data);
    } catch (e) {
      throw Exception(parseError(e as Exception));
    }
  }

  Future<void> deleteExpense(String id) async {
    try {
      await _api.deleteExpense(id);
    } catch (e) {
      throw Exception(parseError(e as Exception));
    }
  }

  Future<Map<String, dynamic>?> fetchAndCacheProfile() async {
    try {
      final profile = await _api.getMe();
      final user = profile['user'] as Map<String, dynamic>?;
      if (user != null) {
        final prefs = await SharedPreferences.getInstance();
        await prefs.setString('cached_user', jsonEncode(user));
        
        // Sync app data from backend to local storage
        if (user['budgets'] != null && (user['budgets'] as Map).isNotEmpty) {
          await prefs.setString('budgets', jsonEncode(user['budgets']));
        }
        if (user['goals'] != null && (user['goals'] as List).isNotEmpty) {
          await prefs.setString('savings_goals', jsonEncode(user['goals']));
        }
        if (user['balances'] != null) {
          await prefs.setString('current_balances', jsonEncode(user['balances']));
        }
        if (user['enableNotifications'] != null) {
          await prefs.setBool('enable_notifications', user['enableNotifications'] as bool);
        }
      }
      return user;
    } catch (_) {
      return null;
    }
  }

  Future<Map<String, dynamic>?> getCachedProfile() async {
    final prefs = await SharedPreferences.getInstance();
    final json = prefs.getString('cached_user');
    if (json != null) return jsonDecode(json) as Map<String, dynamic>;
    return null;
  }

  Future<Map<String, double>> getBudgets() async {
    final prefs = await SharedPreferences.getInstance();
    final json = prefs.getString('budgets');
    final Map<String, double> defaultBudgets = {
      'Food & Dining': 3000.0,
      'Transport': 1500.0,
      'Grocery': 2500.0,
      'Bills': 2000.0,
      'Health': 1000.0,
      'Shopping': 2000.0,
      'Transfer': 0.0,
      'Other': 0.0,
    };
    
    if (json != null) {
      final decoded = jsonDecode(json) as Map<String, dynamic>;
      final parsed = decoded.map((k, v) => MapEntry(k, (v as num).toDouble()));
      for (final key in defaultBudgets.keys) {
        if (!parsed.containsKey(key)) {
          parsed[key] = defaultBudgets[key]!;
        }
      }
      return parsed;
    }
    return defaultBudgets;
  }

  Future<void> saveBudgets(Map<String, double> budgets) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('budgets', jsonEncode(budgets));
  }

  Future<List<SavingsGoal>> getGoals() async {
    final prefs = await SharedPreferences.getInstance();
    final json = prefs.getString('savings_goals');
    if (json != null) {
      final list = jsonDecode(json) as List;
      return list.map((g) => SavingsGoal.fromJson(g as Map<String, dynamic>)).toList();
    }
    final defaultGoals = [SavingsGoal(name: 'Emergency fund', target: 50000, saved: 0)];
    await saveGoals(defaultGoals);
    return defaultGoals;
  }

  Future<void> saveGoals(List<SavingsGoal> goals) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('savings_goals', jsonEncode(goals.map((g) => g.toJson()).toList()));
  }

  Future<bool> getNotificationsEnabled() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool('enable_notifications') ?? true;
  }

  Future<void> setNotificationsEnabled(bool val) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('enable_notifications', val);
  }
  
  Future<void> cacheBalances(Map<String, double> balances) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('current_balances', jsonEncode(balances));
    } catch (_) {}
  }

  Future<void> _queueOfflineExpense(Expense e) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final queueStr = prefs.getString('offline_tx_queue') ?? '[]';
      final queue = jsonDecode(queueStr) as List;
      queue.add(e.toJson());
      await prefs.setString('offline_tx_queue', jsonEncode(queue));
    } catch (_) {}
  }

  String parseError(Exception e) {
    if (e is DioException) {
      final data = e.response?.data;
      if (data is Map && data['error'] != null) return data['error'].toString();
      if (data is Map && data['message'] != null) return data['message'].toString();
      if (e.type == DioExceptionType.connectionTimeout || e.type == DioExceptionType.receiveTimeout) {
        return 'Connection timed out. Check your network.';
      }
      if (e.type == DioExceptionType.connectionError) {
        return 'Cannot reach server. Check your network.';
      }
      if (e.response?.statusCode == 401 || e.response?.statusCode == 403) {
        return 'Session expired. Please log in again.';
      }
      return 'Server error (${e.response?.statusCode ?? "no response"})';
    }
    final s = e.toString();
    if (s.contains('SocketException') || s.contains('Connection refused')) return 'Cannot reach server. Check your network.';
    return s.replaceFirst('Exception: ', '');
  }
}
