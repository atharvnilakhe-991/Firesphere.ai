# Member 1 — Mobile Frontend Implementation Plan

## Ownership
React Native + TypeScript citizen and officer applications, offline-first inspection UX, evidence capture, AI finding presentation, mobile release preparation.

## Phase 1 — Foundation
- [ ] React Native/TypeScript project
- [ ] navigation
- [ ] design system/reusable components
- [ ] API client
- [ ] secure token storage
- [ ] loading/error/empty states
- [ ] network detection
- [ ] offline storage foundation
- [ ] Android build configuration

## Phase 2 — Citizen
- [ ] registration/login/logout
- [ ] profile
- [ ] create/save/submit Fire NOC application
- [ ] application status/timeline
- [ ] document upload/preview/retry/status
- [ ] payment initiation/status/receipt
- [ ] inspection status
- [ ] notifications
- [ ] certificate access
- [ ] renewal reminders

## Phase 3 — Officer
- [ ] dashboard
- [ ] assigned inspections
- [ ] inspection details
- [ ] checklist
- [ ] notes
- [ ] camera/photo evidence
- [ ] GPS + timestamp
- [ ] evidence metadata
- [ ] offline save
- [ ] AI analysis status
- [ ] AI finding/evidence/confidence/explanation
- [ ] confirm/reject/edit AI finding
- [ ] officer findings
- [ ] inspection summary
- [ ] submit inspection

## Phase 4 — Offline-first
- [ ] cache assignments/checklists
- [ ] local evidence queue
- [ ] pending action queue
- [ ] retry/resume sync
- [ ] conflict handling
- [ ] sync status
- [ ] safe local cleanup

## Phase 5 — Security/UX
- [ ] secure token storage
- [ ] permission handling
- [ ] no secrets in source
- [ ] useful validation/errors
- [ ] accessibility basics
- [ ] minimal-tap inspection flow
- [ ] destructive-action confirmation

## Phase 6 — Testing
- [ ] component/navigation tests
- [ ] form/auth tests
- [ ] upload tests
- [ ] offline/sync tests
- [ ] AI finding UI tests
- [ ] inspection submission tests
- [ ] physical Android-device testing

## Completion gate
- [ ] Citizen workflow complete
- [ ] Officer workflow complete
- [ ] Offline inspection complete
- [ ] AI review UI complete
- [ ] Release Android build works
- [ ] No blocker defects
- [ ] README + handoff complete
