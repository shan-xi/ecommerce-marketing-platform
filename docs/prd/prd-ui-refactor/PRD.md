# PRD — 前端 UI 重構（Admin Portal + Member Storefront）

**文件狀態：** Draft — 待產品負責人確認後核准  
**版本：** 0.2.0  
**建立日期：** 2026-11-28  
**依據基線：** `ecommerce-marketing-platform-v0.1.0-ui.zip`（React 19 + TypeScript + Vite；Backend v1.5.0 / Phase 9）  
**文件語言：** 繁體中文；API、程式識別字與狀態值保留英文

---

## 1. 執行摘要與建議

本次重構不是重寫業務功能，而是把現有單頁前端整理成兩個清楚分離的使用體驗：

1. **Admin Portal：** 獨立管理員登入入口；登入後採常見 SaaS 後台版型，左側固定/可收合導覽列、上方工具列與登入者選單，主要內容區承載既有管理功能。
2. **Member Storefront：** 獨立會員入口；採簡化電商活動首頁，不建置完整購物車、商品目錄或結帳。會員可登入/登出、看個人訊息與已領優惠券，並由活動入口前往公開活動頁領券。

### 1.1 UI 技術與視覺建議

**建議採用「Ant Design 風格的企業後台資訊架構」，實作前先確認是否引入 Ant Design 元件庫。** 本專案目前只有 React、React
DOM，沒有現成 UI component library；因此不能假設已有 Ant Design、MUI 或 Tailwind。

- 若接受新增依賴：優先評估 Ant Design，適合表格、表單、側欄、分頁、狀態標籤、Modal、通知等後台常見互動。
- 若希望降低依賴：以 CSS variables + 自建少量共用元件實作相同視覺語言；不可只把整個既有頁面包上 sidebar 就視為完成。
- Admin 採淺色工作區、低彩度側欄、清楚的頁面標題/操作區、卡片與表格；避免過度裝飾、過大 Hero、漸層與大量彩色區塊。
- Member 採獨立品牌導覽列與簡潔活動首頁，與 Admin 不共用後台導覽殼層。
- 本文件先定義視覺與互動規格，不指定未經確認的品牌色、Logo、字體或正式文案。

**建議決策：** 第一階段採 CSS variables + 可重用元件，除非產品負責人明確同意新增 Ant Design。這可避免在尚未確認設計系統前引入大型依賴；若後續選擇
Ant Design，頁面資訊架構與 API contract 不需改變。

## 2. 背景與現況盤點（來自提供的 ZIP）

### 2.1 前端現況

- React 19、TypeScript、Vite；目前為單一 SPA。
- 路由由 `App.tsx` 依 `window.location.pathname` 判斷，沒有 React Router。
- 現有共用登入元件 `LoginForm` 同時服務 ADMIN/MEMBER，登入後依角色導向。
- Admin 現有功能元件：`AudienceAdmin`、`CampaignAdmin`、`CampaignCoupons`、`CampaignLifecycle`、`CampaignAnalyticsPage`。
- Member 現有功能元件：`MemberInbox`、`CampaignLandingPage`。
- `AuthProvider` 管理登入狀態，透過 `/api/v1/auth/me` 還原 Session；access/refresh token 由 HttpOnly Cookie 管理，前端不得將
  token 寫入 localStorage/sessionStorage。
- `styles.css` 目前為全站單一樣式，尚未形成 Admin 與 Member 兩套 Layout。

### 2.2 已存在且本次必須沿用的 API 能力

- Auth：CSRF 初始化、login、refresh、logout、me。
- Admin：Audience Segment CRUD/list/detail/preview；Campaign CRUD/list/detail/audience preview；Coupon CRUD/list；Campaign
  message 編輯；publish/pause；delivery status/retry；analytics。
- Member：profile、message inbox/detail、member coupon claims；公開活動與優惠券列表；coupon claim；engagement events。
- API 由 Spring Security 做角色授權。前端路由保護只改善體驗，不能取代後端授權。

### 2.3 目前差距

- Admin 與 Member 共用 `/login`，沒有明確分離的入口。
- Admin 登入後沒有一致的左側導覽與右上角登入者區。
- Admin 首頁會同時顯示登入表單/功能內容的舊版邏輯，缺乏工作台資訊架構。
- Member 首頁不是一般電商入口，缺少獨立導覽、登入狀態與會員功能入口。
- 會員 profile 與 claimed coupons API 已存在，但現有畫面尚未完整呈現。
- 現有 Campaign 頁面已包含多項複雜操作；重構需保留其行為，不得因版面調整而刪除功能。

## 3. 問題陳述

目前前端偏向功能驗證頁面，Admin 與 Member 的使用情境混在同一個入口及頁面結構中。管理員缺乏標準後台導覽與操作脈絡；會員也缺少一般電商網站熟悉的首頁與會員中心入口。若直接大幅改寫，容易破壞現有
API 整合、活動領券流程、登入續期與安全機制。

## 4. 目標與成功指標

### 4.1 產品目標

1. 管理員與會員有清楚、互不混淆的登入入口與 Layout。
2. Admin 使用一致的 Sidebar + Topbar + Content 工作台。
3. 所有現有 Admin API 能在對應管理畫面被操作，不以靜態假資料取代。
4. Member 有簡單但完整的電商活動入口，可登入/登出、查看個人訊息、查看已領優惠券、進入活動頁領券。
5. 不改變既有 API contract、權限規則、領券一致性與追蹤事件語意。
6. 桌機、平板、手機皆可使用；管理功能以桌機優先但不可在窄螢幕溢出。

### 4.2 驗收指標

- Admin / Member 的登入入口、登入後首頁、導覽及登出流程可獨立驗收。
- 既有 API 功能均有對應 UI 操作及 loading/empty/error/success 狀態。
- 未登入或角色錯誤時不會看到受保護資料；後端仍負責最終授權。
- 不出現將登入表單和已登入後台同時顯示的狀態。
- Member 從訊息或活動入口前往活動頁，能維持既有活動瀏覽、資格判斷、領券與事件追蹤流程。
- `npm run typecheck`、`npm run build`、`npm test` 通過；瀏覽器端完成核心流程驗收。

## 5. 使用者與權限

| 角色      | 入口                             | 可使用範圍                                          |
|-----------|----------------------------------|-----------------------------------------------------|
| ADMIN     | `/admin/login`                   | `/admin/*` 管理後台                                 |
| MEMBER    | `/`、`/member/login`             | `/member/*` 會員中心與公開 `/campaigns/:campaignId` |
| Anonymous | `/`、`/member/login`、公開活動頁 | 瀏覽公開活動；領券前必須以 MEMBER 登入              |

### 5.1 登入入口分流規則

- `/admin/login` 是管理員專用入口。成功登入後必須確認 `role === ADMIN`，導向 `/admin`。
- `/member/login` 是會員專用入口。成功登入後必須確認 `role === MEMBER`，導向 `/` 或安全白名單內的會員 `returnTo`。
- `/login` 舊路徑應保留相容導向：依安全的 returnTo 或既有登入 Session 導向正確入口；不得繼續作為兩種角色共用的主要入口。
- 角色不符時顯示明確錯誤與正確入口連結；不可只隱藏頁面而讓使用者誤以為登入成功。
- 登出成功後清除前端 user state，回到該 Portal 的公開入口；登出 API 失敗時不得假裝登出成功。

### 5.2 安全要求

- 沿用 HttpOnly Cookie、refresh rotation、CSRF header、`credentials: include`。
- 不將 access token / refresh token 放入 Web Storage、URL、DOM 或 log。
- `returnTo` 僅接受站內明確白名單路徑，避免 Open Redirect。
- Member 資料一律由登入 principal 決定，不接受前端傳入 memberId。
- 前端 route guard 是 UX；API 403/401 仍需處理並顯示狀態。
- 不提供「切換角色」按鈕；要切換角色需登出後由另一入口登入。

## 6. 資訊架構與路由

### 6.1 路由表

| 路由                                     | 存取                    | 頁面 / 用途                                                            |
|------------------------------------------|-------------------------|------------------------------------------------------------------------|
| `/`                                      | Public                  | Member Storefront 首頁；訪客可看活動入口，已登入會員顯示個人化快捷入口 |
| `/member/login`                          | Public                  | 會員登入                                                               |
| `/member`                                | MEMBER                  | 會員首頁 / Overview                                                    |
| `/member/messages`                       | MEMBER                  | 會員訊息匣                                                             |
| `/member/coupons`                        | MEMBER                  | 已領取優惠券                                                           |
| `/member/profile`                        | MEMBER                  | 會員資料摘要                                                           |
| `/admin/login`                           | Public                  | Admin 專用登入                                                         |
| `/admin`                                 | ADMIN                   | Admin Dashboard / 工作台                                               |
| `/admin/audience`                        | ADMIN                   | Audience Segments                                                      |
| `/admin/campaigns`                       | ADMIN                   | Campaign 列表 / 新增 / 編輯                                            |
| `/admin/campaigns/:campaignId`           | ADMIN                   | Campaign 工作區（基本資料、Coupons、Message、Lifecycle、Delivery）     |
| `/admin/campaigns/:campaignId/analytics` | ADMIN                   | Campaign Analytics                                                     |
| `/campaigns/:campaignId`                 | Public + MEMBER actions | 公開活動頁；匿名可瀏覽，領券要求 MEMBER                                |
| 未知路由                                 | Any                     | 404 / 導回適當 Portal                                                  |

`/admin/campaigns/:campaignId` 為建議的新資訊架構路由；第一階段可保留現有 CampaignAdmin 的 list/detail
內部互動，再漸進拆出子路由。Analytics 舊路由需保持可用。

### 6.2 Admin Sidebar

- Overview
- Audience Segments
- Campaigns

導覽列底部或頂部可顯示平台名稱 / Logo（由產品提供資產後置換）。不新增未有 API 支援的 Users、Orders、Products、Settings 等功能項目。

### 6.3 Member 導覽

- 首頁 / 活動
- 我的訊息
- 我的優惠券
- 會員資料
- 右側登入/會員選單（依登入狀態變化）

## 7. Admin Portal UX 規格

### 7.1 共用版型

**桌面：**

- 左側 Sidebar 建議寬 240px，支援收合為 icon rail；固定於視窗高度。
- Topbar 約 64px，顯示麵包屑/頁面上下文、必要的全域操作與右上角登入者選單。
- 右上角登入者顯示角色「管理員」及可辨識的 user identifier（目前 `/auth/me` 僅提供 userId/role，不能虛構姓名/Email）；下拉選單至少有登出。
- Content 區使用一致最大寬度、頁面標題、說明、主要操作按鈕、內容卡片與表格。
- Sidebar 不應在登入頁顯示。

**平板/手機：**

- Sidebar 轉為 Drawer；有明確開啟/關閉按鈕與焦點管理。
- 表格容器可水平捲動或轉為卡片列表；不可讓整個頁面水平溢出。
- Topbar 登入者資訊可收斂為頭像/縮寫圖示與選單。

### 7.2 Admin Overview

- 顯示可執行的入口卡片：Audience Segments、Campaigns。
- 顯示最近活動或 KPI 前，必須使用現有 API 真實資料；目前未見 dashboard aggregate endpoint，因此 MVP 不要求全域 KPI 卡或假造圖表。
- 可顯示最近 Campaign 列表（由既有 campaign list API 取得），並提供「建立活動」及「查看全部」。
- 若暫時無法取得資料，顯示空狀態與重試操作，不顯示 0 作為假資料。

### 7.3 Audience Segments

保留既有功能：

- 分頁列表、建立、讀取、編輯、儲存。
- 規則編輯器、規則預覽（preview）。
- 表單驗證、儲存中、儲存成功/失敗、無資料狀態。
- 儲存後重新讀取或更新列表，確保畫面資料與 API 一致。

### 7.4 Campaigns

保留既有功能：

- Campaign 分頁列表、狀態篩選（DRAFT / ACTIVE / PAUSED / ENDED）、新增、讀取、編輯。
- 基本欄位：名稱、描述、起訖時間、Landing Page headline/body、Audience segment/rules。
- Audience Preview。
- Coupon 建立、列表、編輯；僅符合後端規則時允許編輯（已領取 coupon 不可重新設定；活動須為 DRAFT）。
- Message 讀取/編輯/儲存。
- Publish / Pause。
- Delivery Status、processed/total、attempt、last error、Retry（僅 retryable 時）。
- Campaign Analytics：Target/Delivered、Message Engagement、Landing Page、Coupon Claims & Audit、Coupon Performance。
- 所有破壞性/不可逆操作需確認；Publish 明確顯示活動受眾 snapshot 與派送影響提示。

建議以 Campaign detail workspace 的 Tabs/Sections 分區，不把所有表單與報表塞在同一長頁；可採
Overview、Audience、Coupons、Message & Delivery、Analytics。不得讓 Tabs 隱藏重要狀態或繞過權限。

## 8. Member Storefront UX 規格

### 8.1 範圍界線

這是活動與會員互動入口，不是完整電商。MVP 不做商品目錄、購物車、結帳、金流、訂單、物流或庫存商品頁。

### 8.2 首頁 `/`

- 一般電商常見的輕量 Header：品牌區、活動/優惠入口、會員登入或會員選單。
- Hero 區介紹「會員專屬活動與優惠」，主 CTA 為瀏覽活動（活動資料需有 API 支援才顯示動態活動清單）。
- 已登入會員顯示歡迎區與「我的訊息」「我的優惠券」快捷入口。
- 訪客顯示登入 CTA；不顯示管理員登入入口於主導覽，可在頁尾提供低調的「管理員入口」連結至 `/admin/login`。
- ZIP 中目前只有指定 campaignId 的公開活動查詢，未見公開 Campaign discovery/list API。未新增 API
  前，首頁不得假裝能列出全部活動；可以顯示品牌/功能介紹、會員登入入口與「由訊息/活動連結進入活動」提示，或使用產品提供的靜態精選活動連結（需明確由
  owner 提供）。

### 8.3 Member Login

- `/member/login` 使用獨立會員文案與版面。
- 支援 returnTo 活動頁；登入後回到該活動頁，但不可自動領券。
- 若已登入 ADMIN，顯示角色不符與登出/切換入口，不呈現會員個資。

### 8.4 Member Overview / Profile

- `/member` 顯示 profile 摘要、訊息數量（若列表 API 可得）、已領券入口。
- `/member/profile` 使用 `GET /api/v1/member/profile`；可展示 API 回傳的
  displayName、membershipTier、registeredAt、orderCount、totalSpending、lastOrderAt、lastLoginAt 等欄位。
- birthDate、age、gender 屬個人資料，預設只在會員資料頁呈現必要欄位；不要在首頁突出年齡或性別。不得在前端推導不存在的欄位。
- Profile API 為讀取 API；本次不提供修改個人資料表單。

### 8.5 Member Messages

- 沿用 `GET /api/v1/member/messages` 分頁列表。
- 每則訊息顯示活動名稱、標題、內文摘要、送達時間與「查看活動」。
- 點擊活動時保留既有 CLICK event 記錄後導頁行為；列表曝光仍記錄 IMPRESSION。
- 若使用訊息 detail API，需確認其回應欄位與已讀語意；目前文件未定義 read/unread 或 mark-as-read API，因此不可設計已讀/未讀切換。

### 8.6 Member Coupons

- `/member/coupons` 使用 `GET /api/v1/member/coupons` 顯示會員已領優惠券。
- 顯示名稱、折扣類型/數值、活動/有效期間、領取時間及 API 可提供的狀態；欄位以實際 DTO 為準。
- 空狀態提供返回活動入口。
- 不提供核銷、套用訂單或 checkout，因本平台沒有訂單/核銷 API。

### 8.7 Public Campaign Landing Page

- 保留既有公開活動查詢、優惠券狀態、會員資格提示、領券流程及事件追蹤。
- 未登入訪客點擊領券，導向 `/member/login?returnTo=/campaigns/:id`；登入後回到活動頁，需由會員再次點擊領券。
- ADMIN 不可在此頁領券，顯示會員登入入口。
- 不得在前端自行判斷並覆蓋後端 `memberStatus`、`claimable` 或資格結果。
- 保留 idempotency key 與現有 claim error handling。

## 9. API 對應與覆蓋矩陣

| UI 區域         | API                                                      | 用途                          | 備註                              |
|-----------------|----------------------------------------------------------|-------------------------------|-----------------------------------|
| Login / Session | `GET /auth/csrf`                                         | CSRF 初始化                   | 沿用 authApi                      |
| Login / Session | `POST /auth/login`                                       | 登入                          | 依入口驗證 role                   |
| Session         | `GET /auth/me`                                           | 還原 Session                  | role-based route guard            |
| Session         | `POST /auth/refresh`                                     | Session refresh               | 沿用既有自動 refresh              |
| Logout          | `POST /auth/logout`                                      | 登出                          | 成功後清除本地 user               |
| Admin Audience  | `GET/POST /admin/audience-segments`                      | 列表/建立                     | 分頁                              |
| Admin Audience  | `GET/PUT /admin/audience-segments/{id}`                  | 讀取/更新                     |                                   |
| Admin Audience  | `POST /admin/audience-segments/preview`                  | 規則預覽                      |                                   |
| Admin Campaign  | `GET/POST /admin/campaigns`                              | 列表/建立                     | 分頁/狀態篩選                     |
| Admin Campaign  | `GET/PUT /admin/campaigns/{id}`                          | 讀取/更新                     |                                   |
| Admin Campaign  | `POST /admin/campaigns/{id}/audience-preview`            | 受眾預覽                      |                                   |
| Admin Coupon    | `GET/POST /admin/campaigns/{id}/coupons`                 | 列表/建立                     |                                   |
| Admin Coupon    | `PUT /admin/campaigns/{id}/coupons/{couponId}`           | 更新                          | 僅 Draft/未領取符合後端規則       |
| Admin Message   | `GET/PUT /admin/campaigns/{id}/message`                  | 讀取/儲存訊息                 |                                   |
| Admin Lifecycle | `POST /admin/campaigns/{id}/publish`                     | 發布                          | 二次確認                          |
| Admin Lifecycle | `POST /admin/campaigns/{id}/pause`                       | 暫停                          | 二次確認                          |
| Admin Delivery  | `GET /admin/campaigns/{id}/delivery-status`              | 派送狀態                      |                                   |
| Admin Delivery  | `POST /admin/campaigns/{id}/delivery/retry`              | 重試                          | retryable 時顯示                  |
| Admin Analytics | `GET /admin/campaigns/{id}/analytics`                    | 活動報表                      |                                   |
| Member Profile  | `GET /member/profile`                                    | 會員資料摘要                  | 只讀                              |
| Member Inbox    | `GET /member/messages`                                   | 訊息列表                      | 分頁                              |
| Member Message  | `GET /member/messages/{messageId}`                       | 單則訊息                      | API 已存在；read semantics 未定義 |
| Member Coupons  | `GET /member/coupons`                                    | 已領優惠券                    |                                   |
| Public Campaign | `GET /campaigns/{campaignId}`                            | 活動資訊                      | 公開                              |
| Public Coupons  | `GET /campaigns/{campaignId}/coupons`                    | 活動優惠券                    |                                   |
| Claim           | `POST /campaigns/{campaignId}/coupons/{couponId}/claims` | 領券                          | MEMBER + idempotency key          |
| Engagement      | `POST /campaigns/{campaignId}/events`                    | impression/click/landing view | 保留既有 event types              |

完整 path 以 `/api/v1` 為前綴。實作前需以 Controller 與 `docs/api/API_SPECIFICATION.md` 核對 request/response DTO；本表不代表新增
API。

## 10. 非功能需求

### 10.1 Responsive

- Desktop：Admin sidebar + topbar 完整顯示；表格優先。
- Tablet：Sidebar Drawer；表格可水平捲動。
- Mobile：Member 優先提供完整體驗；Admin 可完成基本瀏覽與表單操作，複雜表格允許局部橫向捲動。
- 互動控制最小觸控區建議 44×44 CSS px。

### 10.2 Accessibility

- 語意化 landmark、正確 heading hierarchy、label/input 關聯。
- Drawer、Modal 支援鍵盤操作、Esc 關閉、focus trap/return。
- 狀態不能只靠顏色表達；錯誤訊息使用 `role=alert`，載入狀態使用 `aria-live`。
- 表格有欄位標題；icon-only control 提供 accessible name。
- 對比符合 WCAG 2.1 AA 目標。

### 10.3 Performance / Maintainability

- 共用 Portal Shell、Button、Form Field、Status Badge、Data Table、Empty State、Error State、Confirm Dialog。
- 功能頁與 API client 分層；避免將 API 呼叫塞進 Layout。
- 目前未使用 Router；實作可評估 React Router，但需先決定是否引入依賴。若維持輕量自建 Router，必須集中管理 route
  matching、navigation、history/popstate 與 404。
- 不得因 UI 重構更改 backend domain logic 或 API response shape。

## 11. 不在本次範圍

- 完整商城：商品、購物車、訂單、結帳、金流、物流、退貨。
- 會員註冊、忘記密碼、社群登入、OAuth/SSO。
- 編輯會員個資、已讀/未讀、訊息刪除。
- Admin user management、role management、系統設定。
- Dashboard 新增 aggregate API、報表匯出、排程活動。
- 多語系管理、深色模式、品牌客製化工具。
- 後端 API/資料庫 schema 變更；如實作中確認必要，需另開變更提案與 API PRD。

## 12. 風險與緩解

| 風險                                     | 影響             | 緩解                                                            |
|------------------------------------------|------------------|-----------------------------------------------------------------|
| UI route 分離但後端角色授權未驗證        | 權限風險         | 保留後端 Security；補 401/403 測試                              |
| 共用登入流程改動造成 refresh/CSRF 回歸   | 無法登入/登出    | 保留 AuthProvider/apiRequest，補登入、續期、登出測試            |
| Campaign 功能拆頁時漏掉既有操作          | 管理流程中斷     | 以 API 覆蓋矩陣與現有元件逐項驗收                               |
| Member 首頁沒有公開活動列表 API          | 首頁無動態活動卡 | 不虛構列表；先做品牌入口與會員快捷功能，活動從訊息/明確連結進入 |
| Profile/Coupon DTO 欄位與預期不符        | 顯示錯誤         | 實作前以實際 DTO/JSON contract 為準                             |
| 引入大型 UI library 增加 bundle/版本風險 | 開發成本         | 第一階段預設 CSS design tokens + 自建共用元件；依核准結果再引入 |

## 13. 交付物

1. Admin Portal Shell（Sidebar、Topbar、User Menu、Responsive Drawer）。
2. Admin Login、Overview、Audience、Campaign workspace。
3. Member Storefront Shell、Home、Member Login、Overview、Profile、Messages、Coupons。
4. Public Campaign Landing Page 視覺整合（保留領券及追蹤邏輯）。
5. Route guards、role mismatch、401/403/404 與 logout state handling。
6. UI design tokens / component conventions。
7. 自動化測試與手動驗收紀錄。

## 14. 驗收條件（Definition of Done）

### Admin

- [ ] `/admin/login` 不顯示 Member Storefront header/sidebar。
- [ ] ADMIN 登入後進入 `/admin`；MEMBER 不可進入 Admin，並看到明確提示。
- [ ] Sidebar 三個入口可導向 Overview、Audience Segments、Campaigns。
- [ ] Topbar 右上角顯示目前登入者角色與 userId，登出可用。
- [ ] Audience CRUD、Preview、Campaign CRUD、Coupon CRUD、Message、Publish/Pause、Delivery/Retry、Analytics 全部使用真實 API。
- [ ] 每個非同步頁面都有 loading、empty、error、success feedback。
- [ ] 手機版 Sidebar 可開關，主要表單可操作。

### Member

- [ ] `/` 顯示獨立 Member Storefront，不顯示 Admin Sidebar。
- [ ] `/member/login` 僅接受 MEMBER；ADMIN 不會誤入會員頁。
- [ ] MEMBER 登入後可登出，登入狀態在重新整理後可由 `/auth/me` 還原。
- [ ] `/member/profile` 顯示 API 實際回傳資料。
- [ ] `/member/messages` 顯示訊息並可開啟活動，保留 CLICK/IMPRESSION 追蹤。
- [ ] `/member/coupons` 顯示已領優惠券。
- [ ] 公開活動頁匿名可瀏覽；領券前要求 MEMBER 登入；登入返回活動頁但不自動領券。
- [ ] 領券成功/失敗/已領取/不符合資格/額滿等狀態正確呈現。

### Quality

- [ ] `npm run typecheck`、`npm run build`、`npm test` 通過。
- [ ] 主要流程在 desktop/mobile viewport 手動測試。
- [ ] 無 API contract、security cookie、CSRF、claim idempotency 回歸。
- [ ] 無新增未經核准的 API 或假資料。

## 15. 待確認事項

請在實作授權前確認 `OPEN_DESIGN_QUESTIONS.md`。本 PRD 目前為 Draft，未代表所有設計選擇已核准。
