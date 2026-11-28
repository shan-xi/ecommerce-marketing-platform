# Acceptance Criteria — UI Refactor

## A. Authentication / routing

| ID | Scenario | Expected |
|---|---|---|
| AUTH-01 | 未登入開啟 `/admin` | 導向 `/admin/login`，保留安全 returnTo |
| AUTH-02 | ADMIN 由 `/admin/login` 登入 | 進入 `/admin` |
| AUTH-03 | MEMBER 在 `/admin/login` 輸入正確帳密 | 顯示角色不符，不進入 Admin |
| AUTH-04 | 未登入開啟 `/member/messages` | 導向 `/member/login?returnTo=...` |
| AUTH-05 | MEMBER 由 `/member/login` 登入 | 返回合法 returnTo 或 `/member` |
| AUTH-06 | ADMIN 由 `/member/login` 登入 | 顯示角色不符，不進入 Member |
| AUTH-07 | 登入狀態重新整理 | `/auth/me` 還原正確角色與 session |
| AUTH-08 | Access token 過期 | 沿用 refresh 流程，成功後原請求重試一次 |
| AUTH-09 | Logout API 成功 | 清除 user state 並回 Portal 公開入口 |
| AUTH-10 | Logout API 失敗 | 不顯示登出成功，保留登入狀態與錯誤 |
| AUTH-11 | returnTo 為外部 URL / `//host` / 不允許路徑 | 忽略並導向預設頁 |
| AUTH-12 | 直接呼叫 admin API 的 MEMBER | 後端回 403；UI 顯示無權限 |

## B. Admin

| ID | Scenario | Expected |
|---|---|---|
| ADM-01 | Admin 登入頁 | 不顯示 Sidebar/後台 Topbar |
| ADM-02 | 登入後開啟 `/admin` | 顯示 Sidebar、Topbar、user menu |
| ADM-03 | Topbar user identity | 顯示 ADMIN 與 API 提供的 userId，不虛構姓名 |
| ADM-04 | 點 Sidebar Audience | 顯示 Audience Segments |
| ADM-05 | Audience CRUD/Preview | 對應既有 API，成功/失敗狀態正確 |
| ADM-06 | Campaign list/filter | 使用既有分頁與 status API |
| ADM-07 | Campaign create/edit | 儲存至既有 API，驗證錯誤可見 |
| ADM-08 | Coupon create/update | 遵守 draft/claimed constraints，錯誤不被吞掉 |
| ADM-09 | Message save | GET/PUT API 資料一致 |
| ADM-10 | Publish/Pause | 有確認提示，成功後更新狀態 |
| ADM-11 | Delivery status/retry | 顯示進度與錯誤；僅 retryable 時可重試 |
| ADM-12 | Analytics | 所有既有指標保留，不更改分母/分子語意 |
| ADM-13 | 無 Campaign 資料 | 顯示 empty state，不顯示假 KPI |
| ADM-14 | Mobile sidebar | Drawer 可開啟/關閉並可鍵盤操作 |

## C. Member

| ID | Scenario | Expected |
|---|---|---|
| MEM-01 | 訪客開啟 `/` | 顯示 Member Storefront 與登入入口 |
| MEM-02 | 會員登入後首頁 | 顯示會員快捷入口 |
| MEM-03 | Member profile | 只顯示 profile API 實際欄位 |
| MEM-04 | Member inbox | 顯示已送達訊息與空/錯誤狀態 |
| MEM-05 | 訊息卡片進入 viewport | 保留 IMPRESSION event |
| MEM-06 | 點「查看活動」 | 記錄 CLICK 後前往活動頁 |
| MEM-07 | Coupon wallet | 顯示會員 API 回傳已領取優惠券 |
| MEM-08 | 無優惠券 | 顯示 empty state 與活動入口 |
| MEM-09 | 訪客在活動頁按領取 | 導向會員登入並保留活動 returnTo |
| MEM-10 | 登入返回活動頁 | 不自動領券；須再次按領取 |
| MEM-11 | 不符合資格/已領取/額滿 | 顯示後端狀態，不自行推算 |
| MEM-12 | 領券重複提交/重試 | 保留 idempotency key，結果一致 |
| MEM-13 | Member logout | 清除 session 並回首頁 |

## D. Quality / Responsive / Accessibility

- [ ] Desktop 1440px、Tablet 768px、Mobile 375px 無全頁水平溢出。
- [ ] 所有表單有可見 label、必要欄位提示與錯誤關聯。
- [ ] Drawer/Modal 支援鍵盤、Esc 與 focus return。
- [ ] Loading/Success/Error 不只依顏色表達。
- [ ] API request race 不會讓舊資料覆蓋目前 route。
- [ ] `npm run typecheck`、`npm run build`、`npm test` 通過。
