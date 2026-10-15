# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 專案定位

StepSim（package `com.seeyu90.stepsim`）是「Health Connect 開發與 QA 測試工具」：讓開發者不用實際搖手機，就能把模擬步數寫入 Health Connect，驗證其他健康類 App 的步數讀取功能。

## 技術棧

- Kotlin + Jetpack Compose + Material 3，Gradle Kotlin DSL，版本集中於 `gradle/libs.versions.toml`
- MVVM + StateFlow
- Health Connect SDK（`androidx.health.connect:connect-client`）
- Foreground Service（type: health）、DataStore
- **刻意不使用**：Room、Hilt、網路層（目前階段不需要，不要引入）
- minSdk 28；單一模組 `:app`；AGP 9.x 使用內建 Kotlin，不要另外加 `kotlin.android` plugin
- 開發環境：Windows + PowerShell；測試用 Android 模擬器（API 34+，Google Play 映像檔）

## 常用指令（PowerShell，於專案根目錄）

```powershell
.\gradlew.bat assembleDebug                 # 建置 debug APK
.\gradlew.bat installDebug                  # 安裝到已連線的模擬器/裝置
.\gradlew.bat testDebugUnitTest             # JVM 單元測試
.\gradlew.bat testDebugUnitTest --tests "com.seeyu90.stepsim.ExampleUnitTest"   # 單一測試
.\gradlew.bat connectedDebugAndroidTest     # instrumented 測試（需裝置）
.\gradlew.bat lint
```

## 架構規則

套件分為 `ui/`、`health/`、`simulator/`、`service/`、`data/`（位於 `com.seeyu90.stepsim` 下，用到時才建立）。

- `simulator/`：**純 Kotlin，不得依賴 Android**，以便直接單元測試。
- Health Connect 相關程式碼**全部封裝在 `health/`**，其他套件不得直接使用 HC SDK 類別。
- Health Connect API 以最新官方文件為準；編譯報錯先查文件再修，不要猜。
- 依賴版本使用目前最新穩定版。

## 開發階段與範圍

嚴格依階段進行，不提前實作後續階段，也不順手重構既有程式。

1. 階段 1：授權骨架（HC 可用性檢查、Manifest 權限、Rationale 頁、請求權限與狀態顯示）
2. 階段 2：模擬核心（步數寫入）
3. 階段 3：背景執行（Foreground Service）
4. 階段 4：MVP 驗收

**目前進度：階段 1 進行中。**
