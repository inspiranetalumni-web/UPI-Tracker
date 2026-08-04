import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../providers/expense_viewmodel.dart';
import '../utils/app_theme.dart';
import '../widgets/common_widgets.dart';

class InsightsScreen extends StatelessWidget {
  const InsightsScreen({super.key});

  static const _weekdays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

  @override
  Widget build(BuildContext context) {
    final p       = context.watch<ExpenseViewModel>();
    final dayTotals = p.weekdayTotals;
    final maxDay    = dayTotals.fold(0.0, (a, b) => a > b ? a : b);
    final merchants = p.merchantTotals;
    final maxM    = merchants.isNotEmpty ? merchants.values.reduce((a, b) => a > b ? a : b) : 1.0;
    // #21 — theme-aware chart colors
    final isDark  = Theme.of(context).brightness == Brightness.dark;
    final gridLine = isDark ? const Color(0xFF2A2A2A) : const Color(0xFFF1EFE8);

    return Scaffold(
      appBar: AppBar(title: const Text('Insights')),
      body: RefreshIndicator(  // #17
        onRefresh: p.load,
        child: ListView(padding: const EdgeInsets.fromLTRB(16, 16, 16, 100), children: [

        // ── Smart insight cards ───────────────────────
        const SectionHeader(title: 'SMART INSIGHTS'),
        if (p.aiInsight == null)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 20),
            child: Center(child: CircularProgressIndicator()),
          )
        else
          Card(
            margin: const EdgeInsets.only(bottom: 20),
            elevation: 0,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
              side: BorderSide(color: Theme.of(context).colorScheme.outlineVariant.withValues(alpha: 0.5)),
            ),
            child: Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(16),
                gradient: LinearGradient(
                  colors: isDark 
                      ? [const Color(0xFF162032), const Color(0xFF1E2D4A)]
                      : [const Color(0xFFF0F4FA), const Color(0xFFE2EAF4)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.auto_awesome, size: 20, color: isDark ? Colors.amberAccent : Colors.amber.shade700),
                      const SizedBox(width: 8),
                      Text('AI Insight', style: TextStyle(
                        fontSize: 14, 
                        fontWeight: FontWeight.bold,
                        letterSpacing: 0.5,
                        color: isDark ? Colors.white : Colors.black87,
                      )),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Text(
                    p.aiInsight!, 
                    style: TextStyle(
                      fontSize: 15, 
                      height: 1.5,
                      color: isDark ? Colors.white.withValues(alpha: 0.9) : Colors.black87,
                    ),
                  ),
                ],
              ),
            ),
          ),

        // ── Top merchants ─────────────────────────────
        const SectionHeader(title: 'TOP MERCHANTS'),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              children: merchants.entries.map((e) {
                final cat    = p.expenses.where((ex) => ex.name == e.key).map((ex) => ex.category).firstOrNull ?? 'Other';
                final col    = AppColors.category[cat] ?? const Color(0xFF888780);
                final ico    = AppIcons.category[cat]  ?? Icons.more_horiz;
                return Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: Row(children: [
                    Icon(ico, size: 16, color: col),
                    const SizedBox(width: 8),
                    SizedBox(width: 80, child: Text(e.key, style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant), overflow: TextOverflow.ellipsis)),
                    Expanded(child: LinearProgressIndicator(
                      value: e.value / maxM, minHeight: 8,
                      borderRadius: BorderRadius.circular(4),
                      backgroundColor: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.08),
                      valueColor: AlwaysStoppedAnimation(col),
                    )),
                    const SizedBox(width: 8),
                    SizedBox(width: 60, child: Text(fmtAmt(e.value), style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: col), textAlign: TextAlign.right)),
                  ]),
                );
              }).toList(),
            ),
          ),
        ),
        const SizedBox(height: 20),

        // ── Weekly pattern ────────────────────────────
        const SectionHeader(title: 'WEEKLY PATTERN'),
        Card(
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
            side: BorderSide(color: Theme.of(context).colorScheme.outlineVariant.withValues(alpha: 0.3)),
          ),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 24, 16, 12),
            child: SizedBox(
              height: 180,
              child: BarChart(BarChartData(
                barTouchData: BarTouchData(
                  enabled: true,
                  touchTooltipData: BarTouchTooltipData(
                    getTooltipColor: (_) => isDark ? const Color(0xFF2C2C2C) : Colors.white,
                    tooltipBorder: BorderSide(
                      color: isDark ? Colors.white.withValues(alpha: 0.15) : Colors.black.withValues(alpha: 0.1),
                      width: 1,
                    ),
                    getTooltipItem: (group, groupIndex, rod, rodIndex) {
                      return BarTooltipItem(
                        '${_weekdays[group.x]}\n₹${rod.toY.toStringAsFixed(0)}',
                        TextStyle(
                          color: isDark ? Colors.white : Colors.black,
                          fontWeight: FontWeight.bold,
                          fontSize: 12,
                        ),
                      );
                    },
                  ),
                ),
                barGroups: List.generate(7, (i) => BarChartGroupData(
                  x: i,
                  barRods: [BarChartRodData(
                    toY: dayTotals[i],
                    color: AppTheme.primary,
                    width: 18, 
                    borderRadius: BorderRadius.circular(2), // Classic flat/slightly rounded tops
                  )],
                )),
                titlesData: FlTitlesData(
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
                  rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                  topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                  bottomTitles: AxisTitles(sideTitles: SideTitles(
                    showTitles: true, reservedSize: 28,
                    getTitlesWidget: (v, _) => Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Text(_weekdays[v.toInt()], style: TextStyle(fontSize: 11, color: Theme.of(context).colorScheme.onSurfaceVariant)),
                    ),
                  )),
                ),
                gridData: FlGridData(
                  show: true,
                  drawVerticalLine: false, 
                  horizontalInterval: maxDay > 0 ? (maxDay / 4).ceilToDouble() : 100,
                  getDrawingHorizontalLine: (_) => FlLine(color: gridLine, strokeWidth: 1, dashArray: [4, 4]),
                ),
                borderData: FlBorderData(show: false),
                maxY: maxDay > 0 ? maxDay * 1.2 : 100,
              )),
            ),
          ),
        ),
        const SizedBox(height: 30),
        ]),  // end ListView
      ),     // end RefreshIndicator
    );
  }
}


