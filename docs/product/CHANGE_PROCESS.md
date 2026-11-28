# Product and Documentation Change Process

1. Create or update the relevant `prd/prd-xxx/` initiative.
2. Record the problem, goals, scope, non-goals, and acceptance criteria.
3. Analyze current behavior and identify affected current specifications.
4. Record initiative-specific decisions in `prd/prd-xxx/decisions/`.
5. Record cross-system architecture decisions in `docs/adr/`.
6. Obtain approval for the implementation plan before changing code.
7. Keep plans, implementation reports, test reports, and acceptance evidence in the initiative.
8. After acceptance, preserve the initiative's history. Update shared current documents only to reflect the accepted
   system state.
9. Update `docs/README.md` and the initiative's `README.md` when document locations or status change.

Do not change application code or business requirements as part of a documentation-only restructure.
