# 階段 9.5 文案對照表 v2.1（定稿）

> 交接用文件，階段 9.5 完成後刪除。
> 以使用者提供的 v2 為底，套用 v2.1 修訂與後續決定。標 **[v2.1]** 的條目和 v2 不同。
> key 可以依既有 strings.xml 的命名調整，**通知一律沿用既有 key**（見最後一節）。
> 數字一律加千分位；需要格式參數的用 `%1$s` 等，並依 Locale 格式化。

## 通用
| key | 文字 |
|---|---|
| app_name | StepSim（維持不變） |
| app_subtitle **[v2.1 新增]** | 步數模擬 |
| chip_simulated | 模擬資料 |
| unit_steps | 步 |
| checking **[v2.1 新增]** | 正在確認 Health Connect… |

## 準備
| key | 文字 |
|---|---|
| hero_empty | 先設定要模擬的步數 |
| goal_label | 目標步數 |
| goal_hint | 輸入 1 到 100,000 |
| goal_error | 請輸入 1 到 100,000 之間的數字 |
| speed_label | 速度 |
| speed_walk | 走路（約每分鐘 100 步） |
| speed_run | 跑步（約每分鐘 160 步） |
| estimate | 預計約需 %1$s，步數會依真實時間慢慢存入 |
| cta_start | 開始模擬 |
| cta_hint_need_goal | 請先輸入目標步數 |
| cta_hint_need_permission | 請先允許 StepSim 存入步數 |

## 圓環與詳細資訊 [v2.1 新增]
| key | 文字 |
|---|---|
| ring_goal | 目標 %1$s 步 |
| ring_a11y | 進度 %1$d%%，已存入 %2$s 步，目標 %3$s 步 |
| ring_a11y_no_target | 尚未設定目標，已存入 %1$s 步 |
| detail_batches | 已存入 %1$d 批 |
| detail_last | 最後存入 %1$s |
| detail_range | 存入時間 %1$s–%2$s |

## 進行中
| key | 文字 |
|---|---|
| status_walking | 走路模擬中 |
| status_running | 跑步模擬中 |
| status_rate | 每分鐘約 %1$d 步 |
| remaining | 還剩約 %1$s |
| remaining_soon | 即將完成（只在執行中、剩餘分鐘為 0 時） |
| duration_min | %1$d 分鐘 |
| duration_hour **[v2.1 新增]** | %1$d 小時 |
| duration_hour_min | %1$d 小時 %2$d 分鐘 |
| cta_pause | 暫停 |

## 使用小提醒
| key | 文字 |
|---|---|
| tips_title | 使用小提醒 |
| tip_realtime | 步數只會依真實時間慢慢存入，不能補存過去的時間。 |
| tip_leave | 離開時請回到手機主畫面，不要從「最近使用」把 StepSim 滑掉，部分手機會因此中斷模擬。 |
| tip_background | 離開後會在背景繼續；若有開啟通知，可以從通知列暫停或結束。 |

## 暫停與結束
| key | 文字 |
|---|---|
| status_paused | 已暫停 |
| paused_hint | 暫停期間不會存入步數，按「繼續」會從現在開始接著模擬。 |
| cta_resume | 繼續 |
| cta_end | 結束本次模擬 |
| end_confirm_title | 結束本次模擬？ |
| end_confirm_body | 進度會歸零，已存入 Health Connect 的步數會保留。 |
| end_confirm_yes | 結束 |
| end_confirm_no **[v2.1]** | 取消（v2 是「繼續模擬」：暫停時按下它並不會恢復模擬，所以改掉） |

## 完成
| key | 文字 |
|---|---|
| status_done | 模擬完成 |
| done_title | 已存入 %1$s 步 |
| done_body | 可以到 Health Connect 查看今天的步數。（照 v2 不改） |
| cta_new | 再模擬一次 |

## 失敗
| key | 文字 |
|---|---|
| status_failed | 需要處理 |
| err_generic_title | 這一批步數沒存進去 |
| err_generic_body | 請稍後再試一次。已存入的步數不受影響。 |
| err_rate_limit | 存入得太頻繁了，請稍等一下再試一次。 |
| err_io | 暫時連不上健康資料，請稍後再試一次。（IO 與 REMOTE 共用） |
| err_permission | StepSim 目前沒有存入步數的權限。請重新允許後再試一次。 |
| err_permission_regranted | 權限已恢復，可以再試一次了。 |
| err_unavailable | 目前無法使用 Health Connect。請先安裝或更新，才能存入步數。 |
| err_details_label | 詳細資訊 |
| err_details_batch | 第 %1$d 批 |
| ~~err_details_retry_note~~ **[v2.1 刪除]** | 「同批重送不會重複計算」還沒在裝置上驗證，不能對使用者保證 |
| btn_retry | 再試一次 |
| ~~btn_open_perm_page~~ **[v2.1 刪除]** | — |
| btn_go_allow **[v2.1 新增]** | 前往允許 |
| btn_go_check **[v2.1 新增]** | 前往確認（帶到授權頁） |
| btn_open_hc | 開啟 Health Connect |
| btn_install_hc | 安裝 Health Connect |
| btn_update_hc | 更新 Health Connect |
| bg_unavailable | 這支手機目前無法在背景繼續，請讓 StepSim 保持在畫面上。 |
| toast_install_failed **[v2.1 新增]** | 無法開啟 Play 商店，請手動安裝 Health Connect |
| toast_settings_failed **[v2.1 新增]** | 無法開啟 Health Connect，請到系統設定手動開啟 |

## 常駐說明與使用說明頁
| key | 文字 |
|---|---|
| disclaimer_short | 這些是依你設定模擬出來的步數，以「手動輸入」存入 Health Connect，不是感測器偵測到的真實步數。 |
| disclaimer_use | 請勿用於競賽、獎勵、保險等需要真實運動證明的場合。 |
| disclaimer_link | 使用說明與注意事項 |
| guide_title | 使用前請先了解 |
| guide_1 | StepSim 會把你設定的步數，依真實時間慢慢存入 Health Connect，每隔一小段時間存一批，不能補存過去的時間。 |
| guide_2 | 這些步數是模擬的，會標示為「手動輸入」。 |
| guide_3 | 請勿用於競賽、獎勵、保險、公益活動，或任何需要真實運動證明的場合。 |
| guide_4 | 適合用來示範、教學，或測試某個健康 App 能不能讀到步數。 |
| guide_5 | 部分 App 可能不會計入「手動輸入」的步數，或要等同步後才看得到；StepSim 無法保證所有 App 都讀得到。 |
| guide_6 | 暫停期間不會存入步數；從「最近使用」把 StepSim 滑掉，部分手機會中斷模擬。 |
| guide_7 ⚠️ | StepSim 本身不會上傳你的步數或任何健康資料。存入 Health Connect 的資料，其他已獲你授權的 App 可能會讀取。（「授權」要不要改成「允許」待 Q2 決定） |
| guide_8 | 你可以隨時到 Health Connect 撤銷權限或刪除資料。 |
| （返回按鈕） | 沿用 perm_back「返回」 |

## 授權頁
| key | 文字 |
|---|---|
| perm_title | 允許 StepSim 存入步數 |
| perm_why | StepSim 要把你設定的模擬步數存進手機的健康資料（Health Connect），其他健康 App 才讀得到。需要你允許「步數」權限。 |
| perm_point_1 | 只會存入你在這裡設定的模擬步數。 |
| perm_point_2 **[v2.1]** | StepSim 本身不會上傳你的步數或任何健康資料。 |
| perm_point_3 | 你隨時可以到 Health Connect 撤銷權限或刪除資料。 |
| perm_point_4 | 請勿用於競賽、獎勵、保險等需要真實運動證明的場合。 |
| perm_cta | 繼續 |
| perm_status_granted | 已允許，可以開始模擬了 |
| perm_status_denied | 尚未允許 |
| perm_manual_title | 還沒有取得權限 |
| perm_manual_body | 如果再按一次沒有跳出視窗，請照下面步驟手動開啟： |
| perm_manual_step_1 | 點下方按鈕開啟 Health Connect |
| perm_manual_step_2 | 選「應用程式權限」，再選「StepSim」（選單名稱要在實機核對） |
| perm_manual_step_3 | 打開「步數」的權限，然後回到 StepSim |
| perm_retry | 再試一次 |
| perm_open_hc | 開啟 Health Connect |
| perm_back_to_sim | 回到模擬 |
| perm_reset_perm | 重新設定權限 |
| perm_back | 返回 |
| perm_need_install | 這支手機還沒有 Health Connect。請先安裝，才能存入步數。 |
| perm_need_update | Health Connect 需要更新，才能存入步數。 |
| perm_not_supported | 這支手機不支援 Health Connect，無法使用 StepSim。 |
| perm_check_failed | 暫時無法確認權限，請稍後再試一次。 |

## Rationale 頁（只說明「存入」）
| key | 文字 |
|---|---|
| rationale_title | StepSim 為什麼需要步數權限 |
| rationale_intro | StepSim 是示範與測試工具： |
| rationale_write | 存入步數：把你設定的模擬步數存入健康資料，並標示為「手動輸入」，來源是 StepSim。 |
| rationale_privacy **[v2.1]** | StepSim 本身不會上傳你的步數或任何健康資料。 |

## 通知（沿用既有 key 與參數簽名，不改 `SimulationService.kt`）
| 既有 key（服務的傳入參數） | 新文字 |
|---|---|
| notif_channel_name | 步數模擬進度 |
| notif_title_starting | StepSim |
| notif_title_running（`%1$s`＝步態名稱、`%2$d`＝步頻） | %1$s模擬中 · 每分鐘約 %2$d 步 |
| sim_gait_walk／sim_gait_run（服務會引用，**key 不能改名**） | 走路／跑步 |
| notif_title_paused | 已暫停（模擬） |
| notif_title_failed（服務仍會傳批次編號） | 步數沒存進去 |
| notif_text_failed | 請開啟 StepSim 再試一次 |
| notif_title_permission | 需要重新允許權限 |
| notif_text_permission | 請開啟 StepSim 確認權限 |
| notif_title_completed | 模擬完成 |
| notif_progress（`%1$s`、`%2$s`、`%3$d`；完成時的內文也用這個） | 已存入 %1$s / %2$s 步（%3$d%%） |
| notif_action_pause | 暫停 |
| notif_action_resume | 繼續 |
| notif_action_stop | 結束本次模擬 |

**不採用**的 v2 通知 key：`notif_title_walking`、`notif_title_running`（v2 的版本）、`notif_title_done`、`notif_text_progress`、`notif_text_done`、`notif_action_end`。

## 禁用詞白名單
| key | 只允許出現的詞 |
|---|---|
| disclaimer_short | 偵測 |
| disclaimer_use | 獎勵 |
| guide_3 | 獎勵 |
| perm_point_4 | 獎勵 |
