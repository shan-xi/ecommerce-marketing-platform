# Phase 5 Campaign audience rules fix

## Bug fixed

Saving a Campaign with a Saved audience segment selected and campaign-specific
rules disabled could still send an empty rules value that reached the audience
rule validator. The validator then rejected it with:

`Rule document must contain only version, match and conditions.`

The frontend omits `audience.rules` when the campaign-specific rules checkbox is
disabled. The backend now also normalizes both an absent rules value and an
explicit JSON `null` to `null` before composition validation and persistence.
This makes the API robust to either representation and prevents an absent rule
document from being validated as a rule document.

## Audience composition logic

- Saved Segment only: the selected Segment is the audience; campaign-specific rules are omitted.
- Campaign-specific rules only: no Segment is selected, and the enabled rules define the audience.
- Segment + campaign-specific rules: both are combined with AND, so a member must satisfy the Segment and the campaign
  rules.
- Neither selected nor enabled: the form blocks saving and the backend rejects the empty composition.
- The audience preview evaluates the audience from the saved campaign, not unsaved form edits.

## Verification

- Frontend test suite: 12 tests passed.
- Added backend unit tests for missing and explicit-null campaign rules.
- Backend Maven tests could not be executed in this environment because the Maven wrapper
  distribution could not be downloaded (network/DNS unavailable).

## Scope

This is a request-normalization fix. It does not add publishing, activation, pause,
or any other post-draft workflow.
