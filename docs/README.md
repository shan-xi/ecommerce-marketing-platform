# Documentation Hub

This documentation set separates **initiative history** from **current system specifications**.

## Documentation model

- **Historical / initiative documents (`prd/prd-xxx/`)** preserve why a change was proposed, its analysis and design,
  decisions, implementation, testing, and acceptance. Keep completed initiative records as historical evidence.
- **Current / living documents (`architecture/`, `database/`, `api/`)** describe the system as it is currently
  implemented and accepted. Update these only when an accepted change alters the current system.
- **Decision records:** initiative-specific decisions live under `prd/prd-xxx/decisions/`; cross-system architecture
  decisions live in `adr/`.

## Start here

| Area                      | Entry                                              |
|---------------------------|----------------------------------------------------|
| Product overview          | [Product Overview](product/PRODUCT_OVERVIEW.md)    |
| Product roadmap           | [Product Roadmap](product/PRODUCT_ROADMAP.md)      |
| Change process            | [Change Process](product/CHANGE_PROCESS.md)        |
| MVP initiative history    | [PRD-MVP](prd/prd-mvp/README.md)                       |
| UI refactor initiative    | [PRD-UI-Refactor](prd/prd-ui-refactor/README.md)       |
| Current architecture      | [Architecture](architecture/ARCHITECTURE.md)       |
| Current module design     | [Module Design](architecture/MODULE_DESIGN.md)     |
| Current security design   | [Security Design](architecture/SECURITY_DESIGN.md) |
| Current database design   | [Database Design](database/DATABASE_DESIGN.md)     |
| Current ERD               | [ERD](database/ERD.md)                             |
| Current API specification | [API Specification](api/API_SPECIFICATION.md)      |
| Current OpenAPI contract  | [OpenAPI](api/openapi.yaml)                        |
| Current API error codes   | [Error Codes](api/ERROR_CODES.md)                  |
| Global ADRs               | [Architecture Decision Records](adr/README.md)     |
| Templates                 | [Document Templates](templates/)                   |

## MVP current status

The MVP PRD is v1.5.0. Phases 0–10 are recorded as owner-accepted. Phase 10 environment-dependent verification
follow-ups remain explicitly open/unverified; see [MVP development records](prd/prd-mvp/development/README.md) and the Phase
10 reports.

## New initiative structure

Create a `prd-<initiative-name>/` folder for each substantial feature or product change. Use the templates in
`templates/` and scale the document set to the initiative's complexity. At minimum, retain the requirement, scope, key
decisions, implementation boundary, and acceptance evidence.

Do not move or rewrite current technical specifications merely to create historical copies. The MVP baseline snapshots
under `prd/prd-mvp/` preserve initiative context; the shared specifications remain the source of truth for the current
system.
