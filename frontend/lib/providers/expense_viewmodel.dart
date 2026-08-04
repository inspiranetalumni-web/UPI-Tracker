import 'package:flutter/material.dart';
import '../models/expense.dart';
import '../repositories/expense_repository.dart';
import '../services/expense_service.dart';
import '../services/notification_service.dart';
import '../services/api_service.dart';

class ExpenseViewModel extends ChangeNotifier {
  final ExpenseRepository _repository = ExpenseRepository();
  final ExpenseService _service = ExpenseService();

  // --- STATE ---
  List<Expense> expenses = [];
  List<Map<String, int>> trackedMonths = [];
  bool loading = false;
  String? error;
  
  DateTime filterStartDate = DateTime(DateTime.now().year, DateTime.now().month, 1);
  DateTime filterEndDate = DateTime.now();
  String filterCategory = 'All';
  String sortBy = 'date';
  
  int currentTab = 0;
  bool enableNotifications = true;
  Map<String, dynamic>? currentUser;
  Map<String, double> budgets = {};
  List<SavingsGoal> goals = [];

  ExpenseViewModel() {
    _init();
  }

  Future<void> _init() async {
    budgets = await _repository.getBudgets();
    goals = await _repository.getGoals();
    enableNotifications = await _repository.getNotificationsEnabled();
    currentUser = await _repository.getCachedProfile();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      notifyListeners();
    });
  }

  @override
  void dispose() {
    NotificationService.onExpense = null;
    super.dispose();
  }

  // --- COMPUTED (Delegated to Service) ---
  List<Expense> get filtered => _service.filterAndSort(expenses, filterStartDate, filterEndDate, filterCategory, sortBy);
  
  List<Expense> get dateRangeExpenses => expenses.where((e) {
        final d = DateTime(e.date.year, e.date.month, e.date.day);
        final s = DateTime(filterStartDate.year, filterStartDate.month, filterStartDate.day);
        final en = DateTime(filterEndDate.year, filterEndDate.month, filterEndDate.day);
        return !d.isBefore(s) && !d.isAfter(en);
      }).toList();
  double get monthTotal => _service.calculateMonthTotal(dateRangeExpenses);
  double get monthIncome => _service.calculateMonthIncome(dateRangeExpenses);
  double get todayTotal => _service.calculateTodayTotal(expenses);
  Map<String, double> get categoryTotals => _service.calculateCategoryTotals(dateRangeExpenses);
  Map<String, double> get appTotals => _service.calculateAppTotals(dateRangeExpenses);
  Map<String, double> get merchantTotals => _service.calculateMerchantTotals(dateRangeExpenses);
  List<double> get weekdayTotals => _service.calculateWeekdayTotals(dateRangeExpenses);
  Map<String, double> get currentBalances => _service.extractCurrentBalances(expenses);
  
  int get peakDayIndex {
    final t = weekdayTotals;
    if (t.every((v) => v == 0)) return -1;
    int best = 0;
    for (int i = 1; i < t.length; i++) { if (t[i] > t[best]) best = i; }
    return best;
  }

  Expense? get maxExpense {
    final debits = dateRangeExpenses.where((e) => e.type == 'debit').toList();
    return debits.isEmpty ? null : debits.reduce((a, b) => a.amount > b.amount ? a : b);
  }

  int get uniqueApps => dateRangeExpenses.where((e) => e.type == 'debit').map((e) => e.upiApp).toSet().length;
  String get topApp {
    final totals = appTotals;
    if (totals.isEmpty) return '—';
    return totals.entries.reduce((a, b) => a.value > b.value ? a : b).key;
  }

  // --- ACTIONS ---
  void setTab(int t) { currentTab = t; notifyListeners(); }
  void setDateRange(DateTime start, DateTime end) { filterStartDate = start; filterEndDate = end; load(); }
  void setFilter(String cat) { filterCategory = cat; notifyListeners(); }
  void setSort(String s) { sortBy = s; notifyListeners(); }

  Future<void> setEnableNotifications(bool val) async {
    enableNotifications = val;
    await _repository.setNotificationsEnabled(val);
    ApiService().updateProfile(enableNotifications: val); // Fire and forget sync
    notifyListeners();
  }

  Future<String?> updateUserProfile(String name, String email, String phone) async {
    return "Profile updates temporarily disabled in ViewModel refactor."; // Can wire this up fully later
  }

  String? aiInsight;

  Future<void> setBudget(String category, double amount) async {
    budgets[category] = amount;
    await _repository.saveBudgets(budgets);
    ApiService().updateProfile(budgets: budgets); // Fire and forget sync
    notifyListeners();
  }

  Future<void> autoSuggestBudgets() async {
    final totals = categoryTotals;
    final Map<String, double> newBudgets = Map.from(budgets);
    for (final entry in totals.entries) {
      if (entry.key != 'Transfer' && entry.key != 'Other') {
        double suggested = entry.value * 1.1; // 10% headroom
        suggested = (suggested / 100).ceil() * 100.0;
        if (suggested < 500) suggested = 500;
        newBudgets[entry.key] = suggested;
      }
    }
    budgets = newBudgets;
    await _repository.saveBudgets(budgets);
    ApiService().updateProfile(budgets: budgets); // Fire and forget sync
    notifyListeners();
  }

  Future<void> addGoal(String name, double target) async {
    goals.add(SavingsGoal(name: name, target: target, saved: 0));
    await _repository.saveGoals(goals);
    ApiService().updateProfile(goals: goals.map((g) => g.toJson()).toList()); // Fire and forget sync
    notifyListeners();
  }

  Future<void> updateGoalSaved(int index, double amount) async {
    final current = goals[index].saved;
    goals[index] = goals[index].copyWith(saved: (current + amount).clamp(0, goals[index].target));
    await _repository.saveGoals(goals);
    ApiService().updateProfile(goals: goals.map((g) => g.toJson()).toList()); // Fire and forget sync
    notifyListeners();
  }

  Future<void> removeGoal(int index) async {
    goals.removeAt(index);
    await _repository.saveGoals(goals);
    ApiService().updateProfile(goals: goals.map((g) => g.toJson()).toList()); // Fire and forget sync
    notifyListeners();
  }

  Future<void> fetchInsights() async {
    try {
      aiInsight = null;
      notifyListeners();
      final res = await ApiService().getInsights(
        startDate: filterStartDate.toIso8601String(),
        endDate: filterEndDate.toIso8601String(),
      );
      aiInsight = res['insight'];
      notifyListeners();
    } catch (e) {
      aiInsight = "Could not load AI insights.";
      notifyListeners();
    }
  }

  Future<void> load() async {
    loading = true; error = null; notifyListeners();
    try {
      trackedMonths = await _repository.getTrackedMonths();
      expenses = await _service.getExpenses(filterStartDate, filterEndDate);
      currentUser = await _repository.fetchAndCacheProfile();
      
      // Re-read local values since fetchAndCacheProfile might have overwritten them with synced data
      budgets = await _repository.getBudgets();
      goals = await _repository.getGoals();
      enableNotifications = await _repository.getNotificationsEnabled();
      
      await _repository.cacheBalances(currentBalances);
      ApiService().updateProfile(balances: currentBalances); // Fire and forget sync
      fetchInsights(); // Non-blocking
    } catch (e) {
      error = _repository.parseError(e as Exception);
    } finally {
      loading = false; notifyListeners();
    }
  }

  Future<String?> addExpense(Expense e) async {
    try {
      final saved = await _service.addExpense(e);
      expenses.insert(0, saved);
      error = null;
      notifyListeners();
      return null;
    } catch (ex) {
      error = ex.toString();
      notifyListeners();
      return error;
    }
  }

  Future<String?> updateExpense(String id, Map<String, dynamic> data) async {
    try {
      final updated = await _service.updateExpense(id, data);
      final idx = expenses.indexWhere((e) => e.id == id);
      if (idx >= 0) expenses[idx] = updated;
      notifyListeners();
      return null;
    } catch (e) {
      error = e.toString();
      notifyListeners();
      return error;
    }
  }

  Future<String?> deleteExpense(String id) async {
    final idx = expenses.indexWhere((e) => e.id == id);
    final removed = idx >= 0 ? expenses[idx] : null;
    if (idx >= 0) { expenses.removeAt(idx); notifyListeners(); }
    try {
      await _service.deleteExpense(id);
      return null;
    } catch (e) {
      if (idx >= 0 && removed != null) expenses.insert(idx, removed);
      error = e.toString();
      notifyListeners();
      return error;
    }
  }

  void initNotificationListener() {
    NotificationService.onExpense = (data) {
      final dateStr = data['date'] as String?;
      final date = dateStr != null ? DateTime.parse(dateStr).toLocal() : DateTime.now();
      final e = Expense(
        id:       DateTime.now().millisecondsSinceEpoch.toString(), // Temp ID until load() is called
        name:     data['payee']    as String? ?? 'Unknown',
        amount:   (data['amount'] as num).toDouble(),
        category: data['category'] as String? ?? 'Other',
        upiApp:   data['upiApp']   as String? ?? 'GPay',
        upiRef:   data['upiRef']   as String?,
        date:     date,
        type:     data['type']     as String? ?? 'debit',
      );
      expenses.insert(0, e);
      notifyListeners();
    };
  }
}
