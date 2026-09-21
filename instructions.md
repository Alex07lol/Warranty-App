# WarrantyVault Android App – Instructions

This document provides step‑by‑step guidance for **building**, **running**, and **using** the WarrantyVault Android application that lives in this repository.

---

## 1. Prerequisites

| Requirement | Details |
|------------|---------|
| **Operating System** | Windows 11 (or macOS / Linux – paths will differ) |
| **Java Development Kit** | JDK 17 (download from https://adoptium.net) |
| **Android Studio** | Hedgehog (2023.1.1) or newer – includes the Android SDK and Gradle wrapper |
| **Android SDK** | API level 34 (Android 14) installed via Android Studio SDK Manager |
| **Git** | Needed for version control (already installed with the repo) |
| **Internet** | Only required for the first Gradle sync to download dependencies |

Make sure `java -version` reports `17` and that Android Studio’s **Gradle JDK** is set to this version.

---

## 2. Clone the Repository (if you haven’t already)

```bash
# From a command‑line (PowerShell / CMD / Bash)
git clone https://github.com/Alex07lol/Warranty-App.git
cd Warranty-App
```

> The repository already contains a `.gitignore` that excludes build artefacts.

---

## 3. Open the Project in Android Studio

1. Launch Android Studio.
2. Choose **File → Open…** and navigate to the cloned folder `Warranty-App`.
3. Android Studio will automatically run **Gradle sync**. Let it finish – it may download the Gradle wrapper and all dependencies.
4. If prompted, install any missing SDK components (e.g., **Android 14 (API 34)** platform).

---

## 4. Build the Debug APK

You can build from the command line or from Android Studio.

### From Android Studio
1. Select the **Run** configuration “app” (top toolbar).
2. Click the **Run** button (green triangle) or press **Shift+F10**.
3. Choose an emulator or a connected device.
4. Android Studio will compile and install the app on the selected device.

### From the Command Line
```bash
# Ensure you are at the repository root
./gradlew assembleDebug   # Windows users can run 'gradlew.bat assembleDebug'
```
The resulting APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```
You can manually copy this APK to a device and install it with `adb install`:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. Running the App

When the app launches you will see the **Dashboard** showing:
- Total number of products
- Count of active, expiring‑soon, and expired warranties
- A list of products whose warranty is about to expire

Use the bottom navigation (or the top‑right menu) to access:
- **Products** – searchable list of all products.
- **Add/Edit Product** – a multi‑section form to enter product details.
- **Scan OCR** – capture a receipt/warranty card image; the built‑in OCR extracts data automatically.
- **Settings** – overview of the local‑first architecture, privacy notes, and data export options.
- **Notifications** – view generated alerts for warranty expiry or service reminders.

All data is stored locally using **Room**; no network or cloud services are required.

---

## 6. Export / Import (Future Work)

The Settings screen currently contains placeholder buttons for **Export All Data (JSON + CSV)** and **Import Data**. Implementations are planned to use Android’s `MediaStore` APIs to write the exported files to external storage and to read import files back into the app.

---

## 7. Testing

The project contains unit tests for the warranty engine and data layer. To run tests:
```bash
./gradlew test
```
Test results appear under `app/build/reports/tests/`.

---

## 8. Troubleshooting

- **Gradle sync fails** – make sure you have a stable internet connection; delete the `.gradle` folder in the project root and retry.
- **Build errors about missing `KeyboardOptions`** – the project now uses plain `OutlinedTextField` without that import; clean the project (`Build → Clean Project`).
- **APK not installing on device** – enable **Install via USB** in developer options and ensure the device allows installation from unknown sources.

---

## 9. Contributing

Feel free to fork the repository, make improvements, and submit a Pull Request. Follow the existing code style (Kotlin idiomatic, Compose Material‑3 conventions) and run the tests before submitting.

---

## 10. License

The code is released under the **MIT License**. See the `LICENSE` file for full terms.
