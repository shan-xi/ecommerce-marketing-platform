# Requirements Analysis — UI Refactor

**基線：** `ecommerce-marketing-platform-v0.1.0-ui.zip`  
**日期：** 2026-11-28

## 1. 現況盤點

| 層面 | ZIP 實際狀況 | 對重構的意義 |
|---|---|---|
| Frontend | React 19 + TypeScript + Vite | 保留現有技術棧 |
| Routing | `App.tsx` 以 pathname 判斷，監聽 `popstate` | 需集中路由與導覽邏輯；React Router 是待決策依賴 |
| Auth | `AuthProvider` + `authApi`；HttpOnly cookies、CSRF、refresh | 優先沿用，避免重寫認證 |
| Admin UI | Audience、Campaign、Coupon、Lifecycle、Analytics 元件已存在 | 主要是 Layout / navigation / page composition 重整 |
| Member UI | Campaign landing、Inbox 已存在 | 增加 Storefront、Profile、Coupon wallet |
| Styling | 單一 `styles.css` | 建立 Portal scope 與 design tokens |
| UI library | `package.json` 未列 Ant Design/MUI/Tailwind | 不得假設已安裝元件庫 |

## 2. Admin API 能力清單

- Auth: `/api/v1/auth/{csrf,login,refresh,logout,me}`
- Audience: `/api/v1/admin/audience-segments`（GET/POST）、`/{segmentId}`（GET/PUT）、`/preview`（POST）
- Campaign: `/api/v1/admin/campaigns`（GET/POST）、`/{campaignId}`（GET/PUT）、`/{campaignId}/audience-preview`（POST）
- Coupon: `/api/v1/admin/campaigns/{campaignId}/coupons`（GET/POST）、`/{couponId}`（PUT）
- Message: `/api/v1/admin/campaigns/{campaignId}/message`（GET/PUT）
- Lifecycle: `/{campaignId}/publish`、`/{campaignId}/pause`（POST）
- Delivery: `/{campaignId}/delivery-status`（GET）、`/{campaignId}/delivery/retry`（POST）
- Analytics: `/api/v1/admin/campaigns/{campaignId}/analytics`（GET）

上述 admin 路徑均要求 ADMIN。API 來源為 ZIP 中各 presentation controller 及 `docs/api/API_SPECIFICATION.md`。

## 3. Member / Public API 能力清單

- Profile: `GET /api/v1/member/profile`
- Inbox list/detail: `GET /api/v1/member/messages`、`GET /api/v1/member/messages/{messageId}`
- Claimed coupons: `GET /api/v1/member/coupons`
- Public campaign: `GET /api/v1/campaigns/{campaignId}`
- Public coupons: `GET /api/v1/campaigns/{campaignId}/coupons`
- Claim: `POST /api/v1/campaigns/{campaignId}/coupons/{couponId}/claims`（MEMBER）
- Events: `POST /api/v1/campaigns/{campaignId}/events`

## 4. 明確的 API 缺口 / 不可假設項目

1. **沒有公開 Campaign list/discovery API：** `/` 不能呼叫不存在的「全部活動」端點。先做活動入口與會員功能捷徑；精選活動若採靜態設定，需 owner 提供活動 ID。
2. **沒有 Admin dashboard aggregate API：** Overview 不得展示假 KPI；可以呼叫既有 campaign list 顯示最近活動。
3. **Profile 目前為讀取 API：** 不做 profile update form。
4. **沒有 read/unread 或 mark-as-read API：** Inbox 不做已讀狀態與標記按鈕。
5. **沒有 coupon redemption / order API：** Coupon wallet 只呈現領取資訊，不提供核銷或結帳。
6. **`/auth/me` 只有 userId、role：** Topbar 不可虛構姓名、Email、頭像；可顯示「管理員」與 userId。
7. **Member coupon response DTO：** UI 實作需以 CouponService 的實際 `memberClaims` 回傳型別為準，不自行假設欄位。

## 5. 現有功能保留對照

| 現有元件/功能 | 新位置 | 必須保留 |
|---|---|---|
| `AudienceAdmin` | Admin / Audience Segments | list/create/get/update/preview |
| `CampaignAdmin` | Admin / Campaigns | list/filter/create/edit |
| `CampaignCoupons` | Admin / Campaign workspace / Coupons | create/list/update |
| `CampaignLifecycle` | Admin / Campaign workspace / Message & Delivery | message save, publish, pause, delivery status, retry |
| `CampaignAnalyticsPage` | Admin / Campaign Analytics | 所有既有指標及錯誤處理 |
| `CampaignLandingPage` | Public campaign route | public data, eligibility, claim, idempotency, events |
| `MemberInbox` | Member / Messages | inbox + impression/click tracking |
| AuthProvider | Shared auth layer | session restore, refresh, logout error behavior |

## 6. 使用者旅程

### Admin
`/admin/login` → ADMIN 驗證 → `/admin` → Sidebar 導覽 → Audience/Campaigns → 編輯/發布/派送監控/Analytics → Topbar 登出。

### Member
`/` → `/member/login` → MEMBER 驗證 → `/member` → Profile/Messages/Coupons；從 Message 或活動連結進入 `/campaigns/:id` → 領券。未登入點領券 → `/member/login?returnTo=...` → 登入返回活動頁 → 使用者再次按領取。

## 7. 需求優先級

- **P0：** Admin/Member login split、role guards、兩套 shell、Admin 現有功能保留、Member home/login/logout/messages、公開活動頁領券流程保留。
- **P1：** Member profile、claimed coupon wallet、Admin overview recent campaigns、responsive navigation。
- **P2：** 進階 dashboard KPI、公開活動探索/分類、訊息已讀、個資編輯；需新增 API 或產品需求，不屬本次既有 API UI 範圍。
