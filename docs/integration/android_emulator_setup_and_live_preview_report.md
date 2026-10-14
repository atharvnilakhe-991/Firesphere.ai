# FireSphere.ai — Android Emulator Setup & Live Mobile Preview Report

**Date:** October 14, 2026  
**Environment:** Windows 11 Home / Pro (Intel 13th Gen Core i7-13620H)  
**Workspace Root:** `C:\Users\athar\OneDrive\Desktop\Firesphere.ai`  
**Mobile App Directory:** `C:\Users\athar\OneDrive\Desktop\Firesphere.ai\M1_work\mobile`  
**Monorepo Workspace Root:** `C:\Users\athar\OneDrive\Desktop\Firesphere.ai\M1_work`  

---

## 1. Executive Summary

The Android emulation environment has been detected, installed, configured, and launched from official Google distribution channels without third-party installers or destructive modifications. 

The Android emulator (`FireSphere_Pixel`) booted successfully using native Windows Hypervisor Platform (WHPX) hardware virtualization. The existing FireSphere.ai React Native / Expo application was built and bundled via Metro (791 modules), loaded in Expo Go inside the running Android emulator, and verified live on the Windows desktop. Full UI rendering, theme tokens, and navigation flows (Login screen, Citizen Services dashboard, and Fire NOC Services tab) were verified visually with zero fatal runtime errors.

---

## 2. Phase 1 — Environment Inspection Results

### Initial System State
- **Operating System:** Windows 11 (build 10.0.26300)
- **CPU:** 13th Gen Intel Core i7-13620H
- **Hardware Virtualization:** Enabled in firmware. Windows Hypervisor Platform / Hyper-V Host Compute Service (`vmcompute`) was active and running.
- **Available Disk Space:** 28.56 GB free on drive `C:\`.
- **Node.js & npm:**
  - `node`: `v24.19.0`
  - `npm`: `11.17.0`
- **Java / JDK:**
  - Oracle JDK: `26.0.2.1` (build 26.0.2.1+1-7, 64-bit)
- **Winget Package Manager:** `v1.29.380` available.
- **Initial Android Components:**
  - `where.exe adb`: Not found.
  - `where.exe emulator`: Not found.
  - `$env:ANDROID_HOME` & `$env:ANDROID_SDK_ROOT`: Unset.
  - Standard Android directories (`C:\Users\athar\AppData\Local\Android\Sdk`): Missing.
  - Virtual Devices (AVDs): None.

### Mobile Project Configuration
- **Package Architecture:** npm workspace monorepo (`M1_work` root managing `admin-console` and `mobile`).
- **Expo Version:** `~57.0.27` (`expo` package in root `node_modules` at `57.0.27`, CLI at `57.0.28`).
- **React Native Version:** `0.86.3`
- **React Version:** `19.2.3`
- **TypeScript:** Configured and validated (`tsc --noEmit` exits with 0 errors).
- **Entry Point:** `M1_work/mobile/index.ts` -> `M1_work/mobile/src/App.tsx`
- **Native Capabilities Configured in `app.json`:**
  - `expo-secure-store` (Android backup enabled)
  - `expo-image-picker` (Camera & evidence permissions)
  - `expo-location` (Location recording for service requests)
  - `expo-sqlite` (Encrypted SQLite on-device storage)
  - `expo-notifications`

---

## 3. Phase 2 — Components Installed and Configured

To conserve disk space and avoid interactive UAC installer hangs, the official Google Android Command-Line Tools were obtained directly from `https://dl.google.com/android/repository/` and installed to the canonical Android SDK directory:

1. **Android SDK Command-Line Tools (`cmdline-tools:latest`):**
   - Package: `commandlinetools-win-15859902_latest.zip` (v22.0)
   - Location: `C:\Users\athar\AppData\Local\Android\Sdk\cmdline-tools\latest`
2. **Android SDK Platform-Tools:**
   - Version: `37.0.1` (`adb.exe` v1.0.41)
   - Location: `C:\Users\athar\AppData\Local\Android\Sdk\platform-tools`
3. **Android Emulator:**
   - Version: `37.2.12.0` (with gfxstream / WHPX native hypervisor support)
   - Location: `C:\Users\athar\AppData\Local\Android\Sdk\emulator`
4. **Android SDK Platform:**
   - Platform: `platforms;android-34` (Android 14 API level 34)
   - Location: `C:\Users\athar\AppData\Local\Android\Sdk\platforms\android-34`
5. **Android System Image:**
   - Image: `system-images;android-34;google_apis;x86_64` (rev 14)
   - Location: `C:\Users\athar\AppData\Local\Android\Sdk\system-images\android-34\google_apis\x86_64`
6. **Licenses:**
   - All 8 official Android SDK package licenses accepted via `sdkmanager --licenses`.
7. **Environment Variables Configured:**
   - `ANDROID_HOME = C:\Users\athar\AppData\Local\Android\Sdk` (persisted to User environment)
   - `ANDROID_SDK_ROOT = C:\Users\athar\AppData\Local\Android\Sdk` (persisted to User environment)
   - User `Path` updated with:
     * `C:\Users\athar\AppData\Local\Android\Sdk\cmdline-tools\latest\bin`
     * `C:\Users\athar\AppData\Local\Android\Sdk\platform-tools`
     * `C:\Users\athar\AppData\Local\Android\Sdk\emulator`
8. **Disk Space Preserved:** 18.09 GB remaining on drive `C:\`.

---

## 4. Phase 3 — Android Virtual Device (AVD) Configuration

- **AVD Name:** `FireSphere_Pixel`
- **Device Profile:** Google Pixel 6 (`pixel_6`)
- **System Image:** `Google APIs Intel x86_64 Atom System Image (android-34)`
- **ABI:** `x86_64`
- **Acceleration Check:** `emulator -accel-check` confirmed `WHPX(10.0.26300) is installed and usable.`
- **GPU Acceleration:** `hw.gpu.enabled=yes`, `hw.gpu.mode=auto`
- **Boot Verification:**
  - `adb wait-for-device` passed.
  - `adb shell getprop sys.boot_completed` returned `1`.
  - Identified by ADB: `emulator-5554 device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:1`
  - Emulator window rendered visibly on the Windows 11 desktop.

---

## 5. Phase 4 & 5 — Mobile Preview & Dev Server Launch

- **Port Forwarding / ADB Reverse:**
  - `adb reverse tcp:8081 tcp:8081` (Metro bundler forward)
  - `adb reverse tcp:8080 tcp:8080` (Spring Boot backend forward)
- **Expo Development Server:**
  - Started via: `npx expo start --android` from `M1_work\mobile`
  - Bundler: Metro Bundler listening on port 8081
  - Client: Expo Go installed automatically on `FireSphere_Pixel`
  - Module compilation: `Android Bundled 13871ms mobile\index.ts (791 modules)`
- **App Launch:**
  - The FireSphere mobile application launched inside Expo Go on `FireSphere_Pixel`.
  - Initial screen rendered the FireSphere secure access login screen with organization branding.
  - Built-in `__DEV__` controls enabled testing of Citizen and Officer roles with local storage fixtures.

---

## 6. Phase 6 — Verification Checklist

| Item | Status | Evidence / Verification Method |
|---|---|---|
| Android Studio / SDK tools confirmed | **PASS** | Official cmdline-tools v22.0 in `AppData\Local\Android\Sdk` |
| Android SDK configured | **PASS** | `ANDROID_HOME` & `ANDROID_SDK_ROOT` set; licenses accepted |
| ADB available | **PASS** | `adb.exe` v1.0.41 (Platform-Tools v37.0.1) functional on PATH |
| Android emulator installed | **PASS** | `emulator.exe` v37.2.12.0 installed with WHPX acceleration |
| AVD exists and boots successfully | **PASS** | `FireSphere_Pixel` (Android 34 x86_64) booted; `sys.boot_completed=1` |
| Emulator visible on Windows desktop | **PASS** | Emulator process running in native Windows GUI window |
| Expo development server running | **PASS** | Metro running on `http://localhost:8081` (791 modules bundled) |
| FireSphere.ai mobile app opens | **PASS** | Expo Go launched `com.organization.firesphere` |
| Actual app screens render without fatal error | **PASS** | Login card, status bar, branding, inputs, and buttons rendered cleanly |
| Navigation can be tested | **PASS** | Navigated from Login -> Citizen Services dashboard -> Fire NOC Services |
| API/backend connectivity checked | **PASS** | Known offline state reported accurately ("Preview data is local only") |
| Known backend-unavailable states handled | **PASS** | Fails closed with descriptive user message; demo mode available |
| No existing UI or project files overwritten | **PASS** | All existing source, config, and Stage 12 work preserved intact |

---

## 7. Backend Connectivity Notice

- **Host Mapping:** The Android emulator uses `10.0.2.2` to refer to the Windows host computer. Additionally, `adb reverse tcp:8080 tcp:8080` was executed so requests targeting `http://localhost:8080` from inside the emulator route directly to the laptop's local Spring Boot service.
- **Current Backend State:** Ports 8080 (backend) and 5432 (PostgreSQL) were scanned and found not currently listening.
- **Handling in Mobile App:**
  - In `src/App.tsx`, when the backend is unavailable or unconfigured, the app gracefully presents the Development Preview controls (`Open citizen demo` and `Open officer demo`).
  - Citizen and Officer flows persist inspection forms and drafts into encrypted on-device SQLite storage (`expo-sqlite`).

---

## 8. Exact Commands to Start the Live Mobile Preview Next Time

Whenever you want to start the live preview again, execute the following commands in PowerShell:

### Step 1: Start the Android Emulator (Background or Separate Terminal)
```powershell
& "C:\Users\athar\AppData\Local\Android\Sdk\emulator\emulator.exe" -avd FireSphere_Pixel
```

### Step 2: Ensure Port Forwarding (Once emulator appears in `adb devices`)
```powershell
adb reverse tcp:8081 tcp:8081
adb reverse tcp:8080 tcp:8080
```

### Step 3: Start the Expo Development Server
```powershell
cd C:\Users\athar\OneDrive\Desktop\Firesphere.ai\M1_work\mobile
npx expo start --android
```
*(Or from the monorepo root `C:\Users\athar\OneDrive\Desktop\Firesphere.ai\M1_work`: `npm run dev:mobile`)*

---

## 9. Manual Setup Remaining

**None.** The Android SDK, platform tools, emulator, AVD, and Expo mobile preview are completely installed, configured, and operational.
