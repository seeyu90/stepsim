# 階段 9.5 決定紀錄與待辦

> 交接用文件，階段 9.5 完成後刪除（另開清理 commit）。
> 三份文件衝突時，以本檔為準，其次是 `copy.md`，最後才是 `plan.md`。

## 分支與流程
- 分支：`phase-9.5/ui-redesign`，從 main `5a88a55`（階段 1～3 的 squash 合併）開始；目前 main 之上只有 commit 0 `ab0856a`。
- 一個 commit 做一件事。每個 commit 完成後**停下來回報，等使用者確認**才做下一個。
- 本輪例外：每個 commit 都 push 到 `phase-9.5/ui-redesign`。只限這個分支，不動 main 與其他分支，不開 PR。
- 每個 commit 都要跑 `testDebugUnitTest`、`lint`、`assembleDebug`，並回報測試數、失敗數、lint warning 數。warning 上限 10，不得新增 warning 或 error（見 CLAUDE.md「Lint 現況」）。
- Preview 由使用者在 Android Studio 看；instrumented 測試與背景行為一律實機驗證。

## Commit 順序
| # | 訊息 | 狀態 |
|---|---|---|
| 0 | `refactor(health): 移除未使用的 READ_STEPS 權限` | ✅ 已 push（`ab0856a`）；雲端建置（鏡像）121 測試全過、lint 17；本機驗證待使用者回報 |
| 1 | `feat(ui): 湖水綠主題、共用元件、App icon 與通知小圖示` | ✅ 已 push（`1642d3d`）；雲端建置（鏡像）121 測試全過、lint 10；本機驗證待使用者回報 |
| 2 | `feat(ui): 主畫面改版` | 未開始 |
| 3 | `feat(ui): 授權頁與 Rationale 改版` | 未開始 |
| 4 | `feat(ui): 文案定稿、通知字串與禁用詞掃描測試` | 未開始 |
| 5 | `docs: CLAUDE.md 加入詞彙規則與設計原則` | 未開始 |

各 commit 的內容見 `plan.md` §5。

## 已定案的決定

### 範圍
- 只動 `ui/`、theme、`res/`（strings、colors、drawable、mipmap、themes），以下為例外：
  - commit 0：`AndroidManifest.xml`、`health/` 的權限集合與對應測試
  - `MainActivity`：**只改導覽狀態**，布林值改成 enum `Simulator / Permission / Guide`
  - `service/SimulationService.kt`：**只改 `setSmallIcon` 的資源 ID**，其他一律不動
- `simulator/`、`health/`（commit 0 以外）、`service/`（上面那一行以外）的邏輯不得修改。
- `SimulatorUiState` 既有的屬性與 `SimulatorEvent` 不改名、不改行為；只能新增純顯示用的衍生屬性，而且要附單元測試。
- 不新增依賴。TextAutoSize 屬於 Compose foundation，不算新依賴。
- 不做持久化（不用 DataStore），所以不做首次引導，改用常駐的「使用小提醒」卡片。
- 不放任何購買、解鎖、廣告、捐款的元件或文字，也不預留版位。
- 不為資料管理、清除資料等尚未做的功能預留畫面。
- 品牌字串（`app_name`、副標）只放在 `strings.xml`；icon 不含文字。

### A～F
- **A** 文案以 `copy.md`（v2.1）為準。
- **B** 分支 `phase-9.5/ui-redesign`。後來已依使用者指示 rebase 到新的 main 並 force-push。
- **C** 使用說明頁做成**一般畫面**，不用全螢幕 Dialog（Dialog 在 edge-to-edge 下的 insets 容易出問題）；系統返回鍵回到模擬頁。
- **D** 通知小圖示改用自繪的腳印 `ic_stat_stepsim`（純白、透明背景），放進 commit 1。⚠️ 見待辦 Q3。
- **E** 暫停時保留走路／跑步切換，只隱藏目標步數。
- **F** 不再自動跳轉 Health Connect 設定頁。NEEDS_MANUAL 的設計：
  - 標題「還沒有取得權限」
  - 兩顆按鈕：〈再試一次〉（再跳一次系統視窗）、〈開啟 Health Connect〉
  - 編號步驟，加上一句「如果再按一次沒有跳出視窗，請照下面步驟手動開啟」
  - 文案**不得寫**「系統沒有跳出視窗」：程式分不出「使用者第一次拒絕」和「系統不再跳視窗」

### commit 0（已完成）
- 移除 READ_STEPS；權限集合只剩 WRITE_STEPS；`rationale_body` 先刪掉講讀取用途的那一句。
- 新測試 `HealthConnectPermissionsTest`。風險：它會在 JVM 上呼叫 `HealthPermission.getWritePermission`，若因依賴 Android 類別而失敗，改成比對字串常數。
- 規劃書原本有「只授權部分權限」的測試案例，改成單一權限後不可能發生，所以不寫；commit 3 改成驗證「請求後回傳空集合 → NEEDS_MANUAL」。
- `git grep READ_STEPS` 只剩 CLAUDE.md 的決定紀錄一處，commit 5 再更新。

### icon（commit 1）
- 108 畫布。背景 `#00796B` 純色；前景與圖形純白。
- 圓環：圓心 (54,54)、半徑 25、線寬 5.5、圓頭端點；從 3 點鐘方向順時針畫 300°，缺口在右上。VectorDrawable 不支援 dasharray，改用弧線路徑：`M79,54 A25,25 0 1,1 66.5,32.35`。
- 腳印：四個橢圓，整組以 (54,54) 為圓心旋轉 −15°，轉成路徑弧線：
  - (47,57) rx4.6 ry7.2
  - (47,68.5) rx3.4 ry3.6
  - (61,43) rx4.6 ry7.2
  - (61,54.5) rx3.4 ry3.6
- 安全區驗算（直徑 66）：
  - 圓環外緣半徑 27.75，直徑 55.5 ✅
  - 腳印離圓心最遠 19.74，小於圓環內緣 22.25，不重疊 ✅
- Monochrome 圖層用同一組路徑的單色版。
- `ic_stat_stepsim`：只放腳印，24dp、純白、透明背景。腳印旋轉後外框是 x 43.2–64.3、y 34.5–73.4，等比縮進 24dp 並四周留 2dp。
- 不得使用 Health Connect 或 Google 的圖示與商標。

### 畫面
色彩 token 與對比值照 `plan.md` §2.1，容器內的文字一律用 onPrimaryContainer。另外：
1. **主要動作固定在底部**（捲動區之外）：準備＝開始模擬、進行中＝暫停／繼續、失敗＝依下表、完成＝再模擬一次。捲動區底部要留出按鈕高度的內距，並處理導覽列 insets 與 `imePadding`；輸入框取得焦點時要捲到看得見。
2. 失敗卡片**不放按鈕**，只放文字和可展開的詳細資訊；主按鈕在底部，維持主色，不用紅色。
3. 圓環上限 240dp；準備狀態縮成 176dp；失敗時進度弧改用 outline 灰色。
4. 速度選擇用兩張垂直排列的選項卡（≥56dp）。
5. 「模擬資料」標籤 16sp。
6. 狀態說明分兩行：「走路模擬中」加圖示；「每分鐘約 100 步 · 還剩約 29 分鐘」。
7. 「詳細資訊」預設收合。
8. 剩餘時間：只在**執行中**才顯示。剩餘分鐘為 0 時顯示「即將完成」，剩 1 分鐘仍顯示「還剩約 1 分鐘」；剛好整點時用 `duration_hour`。
9. Preview 壓力測試：目標 100,000、已存入 99,999，套用 1.5 倍、2.0 倍字體與 320×568 小螢幕，確認圓環數字不會超出圓環。
10. TextAutoSize：先用官方 API，在 commit 1 的圓環元件就先確認能編譯。不能用，或 2.0 倍字體下行為不對，就改用 TextMeasurer 方案並說明原因。

### 失敗時的按鈕（要有測試涵蓋這張表）
| 情況 | 底部主按鈕 | 次要（捲動區的文字按鈕） |
|---|---|---|
| 一般錯誤、存入太頻繁（FAILED，含權限未知） | 再試一次 | 結束本次模擬 |
| 權限被撤銷、尚未確認（PERMISSION_REVOKED，權限未知：檢查中或檢查失敗） | 前往允許 | 再試一次、結束本次模擬 |
| 權限已恢復（PERMISSION_REVOKED，已允許） | 再試一次 | 結束本次模擬 |
| 確認未授權（任一失敗狀態） | 前往允許 | 結束本次模擬 |
| Health Connect 不可用（任一失敗狀態） | 前往確認（帶到授權頁） | 結束本次模擬 |

未失敗、但主要內容被提示取代時：
| 情況 | 底部主按鈕 | 次要 |
|---|---|---|
| 權限檢查尚未結束（CHECKING） | 不顯示 | 已開始的模擬：結束本次模擬 |
| 權限檢查本身失敗（CHECK_FAILED） | 再試一次（重新檢查權限） | 已開始的模擬：結束本次模擬 |
| 尚未允許 | 前往允許 | 已開始的模擬：結束本次模擬 |
| Health Connect 不可用 | 前往確認 | 已開始的模擬：結束本次模擬 |

原則：已開始的模擬（執行中、暫停、失敗）在任何狀態都能「結束本次模擬」；失敗且權限未知時一定保留「再試一次」；只有「檢查尚未結束」可以沒有主按鈕。「結束本次模擬」一律跳確認對話框。

### 通知（commit 4，不改 `SimulationService.kt`）
保留既有的 key 名稱與參數簽名，只換文字，對照見 `copy.md`「通知」。v2 文案表裡跟這個衝突的通知 key 一律不採用。完成通知的內文沿用進度文字。要跑 lint，確認格式參數的數量與呼叫端一致。

### 禁用詞測試（commit 4）
- 禁用詞：賺、獎勵、挑戰、排名、偵測、追蹤、記錄。⚠️ 是否另外加上「寫入」「授權」見 Q2。
- 白名單用（key, 詞）配對：`disclaimer_short` 只允許「偵測」；`disclaimer_use`、`guide_3`、`perm_point_4` 只允許「獎勵」。其他 key 出現任何禁用詞就讓測試變紅。
- 檔案路徑以 Gradle 單元測試的模組工作目錄為準：`src/main/res/values/strings.xml`，不寫死絕對路徑。
- 另寫一個測試掃描 `ui/*.kt` 的字串常值，不能寫死中文或 "StepSim"。要排除註解、package、import，並允許 log tag 等白名單例外。如果做起來很脆弱，就只保留 strings.xml 那一個，並回報。
- 兩個測試都要驗證過「故意加入違規後會變紅」。

### 商業模式與名稱（只影響文案）
- 方向：免費核心加一次性進階包；贊助只放在 App 外。不做廣告、訂閱、每日額度。
- 「不會上傳」一律寫成「StepSim 本身不會上傳你的步數或任何健康資料」。
- 名稱暫定 StepSim、副標「步數模擬」，還沒做商店與商標查核。提到 Health Connect 只用來描述功能，不要暗示跟 Google 有官方關係。

## 待辦：等使用者回覆
- **Q1** commit 0 在本機的驗證結果：測試數、失敗數、lint warning 數。
- **Q2** 禁用詞：規劃書 §10.6 比使用者的清單多了「寫入」「授權」。
  - (甲) 取聯集，並把 `guide_7`「已獲你授權的 App」改成「已獲你允許的 App」（建議）
  - (乙) 這兩個詞只當用詞建議，不放進測試
- **Q3** 通知小圖示：main 已經把 `setSmallIcon` 改成 `ic_stat_steps`，圖案是 Material Icons 的走路人（`directions_walk`，Apache 授權）。這跟「不得使用 Google 圖示」衝突。建議照決定 D 換成 `ic_stat_stepsim`，並刪除 `ic_stat_steps`。還要決定 CLAUDE.md 待驗證清單裡「走路人剪影」那一條要跟著改。
- **Q4** 建置環境：改到本機開發後就不需要了（雲端環境的 Maven Central 被限流）。

## 已知風險（列入實機驗證）
- 舊字串 `notif_title_failed` 仍帶 `%1$d`，換成沒有參數的新文字後，服務呼叫端還是會傳參數。lint 對「有傳參數、字串卻沒有格式符號」的處理要實際跑過才知道。
- 小螢幕加上鍵盤打開時，捲動區會變矮。
- Health Connect 的選單名稱（「應用程式權限」等）要在實機核對，不確定的不要寫死。
