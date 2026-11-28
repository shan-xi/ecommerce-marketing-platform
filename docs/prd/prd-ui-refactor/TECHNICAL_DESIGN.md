# Technical Design — UI Refactor

**狀態：** Proposed  
**原則：** 先重整 UI 架構與路由，不變更後端 API contract。

## 1. 建議前端分層

```text
frontend/src/
  app/
    App.tsx
    router.ts                 # 集中 route match / navigation
    routeGuards.tsx           # Auth loading / role / anonymous guards
  layouts/
    AdminLayout.tsx           # Sidebar + Topbar + Outlet/content
    MemberLayout.tsx          # Storefront Header + Footer
    AuthLayout.tsx            # Admin / Member login variants
  features/
    auth/
    admin/
      dashboard/
      audience/
      campaigns/
      analytics/
    member/
      home/
      profile/
      messages/
      coupons/
    engagement/
      CampaignLandingPage.tsx
  shared/
    components/
      Button, PageHeader, DataTable, StatusBadge,
      EmptyState, ErrorState, LoadingState, ConfirmDialog
    lib/                      # 保留現有 API clients
    styles/
      tokens.css
      admin.css
      member.css
```

可先以現有 feature component 路徑逐步搬移，避免一次性大改造成 regression。不要在第一個 commit 同時搬檔、改 API、改 domain
logic。

## 2. Routing

目前 `App.tsx` 直接讀取 pathname。重構應集中以下責任：

- `navigate(path)`：pushState + 更新 route state。
- `popstate`：同步 browser back/forward。
- route table：精確匹配靜態路徑與 UUID path。
- unknown path：顯示 404。
- route guard：依 AuthProvider 的 `loading / authenticated / anonymous / error` 狀態決定 render。
- `returnTo`：只允許 `/campaigns/:uuid` 或明確白名單內的 member routes；拒絕 protocol-relative、外部 URL、反斜線與未知路徑。

是否引入 React Router 是 Open Design Question。若引入，需評估 package-lock、bundle 與測試；若不引入，需避免散落在多個
component 的 `window.location` 與 pathname 判斷。

## 3. Auth / Role Guard

- 共用 AuthProvider 與 `authApi`；不建立第二套 token/session 管理。
- `AdminLoginPage` 與 `MemberLoginPage` 可共用 `LoginForm` 的表單邏輯，但入口、標題、說明、role expectation 不同。
- `signIn` 後立即驗證 role；若 role mismatch，清楚顯示錯誤並避免進入錯誤 Portal。可選擇呼叫 logout 清除錯誤角色
  session，但需明確處理 logout failure。
- 已登入使用者訪問另一角色 Portal 時，顯示 access denied / switch account instruction；不可靜默將不同角色當成正確角色。
- Auth state `error`（後端不可用）不得被當作 anonymous；提供 retry。
- Logout 只有 server API 成功才更新成 anonymous（沿用現有 AuthProvider 語意）。

## 4. Layout Composition

### AdminLayout

- Sidebar：品牌標題、Overview、Audience Segments、Campaigns。
- Topbar：breadcrumb/page title context、user menu（角色 + userId + logout）。
- Main：`PageHeader` + page content。
- 登入頁不套 AdminLayout。
- Responsive：桌機固定/可收合；窄螢幕 Drawer。

### MemberLayout

- Header：品牌、活動入口、我的訊息、我的優惠券、登入/會員 menu。
- Footer：一般會員入口與低調 Admin Login link。
- 不共用 Admin Sidebar。
- 活動頁沿用 Member/Public shell，避免重新載入導致活動追蹤或 claim state 遺失。

## 5. Design Tokens

以 CSS custom properties 集中定義；以下是設計方向而非已核准品牌色：

```css
:root {
  --color-bg: #f5f7fa;
  --color-surface: #ffffff;
  --color-text: #172033;
  --color-muted: #667085;
  --color-border: #e5e9f0;
  --color-primary: #315efb;
  --color-success: #16845b;
  --color-warning: #b7791f;
  --color-danger: #c43d3d;
  --radius-card: 12px;
  --shadow-elevation-1: 0 1px 3px rgb(16 24 40 / 8%);
}
```

Admin 用中性工作台視覺；Member 可使用同一套基礎 token，但品牌展示與 layout spacing 可不同。色值應在 owner approval
後收斂，狀態色需兼顧對比度。

## 6. API Client 使用規範

- 保留 `apiRequest` 的 credentials、CSRF、refresh retry 與錯誤 envelope handling。
- 不在 component 內直接散落 `fetch`。
- 為 member profile 與 coupon wallet 新增 typed API client；型別必須依 controller/service DTO 實際 response 建立。
- 既有 campaign/audience/analytics/engagement API client 保持相容。
- 所有 API 失敗需區分 401、403、404、409、5xx；不將錯誤吞掉後顯示成功。
- Publish/Pause/Retry/Claim 等 mutation 防止重複點擊；claim 保留 idempotency key。

## 7. 頁面資料狀態

每個 API-driven page 至少定義：

- `loading`：骨架或明確 loading。
- `success with data`：真實 API data。
- `success empty`：empty state + 合理下一步。
- `error`：錯誤訊息 + retry。
- `submitting`：避免重複送出。
- `success feedback`：toast/inline message，必要時 refresh data。

避免 `useEffect` race：route/entity ID 改變時，舊 request 不可覆蓋新頁資料；可用 AbortController 或 request sequence。

## 8. Admin 功能拆分建議

- Audience Segments：列表與 editor 可先保留在一個 feature route，後續再拆 component。
- Campaign list：列表/篩選/建立。
- Campaign workspace：Basic Info、Audience、Coupons、Message & Delivery。
- Analytics：保留獨立 route `/admin/campaigns/:id/analytics`。
- Overview：只顯示既有 Campaign API 可取得的最近活動與快捷操作；無 aggregate API 時不做 KPI。

## 9. Member 功能拆分建議

- Home：靜態品牌入口 + 登入後 shortcuts；不聲稱能列出所有活動。
- Profile：讀取 `GET /member/profile`。
- Messages：保留 impression/click event。
- Coupons：讀取 `GET /member/coupons`。
- Campaign landing：沿用既有 public APIs 與 claim flow。

## 10. 風險控制與相容性

- 逐步遷移，保持舊 `/login` 與 analytics URL 相容一段時間。
- 不變更 API 路徑、DTO、角色名稱、cookie path、CSRF header。
- 不修改 coupon claim idempotency 與 event payload。
- 將舊頁面行為列入 regression checklist。
- UI library 未核准前不新增依賴。
