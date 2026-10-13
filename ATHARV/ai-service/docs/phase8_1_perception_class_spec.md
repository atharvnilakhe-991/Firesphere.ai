# FireSphere.ai — Phase 8.1: Production Perception Class Specification

**Document Version:** `1.0.0-phase8.1`  
**Classification:** `TECHNICAL_SPECIFICATION`  
**Lifecycle Status:** `FROZEN`  
**Service Version:** `0.8.1-phase8.1`  
**Author:** Member 4 — AI/ML, Computer Vision & AI Productivity  
**Date:** 2026-10-08  

---

## 1. Architectural Distinction: Perception Class vs. Finding Class

A strict architectural separation is enforced between **Perception Classes** and **Finding Classes**:

```text
┌────────────────────────────────────────────────────────┐
│             PERCEPTION CLASS (Stage 1)                 │
│  - Describes an atomic physical entity in the image.   │
│  - Neutral, objective, and non-judgmental.             │
│  - Answers: "WHAT physical object exists at (x,y)?"    │
│  - Examples: FIRE_EXTINGUISHER, EXIT_DOOR, OBSTACLE    │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
             [ Spatial & Rule Reasoning Engine ]
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│               FINDING CLASS (Stage 2)                  │
│  - Describes a statutory fire-safety non-compliance.   │
│  - Synthesized via spatial geometry, context, or OCR.  │
│  - Answers: "IS there a legal or life-safety defect?"  │
│  - Examples: FIRE_EXTINGUISHER_BLOCKED,                │
│              EMERGENCY_EXIT_OBSTRUCTED                 │
└────────────────────────────────────────────────────────┘
```

> **GOVERNANCE MANDATE:** Under no circumstances shall an object detector class be conflated with a statutory violation finding. For example, detecting a `FIRE_EXTINGUISHER` does not mean the equipment is compliant, accessible, or expired; detecting an `OBSTACLE` does not mean an illegal violation exists unless it infringes a defined life-safety clearance perimeter.

---

## 2. Minimum Sufficient Perception Vocabulary (Production MVP)

To maximize model generalizability, reduce annotation noise, and avoid model dilution, the Production MVP defines the **Minimum Sufficient Perception Vocabulary**: 4 atomic perception classes.

| Class Index | Perception Class Name | Category | Primary Visual Target | Justification & Need |
| :---: | :--- | :--- | :--- | :--- |
| `0` | `FIRE_EXTINGUISHER` | Life-Safety Equipment | Portable extinguisher cylinders (all classes: A, B, C, D, K) | Required for `FIRE_EXTINGUISHER_BLOCKED` and macro crop for `EXPIRED` |
| `1` | `EXIT_DOOR` | Egress Infrastructure | Emergency egress doors, frames, push-bars, and exit portals | Required for `EMERGENCY_EXIT_OBSTRUCTED` and lintel anchor for missing signage |
| `2` | `EXIT_SIGN` | Egress Infrastructure | Illuminated and photoluminescent emergency exit signs | Required for egress path validation and `EXIT_SIGNAGE_MISSING` logic |
| `3` | `OBSTACLE` | Hazard / Encroachment | Physical clutter: cardboard boxes, wooden pallets, carts, furniture | Required by Spatial Clearance Engine to evaluate egress and equipment blockage |

---

## 3. Detailed MVP Perception Class Specifications

---

### Class 0: `FIRE_EXTINGUISHER`

* **Class Name:** `FIRE_EXTINGUISHER`
* **Purpose:** Localize portable fire extinguisher cylinders mounted on walls, stands, or inside cabinets.
* **Why It Is Needed:** Provides the geometric life-safety anchor for obstruction calculation and the crop boundary for condition/tag inspection.
* **Dependent Findings:**
  - `FIRE_EXTINGUISHER_BLOCKED` (Finding 1 - MVP)
  - `FIRE_EXTINGUISHER_EXPIRED` (Finding 2 - Future Macro Workflow)
  - `FIRE_EXTINGUISHER_MISSING` (Finding 0 - Co-location verification)
* **Expected Visual Semantics:**
  - Standard pressurized cylindrical steel/aluminum canisters (red, silver/chrome, green, blue).
  - Includes hose, nozzle, handle/lever assembly, pressure gauge, and mounting bracket when attached.
  - Includes units mounted inside recessed metal cabinets with clear glass/acrylic windows.
* **What It Must NOT Mean:**
  - It does **NOT** mean the extinguisher is compliant.
  - It does **NOT** mean the extinguisher is pressurized.
  - It does **NOT** mean the extinguisher is unblocked or accessible.
  - It does **NOT** mean the extinguisher is within its annual maintenance date.
* **Dataset Requirements:**
  - Tight bounding boxes enclosing the complete visible cylinder and discharge assembly.
  - Supported in canonical dataset with 2,610 verified instances and Dataset 8 with 4,021 modal segmentation instances.
* **Training Status:** `TRAINING_CANDIDATE` (Canonical Layer 1).
* **Inference Role:** Base equipment fixture for spatial clearance rules.

---

### Class 1: `EXIT_DOOR`

* **Class Name:** `EXIT_DOOR`
* **Purpose:** Localize doors designated or utilized as building egress pathways.
* **Why It Is Needed:** Establishes the escape portal boundary and door swing perimeter for obstruction and signage checks.
* **Dependent Findings:**
  - `EMERGENCY_EXIT_OBSTRUCTED` (Finding 3 - MVP)
  - `EXIT_SIGNAGE_MISSING` (Finding 4 - Future)
* **Expected Visual Semantics:**
  - Full architectural door leaf and frame, single doors, double doors.
  - Doors equipped with panic hardware (crash bars), push pads, or overhead emergency exit signs.
  - Egress corridor portal thresholds.
* **What It Must NOT Mean:**
  - It does **NOT** mean the exit is unobstructed or legal.
  - It does **NOT** mean the door swings freely.
  - It does **NOT** mean the door is an emergency exit (unless confirmed by panic hardware or signage).
  - It must **NOT** be relabeled as `EMERGENCY_EXIT_OBSTRUCTED` on its own.
* **Dataset Requirements:**
  - Bounding box enclosing the full door leaf and outer frame.
  - Supported in Dataset 6 (quarantined generic doors) and canonical egress datasets.
* **Training Status:** `TRAINING_CANDIDATE` (Canonical Layer 1).
* **Inference Role:** Egress reference zone for spatial obstruction engine.

---

### Class 2: `EXIT_SIGN`

* **Class Name:** `EXIT_SIGN`
* **Purpose:** Localize overhead, wall-mounted, and directional emergency exit signs.
* **Why It Is Needed:** Confirms visual guidance along egress paths; acts as the positive reference for missing signage detection.
* **Dependent Findings:**
  - `EXIT_SIGNAGE_MISSING` (Finding 4 - Future)
  - Egress Corridor Route Validation
* **Expected Visual Semantics:**
  - Internally illuminated signs (red, green LED letters: "EXIT", "SALIDA", running man ISO 7010).
  - Non-illuminated photoluminescent glow-in-the-dark signs.
  - Directional arrows and exit path indicators.
* **What It Must NOT Mean:**
  - It does **NOT** mean the sign battery backup is functional.
  - It does **NOT** mean the sign illumination is active during power failure.
  - It does **NOT** mean the door below is unobstructed.
* **Dataset Requirements:**
  - Tight bounding box enclosing the sign face and frame.
  - Supported in canonical dataset with 2,272 verified instances.
* **Training Status:** `TRAINING_CANDIDATE` (Canonical Layer 1).
* **Inference Role:** Positive anchor for lintel spatial verification.

---

### Class 3: `OBSTACLE`

* **Class Name:** `OBSTACLE`
* **Purpose:** Localize moveable or temporary physical clutter that encroaches upon life-safety zones.
* **Why It Is Needed:** Provides the secondary geometric entity required to calculate spatial obstruction violations.
* **Dependent Findings:**
  - `EMERGENCY_EXIT_OBSTRUCTED` (Finding 3 - MVP)
  - `FIRE_EXTINGUISHER_BLOCKED` (Finding 1 - MVP)
  - `MANUAL_CALL_POINT_BLOCKED` (Finding 8 - Future)
  - `HOSE_REEL_OBSTRUCTED` (Finding 9 - Future)
* **Expected Visual Semantics:**
  - Stacked or loose cardboard boxes, packing crates.
  - Wooden pallets, shipping skids.
  - Wheeled carts, rolling hampers, hand trucks.
  - Misplaced furniture, chairs, tables, ladders, maintenance equipment.
  - Trash cans, recycling bins placed in corridors.
* **What It Must NOT Mean:**
  - It does **NOT** mean an illegal violation exists if located in a designated storage warehouse rack.
  - It does **NOT** mean combustible material hazard unless co-located with ignition sources or restricted egress.
  - It is **NOT** a finding on its own; it is an obstacle entity.
* **Dataset Requirements:**
  - Bounding box enclosing the perimeter of the encroaching object cluster.
  - Supported in Dataset 6 (`boxes blocking exit`, `objects blocked emergency exit`).
* **Training Status:** `TRAINING_CANDIDATE` (Canonical Layer 1).
* **Inference Role:** Secondary entity evaluated by Spatial Clearance Engine.

---

## 4. Auxiliary & Phase 2 Perception Classes

These classes represent additional fire safety equipment present in commercial buildings. While annotated in canonical datasets, they are assigned to **Phase 2 / Auxiliary Status** because their associated statutory violation findings require further paired data:

| Class Name | Primary Visual Target | Dependent Finding | Canonical Support | Production Status |
| :--- | :--- | :--- | :---: | :---: |
| `FIRE_HOSE_REEL` | Recessed red hose drum, nozzle, cabinet | `HOSE_REEL_OBSTRUCTED` | 918 instances | `AUXILIARY` (Presence localization) |
| `MANUAL_CALL_POINT` | Red break-glass box, pull station lever | `MANUAL_CALL_POINT_BLOCKED` | 784 instances | `AUXILIARY` (Presence localization) |
| `ELECTRICAL_PANEL` | Metal breaker enclosure, distribution cabinet | `ELECTRICAL_PANEL_HAZARD` | Limited | `PHASE_2_FUTURE` |
| `EMPTY_BRACKET_CABINET` | Wall mounting hook, empty red cabinet | `FIRE_EXTINGUISHER_MISSING` | 50 instances (1 scene) | `PHASE_2_FUTURE` |

---

## 5. Excluded Perception Classes

### `SPRINKLER_HEAD`
* **Status:** `EXCLUDED FROM PERCEPTION VOCABULARY`.
* **Technical Justification:**
  1. Optical scale disparity ($< 25 \text{ px}$ in standard $640 \times 640$ inspection photos).
  2. Sub-pixel defect requirements (corrosion, paint, glass bulb rupture cannot be resolved).
  3. Strict commercial licensing exclusion of the Zenodo SHD-Bench dataset (`CC BY-NC 4.0`).
* **Operational Alternative:** Manual officer physical inspection checklist.
