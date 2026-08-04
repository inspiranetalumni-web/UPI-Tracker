# UPI Tracker - AI Agent Guidelines (`agents.md`)

Welcome! You are assisting with **UPI Tracker**, an automated, zero-friction expense tracking application.

## 1. Project Overview
UPI Tracker automatically reads SMS and push notifications from UPI apps (GPay, PhonePe, Paytm, BHIM, etc.) running in the background, extracts the transaction details, categorizes them, and securely syncs them to a cloud backend. The user relies on this app to build a financial dashboard without manual data entry.

## 2. Technology Stack
- **Frontend**: Flutter (Dart)
- **State Management**: `provider`
- **Native Android**: Kotlin (`NotificationListenerService` and `MethodChannels`)
- **Backend**: Java 21, Spring Boot 4.1.0
- **Database**: MongoDB (via Spring Data)
- **Authentication**: Firebase Authentication (Phone/OTP & JWT verification on backend)
- **API Client**: `dio`

## 3. Directory Structure
```
E:\UPI-Tracker\
  ├── frontend/                 # Flutter application
  │   ├── android/app/src/main/kotlin/com/careersync/app/  # Native Android code
  │   ├── lib/
  │   │   ├── models/           # Data models (expense.dart)
  │   │   ├── providers/        # State management (expense_provider.dart)
  │   │   ├── screens/          # UI pages
  │   │   ├── services/         # API & Notification services
  │   │   ├── utils/            # Theme & constants
  │   │   └── widgets/          # Reusable UI components
  │   └── .env                  # Environment variables (loaded via flutter_dotenv)
  └── backend/                  # Spring Boot application
      ├── src/main/java/com/upitracker/backend/
      │   ├── controller/       # REST API endpoints
      │   ├── model/            # MongoDB document models (Expense.java)
      │   ├── repository/       # Spring Data Mongo repositories
      │   └── service/          # Business logic and Firebase verification
      └── build.gradle          # Gradle build script
```

## 4. Key Architectural Concepts & Agent Rules

### A. The Native Android Pipeline (Crucial)
The app relies heavily on `UpiNotificationService.kt` for its magic. 
- **Parsing**: It uses Regex to parse SMS for amounts (`AMOUNT_PATTERN`), balances (`BALANCE_PATTERN`), and accounts (`ACCOUNT_PATTERN`).
- **Offline Queue**: If the device is offline, Kotlin saves transactions to a `SharedPreferences` JSON array. 
- **Rule**: If you modify the `Expense` data model, you MUST update the Kotlin JSON payload builder in `UpiNotificationService.kt`, the Java Backend model, and the Flutter `expense.dart` model.

### B. State Management
- Use `ChangeNotifierProvider` and `Selector`/`Consumer` from the `provider` package.
- Avoid wrapping `MaterialApp` with `Consumer<ExpenseProvider>` as it causes full app rebuilds and navigation crashes. Use `Selector` for specific fields (like `themeMode`).
- All network calls must pass through `ApiService` which handles Dio interceptors and JWT tokens.

### C. Error Handling
- The backend returns standard HTTP status codes (401, 403, 422).
- API errors are wrapped in a `DioException`. The `ExpenseProvider` parses the `response.data['error']` to show friendly UI `SnackBars`. 
- **Rule**: Never show raw stack traces to the user. Always parse `DioException` gracefully.

### D. UI/UX Standards
- The app uses a custom theme defined in `app_theme.dart`.
- Use `AppTheme.primary`, `AppTheme.success`, `AppTheme.warning`, and `AppTheme.danger`.
- **Overflow Prevention**: When building grids or lists, always use `TextOverflow.ellipsis`, `maxLines: 1`, and `FittedBox` for dynamic text (like amounts or payees) to prevent `BOTTOM OVERFLOWED` layout errors.

### E. Environment Configuration
- **DO NOT** use `--dart-define` for configurations. We have migrated entirely to `flutter_dotenv`.
- The `.env` file is loaded at startup in `main.dart` and is used to dynamically read the backend URL (`API_BASE_URL`) and Firebase configs.
- The backend uses standard Render environment variables. Do NOT hardcode endpoints or secrets anywhere in the source code.

### F. Date/Time Syncing
- **Rule**: When sending dates from Flutter to the Spring Boot backend, you MUST convert them to UTC and append `Z` (e.g., `date.toUtc().toIso8601String() + 'Z'`). Java's `Instant.parse()` strictly requires the UTC designator and will crash the backend if it is missing.

## 5. Development Commands

### Running the Backend
```powershell
cd E:\UPI-Tracker\backend
.\gradlew bootRun
```

### Running the Frontend
```powershell
cd E:\UPI-Tracker\frontend
flutter run
```

### Building for Production
```powershell
flutter build apk --release
```

## 6. Current Feature State
- [x] Firebase Auth & JWT Sync
- [x] Background SMS Parsing
- [x] Offline Transaction Queue
- [x] Multiple Bank Account Detection & Live Balances
- [x] Dynamic Calendar Filtering for Past Records
- [x] Production Ready (Dockerized Spring Boot & Secure APK)
- [ ] iOS Support (Currently Android-only due to NotificationListenerService)
