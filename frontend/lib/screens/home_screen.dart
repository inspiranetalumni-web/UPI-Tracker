import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../providers/expense_viewmodel.dart';
import '../utils/app_theme.dart';
import '../widgets/common_widgets.dart';
import 'package:permission_handler/permission_handler.dart';
import 'settings_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});
  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  bool _showLine = false;

  @override
  void initState() {
    super.initState();
    _requestNotificationPermission();
  }

  Future<void> _requestNotificationPermission() async {
    final status = await Permission.notification.status;
    if (status.isDenied) {
      await Permission.notification.request();
    }
  }

  @override
  Widget build(BuildContext context) {
    final p    = context.watch<ExpenseViewModel>();
    final days = p.filterEndDate.difference(p.filterStartDate).inDays + 1;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    // Daily data (mapped by days since start date)
    final dailyMap = <int, double>{};
    for (final e in p.dateRangeExpenses) {
      // Create date-only references to compare correctly ignoring time
      final d = DateTime(e.date.year, e.date.month, e.date.day);
      final s = DateTime(p.filterStartDate.year, p.filterStartDate.month, p.filterStartDate.day);
      int dayIndex = d.difference(s).inDays;
      if (dayIndex >= 0 && dayIndex < days) {
        dailyMap[dayIndex] = (dailyMap[dayIndex] ?? 0) + e.amount;
      }
    }
    final maxDaily = dailyMap.isEmpty ? 0.0 : dailyMap.values.fold(0.0, (a, b) => a > b ? a : b);

    final cats    = p.categoryTotals.entries.toList()..sort((a, b) => b.value.compareTo(a.value));
    final maxCat  = cats.isEmpty ? 1.0 : cats.first.value;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Overview'),
        actions: [
          // Month picker
          Padding(
            padding: const EdgeInsets.only(right: 4),
            child: TextButton.icon(
              icon: Icon(
                Icons.calendar_month,
                color: Theme.of(context).colorScheme.onSurface,
              ),
              label: Text(
                '${DateFormat('MMM d').format(p.filterStartDate)} - ${DateFormat('MMM d').format(p.filterEndDate)}',
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: Theme.of(context).colorScheme.onSurface,
                ),
              ),
              onPressed: () async {
                DateTime firstAllowed = DateTime(2020);
                if (p.trackedMonths.isNotEmpty) {
                  int minYear = p.trackedMonths.map((m) => m['year']!).reduce((a, b) => a < b ? a : b);
                  int minMonth = p.trackedMonths.where((m) => m['year'] == minYear).map((m) => m['month']!).reduce((a, b) => a < b ? a : b);
                  firstAllowed = DateTime(minYear, minMonth, 1);
                }
                
                final now = DateTime.now();
                DateTime initialStart = p.filterStartDate;
                DateTime initialEnd = p.filterEndDate;
                
                if (initialStart.isBefore(firstAllowed)) initialStart = firstAllowed;
                if (initialEnd.isAfter(now)) initialEnd = now;

                final picked = await showDateRangePicker(
                  context: context,
                  initialDateRange: DateTimeRange(start: initialStart, end: initialEnd),
                  firstDate: firstAllowed,
                  lastDate: now,
                );
                if (picked != null) {
                  p.setDateRange(picked.start, picked.end);
                }
              },
            ),
          ),
          // Settings button
          IconButton(
            icon: const Icon(Icons.settings, size: 20),
            tooltip: 'Settings',
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const SettingsScreen()),
            ),
          ),
        ],
      ),

      // #15 — Show error state when loading fails
      body: p.loading
          ? const Center(child: CircularProgressIndicator())
          : p.error != null && p.dateRangeExpenses.isEmpty
              ? Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
                  const Icon(Icons.wifi_off_rounded, size: 48, color: Color(0xFF888780)),
                  const SizedBox(height: 12),
                  Text(p.error!, textAlign: TextAlign.center, style: const TextStyle(color: Color(0xFF888780))),
                  const SizedBox(height: 16),
                  ElevatedButton.icon(
                    onPressed: p.load,
                    icon: const Icon(Icons.refresh),
                    label: const Text('Retry'),
                  ),
                ]))
              : RefreshIndicator(
                  onRefresh: p.load,
                  child: ListView(padding: const EdgeInsets.fromLTRB(16, 16, 16, 100), children: [

                    // ── Live Bank Balances ─────────────────────────────
                    if (p.currentBalances.isNotEmpty) ...[
                      const SectionHeader(title: 'MY ACCOUNTS'),
                      SizedBox(
                        height: 70,
                        child: ListView(
                          scrollDirection: Axis.horizontal,
                          children: p.currentBalances.entries.map((e) => Container(
                            margin: const EdgeInsets.only(right: 12),
                            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                            decoration: BoxDecoration(
                              color: isDark ? const Color(0xFF1E1E1E) : Colors.white,
                              borderRadius: BorderRadius.circular(12),
                              border: Border.all(color: Theme.of(context).dividerColor.withValues(alpha: 0.5)),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Text(e.key, style: const TextStyle(fontSize: 12, color: Color(0xFF888780))),
                                const SizedBox(height: 4),
                                Text(fmtAmt(e.value), style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                              ],
                            ),
                          )).toList(),
                        ),
                      ),
                      const SizedBox(height: 20),
                    ],

                    // ── Metric cards ──────────────────────────────────
                    GridView.count(
                      crossAxisCount: 2, shrinkWrap: true, physics: const NeverScrollableScrollPhysics(),
                      mainAxisSpacing: 10, crossAxisSpacing: 10, childAspectRatio: 1.6,
                      children: [
                        MetricCard(label: 'Total spent', value: fmtAmt(p.monthTotal), sub: '${p.dateRangeExpenses.length} transactions', valueColor: AppTheme.danger),
                        MetricCard(label: 'Total income', value: fmtAmt(p.monthIncome), sub: 'This month', valueColor: AppTheme.success),
                        MetricCard(label: 'Daily spent', value: fmtAmt(p.todayTotal), sub: 'Today', valueColor: AppTheme.warning),
                        MetricCard(label: 'Largest txn', value: fmtAmt(p.maxExpense?.amount ?? 0), sub: p.maxExpense?.name ?? '—'),
                      ],
                    ),
                    const SizedBox(height: 20),

                    // ── Category breakdown ────────────────────────────
                    if (cats.isNotEmpty) ...[
                      const SectionHeader(title: 'SPENDING BY CATEGORY'),
                      ...cats.map((e) => BarRow(
                        label: e.key, value: e.value, maxValue: maxCat,
                        color: AppColors.category[e.key] ?? const Color(0xFF888780),
                      )),
                      const SizedBox(height: 20),
                    ],

                    // ── Daily trend chart ──────────────────────────────
                    SectionHeader(
                      title: 'DAILY TREND',
                      trailing: Row(children: [
                        _chartToggle('Bar', !_showLine, () => setState(() => _showLine = false)),
                        const SizedBox(width: 6),
                        _chartToggle('Line', _showLine,  () => setState(() => _showLine = true)),
                      ]),
                    ),
                    SizedBox(
                      height: 160,
                      child: _showLine
                          ? LineChart(LineChartData(
                              lineTouchData: LineTouchData(
                                touchTooltipData: LineTouchTooltipData(
                                  getTooltipColor: (_) => isDark ? const Color(0xFF2C2C2C) : Colors.white,
                                  tooltipBorder: BorderSide(
                                    color: isDark ? Colors.white.withValues(alpha: 0.15) : Colors.black.withValues(alpha: 0.1),
                                    width: 1,
                                  ),
                                  getTooltipItems: (touchedSpots) {
                                    return touchedSpots.map((spot) {
                                      return LineTooltipItem(
                                        'Day ${spot.x.toInt()}\n₹${spot.y.toStringAsFixed(2)}',
                                        TextStyle(
                                          color: isDark ? Colors.white : Colors.black,
                                          fontWeight: FontWeight.bold,
                                          fontSize: 12,
                                        ),
                                      );
                                    }).toList();
                                  },
                                ),
                              ),
                                lineBarsData: [LineChartBarData(
                                  spots: List.generate(days, (i) => FlSpot(i.toDouble(), dailyMap[i] ?? 0)),
                                  isCurved: true, color: AppTheme.primary, barWidth: 2,
                                dotData: const FlDotData(show: false),
                                belowBarData: BarAreaData(show: true, color: AppTheme.primary.withValues(alpha: 0.08)),
                              )],
                              titlesData: _chartTitles(context, days, maxDaily),
                              gridData: FlGridData(
                                show: true,
                                drawVerticalLine: false, 
                                horizontalInterval: maxDaily > 0 ? (maxDaily / 4).ceilToDouble() : 100,
                                getDrawingHorizontalLine: (_) => FlLine(
                                  color: Theme.of(context).colorScheme.outlineVariant.withValues(alpha: 0.3), 
                                  strokeWidth: 1, 
                                  dashArray: [4, 4]
                                ),
                              ),
                              borderData: FlBorderData(show: false),
                            ))
                          : BarChart(BarChartData(
                              barTouchData: BarTouchData(
                                touchTooltipData: BarTouchTooltipData(
                                  getTooltipColor: (_) => isDark ? const Color(0xFF2C2C2C) : Colors.white,
                                  tooltipBorder: BorderSide(
                                    color: isDark ? Colors.white.withValues(alpha: 0.15) : Colors.black.withValues(alpha: 0.1),
                                    width: 1,
                                  ),
                                  getTooltipItem: (group, groupIndex, rod, rodIndex) {
                                    return BarTooltipItem(
                                      'Day ${group.x}\n₹${rod.toY.toStringAsFixed(2)}',
                                      TextStyle(
                                        color: isDark ? Colors.white : Colors.black,
                                        fontWeight: FontWeight.bold,
                                        fontSize: 12,
                                      ),
                                    );
                                  },
                                ),
                              ),
                                barGroups: List.generate(days, (i) => BarChartGroupData(
                                  x: i,
                                  barRods: [BarChartRodData(toY: dailyMap[i] ?? 0, color: AppTheme.primary.withValues(alpha: 0.5), width: 6, borderRadius: BorderRadius.circular(3))],
                                )),
                              titlesData: _chartTitles(context, days, maxDaily),
                              gridData: FlGridData(
                                show: true,
                                drawVerticalLine: false, 
                                horizontalInterval: maxDaily > 0 ? (maxDaily / 4).ceilToDouble() : 100,
                                getDrawingHorizontalLine: (_) => FlLine(
                                  color: Theme.of(context).colorScheme.outlineVariant.withValues(alpha: 0.3), 
                                  strokeWidth: 1, 
                                  dashArray: [4, 4]
                                ),
                              ),
                              borderData: FlBorderData(show: false),
                              maxY: maxDaily > 0 ? maxDaily * 1.2 : 100,
                            )),
                    ),
                    const SizedBox(height: 20),

                    // ── Recent transactions ────────────────────────────
                    SectionHeader(
                      title: 'RECENT',
                      trailing: TextButton(
                        // #5 fix — navigate via provider.setTab()
                        onPressed: () => context.read<ExpenseViewModel>().setTab(1),
                        child: const Text('See all', style: TextStyle(fontSize: 12)),
                      ),
                    ),
                    if (p.dateRangeExpenses.isEmpty)
                      const EmptyState(message: 'No transactions in this period')
                    else
                      ...p.dateRangeExpenses.take(5).map((e) => Column(children: [
                        TxnTile(
                          expense: e,
                          onDelete: e.id != null ? () async {
                            final err = await p.deleteExpense(e.id!);
                            if (err != null && context.mounted) {
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(content: Text(err), backgroundColor: Colors.red.shade700),
                              );
                            }
                          } : null,
                        ),
                        const Divider(height: 0.5),
                      ])),
                  ]),
                ),
    );
  }

  Widget _chartToggle(String label, bool active, VoidCallback onTap) => GestureDetector(
    onTap: onTap,
    child: Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: active ? AppTheme.primary : Colors.transparent,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppTheme.primary.withValues(alpha: 0.3)),
      ),
      child: Text(label, style: TextStyle(fontSize: 11, color: active ? Colors.white : AppTheme.primary)),
    ),
  );

  FlTitlesData _chartTitles(BuildContext context, int days, double maxDaily) => FlTitlesData(
    leftTitles: AxisTitles(
      sideTitles: SideTitles(
        showTitles: true,
        reservedSize: 40,
        getTitlesWidget: (v, meta) {
          if (v == meta.max || v == meta.min) return const SizedBox.shrink();
          return Text(
            '₹${v.toInt()}', 
            style: TextStyle(fontSize: 10, color: Theme.of(context).colorScheme.onSurfaceVariant),
            textAlign: TextAlign.left,
          );
        },
      ),
    ),
    rightTitles:  const AxisTitles(sideTitles: SideTitles(showTitles: false)),
    topTitles:    const AxisTitles(sideTitles: SideTitles(showTitles: false)),
    bottomTitles: AxisTitles(sideTitles: SideTitles(showTitles: true, reservedSize: 22,
      getTitlesWidget: (v, _) => v % 5 == 0 ? Text('${v.toInt()}', style: TextStyle(fontSize: 10, color: Theme.of(context).colorScheme.onSurfaceVariant)) : const SizedBox(),
    )),
  );
}
