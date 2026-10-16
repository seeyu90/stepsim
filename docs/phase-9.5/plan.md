# 階段 9.5：UI/UX 重新設計計畫（定案版）

> 交接用文件，階段 9.5 完成後刪除。決定與待辦見 `decisions.md`（優先），文案見 `copy.md`。
> 視覺稿原始檔在 `mockups/`；畫布在 https://claude.ai/artifact/27XKZSAQYSxXGdZj31koEb（私人）。
> ⚠️ 視覺稿畫在定案之前，與定案不同的地方：主按鈕沒有固定在底部、失敗卡片裡有紅色按鈕、文案是 v1、標籤是 14sp、狀態說明只有一行。**實作以本檔與 decisions.md 為準。**

## 定位
- 受眾是一般民眾，用途是示範、教學、測試健康 App 讀不讀得到步數。
- 只依真實時間累積，不能補登過去的時間；步數以「手動輸入」存入 Health Connect。
- 「模擬資料」必須常駐在主畫面上，並提供完整的使用說明頁。
- 風格：乾淨的健康類 App 感、白底、單一主色湖水綠、大圓環配大數字、卡片分層。使用 Material 3，**關閉 dynamic color**，支援淺色與深色。字型用系統預設，數字一律等寬（tnum）。

## 1. 檔案清單

### 修改
| 檔案 | 內容 | commit |
|---|---|---|
| `ui/theme/Color.kt`、`Theme.kt`、`Type.kt` | 湖水綠 token、關閉 dynamic color、`StepSimExtraColors`（圓環軌道等）、字級表與 tnum | 1 |
| `res/values/colors.xml`、`themes.xml`，新增 `values-night/themes.xml` | icon 背景色；視窗背景跟著深淺色走，避免啟動時閃白 | 1 |
| `res/drawable/ic_launcher_foreground.xml`、`ic_launcher_background.xml`，新增 `ic_launcher_monochrome.xml`；`mipmap-anydpi/ic_launcher*.xml` | 新 icon（規格見 decisions.md） | 1 |
| 新增 `res/drawable/ic_stat_stepsim.xml`；`service/SimulationService.kt` **只改 `setSmallIcon`** | 通知小圖示（待 Q3） | 1 |
| `MainActivity.kt` | **只改導覽**：布林值改成 enum `Simulator / Permission / Guide`；Guide 和 Permission 用 BackHandler 回到模擬頁 | 2 |
| `ui/SimulatorScreen.kt`、`SimulatorScreenPreviews.kt` | 主畫面重寫；`SimulatorRoute` 的權限檢查與通知權限請求流程不變 | 2 |
| `ui/SimulatorUiState.kt` | 只新增衍生屬性 | 2 |
| `ui/PermissionScreen.kt`、`PermissionRoute.kt`、`PermissionViewModel.kt` | 授權頁改版；新增 `onPermissionResult`、`stage`；`PermissionAutoReturn` 不動 | 3 |
| `ui/PermissionsRationaleActivity.kt` | Rationale 改版 | 3 |
| `res/values/strings.xml` | commit 2、3 先加各自要用的新 key；commit 4 刪掉舊 key、更新通知文字 | 2–4 |

### 新增
| 檔案 | 內容 | commit |
|---|---|---|
| `ui/components/StepRing.kt` | 大圓環：Canvas 畫弧，16dp 粗、圓頭端點；語意描述；數字用 TextAutoSize | 1 |
| `ui/components/SimulatedDataBadge.kt`、`Cards.kt`、`Buttons.kt`、`ExpandableDetails.kt` | 共用元件；展開狀態用 `rememberSaveable`，不持久化 | 1 |
| `res/drawable/ic_*.xml` | 手繪 vector 圖示：走路、跑步、暫停、播放、完成、警告、資訊、展開箭頭、模擬標籤。**不加** `material-icons-extended` | 1 |
| `ui/SimulatorDisplay.kt` | 純函式，不依賴 Compose | 2 |
| `ui/EndSimulationDialog.kt` | 「結束本次模擬」確認對話框；開啟狀態用 `rememberSaveable` | 2 |
| `ui/GuideScreen.kt` | 使用說明頁（一般畫面） | 2 |
| `ui/PermissionPreviews.kt` | 授權頁、Rationale、對話框、說明頁的 Preview | 3 |
| `test/.../ui/SimulatorDisplayTest.kt`、`SimulatorActionsTest.kt` | 純函式與失敗按鈕對照表 | 2 |
| `test/.../ui/PermissionStageTest.kt` | 授權頁階段判斷 | 3 |
| `test/.../StringsVocabularyTest.kt`、`UiHardcodedStringTest.kt` | 禁用詞掃描、寫死字串掃描 | 4 |

範本留下的 `mipmap-*dpi/*.webp`：minSdk 28 一律用 `mipmap-anydpi`，這些檔案不會被用到。本輪不刪，之後另開清理 commit。

## 1.2 純函式、衍生屬性與測試案例
| 名稱 | 規格 | 測試案例 |
|---|---|---|
| `formatSteps(steps, locale)` | `NumberFormat.getIntegerInstance(locale)` | 0→`0`；1000→`1,000`；100000→`100,000`；`Locale.GERMANY` 的 1000→`1.000` |
| `RemainingTime.of(minutes)` → `Soon` / `Minutes(m)` / `Hours(h)` / `HoursMinutes(h,m)` | 0→Soon；整點→Hours | 0→Soon；1→1 分鐘；59→59 分鐘；**60→1 小時**；61→1 小時 1 分鐘；**120→2 小時**；1000→16 小時 40 分鐘 |
| `SimulatorUiState.showRemaining` | 只在 RUNNING 時為 true | 逐狀態檢查 |
| `SimulatorUiState.progressPercent` | `writtenSteps * 100 / target`，無條件捨去（與通知同算法） | 0/5000→0；2100/5000→42；**99,999/100,000→99**；完成→100；無目標→0 |
| `SimulatorUiState.statusDisplay` | NEED_TARGET、READY、WALKING、RUNNING、PAUSED、COMPLETED、FAILED | 每個狀態 × 每種步態 |
| `SimulatorUiState.bottomAction` | START、PAUSE、RESUME、RETRY、GO_ALLOW、GO_CHECK、START_NEW | **涵蓋 decisions.md 的失敗按鈕表**；準備狀態 canStart=false 時停用 |
| `SimulatorUiState.secondaryActions` | 例如 [RETRY, END] / [END] | 同上；權限未知時一定有 RETRY；所有失敗狀態都有 END |
| `SimulatorUiState.showTargetInput` / `showGaitSelector` | IDLE/READY 兩個都顯示；PAUSED 只顯示步態 | 逐狀態 |
| `SimulatorUiState.ringSize` | 準備狀態（還沒有進度）用 COMPACT（176dp），其餘 FULL（240dp） | 逐狀態 |
| `formatClockTime(instant, zone, locale)` | `DateTimeFormatter.ofLocalizedTime(SHORT)` | 固定 zone 與 locale；跨日 |
| `PermissionUiState.stage` | CHECKING、CHECK_FAILED、NEEDS_INSTALL、NEEDS_UPDATE、NOT_SUPPORTED、NOT_GRANTED、NEEDS_MANUAL、GRANTED | 請求後回傳空集合→NEEDS_MANUAL；之後授權成功→GRANTED；NEEDS_MANUAL 時按再試一次仍可再發請求 |

## 2. 主題規格

### 2.1 色彩 token（WCAG 2.x 對比值）
**淺色**
| Token | 值 | 搭配 | 對比 |
|---|---|---|---|
| primary | `#00796B` | 在 surface `#FFFFFF`／background `#F6FAF9` 上 | 5.32 / 5.06 ✅ |
| onPrimary | `#FFFFFF` | 在 primary 上 | 5.32 ✅ |
| onSurface | `#17201F` | 在 surface／background 上 | 16.63 / 15.81 ✅ |
| onSurfaceVariant | `#3F4947` | 在 surface／background 上 | 9.31 / 8.85 ✅ |
| primaryContainer | `#D5F0EC` | 搭 onPrimaryContainer `#00201C` | 14.31 ✅ |
| ⚠️ primary 文字 | | 在 primaryContainer 上 | **4.43 不及格** → 容器裡的文字一律用 onPrimaryContainer |
| error | `#B3261E` | 在 surface 上／onError 白字 | 6.54 / 6.54 ✅ |
| errorContainer | `#F9DEDC` | 搭 onErrorContainer `#410E0B` | 12.77 ✅ |
| outline | `#6F7977` | 外框（需 ≥3:1）；失敗時的進度弧 | 4.49 ✅ |
| 圓環 | 進度 primary／軌道 primaryContainer | 圖形元件 | 4.43 ✅ |

**深色**
| Token | 值 | 搭配 | 對比 |
|---|---|---|---|
| primary | `#4DB6AC` | 在 surface `#161D1C`／background `#0F1514` 上 | 7.01 / 7.56 ✅ |
| onPrimary | `#00201C` | 在 primary 上 | 7.03 ✅ |
| onSurface | `#E0E8E6` | 在 surface／background 上 | 13.74 / 14.82 ✅ |
| onSurfaceVariant | `#BEC9C6` | 在 surface 上 | 10.08 ✅ |
| primaryContainer | `#00504A` | 搭 onPrimaryContainer `#D5F0EC` | 7.79 ✅ |
| error | `#F2B8B5` | 在 surface 上／onError `#601410` | 10.03 / 7.66 ✅ |
| errorContainer | `#8C1D18` | 搭 onErrorContainer `#F9DEDC` | 7.17 ✅ |
| outline | `#899390` | 在 surface 上 | 5.41 ✅ |
| 圓環軌道 | `#2A3533` | 與進度 `#4DB6AC` | 5.20 ✅（改用 primaryContainer 只有 3.83，不採用） |

### 2.2 字級（系統字型）
| 用途 | 樣式 | 大小／行高 |
|---|---|---|
| 圓環大數字 | displayLarge＋tnum、SemiBold | 最大 56sp，以 autoSize 縮到放得進圓環 |
| 頁面標題 | headlineSmall | 24/32sp |
| 卡片標題 | titleMedium | 18/24sp |
| 內文 | bodyLarge | 16/24sp（**內文最低就是這級**） |
| 輔助說明 | bodyMedium | 16/22sp |
| 按鈕 | labelLarge | 16/24sp、Medium |
| 「模擬資料」標籤 | labelLarge | **16sp** |

### 2.3 形狀與尺寸
- 卡片 16dp；對話框 28dp；標籤、Chip、主按鈕全圓角
- 主按鈕高 ≥56dp、填滿寬度，固定在底部；可點擊元件 ≥48dp
- 圓環：上限 240dp，準備狀態 176dp；線寬 16dp；從 12 點鐘方向順時針
- 左右邊距 20dp、區塊間距 16dp、卡片內距 16dp

## 3. 各狀態線框圖（文字見 copy.md）
圖例：`[■ …]` 是底部固定的主按鈕；`〈…〉` 是捲動區裡的文字按鈕。

### 主畫面共用版型
```
StepSim                              [◌ 模擬資料]
            ( 圓環 )
          ring_goal · 步態
 狀態說明（兩行，liveRegion 只在狀態改變時播報）
 [設定卡片：目標步數＋快速選項；速度選項卡 ×2]  ← 只在準備狀態
 〈次要操作〉
 ┌ 使用小提醒 ┐  tip_realtime / tip_leave / tip_background
 disclaimer_short
 disclaimer_use
 〈使用說明與注意事項〉
 ▸ 詳細資訊（預設收合）
────────────────────────────── 捲動區結束（底部留出按鈕高度）
[■ 主按鈕]                       ← 加上導覽列 insets 與 imePadding
```

| 狀態 | 圓環 | 狀態說明 | 底部主按鈕 | 次要操作 |
|---|---|---|---|---|
| 準備（空） | 176dp、0；中央 hero_empty | — | 開始模擬（停用）＋提示 cta_hint_need_goal | — |
| 準備（已設定） | 176dp、0 | estimate | 開始模擬 | — |
| 走路中／跑步中 | 240dp | 圖示＋status_walking/running；status_rate · remaining | 暫停 | 結束本次模擬 |
| 暫停 | 240dp | 圖示＋status_paused；paused_hint；**速度選項卡** | 繼續 | 結束本次模擬 |
| 完成 | 240dp、100% | done_title；done_body | 再模擬一次（不需確認） | — |
| 失敗 | 240dp、進度弧用 outline 灰色 | 錯誤卡（標題＋內文＋▸詳細資訊：err_details_batch＋例外名稱），**卡內不放按鈕** | 依失敗按鈕表 | 依失敗按鈕表 |
| 背景不可用 | — | 加一張提醒卡 bg_unavailable（屬於提示，不算失敗）；這時隱藏 tip_background | 不變 | 不變 |
| 未授權（主內容被權限提示取代） | — | cta_hint_need_permission | 前往允許 | 失敗中才顯示「結束本次模擬」 |

- **確認對話框**：end_confirm_title／end_confirm_body；〈取消〉〈結束〉，「結束」用 error 色文字按鈕。
- **使用說明頁**（一般畫面）：頁首用橫式標誌（icon＋app_name＋app_subtitle），接著 guide_title、guide_1～8、〈返回〉；系統返回鍵回到模擬頁。
- **授權頁**：
  - 未授權：perm_title、perm_why、perm_point_1～4，主按鈕 perm_cta
  - NEEDS_MANUAL：perm_manual_title、perm_manual_body、編號步驟 1～3，按鈕〈perm_retry〉〈perm_open_hc〉
  - 已授權：perm_status_granted，按鈕〈perm_back_to_sim〉，另有〈perm_reset_perm〉
  - 未安裝、需要更新、不支援、檢查失敗：各一句白話加一個主按鈕；不支援時沒有按鈕
- **Rationale**：rationale_title、rationale_intro、rationale_write、rationale_privacy。
- **通知五種狀態**：見 copy.md「通知」。

## 4. Preview 規劃
- **主畫面**（每個狀態一個）：準備（空）、準備（已設定）、目標超出範圍、走路中、跑步中、暫停、完成、失敗-一般、失敗-太頻繁、失敗-權限未確認、失敗-權限已恢復、權限確認未授權、Health Connect 不可用、背景不可用、檢查中
- **授權頁**：未授權、NEEDS_MANUAL、已授權、未安裝、需更新、不支援、檢查失敗
- **其他**：Rationale、確認對話框、使用說明頁
- **變體**（用自訂的 multipreview 註解 `@StepSimPreviews`）：深色、字體 1.5 倍、2.0 倍、小螢幕 320×568、小螢幕＋2.0 倍
- **壓力測試**：目標 100,000、已存入 99,999 × 1.5 倍、2.0 倍、小螢幕

## 5. 各 commit 內容
0. ✅ 移除 READ_STEPS（見 decisions.md）
1. 主題、共用元件（含 StepRing，並確認 TextAutoSize 能編譯）、App icon（含 monochrome）、`ic_stat_stepsim` 加上 `setSmallIcon` 那一行
2. 主畫面：底部固定主按鈕、確認對話框、使用說明頁、`MainActivity` 的 enum 導覽、衍生屬性與純函式加測試（含失敗按鈕表）、主畫面 Preview（含壓力測試）
3. 授權頁與 Rationale：stage 加測試、NEEDS_MANUAL、對應 Preview
4. 文案定稿：刪掉舊 key、更新通知文字（沿用既有 key）、禁用詞與寫死字串的測試（各自驗證過違規會變紅）
5. CLAUDE.md：詞彙規則與設計原則（精簡版）；更新 READ_STEPS 的決定紀錄；導覽描述改成 enum

## 6. 只能在實機驗證的項目
- 通知：小圖示、進度條、按鈕文字長度（各品牌可能截斷）、深色通知欄
- TalkBack：圓環描述；liveRegion 不會隨每 10 秒的步數更新重複播報；對話框焦點；說明頁返回
- 系統字體 1.5／2.0 倍加上「顯示大小」放大後的版面
- 小螢幕加鍵盤時捲動區變矮；輸入框取得焦點時有沒有捲到看得見
- 主題化圖示（Android 13+）、啟動畫面（Android 12+）、深色模式啟動有沒有閃白
- Health Connect 手動授權的實際選單名稱
- NEEDS_MANUAL：系統不再跳視窗時的實際行為

## 7. 刻意不做
首次引導與任何持久化；資料管理、清除資料的畫面；新依賴；任何購買、廣告、捐款元件；`simulator/`、`health/`、`service/` 的邏輯修改（例外見 decisions.md）；刪除範本的 webp。

## 8. 品牌識別（BI）
- **定位**：讓健康 App 的步數測試，不必真的走路。誠實標示「模擬」，依真實時間慢慢存入。
- **個性**：誠實透明、平靜可靠、白話溫和、克制（一個畫面一件事）；不遊戲化，沒有徽章、連續天數、慶祝動畫。
- **標誌**：開口進度環加兩個腳印；字標「StepSim」用系統字型 Medium；中文副標「步數模擬」。淨空至少 1/4 符號寬；24dp 以下只用腳印。禁止加漸層、陰影、旋轉、非品牌色，也不要在旁邊加心跳線或感測器波形。
- **色彩**：主色只用在主按鈕、圓環、標籤和 icon 背景，約 10% 主色、90% 中性色。不另外設成功綠，完成狀態用主色加 ✓。
- **圖示**：線寬 2dp、圓頭、24dp 網格、手繪 vector；狀態一律「圖示＋文字」。
- **語氣**：稱呼「你」、自稱「StepSim」；動詞統一用模擬、存入、允許；錯誤訊息的公式是「發生什麼＋已存入的不受影響＋下一步」；不用驚嘆號和 emoji。
- 階段 10 再做：Play 商店 512px icon、主視覺圖、截圖模板。

## 9. 商業模式（已定方向，細節未定）
- 免費核心加一次性進階包；贊助只放在 App 外（GitHub、網站），**App 內不放**捐款按鈕或連結。不做廣告、訂閱、每日額度。
- 進階包只賣「測試的便利性」，例如情境腳本、跨日邊界、CSV 匯出，不賣「更多步數」。之後另外設計。
- 防濫用優先：任何「付費得到更多或更快步數」的設計都不做。

## 10. 名稱（暫定，尚未做商店與商標查核）
- 顯示名稱 `StepSim`；App 內標題與說明頁用「StepSim 步數模擬」。
- Play 商店名稱候選「StepSim 步數模擬・Health Connect」（27 字，上限 30）。
- applicationId `com.seeyu90.stepsim` **上架後就永遠不能改**，建議維持不動。
- 名稱裡要看得出是模擬或測試；不用他人商標當名稱主體；不用賺、獎勵、挑戰之類的字。
