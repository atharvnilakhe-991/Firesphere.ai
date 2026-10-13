# Phase 17 — AI Findings-to-Checklist Mapping Report

**Document ID:** `docs/integration/phase17_ai_findings_checklist_mapping.md`  
**Timestamp:** 2026-10-13  
**Project:** FireSphere.ai  
**Phase:** 17 — AI Findings-to-Checklist Mapping  
**Workspace:** `C:\Users\athar\OneDrive\Desktop\Firesphere.ai`  
**Backend:** `M2_work\Firesphere m2\backend` (Spring Boot 3.2.4, JDK 21, Maven 3.9.6)  
**Database Schema:** `M3_work\IP_DB\database\migrations` (Flyway PostgreSQL)

---

## 1. Executive Summary

Phase 17 implements a deterministic, traceable, and secure mapping layer connecting AI inspection findings (originating from Computer Vision and Vision-Language Model pipelines) to applicable statutory inspection checklist items.

All 14 requirements set forth in the Phase 17 specification have been implemented and verified with exhaustive test coverage:
- **Total Tests Executed:** 45 tests (30 `ChecklistDomainTests` + 15 `AiAnalysisTests`)
- **Total Failures:** 0
- **Total Errors:** 0
- **Total Skips:** 0
- **Build Status:** `BUILD SUCCESS`

### Architectural Status
| Requirement Area | Status | Evidence Summary |
|---|---|---|
| Defensible Item Mapping | `VERIFIED` | Exact deterministic tag matching (`FIRE_EXTINGUISHER`, `EXIT_SIGN`, `EXIT_DOOR`, `OBSTACLE`) mapped to NBC 2016 Part 4 checklist items. |
| Unsupported Category Handling | `VERIFIED` | Categories like `SPRINKLER_HEAD_DEFECT`, `ELECTRICAL_PANEL_HAZARD`, and generic object classes remain unmapped with clear statutory explanations; no checklist items fabricated. |
| Ambiguous Category Handling | `VERIFIED` | `EMERGENCY_EXIT_OBSTRUCTED`, `COMBUSTIBLE_MATERIAL_HAZARD`, and `GENERIC_FIRE_SAFETY_DEFECT` are classified as `AMBIGUOUS` and held for officer disambiguation; never silently linked. |
| Provenance & Conflict Preservation | `VERIFIED` | Links capture `sourceProvenance` (`CV`, `VLM`, `CROSS_VALIDATED`), `modelName`, `modelVersion`, confidence, and `conflictType` (`CV_VLM_CONFLICT`). Both conflicting findings are visible side-by-side to inspecting officers. |
| Security & Tenant Boundaries | `VERIFIED` | Cross-tenant and cross-inspection findings and requests are strictly rejected (`400 BAD_REQUEST` / `403 FORBIDDEN`). Role authorization enforced (`OFFICER`/`ADMIN` for mapping execution; `CITIZEN` read-only). |
| Concurrency & Idempotency | `VERIFIED` | Database unique constraint `uq_finding_link_resp_finding` on `(checklist_response_id, finding_id)` plus service-level deduplication guarantee duplicate requests never duplicate links. |
| Human Decision Boundary | `VERIFIED` | Mapping creation, acceptance, or dismissal NEVER alters `ChecklistResponseStatus` or officer remarks. Officer response remains an explicit, independent action. Advisory suggestions never bypass mandatory photographic evidence. |
| Completed Checklist Protection | `VERIFIED` | Completed checklists reject mapping mutations and officer responses with `400 BAD_REQUEST`. |
| Backward Compatibility | `VERIFIED` | Existing Phase 16 checklist tests and Phase 15 AI analysis tests pass 100% without modification or regression. |

---

## 2. Actual Files Inspected and Modified

### Inspected Files
- **M2 Backend Domain & Service Layer:**
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/entity/ChecklistFindingLink.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/entity/LinkProvenance.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/entity/FindingLinkStatus.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/entity/InspectionChecklist.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/entity/InspectionChecklistResponse.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/entity/ChecklistTemplate.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/entity/ChecklistTemplateItem.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/repository/ChecklistFindingLinkRepository.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/repository/InspectionChecklistRepository.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/service/InspectionChecklistService.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/controller/InspectionChecklistController.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/dto/AiSuggestionLinkDto.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/dto/InspectionChecklistItemDetailDto.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/dto/LinkAiFindingRequest.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/entity/AiAnalysisFinding.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/entity/AiAnalysisRun.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/repository/AiFindingRepository.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/repository/AiAnalysisRunRepository.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/common/audit/AuditEventType.java`
  - `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/common/security/TenantContext.java`
- **M4 AI Service Schemas:**
  - `ATHARV/ai-service/app/schemas/finding.py` (`FindingType`, `FindingSeverity`, `ProvenanceMetadata`)
- **Database Migrations:**
  - `M3_work/IP_DB/database/migrations/V1.0.8__inspection_checklists.sql`

### Modified and Created Files
1. `M3_work/IP_DB/database/migrations/V1.0.9__ai_finding_checklist_mapping.sql` *(New)*:
   - Added `source_provenance VARCHAR(50)` to `checklist_finding_links`.
   - Added `mapping_reason TEXT` to `checklist_finding_links`.
   - Created unique index `uq_finding_link_resp_finding` on `(checklist_response_id, finding_id)`.
2. `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/entity/ChecklistFindingLink.java`:
   - Added `sourceProvenance` (`VARCHAR(50)`) and `mappingReason` (`TEXT`) fields with getters/setters.
   - Added class-level unique constraint `@UniqueConstraint(name = "uq_finding_link_resp_finding", columnNames = {"checklist_response_id", "finding_id"})`.
3. `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/dto/AiFindingMappingDto.java` *(New)*:
   - DTO containing full mapping detail including finding ID, item ID, item code/title, suggested compliance, link/review status, source provenance, model metadata, evidence UUID, conflict type, and timestamps.
4. `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/dto/UnmappedFindingDto.java` *(New)*:
   - DTO detailing unmapped and ambiguous findings with finding ID, inspection ID, evidence ID, original category, confidence, status (`UNMAPPED` vs `AMBIGUOUS`), and rationale.
5. `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/dto/AiChecklistMappingResult.java` *(New)*:
   - Summary response DTO containing counts (`totalFindingsCount`, `mappedCount`, `unmappedCount`, `ambiguousCount`) and lists of mapped and unmapped findings.
6. `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/dto/LinkAiFindingRequest.java`:
   - Enhanced with `sourceProvenance` and `mappingReason` while preserving backward-compatible constructors.
7. `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/dto/AiSuggestionLinkDto.java`:
   - Enhanced with `sourceProvenance`, `mappingReason`, `modelName`, and `modelVersion` while providing an overloaded constructor for backward compatibility.
8. `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/common/audit/AuditEventType.java`:
   - Registered `CHECKLIST_MAPPINGS_APPLIED` audit event type.
9. `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/ai/repository/AiFindingRepository.java`:
   - Added JPQL query methods `findAllByInspectionIdAndOrgId` and `findAllByInspectionId`.
10. `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/repository/ChecklistFindingLinkRepository.java`:
    - Added JPQL query methods `findAllByChecklistId` and `findAllByInspectionIdAndOrgId`.
11. `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/service/InspectionChecklistService.java`:
    - Implemented `mapAiFindingsToChecklist(UUID inspectionId)` and `getAiChecklistMappings(UUID inspectionId)`.
    - Implemented deterministic mapping logic, ambiguous and unsupported categorization, and CV-VLM conflict detection.
    - Updated `linkAiFindingToChecklist` and `toItemDetailDto` to preserve `sourceProvenance` and `mappingReason`.
12. `M2_work/Firesphere m2/backend/src/main/java/com/firesphere/backend/checklist/controller/InspectionChecklistController.java`:
    - Exposed `POST /api/v1/inspections/{inspectionId}/checklist/map-ai-findings` (`ROLE_OFFICER`, `ROLE_ADMIN`).
    - Exposed `GET /api/v1/inspections/{inspectionId}/checklist/ai-mappings` (`ROLE_OFFICER`, `ROLE_ADMIN`, `ROLE_CITIZEN`).
13. `M2_work/Firesphere m2/backend/src/test/java/com/firesphere/backend/ChecklistDomainTests.java`:
    - Added 13 new integration tests verifying all Phase 17 mapping, security, review, and provenance requirements.

---

## 3. Mapping Contract and Supported Categories

Mappings are deterministic and governed by approved statutory checklist templates (`NBC_2016_COMMERCIAL` and `NBC_2016_RESIDENTIAL`):

| AI Finding Type | Target Checklist Tag | Target Item Code | Statutory Basis | Suggested Compliance |
|---|---|---|---|---|
| `FIRE_EXTINGUISHER` | `FIRE_EXTINGUISHER` | `EXT-01` | NBC 2016 Part 4 Fire Extinguisher Availability & Maintenance | `NEEDS_FURTHER_INSPECTION` |
| `FIRE_EXTINGUISHER_MISSING` | `FIRE_EXTINGUISHER` | `EXT-01` | NBC 2016 Part 4 Fire Extinguisher Availability | `NON_COMPLIANT` |
| `FIRE_EXTINGUISHER_BLOCKED` | `FIRE_EXTINGUISHER` | `EXT-01` | NBC 2016 Part 4 Extinguisher Clearance & Accessibility | `NON_COMPLIANT` |
| `FIRE_EXTINGUISHER_EXPIRED` | `FIRE_EXTINGUISHER` | `EXT-01` | NBC 2016 Part 4 Extinguisher Periodic Maintenance Tag | `NON_COMPLIANT` |
| `EXIT_SIGN` | `EXIT_SIGN` | `EXIT-01` | NBC 2016 Part 4 Illuminated Exit Signage & Visibility | `NEEDS_FURTHER_INSPECTION` |
| `EXIT_SIGNAGE_MISSING` | `EXIT_SIGN` | `EXIT-01` | NBC 2016 Part 4 Exit Signage Mandatory Installation | `NON_COMPLIANT` |
| `EXIT_DOOR` | `EXIT_DOOR` | `EXIT-02` (Comm) / `DOOR-01` (Res) | NBC 2016 Part 4 Emergency Exit Door Width & Swing | `NEEDS_FURTHER_INSPECTION` |
| `OBSTACLE` | `OBSTACLE` | `OBS-01` | NBC 2016 Part 4 Corridor & Egress Pathway Clearance | `NON_COMPLIANT` |

---

## 4. Unmapped and Ambiguous Finding Behavior

To prevent silent hallucinations or arbitrary assignments, findings without a strict statutory counterpart are isolated:

### Ambiguous Categories (`status: "AMBIGUOUS"`)
- `EMERGENCY_EXIT_OBSTRUCTED`: Egress obstruction could represent exit door swing impedence (`EXIT-02`/`DOOR-01`) or corridor pathway obstruction (`OBS-01`). Without localized multi-view geometry, automatic attribution is ambiguous.
- `COMBUSTIBLE_MATERIAL_HAZARD`: Flammable storage cannot be mapped without knowing if it lies within a designated electrical closet, refuge area, or general storage zone.
- `GENERIC_FIRE_SAFETY_DEFECT`: Lacks statutory specificity required by NBC clauses.

*Behavior:* Stored in the mapping response as `unmappedFindings` with `status: "AMBIGUOUS"` and descriptive instructions directing the human officer to inspect and disambiguate.

### Unsupported Categories (`status: "UNMAPPED"`)
- `SPRINKLER_HEAD_DEFECT`: Active approved templates (`NBC_2016_COMMERCIAL` v1) do not currently have an approved sprinkler clause.
- `ELECTRICAL_PANEL_HAZARD`: Active templates do not contain an approved electrical panel tag.
- Generic object classes (`CHAIR`, `PERSON`, `FURNITURE`, etc.): Do not map to any statutory tag.

*Behavior:* Retained in `unmappedFindings` with `status: "UNMAPPED"` and statutory explanation. No dummy checklist questions are created.

---

## 5. Provenance and Conflict Preservation

1. **Model Source Provenance:**
   - Captured in `source_provenance`: `"CV"`, `"VLM"`, or `"CROSS_VALIDATED"`.
   - Populated from the underlying `AiAnalysisRun` metadata (`model_name`, `model_provider`).
2. **Conflict Resolution Boundary:**
   - When conflicting findings exist on the same inspection (e.g. CV detects `FIRE_EXTINGUISHER` present while VLM detects `FIRE_EXTINGUISHER_MISSING`), both links are created and linked to the same checklist item `EXT-01`.
   - Both links maintain their independent `confidence`, `sourceProvenance`, `suggestedCompliance`, and `reviewStatus`.
   - Both links are flagged with `conflictType: "CV_VLM_CONFLICT"`.
   - The conflict is explicitly highlighted in the UI payload without suppressing either model's signal.
3. **No Artificial Calibration:**
   - Model confidence is stored exactly as reported by the inference engine (0.00 to 1.00) without unverified scaling.

---

## 6. Security and Idempotency Controls

1. **Tenant & Inspection Isolation:**
   - Finding runs must strictly match `inspection.getId()` and `inspection.getOrgId()`.
   - Manual or automated link requests attempting cross-inspection or cross-tenant linking are rejected with `400 BAD_REQUEST` or `403 FORBIDDEN`.
2. **Database Unique Constraint:**
   - `uq_finding_link_resp_finding` on `(checklist_response_id, finding_id)` prevents concurrent double-insertions at the relational engine level.
3. **Service Idempotency:**
   - `evaluateAndApplyMappings` performs in-memory mapping against existing links before persistence. Re-running mapping on an inspection returns the existing links with 0 new DB inserts.
4. **Lifecycle Immutability:**
   - If an inspection checklist is in `COMPLETED` status, `mapAiFindingsToChecklist`, `linkAiFindingToChecklist`, and `reviewAiFindingLink` all reject mutations with `400 BAD_REQUEST`.
5. **Audit Logging:**
   - Application of automated mappings emits `AuditEventType.CHECKLIST_MAPPINGS_APPLIED`.
   - Officer suggestion review emits `AuditEventType.CHECKLIST_ITEM_UPDATED`.

---

## 7. Officer Review Semantics and Human Decision Boundary

The system maintains a strict separation between advisory AI signals and authoritative officer determinations:

1. **Advisory suggestions only:** An AI link is initialized with `linkStatus = SUGGESTED` and `reviewStatus = PENDING_REVIEW`.
2. **Officer response unchanged:** Linking an AI finding NEVER modifies `ChecklistResponseStatus` (which remains `NOT_REVIEWED` or whatever status the officer recorded).
3. **Accepting a link (`CONFIRMED`):** Sets `linkStatus = ACCEPTED`. This acknowledges that the AI finding is relevant to the question. It does NOT set the item to `NON_COMPLIANT` or `COMPLIANT`.
4. **Dismissing a link (`DISMISSED`):** Sets `linkStatus = DISMISSED` and logs the officer's dismissal remarks. The underlying AI finding row in `ai_findings` and its evidence references are strictly preserved.
5. **Independent checklist response:** The inspecting officer must independently invoke `PUT /checklist/items/{itemId}/response` to record their statutory determination.
6. **Mandatory photographic evidence gate:** For items requiring evidence (`evidenceRequired: true`), an AI link can never substitute for active photographic evidence. Checklists missing photos are rejected from completion.

---

## 8. Test Commands and Actual Results

All testing was executed against isolated local test infrastructure using JDK 21 and Maven 3.9.6 without calling remote AI providers or external networks.

### Execution Command
```bash
cmd.exe /c "set JAVA_HOME=C:\Users\athar\OneDrive\Desktop\Firesphere.ai\M2_work\Firesphere m2\jdk-21.0.2+13&& set PATH=%JAVA_HOME%\bin;%PATH%&& ..\apache-maven-3.9.6\bin\mvn.cmd test -Dtest=ChecklistDomainTests,AiAnalysisTests"
```

### Maven Output
```
[INFO] Running com.firesphere.backend.ChecklistDomainTests
[INFO] Tests run: 30, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.328 s -- in com.firesphere.backend.ChecklistDomainTests
[INFO] Running com.firesphere.backend.AiAnalysisTests
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 13.08 s -- in com.firesphere.backend.AiAnalysisTests
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 45, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  22.867 s
[INFO] Finished at: 2026-10-13T10:54:21+05:30
```

### Coverage by Requirement
| # | Requirement Tested | Test Method | Result |
|---|---|---|---|
| 1 | Valid finding maps to checklist item | `testValidFindingMapsToApplicableItem` | `PASSED` |
| 2 | Unsupported category remains unmapped | `testUnsupportedCategoryRemainsUnmapped` | `PASSED` |
| 3 | Ambiguous category is not silently mapped | `testAmbiguousCategoryNotSilentlyMapped` | `PASSED` |
| 4 | Cross-inspection finding links rejected | `testCrossInspectionFindingLinkRejected` | `PASSED` |
| 5 | Cross-tenant finding links rejected | `testCrossTenantFindingLinkRejected` | `PASSED` |
| 6 | Duplicate mapping requests idempotent | `testDuplicateMappingIdempotent` | `PASSED` |
| 7 | Mapping does not change officer response | `testMappingDoesNotChangeOfficerResponse` | `PASSED` |
| 8 | Accepting suggestion does not set compliance | `testAcceptingSuggestionDoesNotSetComplianceResult` | `PASSED` |
| 9 | Dismissing suggestion preserves finding | `testDismissingSuggestionPreservesFinding` | `PASSED` |
| 10 | Conflicting CV and VLM retain provenance | `testConflictingCvAndVlmFindingsRetainProvenance` | `PASSED` |
| 11 | Missing evidence rejects completion | `testMissingEvidenceDoesNotCreateCompliancePass` | `PASSED` |
| 12 | Completed checklist rejects mutations | `testCompletedChecklistRejectsMappingMutations` | `PASSED` |
| 13 | Unauthorized users rejected | `testUnauthorizedUserCannotCreateOrReviewMappings` | `PASSED` |
| 14 | Existing tests regression compatibility | `ChecklistDomainTests` (17 base tests) + `AiAnalysisTests` (15 tests) | `PASSED` |

---

## 9. Known Limitations and Unresolved Decisions

1. **Future Statutory Clauses:**
   - Categories like `SPRINKLER_HEAD_DEFECT` and `ELECTRICAL_PANEL_HAZARD` are currently marked `UNMAPPED` because v1 templates focus on NBC Part 4 Means of Egress and First-Aid Fire Fighting Appliances. When state-specific or specialized industrial occupancy templates are introduced in future phases, the mapping matrix can be expanded without changing database schemas.
2. **Multi-Camera Ambiguity:**
   - `EMERGENCY_EXIT_OBSTRUCTED` is classified as ambiguous because monocular 2D images cannot distinguish whether an obstacle is within 1 meter of a door swing or merely adjacent in a wider corridor. Spatial bounding box 3D projection or multi-angle correlation will remain for future optimization.
3. **No Automatic NOC Issuance:**
   - In accordance with safety policies, AI analysis and checklist linkages remain strictly advisory. Final Fire NOC issuance requires human officer checklist submission and statutory approval.

---

## 10. Integration Readiness Assessment

| Milestone / Gate | Status | Notes |
|---|---|---|
| Phase 16 Checklist Domain Model | `VERIFIED` | Full entity, template, and response models operational. |
| Phase 16.1 Checklist Audit Safeguards | `VERIFIED` | Revision tracking, mandatory remarks, evidence checks verified. |
| Phase 17 AI Finding Mapping | `VERIFIED` | Traceable, auditable mapping, conflict tracking, and review semantics complete. |
| Ready for Next Phase | **READY** | Backward compatibility confirmed (45/45 tests passing). Ready for subsequent workflow integration. |

---

*Phase 17 complete. Stopping as instructed: final approval gate, officer mobile UI, and Phase 18 have not been initiated.*
