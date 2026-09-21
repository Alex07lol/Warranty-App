# WarrantyVault Android App

A native Android app for tracking product warranties, receipts, service history, and OCR-extracted warranty data — all stored **locally** on your device using **Room Database**. No cloud account required, no internet needed for core features.

## Features

- **Local-First Architecture**: All data stored in SQLite via Room on your device
- **Product Management**: Add, edit, view, and delete products with warranty details
- **Warranty Status Engine**: Automatic computation of warranty status (Active, Expiring Soon, Expired, Not Started, Unknown)
- **Dashboard**: Overview with stats, expiring warranties, and recent products
- **Receipt/Warranty Card Scanning**: Simulated local OCR for extracting warranty details
- **Notifications**: Local notifications for expiring warranties and service reminders
- **Settings & Privacy**: Clear data ownership information
- **Material 3 Design**: Modern UI with dark/light theme support

## Data Models (matching the web platform)

- **User** - Local user profile
- **Product** - Products with warranty info, lifecycle status, tags
- **WarrantyPeriod** - Multiple coverage periods per product (standard, extended, accidental)
- **Document** - Receipts, warranty cards, manuals with OCR data
- **ServiceHistory** - Repair/maintenance records with next-service dates
- **Notification** - Local alerts for expiries, service due, document processing

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **Database**: Room (SQLite)
- **Async**: Coroutines + Flow
- **Navigation**: Navigation Compose
- **Image Loading**: Coil
- **Serialization**: Kotlinx Serialization
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

## Project Structure

```
app/
├── src/main/
│   ├── java/com/warrantyvault/
│   │   ├── data/           # Room entities, DAOs, Database
│   │   ├── notification/   # Broadcast receivers, workers
│   │   ├── ui/
│   │   │   ├── screens/    # Compose screens
│   │   │   └── theme/      # Material 3 theme
│   │   ├── MainActivity.kt
│   │   ├── WarrantyEngine.kt    # Warranty status computation
│   │   └── WarrantyVaultApplication.kt
│   ├── res/
│   │   ├── values/
│   │   ├── mipmap-anydpi-v26/
│   │   └── xml/
│   └── AndroidManifest.xml
├── build.gradle.kts
└── .gitignore
```

## Building & Running

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34

### Steps
1. Open the project in Android Studio:
   ```
   File → Open → C:\Users\aakas\OneDrive\Desktop\warranty-checker\WarrantyVault-Android
   ```
2. Wait for Gradle sync to complete
3. Run on emulator or device: `Run → Run 'app'` (Shift+F10)

### Command Line Build
```bash
cd C:\Users\aakas\OneDrive\Desktop\warranty-checker\WarrantyVault-Android
./gradlew assembleDebug
```

## Key Differences from Web Version

| Feature | Web (WarrantyVault) | Android App |
|---------|---------------------|-------------|
| **Storage** | MongoDB Atlas + Cloudinary | Local SQLite (Room) |
| **Auth** | JWT + bcrypt | Local user (no login needed) |
| **OCR** | Tesseract.js + MuPDF | Simulated / placeholder for local Tesseract |
| **Notifications** | Server-side cron + push | Local WorkManager (planned) |
| **Sharing** | Secure links | Local export/import (planned) |
| **Repair Centers** | Google Places API | Not included (offline-first) |

## License

MIT — Based on the WarrantyVault web platform.