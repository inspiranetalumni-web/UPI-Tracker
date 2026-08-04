import 'package:firebase_core/firebase_core.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'firebase_options.dart';
import 'providers/expense_viewmodel.dart';
import 'providers/theme_provider.dart';
import 'screens/home_screen.dart';
import 'screens/transactions_screen.dart';
import 'screens/add_expense_screen.dart';
import 'screens/budget_screen.dart';
import 'screens/insights_screen.dart';
import 'screens/login_screen.dart';
import 'services/api_service.dart';
import 'services/notification_service.dart';
import 'utils/app_theme.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  try {
    await dotenv.load(fileName: ".env");
  } catch (e) {
    debugPrint("Warning: failed to load .env file: $e");
  }
  String apiBaseUrl = dotenv.env['API_BASE_URL'] ?? '';

  try {
    await Firebase.initializeApp(
      options: DefaultFirebaseOptions.currentPlatform,
    );
  } catch (e) {
    if (e.toString().contains('duplicate-app')) {
      await Firebase.apps.isEmpty ? await Firebase.initializeApp() : null;
    } else {
      rethrow;
    }
  }

  // Persist into SharedPreferences so the Kotlin NotificationListenerService
  // can read the URL at runtime without any hardcoded values.
  final prefs = await SharedPreferences.getInstance();
  await prefs.setString('api_base_url', apiBaseUrl);

  await NotificationService.init();
  await NotificationService.flushOfflineQueue();
  runApp(const UpiTrackerApp());
}

class UpiTrackerApp extends StatelessWidget {
  const UpiTrackerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => ThemeProvider()),
        ChangeNotifierProvider(create: (_) => ExpenseViewModel()..initNotificationListener()),
      ],
      child: Consumer<ThemeProvider>(
        builder: (context, themeProvider, _) => MaterialApp(
          title: 'UPI Tracker',
          debugShowCheckedModeBanner: false,
          theme: AppTheme.light(),
          darkTheme: AppTheme.dark(),
          themeMode: themeProvider.themeMode,
          home: const _Splash(),
          routes: {
            '/home':  (_) => const MainShell(),
            '/login': (_) => const LoginScreen(),
          },
        ),
      ),
    );
  }
}

class _Splash extends StatefulWidget {
  const _Splash();
  @override
  State<_Splash> createState() => _SplashState();
}

class _SplashState extends State<_Splash> {
  @override
  void initState() {
    super.initState();
    _check();
  }

  Future<void> _check() async {
    await Future.delayed(const Duration(milliseconds: 400));
    final ok = await ApiService().isLoggedIn();
    if (mounted) {
      if (ok) {
        Navigator.pushReplacementNamed(context, '/home');
      } else {
        Navigator.pushReplacementNamed(context, '/login');
      }
    }
  }

  @override
  Widget build(BuildContext context) => const Scaffold(
    body: Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
      Icon(Icons.account_balance_wallet_outlined, size: 48, color: AppTheme.primary),
      SizedBox(height: 16),
      CircularProgressIndicator(color: AppTheme.primary, strokeWidth: 2),
    ])),
  );
}

class MainShell extends StatefulWidget {
  const MainShell({super.key});
  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<ExpenseViewModel>().load();
    });
  }

  final _screens = [
    const HomeScreen(),
    const TransactionsScreen(),
    const AddExpenseScreen(),
    const BudgetScreen(),
    const InsightsScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    final p = context.watch<ExpenseViewModel>();
    return Scaffold(
      body: IndexedStack(index: p.currentTab, children: _screens),
      bottomNavigationBar: Container(
        decoration: BoxDecoration(
          border: Border(top: BorderSide(color: Theme.of(context).dividerColor, width: 0.5)),
        ),
        child: BottomNavigationBar(
          currentIndex: p.currentTab,
          onTap: (i) => context.read<ExpenseViewModel>().setTab(i),
          items: const [
            BottomNavigationBarItem(icon: Icon(Icons.home_outlined),         activeIcon: Icon(Icons.home),         label: 'Home'),
            BottomNavigationBarItem(icon: Icon(Icons.list_alt_outlined),      activeIcon: Icon(Icons.list_alt),     label: 'Txns'),
            BottomNavigationBarItem(icon: Icon(Icons.add_circle_outline),     activeIcon: Icon(Icons.add_circle),   label: 'Add'),
            BottomNavigationBarItem(icon: Icon(Icons.wallet_outlined),        activeIcon: Icon(Icons.wallet),       label: 'Budget'),
            BottomNavigationBarItem(icon: Icon(Icons.lightbulb_outline),      activeIcon: Icon(Icons.lightbulb),    label: 'Insights'),
          ],
        ),
      ),
    );
  }
}
