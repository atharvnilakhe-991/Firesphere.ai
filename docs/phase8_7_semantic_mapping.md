# FireSphere.ai — Phase 8.7: Strict Semantic Mapping & Vocabulary Governance

**Document ID:** `FS-AI-DOC-8.7-SEMANTICS`  
**Phase:** Phase 8.7 — Targeted Minority-Class Dataset Expansion & Confidence Calibration  
**Classification:** `TECHNICAL_SPECIFICATION`  
**Governance Policy:** `TRUTH_IN_AI_AND_NON_COERCIVE_MAPPING`  
**Date:** 2026-10-08  
**Author:** Antigravity AI/ML Engineering Agent (Pair Programming)  

---

## 1. Governance Principles & Decision Framework

In computer vision applications for life-safety compliance, dataset mapping errors are safety-critical failures. Coercively mapping ambiguous or incompatible source annotations into detector target classes causes catastrophic real-world failure modes:
1. **Semantic Dilution:** Training a detector on generic domestic objects (e.g. kitchen trash cans) teaches the network to identify benign items rather than hazardous obstructions.
2. **Coercive Relabeling Failure:** Labeling compliant safety infrastructure (e.g., sand buckets, fire alarm panels) as "obstacles" trains neural networks to treat legal fire appliances as life-safety hazards.
3. **Domain Shift & Studio Background Pollution:** Training on e-commerce catalog photos with pure white backgrounds destroys the model's ability to localize objects in complex indoor architectural environments.

To prevent these defects, Phase 8.7 enforces the **Strict Semantic Mapping Triad**:

```text
┌────────────────────────────────────────────────────────────────────────┐
│                   SEMANTIC MAPPING DECISION MATRIX                    │
├───────────────────┬────────────────────────────────────────────────────┤
│ ACCEPT            │ Unambiguous visual correspondence with frozen     │
│                   │ Phase 8.1 perception specification. Real indoor    │
│                   │ architectural context verified.                    │
├───────────────────┼────────────────────────────────────────────────────┤
│ REJECT            │ Incompatible semantics, e-commerce catalog photo,  │
│                   │ life-safety equipment mislabeled as hazard, or     │
│                   │ compound violation boundary box.                   │
├───────────────────┼────────────────────────────────────────────────────┤
│ REVIEW_REQUIRED   │ Plausible correspondence but ambiguous scene       │
│                   │ context or resolution. Quarantined from training.  │
└───────────────────┴────────────────────────────────────────────────────┘
```

---

## 2. Frozen Perception Vocabulary (Stage 1)

Phase 8.1 froze the 4 atomic perception classes for Stage-1 object localization:

* `0: FIRE_EXTINGUISHER`: Portable pressurized fire extinguisher cylinders.
* `1: EXIT_DOOR`: Architectural egress doors, frames, panic hardware, and egress portals.
* `2: EXIT_SIGN`: Internally illuminated and photoluminescent emergency exit signs.
* `3: OBSTACLE`: Moveable or temporary physical clutter (boxes, pallets, carts, furniture) occupying life-safety zones.

---

## 3. Comprehensive Semantic Mapping Decisions

### 3.1 Candidate Dataset: `fire_safety_multi` (`Fire Safety.v1i.yolov8.zip`)

| Source Class Name | Source Class ID | Target Class | Decision | Empirical Finding & Semantic Rationale |
| :--- | :---: | :---: | :---: | :--- |
| `door` | 4 | `EXIT_DOOR` (1) | **ACCEPT** | Audited across 529 unique physical scenes. Real indoor commercial, corridor, and institutional building doors. White background catalog shots filtered out. Expands physical scene diversity from 14 to 545 scenes. |
| `trash_can` | 11 | None | **REJECT** | Visual audit revealed scraped e-commerce stock catalog photos of kitchen wastebaskets on pure white backgrounds (`vgradni-kos-za-smeti`, `poubelle-de-bureau`). Lacks corridor context; causes severe domain shift. |
| `sand_bucket` | 10 | None | **REJECT** | Dedicated fire safety equipment. Fire code mandates sand buckets at chemical/electrical hazards; treating life-safety equipment as an obstruction is legally and semantically invalid. |
| `First Aid` | 2 | None | **REJECT** | Auxiliary medical equipment; outside frozen 4-class perception vocabulary. |
| `Fire alarm` | 1 | None | **REJECT** | Life safety alarm fixture; outside frozen 4-class perception vocabulary. |
| `fire hydrant` / `fire-hydrant` | 5, 6 | None | **REJECT** | Exterior municipal fire infrastructure; outside building interior perception. |
| `window` | 12 | None | **REJECT** | Generic architectural glazing; not an approved egress portal. |
| `Exit sign` | 0 | `EXIT_SIGN` (2) | **ACCEPT** | Genuine illuminated and luminescent emergency exit signs. |
| `fire_extinguisher` | 7 | `FIRE_EXTINGUISHER` (0) | **ACCEPT** | Verified portable fire extinguishers. |

---

### 3.2 Candidate Dataset: `roboflow-blocked-emergency-exit-v1` (Dataset 6)

| Source Class Name | Source Class ID | Target Class | Decision | Empirical Finding & Semantic Rationale |
| :--- | :---: | :---: | :---: | :--- |
| `emergency exit door` | 2 | `EXIT_DOOR` (1) | **ACCEPT** | 42 boxes across 14 scenes. Direct architectural exit door with panic push-bars and emergency signage. |
| `boxes blocking exit` | 1 | `OBSTACLE` (3) | **ACCEPT** | 39 boxes across 13 scenes. Combustible cardboard boxes obstructing exit door swing clearance. |
| `objects blocked emergency exit` | 4 | `OBSTACLE` (3) | **ACCEPT** | 54 boxes across 17 scenes. Physical obstacles (furniture, pallets, carts, clutter) encroaching on egress path. |
| `blocked emergency exit` | 0 | None | **REJECT** | 18 boxes. Bounding box encapsulates the entire compound obstruction area rather than discrete physical items. Handled in Stage-2 rule reasoning. |
| `exit block violation` | 3 | None | **REJECT** | 15 boxes. High-level violation indicator, not an atomic obstacle object. Handled in Stage-2 rule reasoning. |

---

### 3.3 Candidate Dataset: `patyas-workspace-my-first-project-v6i` (Dataset 7)

| Source Class Name | Source Class ID | Target Class | Decision | Empirical Finding & Semantic Rationale |
| :--- | :---: | :---: | :---: | :--- |
| `exit door` | 7 | `EXIT_DOOR` (1) | **ACCEPT** | 2 polygon annotations across 2 physical scenes. Converted polygon bounding box to standard YOLO format. |
| `exit sign` | 8 | `EXIT_SIGN` (2) | **ACCEPT** | 20 polygon annotations across 20 physical scenes. Emergency exit signage. |
| `missing fire extinguisher` | 22 | None | **REJECT** | 50 polygon annotations originating from a single video clip (`Sna54U5CjZHZECx9dyDf_2`). Represents empty wall brackets (absence), not equipment presence. |
| `fire extinguisher tagged as noncompliant` | 13 | None | **REJECT** | 158 annotations. Extinguishers with inspection tags; contains visual clutter rather than verified atomic presence. |

---

## 4. Visual Audit Evidence Summary

To ensure complete transparency, representative visual audit samples were rendered and archived in `ai-service/artifacts/phase8_7/audit_samples/`:

1. **`doors/`**: Contains 10 audited crops from `fire_safety_multi` demonstrating genuine physical indoor commercial doors with realistic ambient illumination and natural floor/wall geometry.
2. **`obstacles/`**: Contains 10 audited crops from Dataset 6 showing verified cardboard cartons, furniture, and shipping pallets obstructing emergency doorways.
3. **`trash_cans/`**: Contains 10 audited crops from `fire_safety_multi` demonstrating why `trash_can` was **REJECTED** (e-commerce product catalog photographs with isolated white backgrounds and no physical room context).

---

## 5. Summary of Semantic Mapping Acceptance

| Perception Class | Total Candidate Boxes Audited | Accepted Boxes | Rejected Boxes | Acceptance Rate | Primary Rejection Reason |
| :--- | :---: | :---: | :---: | :---: | :--- |
| `FIRE_EXTINGUISHER` | 4,222 | 4,112 | 110 | 97.4% | Tagged visual noise / corrupted boxes |
| `EXIT_DOOR` | 3,499 | 739 | 2,760 | 21.1% | Augmented duplicates / white bg / unverified context |
| `EXIT_SIGN` | 2,239 | 2,239 | 0 | 100.0% | Clean illuminated signage |
| `OBSTACLE` | 5,261 | 75 | 5,186 | 1.4% | E-commerce white-background bins (4,736) & safety buckets (399) |
