# ADR-UI-001 — CSS Tokens, Shared Components, and Lightweight Routing

- **Status:** Approved by product owner
- **Approved:** 2026-11-28
- **Context:** UI refactor separates Admin Portal and Member Storefront while preserving the existing React/TypeScript
  stack and security/API contracts.
- **Decision:** Use CSS custom-property design tokens and project-owned shared UI components; no third-party UI library.
  Keep lightweight routing and consolidate route definitions, navigation, browser history, role return paths, and guards
  in `frontend/src/app/router.ts` and `App.tsx`.
- **Consequences:** Lower dependency and bundle risk; styles remain customizable. Routing responsibilities are
  centralized but the app must keep route tests and safe-return-path allowlists in sync.
- **Visual direction:** Neutral SaaS Admin workspace; independent Member storefront with a public campaign presentation.
  No formal brand assets were supplied, so MarketFlow is a temporary visual label and must not be treated as official
  brand identity.
