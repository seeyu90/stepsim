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
.\gradlew.bat testDebugUnitTest --tests "com.seeyu90.stepsim.ui.PermissionViewModelTest"   # 單一測試
.\gradlew.bat connectedDebugAndroidTest     # instrumented 測試（需裝置）
.\gradlew.bat lint
```

## 架構規則

套件分為 `ui/`、`health/`、`simulator/`、`service/`、`data/`（位於 `com.seeyu90.stepsim` 下，用到時才建立）。

- `simulator/`：**純 Kotlin，不得依賴 Android**，以便直接單元測試。
- Health Connect 相關程式碼**全部封裝在 `health/`**，其他套件不得直接使用 HC SDK 類別。
- Health Connect API 以最新官方文件為準；編譯報錯先查文件再修，不要猜。
- 依賴版本使用目前最新穩定版。
- ViewModel 的依賴一律以建構子注入「介面」，搭配 ViewModelProvider.Factory，不引入 Hilt
- 新增邏輯要附單元測試，fake 寫在測試檔內；修競態類 bug 時，測試必須驗證「拿掉修正會變紅」
- 步數寫入：只寫「已過去」的時間區間；區間連續且不重疊（重疊會讓步數重複計算）；以 Metadata 標明紀錄方式與來源

## 裝置與模擬器（Windows）
- 安裝前先 `adb devices`，必須看到 `device` 才執行 installDebug；多台裝置時用 `adb -s <序號>`
- 截圖不要用 `adb exec-out ... >` 重導向（PowerShell 會損壞 PNG），改用：
  `adb shell screencap -p /sdcard/screen.png; adb pull /sdcard/screen.png .\tmp\screen.png`
- UI 結構：`adb shell uiautomator dump /sdcard/ui.xml; adb pull /sdcard/ui.xml .\tmp\ui.xml`
- 背景行為不能靠截圖判斷，看 `adb logcat`；階段 3 之後的背景行為必須用實機驗證
- 檔案一律 UTF-8；PowerShell 讀中文檔用 `Get-Content -Encoding UTF8`

## UI 備註
- 目前 UI 為功能性版本，正式上線前需整體重新規劃，現階段不要花時間美化或微調
- UI 重新規劃只動 ui/ 的 Composable；SimulatorUiState 與事件介面盡量保持穩定

## 開發階段與範圍

嚴格依階段進行，不提前實作後續階段，也不順手重構既有程式。

1. 階段 1：授權骨架（HC 可用性檢查、Manifest 權限、Rationale 頁、請求權限與狀態顯示）
2. 階段 2：模擬核心（步數寫入）
3. 階段 3：背景執行（Foreground Service）
4. 階段 4：MVP 驗收
- 階段 9.5：UI/UX 重新規劃（正式版畫面、免責聲明與責任使用說明、無障礙、多語系）

**目前進度：**
- 階段 1：授權流程已於 Android 15 實機驗證（授權後狀態顯示、可寫入）。尚未驗證：部分授權、連續拒絕兩次後的導向
- 階段 2：寫入與總數已於實機驗證（300 步總數正確）。尚未驗證：同 clientRecordId 重送的去重、寫入頻率限制、Health Connect 明細的「紀錄方式」標示
- 階段 3a：前景服務啟動、背景與鎖屏持續寫入、通知的暫停／繼續／結束已於實機驗證（ASUS Android 15）。已知限制：從最近使用清單滑掉 App 會被 ASUS 桌面強制停止（見決定紀錄），不處理

## 待驗證清單
- 拒絕通知權限後重測一輪：不崩潰、模擬照跑
- 執行中在 Health Connect 撤銷權限：通知顯示錯誤、「結束本次模擬」可用、不崩潰
- 長時間背景執行（3,000 步以上，回到主畫面離開並鎖屏）
- 最近使用清單「鎖定」是否能避免被強制停止
- Health Connect 明細：紀錄方式的標示、鎖屏期間實際批次間隔
- 同 clientRecordId 重送是否去重（需 debug 開關，尚未做）
- 通知小圖示是否顯示成白色方塊
- 前景服務對帳（commit a952fab）：
  - 20 步目標跑完立刻重設再開始，連續 10 次；之後 logcat 不應出現 did not start 或 ForegroundService 相關錯誤
  - 啟動失敗用的 debug 版（暫時拿掉 `HIGH_SAMPLING_RATE_SENSORS`，不要 commit）：開始 → 結束 → 再開始，應再次嘗試啟動；離開前景需求時「無法在背景執行」提示消失
  - 還原權限的版本：暫停、繼續後提示消失、通知出現
  - 觀察：重設後快速再開始時，logcat 是否出現連續兩次 onStartCommand（舊服務 onDestroy 的時序）
- 順序：上面的啟動失敗驗證要在 specialUse 遷移之前完成；遷移後的失敗注入改為暫時拿掉 `FOREGROUND_SERVICE_SPECIAL_USE` 權限，並須在實機確認這樣會拋 SecurityException（目前未驗證）

## Lint 現況
- 0 error、17 warning；warning 上限 17，不得新增 error。每個 commit 回報 warning 數
- 待辦（本輪不處理）：依賴版本更新（GradleDependency 5、NewerVersionAvailable 1）
- UnusedResources 7、RedundantLabel 2 預計在階段 9.5 自然消失；ObsoleteSdkInt 1 未處理；HighSamplingRate 1 見下方階段 10 阻擋項

## 階段 10 上架阻擋項（階段 10 完成前不得送審）
- `HIGH_SAMPLING_RATE_SENSORS` 可能讓 Play 要求審核用途，甚至拒絕上架。lint（HighSamplingRate）訊息摘要：多數 App 不需要高取樣率感測器；需確認確實需要 > 200Hz；用途經 Play 政策團隊審核確認前，可能被拒絕上架
- 本 App 不使用感測器，這項權限只是為了滿足 health 前景服務的啟動前提，用途說明站不住腳
- 結論：**建議遷移到 specialUse**
  - health 類型在 Play Console 的預設用途是「Health Data Sync」（範例為計步器、運動追蹤），StepSim 是寫入模擬步數的開發／QA 工具，不符合
  - specialUse 的執行時前提為「None」，遷移後可拿掉 `HIGH_SAMPLING_RATE_SENSORS`
  - 能否過審未知。備案：Play 版本不含背景執行，或改以 APK 直接分發
  - 改完要在實機重新驗證前景服務的啟動與失敗路徑
- 官方來源：
  - Foreground service types（specialUse：權限 `FOREGROUND_SERVICE_SPECIAL_USE`、類型 `specialUse`、`PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 說明在送審時審查、執行時前提 None；health 的執行時前提）：https://developer.android.com/develop/background-work/services/fgs/service-types
  - Play Console 前景服務申報（Policy > App content：功能說明、被延後或中斷的影響、示範影片、用途分類；health 的用途為 Health Data Sync；所有類型都會被審查）：https://support.google.com/googleplay/android-developer/answer/13392821
  - 前景服務逾時（目前只有 dataSync、mediaProcessing 每 24 小時 6 小時，shortService 另有限制；未列 specialUse 與 health）：https://developer.android.com/develop/background-work/services/fgs/timeout
- 未解問題（查不到官方說明）：
  - `HIGH_SAMPLING_RATE_SENSORS` 在 Play 審核的處理
  - specialUse 的核准標準

## 驗收與進度
- 「階段 N 完成」只在使用者手動驗收通過後才能寫入；程式碼寫完、測試通過都不算完成
- 一個 commit 只做一件事；清理與重構獨立 commit

## 決定紀錄
- 從最近使用清單滑掉 App 會終止模擬（已確認原因）：ASUS Android 15 上 logcat 顯示 `stop com.seeyu90.stepsim due to from pid <...> (com.asus.launcher)`，是桌面程式對套件執行強制停止。強制停止無法在 App 內攔截，也不會自動重啟；前景服務、START_STICKY、WorkManager 都無法防止。結論：不處理，改以提示使用者避免從最近使用清單滑掉；是否做 3b（狀態恢復）列為之後的選配。已寫入的批次不受影響。
- health 類型前景服務的啟動前提（Android 14+）：以在 Manifest 宣告 `HIGH_SAMPLING_RATE_SENSORS` 滿足（一般權限，安裝時自動授予、使用者無法撤銷）；App 不讀取任何感測器。Health Connect 的 READ_STEPS／WRITE_STEPS 不在前提清單內，不能用來滿足。備案：階段 10 上架前改評估 `specialUse` 類型（需在 Play Console 申報用途；見「階段 10 上架阻擋項」）。
