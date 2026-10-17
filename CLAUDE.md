# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 專案定位

StepSim（package `com.seeyu90.stepsim`）是「Health Connect 開發與 QA 測試工具」：讓開發者不用實際搖手機，就能把模擬步數寫入 Health Connect，驗證其他健康類 App 的步數讀取功能。

## 技術棧

- Kotlin + Jetpack Compose + Material 3，Gradle Kotlin DSL，版本集中於 `gradle/libs.versions.toml`
- MVVM + StateFlow
- Health Connect SDK（`androidx.health.connect:connect-client`）
- Foreground Service（type: health）；DataStore 為預定使用，尚未加入依賴
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

## 架構概觀（跨檔案的資料流）

- **模擬是程序層級的單例**：`StepSimApp.simulation`（`SimulationController` 介面，實作為 `SimulationRunner`）不隨畫面建立或銷毀。畫面（`SimulatorViewModel`）與通知（`SimulationService`）都只觀察 `state: StateFlow<SimulationState>` 並送出指令，誰都不持有計時迴圈。
- **`SimulationRunner`**：跑在序列化 dispatcher（`Dispatchers.Default.limitedParallelism(1)`：同一時間只有一個工作，但不固定在同一條執行緒）上；計時迴圈由狀態驅動，RUNNING 時有且只有一個（`collectLatest`），每 10 秒呼叫一次 `tick()`。指令非同步執行，`CancellationException` 先重新拋出，再忽略 `IllegalStateException`／`IllegalArgumentException`，以容忍連點與畫面／通知同時操作。
- **`StepSimulator`**：狀態機本體，所有操作以 `Mutex` 串行。關鍵不變量：
  - 每批區間為 `[上一批結束, 現在)`，只寫已過去的時間、連續且不重疊；小數步數以整數單位累積到下一批，最後一批截到剛好達標
  - 暫停、寫入失敗期間留空，恢復後從當下開始，不補寫空窗
  - 寫入途中被取消（結果未知）的批次記為 pending，下次以相同 `clientRecordId` 重送
  - 時間一律透過 `TimeSource` 取得（`SystemTimeSource` 是唯一呼叫 `Instant.now()` 之處），測試注入假時間
- **寫入邊界**：`simulator/StepWriter` 是介面，HC 實作是 `health/HealthConnectStepWriter`。HC 例外在 `health/` 內轉成 `StepWritePermissionException`（→ PERMISSION_REVOKED）或 `StepWriteRateLimitException`（→ 一般可重試失敗），simulator 不認得任何 HC 類別。紀錄用 `Metadata.manualEntry` 並帶固定 `clientRecordVersion`，讓重送不會重複計步。
- **前景服務（依狀態對帳）**：`StepSimApp` 啟動的 `ForegroundReconciler`（純 Kotlin）以 `combine(模擬狀態, 前景服務狀態)` 取「目前的值」對帳，交給 `service/ForegroundPolicy.kt` 的純函式（`appReconcile`、`reconcileForeground`、`nextFailedAt`）決定動作；App 只處理 START，停止由服務自己以 `shouldRunForeground` 判斷並 `stopSelf(lastStartId)`。`BackgroundAvailability` 持有 `(alive, failedAt)`，只能在主執行緒呼叫；啟動前景失敗不會崩潰，記下失敗時的狀態種類，狀態種類改變才重試，離開前景需求時失敗紀錄作廢。通知內容由 `notificationModel`／`stopNotificationModel` 決定（完成通知可滑掉，其他快照不留通知），服務只負責繪製與轉送按鈕指令。
- **ViewModel 注入**：各 ViewModel 的 `companion object Factory` 從 `APPLICATION_KEY` 取得 `StepSimApp`，把 `app.simulation`、`AndroidHealthConnectManager`、`backgroundAvailability.available` 以介面傳入建構子；測試則直接以 fake 建構。
- **導覽**：沒有 Navigation 元件，`MainActivity` 以 `rememberSaveable` 的 enum（Simulator／Permission／Guide）切換畫面，授權頁與使用說明頁的返回都回到模擬頁。`PermissionsRationaleActivity` 與 `ViewPermissionUsageActivity` alias 是 HC 授權視窗必要的 Manifest 入口，缺少時授權視窗不會跳出。

## 裝置與模擬器（Windows）
- 安裝前先 `adb devices`，必須看到 `device` 才執行 installDebug；多台裝置時用 `adb -s <序號>`
- 截圖不要用 `adb exec-out ... >` 重導向（PowerShell 會損壞 PNG），改用：
  `adb shell screencap -p /sdcard/screen.png; adb pull /sdcard/screen.png .\tmp\screen.png`
- UI 結構：`adb shell uiautomator dump /sdcard/ui.xml; adb pull /sdcard/ui.xml .\tmp\ui.xml`
- 背景行為不能靠截圖判斷，看 `adb logcat`；階段 3 之後的背景行為必須用實機驗證
- 檔案一律 UTF-8；PowerShell 讀中文檔用 `Get-Content -Encoding UTF8`

## 文案與詞彙
- 文案一律放 `strings.xml`；品牌字串（`app_name` 等）不寫死在程式或 drawable，icon 不含文字
- 統一用語：存入（不用「寫入」）、允許（不用「授權」）、再試一次、結束本次模擬、再模擬一次
- 禁用詞：賺、獎勵、挑戰、排名、偵測、追蹤、記錄、寫入、授權。只在否定語境（禁止用途、「不是感測器偵測到的…」）例外，以（key, 詞）配對列在 `StringsVocabularyTest` 的白名單；白名單裡的詞若已不在文字中，測試也會變紅
- `StringsVocabularyTest`：掃 `<string>`、`<plurals>`、`<string-array>` 的文字，不含 XML 註解
- `UiHardcodedStringTest`：只檢查 `ui/` 的字串常值，只抓中日韓文字與 "StepSim"，**不抓寫死的英文**；略過註解、import／package、`@Preview(...)` 參數
- 通知字串的 key 與參數簽名由 `SimulationService` 決定，只能換文字；佔位符數量要與呼叫端一致，否則 lint 報 StringFormatInvalid（error）

## UI 設計原則
- 任何狀態都不得讓使用者沒有出口：已開始的模擬一律由 `secondaryActions` 提供「結束本次模擬」（無條件渲染，先跳確認對話框）；授權頁在所有階段都有畫面上的返回與系統返回
- 主要動作固定在底部（捲動區之外），一個畫面只有一個
- 錯誤主訊息一句白話；技術細節（例外、批次編號）放可展開的「詳細資訊」
- 「模擬資料」標籤與責任使用條文常駐（主畫面、授權頁、使用說明頁）
- 圓環中央數字用 TextAutoSize，大字體與小螢幕下不得超出圓環
- 色彩以 `ui/theme/Color.kt` 為準，不用 dynamic color；文字對比至少 WCAG AA（4.5:1），容器內的文字用 onPrimaryContainer；內文至少 16sp，可點擊元件至少 48dp；狀態不單靠顏色表達
- UI 修改只動 `ui/`；`SimulatorUiState` 與事件介面可以新增，不改名、不改既有行為；沒有使用者的屬性可以刪除，刪之前要 `git grep` 確認

## 測試與驗證
- 每項保護都要有「拿掉修正會變紅」的證據，回報時附失敗的測試名稱
- 突變驗證前先確認測試真的有執行（看報告的測試數）；還原後 `git diff` 必須為空
- lint：不得有 error，warning 上限見「Lint 現況」

## 分支與推送
- 同一個分支同一時間只有一個環境寫入
- 新分支一律從最新 main 切出，不疊在會被 squash 的分支上
- 不得對 main 或他人的分支 force push；rebase 後只用 `--force-with-lease`
- PR 由使用者合併
- repo 是公開的：不放私人連結、商業決定、計畫文件

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
- 階段 9.5：程式完成（含 code review 修正），待本機與實機驗證（分支 `phase-9.5/ui-redesign`）

## 待驗證清單
- 拒絕通知權限後重測一輪：不崩潰、模擬照跑
- 撤銷權限後程序被終止，重開回到允許權限；已存入的步數仍在
- 長時間背景執行（3,000 步以上，回到主畫面離開並鎖屏）。**請拔掉 USB** 再測：接電時 CPU 不一定休眠，會測不出 wake lock 的差別。跑完後從 Health Connect 明細確認批次間隔約 10 秒，而不是醒來後一次補寫的大批次；另在執行中途接上 USB 執行 `adb shell dumpsys power`，應可看到 `stepsim:simulation`，暫停或結束後消失
- 最近使用清單「鎖定」是否能避免被強制停止
- Health Connect 明細：紀錄方式的標示、鎖屏期間實際批次間隔
- 同 clientRecordId 重送是否去重（需 debug 開關，尚未做）
- 通知小圖示：已由 launcher 的 adaptive 圖示改為單色向量圖（`ic_stat_steps`），待實機確認狀態列顯示為腳印而非白色方塊；若腳掌與腳跟糊成一塊，只對 `ic_stat_steps` 把腳跟下移約 1.5 單位並重算平移值
- 前景服務對帳（commit a952fab）：
  - 20 步目標跑完立刻重設再開始，連續 10 次；之後 logcat 不應出現 did not start 或 ForegroundService 相關錯誤
  - 啟動失敗用的 debug 版（暫時拿掉 `HIGH_SAMPLING_RATE_SENSORS`，不要 commit）：開始 → 結束 → 再開始，應再次嘗試啟動；離開前景需求時「無法在背景執行」提示消失
  - 還原權限的版本：暫停、繼續後提示消失、通知出現
  - 觀察：重設後快速再開始時，logcat 是否出現連續兩次 onStartCommand（舊服務 onDestroy 的時序）
- 順序：上面的啟動失敗驗證要在 specialUse 遷移之前完成；遷移後的失敗注入改為暫時拿掉 `FOREGROUND_SERVICE_SPECIAL_USE` 權限，並須在實機確認這樣會拋 SecurityException（目前未驗證）

## Lint 現況
- 0 error、8 warning；warning 上限 8，不得新增 warning 或 error。每個 commit 回報 warning 數
- 待辦（本輪不處理）：依賴版本更新（GradleDependency 5、NewerVersionAvailable 1）
- UnusedResources 7（範本顏色）與 RedundantLabel 2（重複的 activity label）已於階段 9.5 移除；ObsoleteSdkInt 1 未處理；HighSamplingRate 1 見下方階段 10 阻擋項

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
- 執行中持有 `PARTIAL_WAKE_LOCK`（`WAKE_LOCK` 為一般權限）：前景服務只保證程序不被終止，不保證 CPU 不休眠；螢幕熄滅且沒接電時，`delay` 型的計時迴圈會停住，醒來後才一次補寫整段休眠時間。只在 RUNNING 持有（`shouldHoldWakeLock`），暫停、失敗、結束與服務銷毀時釋放；帶 5 分鐘保險逾時並由每次狀態更新續約，服務異常中止時最久多撐 5 分鐘。phase-3 已移除的 `keepScreenOn` 不需要恢復。**尚未實機驗證**（見待驗證清單）。
- 從最近使用清單滑掉 App 會終止模擬（已確認原因）：ASUS Android 15 上 logcat 顯示 `stop com.seeyu90.stepsim due to from pid <...> (com.asus.launcher)`，是桌面程式對套件執行強制停止。強制停止無法在 App 內攔截，也不會自動重啟；前景服務、START_STICKY、WorkManager 都無法防止。結論：不處理，改以提示使用者避免從最近使用清單滑掉；是否做 3b（狀態恢復）列為之後的選配。已寫入的批次不受影響。
- health 類型前景服務的啟動前提（Android 14+）：以在 Manifest 宣告 `HIGH_SAMPLING_RATE_SENSORS` 滿足（一般權限，安裝時自動授予、使用者無法撤銷）；App 不讀取任何感測器。Health Connect 的 READ_STEPS／WRITE_STEPS 不在前提清單內，不能用來滿足。備案：階段 10 上架前改評估 `specialUse` 類型（需在 Play Console 申報用途；見「階段 10 上架阻擋項」）。
- 在系統設定或 Health Connect 撤銷權限時，系統會終止 App 程序（實機觀察，ASUS Android 15）：執行中的模擬與前景服務一起結束，重開是全新狀態，畫面指向允許權限。因此 PERMISSION_REVOKED 相關畫面（失敗＋權限、完成＋權限）在實機上幾乎碰不到，屬防禦性設計，只靠單元測試與 Preview 守住。
