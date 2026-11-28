# PRD-UI-Refactor

**Status:** Approved for implementation; Phase 1–4 implementation candidate delivered, Phase 5 verification pending.  
**Version:** 0.2.0  
**Created:** 2026-11-28

本 initiative 將現有單一 SPA 重構為分離的 Admin Portal 與 Member Storefront。所有需求以目前提供的
`ecommerce-marketing-platform-v0.1.0-ui.zip` 為基線；不代表已修改或實作前端。

## 文件導覽

- [PRD.md](PRD.md) — 產品目標、角色、路由、UI/UX、API 對應、驗收條件
- [REQUIREMENTS_ANALYSIS.md](REQUIREMENTS_ANALYSIS.md) — 現況盤點、需求拆解與 API 能力邊界
- [TECHNICAL_DESIGN.md](TECHNICAL_DESIGN.md) — 前端架構、Layout、Routing、Auth、元件與狀態設計
- [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) — 分階段實作順序與測試策略
- [ACCEPTANCE_CRITERIA.md](ACCEPTANCE_CRITERIA.md) — 可執行的驗收案例
- [OPEN_DESIGN_QUESTIONS.md](OPEN_DESIGN_QUESTIONS.md) — 待 owner 決策項目
- [decisions/README.md](decisions/README.md) — 本 initiative 的設計決策紀錄
- [ADR-UI-001](decisions/ADR-UI-001-css-components-and-lightweight-router.md) — 已核准 UI library / routing 決策
- [Implementation Report](implementation/IMPLEMENTATION_REPORT.md) — 實作與驗證狀態

## 核心原則

1. Admin 與 Member 使用不同入口與 Layout。
2. 保留現有 backend API 與 security contract；UI route guard 不取代 server authorization。
3. 不以假資料呈現不存在的 dashboard KPI 或公開活動列表。
4. 保留活動領券、idempotency key、CSRF、refresh cookie 與 engagement tracking 行為。
5. 此 PRD 尚待核准；實作前先確認 Open Design Questions。
