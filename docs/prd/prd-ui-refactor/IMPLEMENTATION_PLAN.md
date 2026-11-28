# Implementation Plan — UI Refactor

**狀態：** Proposed；依賴 PRD / Open Design Questions 核准。

## Phase 0 — 決策與基線
1. 核准 Admin/Member 分流、路由、視覺方向與 UI library 策略。
2. 記錄目前核心流程的截圖/測試基線。
3. 執行並記錄 `npm run typecheck`、`npm run build`、`npm test`。
4. 核對 member profile / member coupon API 的實際 JSON response。

**Exit:** 決策項目已關閉；原始基線測試結果已保存。

## Phase 1 — Foundation / Routing / Auth
1. 建立集中 route table/navigation helper。
2. 實作 Admin / Member / Public route guards。
3. 新增 `/admin/login`、`/member/login`，保留 `/login` 相容導向。
4. 驗證 role mismatch、Auth loading/error、refresh、logout、safe returnTo。
5. 加入 404。

**Exit:** 兩種角色登入入口獨立且不破壞 Cookie/CSRF/refresh。

## Phase 2 — Admin Portal Shell
1. 實作 Sidebar、Topbar、User Menu、responsive Drawer。
2. 建立 Overview、PageHeader、Breadcrumb、共用狀態元件。
3. 將 Audience、Campaign、Analytics 現有元件掛入新 Shell。
4. 完成 sidebar active state、collapsed state 與 responsive behavior。

**Exit:** Admin 全部頁面在一致 shell 中可操作。

## Phase 3 — Admin Workflow Polish
1. Audience list/editor/preview 狀態一致化。
2. Campaign list/filter/create/edit。
3. Campaign workspace 整理 Coupon、Message、Lifecycle、Delivery。
4. Publish/Pause/Retry confirmation 與 feedback。
5. Analytics 視覺整理但不改計算口徑。

**Exit:** API coverage matrix 所列 Admin endpoint 均可由 UI 觸發。

## Phase 4 — Member Storefront
1. Member Header/Footer/Home。
2. Member login/logout。
3. Member overview/profile。
4. Messages 與既有 event tracking。
5. Coupon wallet。
6. Public campaign page 接入新 Member shell，保留 claim flow。

**Exit:** 會員核心旅程可完成；不引入購物車/結帳等超出範圍功能。

## Phase 5 — QA / Accessibility / Responsive
1. 跑 typecheck/build/unit tests。
2. 加入 route/auth/API client/component tests。
3. 驗證 desktop/tablet/mobile。
4. 驗證鍵盤、focus、表單 label、aria live、contrast。
5. 驗證 401/403/404/409、網路中斷、空資料、重複提交。
6. 產出 Phase implementation report 與 acceptance evidence。

**Exit:** 全部 P0/P1 acceptance cases 通過；未通過項目明確記錄，不宣稱完成。

## 建議實作順序

先做 Phase 1，再做 Admin Shell 與 Member Shell。避免先重寫每個 feature component；先讓現有功能安全地置入新 Layout，再逐頁改善表格與表單體驗。
