# Zona (Compose Multiplatform)

Learning app with roles (student/teacher), tests, progress, homework, and live sessions.

## Quick Start (No PostgreSQL)

1. Start backend server (H2 in-file DB):

```powershell
cd "c:\Users\germa\OneDrive\Рабочий стол\диплом"
.\scripts\run-server.ps1
```

2. Verify API is reachable:

```powershell
.\scripts\check-api.ps1
```

3. Start client:
- Desktop: `.\gradlew.bat :composeApp:run`
- Android emulator: run app from Android Studio.

## Important for Android Emulator

- Emulator must use `http://10.0.2.2:8080` to reach server on your PC.
- Server must be running before login.
- If login times out, allow Java in Windows Firewall for port `8080`.

## Demo Accounts

- `admin@zona.local` / `admin123`
- `teacher@zona.local` / `teacher123`
- `student@zona.local` / `student123`

## Optional: PostgreSQL Later

If needed, set:

```powershell
$env:ZONA_JDBC_URL = "jdbc:postgresql://localhost:5432/zona"
$env:ZONA_DB_USER = "zona"
$env:ZONA_DB_PASSWORD = "zona"
.\gradlew.bat :server:run
```
