# Open Design Questions

以下項目需在開始 UI implementation 前由產品負責人確認。文件中的建議值可作為預設方案，但尚未視為核准。

| ID   | 問題                                          | 建議預設                                                                       | 影響                              |
|------|-----------------------------------------------|--------------------------------------------------------------------------------|-----------------------------------|
| Q-01 | 是否接受新增 Ant Design？                     | **已核准：CSS tokens + 自建共用元件，不新增 UI Library。**                     | dependency、bundle、表單/表格元件 |
| Q-02 | 是否有正式品牌 Logo / 色彩 / 字體？           | 暫用中性品牌 placeholder，不自行創作正式品牌識別                               | Member header、Admin sidebar      |
| Q-03 | Admin user menu 要顯示什麼？                  | 目前 API 只有 userId/role，顯示 role + userId                                  | Topbar identity                   |
| Q-04 | Member 首頁是否提供精選活動？                 | 若無公開活動列表 API，由 owner 提供固定 campaign UUID；否則不顯示假活動        | Home content                      |
| Q-05 | Member coupon card 顯示欄位？                 | 依 `GET /member/coupons` 實際 response DTO 定義                                | Coupon wallet                     |
| Q-06 | Member 首頁登入後預設落點？                   | `/member` overview；活動登入 returnTo 優先                                     | routing                           |
| Q-07 | 是否引入 React Router？                       | **已核准：保留輕量 Router，集中 route table、navigation、history、guards。**   | dependency、route guard           |
| Q-08 | Admin Campaign workspace 使用 Tabs 或分段頁？ | Tabs：Overview / Audience / Coupons / Message & Delivery；Analytics 獨立 route | Campaign information architecture |
| Q-09 | 是否需要登入頁 demo credentials / 預填帳號？  | 正式 UI 不預填 admin email/password；僅在 local dev 明確開發模式允許           | credential safety                 |

## 已知不可由前端單獨解決的能力缺口

- 公開活動探索列表：需新增公開 campaign discovery API 或由 owner 提供靜態活動 ID。
- Admin Dashboard aggregate metrics：需新增專用 API；本次不造假。
- 訊息已讀/未讀：需新增 read state 與 mark-read API。
- Profile update：需新增更新 API 與欄位驗證規格。
- Coupon redemption / checkout：不在現有平台 API 能力內。
