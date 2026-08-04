import '../models/expense.dart';
import '../repositories/expense_repository.dart';

class ExpenseService {
  final ExpenseRepository _repository = ExpenseRepository();

  Future<List<Expense>> getExpenses(DateTime startDate, DateTime endDate) {
    return _repository.getExpenses(startDate, endDate);
  }

  Future<Expense> addExpense(Expense e) {
    return _repository.addExpense(e);
  }

  Future<Expense> updateExpense(String id, Map<String, dynamic> data) {
    return _repository.updateExpense(id, data);
  }

  Future<void> deleteExpense(String id) {
    return _repository.deleteExpense(id);
  }

  List<Expense> filterAndSort(
    List<Expense> expenses,
    DateTime startDate,
    DateTime endDate,
    String category,
    String sortBy,
  ) {
    var list = expenses.where((e) {
      final d = DateTime(e.date.year, e.date.month, e.date.day);
      final s = DateTime(startDate.year, startDate.month, startDate.day);
      final en = DateTime(endDate.year, endDate.month, endDate.day);
      return !d.isBefore(s) && !d.isAfter(en);
    }).toList();
    if (category != 'All') {
      list = list.where((e) => e.category == category).toList();
    }
    switch (sortBy) {
      case 'amount':
        list.sort((a, b) => b.amount.compareTo(a.amount));
        break;
      case 'payee':
        list.sort((a, b) => a.name.compareTo(b.name));
        break;
      default:
        list.sort((a, b) => b.date.compareTo(a.date));
    }
    return list;
  }

  double calculateMonthTotal(List<Expense> expenses) {
    return expenses.where((e) => e.type == 'debit').fold(0.0, (s, e) => s + e.amount);
  }

  double calculateMonthIncome(List<Expense> expenses) {
    return expenses.where((e) => e.type == 'credit').fold(0.0, (s, e) => s + e.amount);
  }

  double calculateTodayTotal(List<Expense> expenses) {
    final now = DateTime.now();
    return expenses
        .where((e) => e.date.day == now.day && e.date.month == now.month && e.date.year == now.year && e.type == 'debit')
        .fold(0.0, (s, e) => s + e.amount);
  }

  Map<String, double> calculateCategoryTotals(List<Expense> expenses) {
    final map = <String, double>{};
    for (final e in expenses.where((e) => e.type == 'debit')) {
      map[e.category] = (map[e.category] ?? 0) + e.amount;
    }
    map.removeWhere((_, v) => v == 0);
    return map;
  }

  Map<String, double> calculateAppTotals(List<Expense> expenses) {
    final map = <String, double>{};
    for (final e in expenses.where((e) => e.type == 'debit')) {
      map[e.upiApp] = (map[e.upiApp] ?? 0) + e.amount;
    }
    return map;
  }

  Map<String, double> calculateMerchantTotals(List<Expense> expenses) {
    final map = <String, double>{};
    for (final e in expenses.where((e) => e.type == 'debit')) {
      map[e.name] = (map[e.name] ?? 0) + e.amount;
    }
    final sorted = Map.fromEntries(map.entries.toList()..sort((a, b) => b.value.compareTo(a.value)));
    return Map.fromEntries(sorted.entries.take(5));
  }

  List<double> calculateWeekdayTotals(List<Expense> expenses) {
    final totals = List<double>.filled(7, 0);
    for (final e in expenses.where((e) => e.type == 'debit')) {
      final wd = e.date.weekday % 7;
      totals[wd] += e.amount;
    }
    return totals;
  }

  Map<String, double> extractCurrentBalances(List<Expense> expenses) {
    final map = <String, double>{};
    final sorted = List<Expense>.from(expenses)..sort((a, b) => b.date.compareTo(a.date));
    for (final e in sorted) {
      if (e.accountName != null && e.accountBalance != null) {
        if (!map.containsKey(e.accountName)) {
          map[e.accountName!] = e.accountBalance!;
        }
      }
    }
    return map;
  }
}
