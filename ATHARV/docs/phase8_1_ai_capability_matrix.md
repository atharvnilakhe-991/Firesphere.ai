# FireSphere.ai — Phase 8.1: AI Capability Matrix

**Document Version:** `1.0.0-phase8.1`  
**Classification:** `TECHNICAL_SPECIFICATION`  
**Lifecycle Status:** `FROZEN`  
**Service Version:** `0.8.1-phase8.1`  
**Author:** Member 4 — AI/ML, Computer Vision & AI Productivity  
**Date:** 2026-10-08  

---

## 1. Master Capability Matrix

The following table defines the official capability status for all 10 target statutory fire safety findings recognized by FireSphere.ai:

| Finding ID | FireSphere Finding | Required Perception | Reasoning Required | OCR Required | Current Data Support | Production Readiness | Officer Verification |
| :---: | :--- | :--- | :--- | :---: | :---: | :---: | :---: |
| **0** | `FIRE_EXTINGUISHER_MISSING` | `FIRE_EXTINGUISHER`, `EMPTY_BRACKET_CABINET`, `FIRE_EXTINGUISHER_SIGN` | Expected-Location & Absence Reasoning | No | `INSUFFICIENT_DATA` | `PHASE_2_FUTURE` | Mandatory (Confirm decommission vs. violation) |
| **1** | `FIRE_EXTINGUISHER_BLOCKED` | `FIRE_EXTINGUISHER`, `OBSTACLE` (Auxiliary: Amodal Occlusion) | Spatial Clearance (36-inch clearance envelope, 2D IoU, Proximity) | No | `PARTIALLY_SUPPORTED` | `PRODUCTION_MVP` | Mandatory (Verify 3D depth and manual reach) |
| **2** | `FIRE_EXTINGUISHER_EXPIRED` | `FIRE_EXTINGUISHER` (Macro close-up crop of tag/gauge) | Date Normalization, Gauge Reading & Schedule Logic | Yes (Mandatory) | `INSUFFICIENT_DATA` | `PHASE_2_FUTURE` | Mandatory (Physical stamp & database check) |
| **3** | `EMERGENCY_EXIT_OBSTRUCTED` | Direct Obstruction Box, `EXIT_DOOR`, `OBSTACLE` | Egress Corridor Clearance & Door Swing Envelope | No | `DIRECTLY_SUPPORTED` | `PRODUCTION_MVP` | Mandatory (Confirm egress pathway & door swing) |
| **4** | `EXIT_SIGNAGE_MISSING` | `EXIT_DOOR`, `EXIT_SIGN` | Architectural Lintel Proximity & Egress Designation | No | `PARTIALLY_SUPPORTED` | `PHASE_2_FUTURE` | Mandatory (Verify if door is statutory fire exit) |
| **5** | `SPRINKLER_HEAD_DEFECT` | `SPRINKLER_HEAD` (Micro-scale fixture) | Crop Condition Analysis (Corrosion, Paint, Deflector) | No | `NOT_CURRENTLY_SUPPORTED` | `NOT_SUPPORTED` | Mandatory (Manual physical inspection only) |
| **6** | `ELECTRICAL_PANEL_HAZARD` | `ELECTRICAL_PANEL`, `OBSTACLE` | Door State (Open/Exposed) + 36-inch Clearance Rule | No | `INSUFFICIENT_DATA` | `PHASE_2_FUTURE` | Mandatory (Inspect wiring, breakers, clearance) |
| **7** | `COMBUSTIBLE_MATERIAL_HAZARD` | `COMBUSTIBLE_MATERIAL`, `OBSTACLE` | Contextual Occupancy (Stairwells, Boiler, Ignition Proximity)| No | `INSUFFICIENT_DATA` | `PHASE_2_FUTURE` | Mandatory (Assess fuel load, storage legality) |
| **8** | `MANUAL_CALL_POINT_BLOCKED` | `MANUAL_CALL_POINT`, `OBSTACLE` | Spatial Clearance & Direct Frontal Access Cone | No | `INSUFFICIENT_DATA` | `PHASE_2_FUTURE` | Mandatory (Verify pull station reach and visibility)|
| **9** | `HOSE_REEL_OBSTRUCTED` | `FIRE_HOSE_REEL`, `OBSTACLE` | Spatial Clearance & Reel Cabinet Swing Perimeter | No | `INSUFFICIENT_DATA` | `PHASE_2_FUTURE` | Mandatory (Verify cabinet door swing & hose run) |

---

## 2. In-Depth Technical Specification for All 10 Target Findings

---

### Finding 0: `FIRE_EXTINGUISHER_MISSING`

* **Classification Taxonomy:** `E. expected-location/absence reasoning` (Hybrid with empty bracket perception).
* **Primary Implementation Strategy:**
  1. Detect visual anchor: `EMPTY_BRACKET_CABINET` or `FIRE_EXTINGUISHER_SIGN`.
  2. Compute spatial neighborhood: search for co-located `FIRE_EXTINGUISHER` within the vertical bounding column.
  3. If anchor is present but extinguisher cylinder is absent $\to$ trigger candidate `FIRE_EXTINGUISHER_MISSING`.
* **Perception Dependencies:** `FIRE_EXTINGUISHER`, `EMPTY_BRACKET_CABINET`, `FIRE_EXTINGUISHER_SIGN`.
* **Reasoning Dependencies:** Absence logic based on designated architectural mounting anchors. The model does NOT attempt to draw bounding boxes around empty air on unmarked walls.
* **OCR Dependencies:** None for initial absence trigger; auxiliary OCR on signage text ("FIRE EXTINGUISHER") provides reinforcement.
* **Current Data Support:** `INSUFFICIENT_DATA`.
  - Dataset 7 provides 50 verified `missing fire extinguisher` polygon annotations enclosing empty wall cabinets.
  - However, all 50 samples originate from a single video clip (`Sna54U5CjZHZECx9dyDf_2`), representing a single physical scene.
  - Canonical data contains 2,610 extinguishers and 2,272 signs, but zero dedicated empty bracket annotations.
* **Production Readiness Status:** `PHASE_2_FUTURE`.
* **Officer Verification Protocol:** Officer must verify whether the mounting point is an active statutory extinguisher station or a decommissioned fixture undergoing architectural remodeling.
* **Failure & Uncertainty Behavior:** If an image depicts a blank wall with no mounting bracket or signage, the model outputs `INCONCLUSIVE_LOCATION_CONTEXT`. It never predicts a missing extinguisher from an unanchored wall.

---

### Finding 1: `FIRE_EXTINGUISHER_BLOCKED`

* **Classification Taxonomy:** `B. object + spatial reasoning` (Object perception + geometric clearance engine).
* **Primary Implementation Strategy:**
  1. Detect life-safety equipment: `FIRE_EXTINGUISHER`.
  2. Detect physical clutter: `OBSTACLE` (boxes, crates, furniture, carts).
  3. Execute Spatial Clearance Engine (`app.intelligence.spatial`):
     - Calculate 2D IoU overlap.
     - Evaluate vertical overlap and ground-plane contact line proximity.
     - If obstacle infringes the 36-inch (0.9 m) access cone $\to$ trigger `FIRE_EXTINGUISHER_BLOCKED`.
  4. Auxiliary Occlusion Cue: Ingest modal vs. amodal bounding box ratio from auxiliary segmentation models to flag visual occlusion.
* **Perception Dependencies:** `FIRE_EXTINGUISHER`, `OBSTACLE`.
* **Reasoning Dependencies:** Deterministic geometric calculation (IoU, Euclidean distance between bounding box centroids, aspect ratio of ground overlap).
* **OCR Dependencies:** None.
* **Current Data Support:** `PARTIALLY_SUPPORTED`.
  - Canonical dataset contains 2,610 verified `FIRE_EXTINGUISHER` instances.
  - Dataset 8 (FireSafetyNet) provides 2,363 unique images with 4,021 modal and 3,230 amodal extinguisher segmentations.
  - Obstacle classes available from Dataset 6.
* **Production Readiness Status:** `PRODUCTION_MVP`.
* **Officer Verification Protocol:** Officer must confirm physical clearance in three dimensions, accounting for perspective foreshortening that may make distant objects appear adjacent.
* **Failure & Uncertainty Behavior:** When obstacle overlap is borderline ($0.05 < \text{IoU} < 0.15$), the system emits an `ADVISORY_LOW_CONFIDENCE` flag requesting an orthogonal angle or manual measurement.

---

### Finding 2: `FIRE_EXTINGUISHER_EXPIRED`

* **Classification Taxonomy:** `D. OCR/date intelligence` (High-resolution crop + OCR text parsing + maintenance logic).
* **Primary Implementation Strategy:**
  1. Detect `FIRE_EXTINGUISHER` cylinder.
  2. Localize inspection tag sub-region or ingest dedicated close-up macro photograph captured via mobile inspection flow.
  3. Execute OCR engine (PaddleOCR / Tesseract) on tag bounding region.
  4. Parse inspection date strings, year/month punch grids, or QR certification codes.
  5. Compare parsed maintenance date against inspection timestamp ($> 365\text{ days} \to$ Expired under NFPA 10).
* **Perception Dependencies:** High-resolution `FIRE_EXTINGUISHER` crop or dedicated macro tag image.
* **Reasoning Dependencies:** Temporal difference calculation, regex date extraction, calendar normalization.
* **OCR Dependencies:** Mandatory.
* **Current Data Support:** `INSUFFICIENT_DATA`.
  - Canonical dataset contains 0 tag-level date annotations.
  - Dataset 7 contains only 1 annotated inspection tag polygon.
  - Standard wide-angle inspection photos lack optical resolution to read punched tag dates.
* **Production Readiness Status:** `PHASE_2_FUTURE`.
* **Officer Verification Protocol:** Officer must physically examine the punched inspection tag, verify the service company seal, and check the hydrostatic test date stamped on the cylinder shoulder.
* **Failure & Uncertainty Behavior:** If the tag cannot be legibly resolved by OCR, the system marks the field `UNVERIFIED_INSPECTION_TAG (UNCERTAIN)` and instructs the officer to manually inspect the physical tag. The AI **NEVER fabricates dates or assumes expiration**.

---

### Finding 3: `EMERGENCY_EXIT_OBSTRUCTED`

* **Classification Taxonomy:** `G. hybrid` (Direct violation detection + Egress fixture & obstacle spatial reasoning).
* **Primary Implementation Strategy:**
  1. Detect direct violation patterns trained on verified obstruction data (`blocked emergency exit`, `boxes blocking exit`, `exit block violation`).
  2. Concurrently detect `EXIT_DOOR` and `OBSTACLE`.
  3. Execute egress corridor clearance calculation: verify whether obstacle bounding box intersects the door frame or egress path.
* **Perception Dependencies:** Direct violation detector head, `EXIT_DOOR`, `OBSTACLE`.
* **Reasoning Dependencies:** Intersection analysis with egress swing radius and minimum escape route width (typically 28–36 inches under NFPA 101).
* **OCR Dependencies:** None.
* **Current Data Support:** `DIRECTLY_SUPPORTED`.
  - Dataset 6 (`blocked emergency exit.v1i.yolov8.zip`) contains 120 verified images with 126 genuine violation annotations across 52 physical scenes.
  - Canonical dataset provides generic doors, corridors, and exit signage.
* **Production Readiness Status:** `PRODUCTION_MVP`.
* **Officer Verification Protocol:** Officer physically checks door swing arc, latch operation, and verifies whether temporary items can be immediately cleared during the walkthrough.
* **Failure & Uncertainty Behavior:** If door is partially visible or obstacle is distant, system generates an advisory bounding box with an explicit camera perspective disclaimer.

---

### Finding 4: `EXIT_SIGNAGE_MISSING`

* **Classification Taxonomy:** `E. expected-location/absence reasoning`.
* **Primary Implementation Strategy:**
  1. Detect `EXIT_DOOR` (egress doorway fixture).
  2. Define lintel search zone: bounding envelope extending $0.5 \times \text{height}$ directly above the upper door frame.
  3. Check for co-located `EXIT_SIGN` within the lintel zone.
  4. If door is designated an emergency egress door and no `EXIT_SIGN` is detected $\to$ flag candidate `EXIT_SIGNAGE_MISSING`.
* **Perception Dependencies:** `EXIT_DOOR`, `EXIT_SIGN`.
* **Reasoning Dependencies:** Geometric lintel proximity rule + architectural designation schedule.
* **OCR Dependencies:** Optional (OCR reading "EXIT" or directional arrows on sign for confirmation).
* **Current Data Support:** `PARTIALLY_SUPPORTED`.
  - Canonical dataset contains 2,272 verified `EXIT_SIGN` and door instances.
  - Lintel proximity geometry is mathematically definable.
  - However, datasets lack annotations denoting which doors are statutory egress doors vs. interior office doors.
* **Production Readiness Status:** `PHASE_2_FUTURE`.
* **Officer Verification Protocol:** Officer cross-references the building's approved fire-safety plan to determine whether the door is a designated emergency exit or a standard non-egress door.
* **Failure & Uncertainty Behavior:** The system suppresses missing sign alerts on non-egress doors unless the user explicitly tags the door as an "Emergency Exit" in the mobile app.

---

### Finding 5: `SPRINKLER_HEAD_DEFECT`

* **Classification Taxonomy:** `C. object + condition analysis`.
* **Primary Implementation Strategy:**
  - Micro-scale fixture detection + condition classifier (corrosion, paint loading, bent deflector).
* **Perception Dependencies:** `SPRINKLER_HEAD`.
* **Reasoning Dependencies:** Condition classifier on tight crops.
* **OCR Dependencies:** None.
* **Current Data Support:** `NOT_CURRENTLY_SUPPORTED`.
  - Zenodo SHD-Bench dataset is restricted by a `CC BY-NC 4.0` (Non-Commercial) license, making it legally incompatible with commercial SaaS deployment.
  - Sprinkler heads in room photos typically measure $< 30 \times 30$ pixels, creating an irrecoverable optical scale disparity.
* **Production Readiness Status:** `NOT_SUPPORTED`.
* **Officer Verification Protocol:** Mandatory manual inspection. Officer checks deflector clearance, escutcheon plate fit, and bulb fluid color/condition.
* **Failure & Uncertainty Behavior:** Completely excluded from automated inference pipeline; handled via manual checklist.

---

### Finding 6: `ELECTRICAL_PANEL_HAZARD`

* **Classification Taxonomy:** `G. hybrid` (Panel detection + open-door condition head OR panel + obstacle spatial proximity).
* **Primary Implementation Strategy:**
  1. Detect `ELECTRICAL_PANEL` enclosure.
  2. Evaluate door latch state (open door showing exposed breakers/wires vs. closed secure panel).
  3. Evaluate spatial clearance: check for combustible clutter within the mandatory 36-inch (NFPA 70) working space.
* **Perception Dependencies:** `ELECTRICAL_PANEL`, `OBSTACLE`.
* **Reasoning Dependencies:** Condition classification (open/closed) + 36-inch clearance envelope.
* **OCR Dependencies:** None.
* **Current Data Support:** `INSUFFICIENT_DATA`.
  - Canonical dataset contains fire alarm panels, but negligible power distribution panel hazard data.
* **Production Readiness Status:** `PHASE_2_FUTURE`.
* **Officer Verification Protocol:** Officer confirms whether cabinet houses low-voltage signaling or high-voltage switchgear and verifies deadfront covers.
* **Failure & Uncertainty Behavior:** Suppressed from MVP automated pipeline; available in manual checklist.

---

### Finding 7: `COMBUSTIBLE_MATERIAL_HAZARD`

* **Classification Taxonomy:** `B. object + spatial reasoning`.
* **Primary Implementation Strategy:**
  1. Detect `COMBUSTIBLE_MATERIAL` (pallets, cardboard clusters, scrap lumber, flammable drums).
  2. Correlate with room context (stairwells, mechanical rooms, proximity to heaters/electrical gear).
* **Perception Dependencies:** `COMBUSTIBLE_MATERIAL`, `OBSTACLE`.
* **Reasoning Dependencies:** Room occupancy and proximity to ignition sources.
* **OCR Dependencies:** None.
* **Current Data Support:** `INSUFFICIENT_DATA`.
  - While clutter objects are detectable, distinguishing permitted warehouse storage from illegal combustible accumulation requires building occupancy context.
* **Production Readiness Status:** `PHASE_2_FUTURE`.
* **Officer Verification Protocol:** Officer evaluates fuel load, sprinkler coverage density, and facility occupancy classification.
* **Failure & Uncertainty Behavior:** Suppressed from MVP except when classified as an obstruction to egress or extinguishers.

---

### Finding 8: `MANUAL_CALL_POINT_BLOCKED`

* **Classification Taxonomy:** `B. object + spatial reasoning`.
* **Primary Implementation Strategy:**
  1. Detect `MANUAL_CALL_POINT` (pull station / break-glass box).
  2. Detect `OBSTACLE` within immediate operational perimeter.
  3. Calculate clearance infringement.
* **Perception Dependencies:** `MANUAL_CALL_POINT`, `OBSTACLE`.
* **Reasoning Dependencies:** Spatial clearance rule.
* **OCR Dependencies:** None.
* **Current Data Support:** `INSUFFICIENT_DATA`.
  - Canonical dataset has 784 call point annotations, but virtually zero co-occurring obstacle pairs.
* **Production Readiness Status:** `PHASE_2_FUTURE`.
* **Officer Verification Protocol:** Officer verifies pull station accessibility and operational visibility.
* **Failure & Uncertainty Behavior:** Suppressed from MVP automated pipeline.

---

### Finding 9: `HOSE_REEL_OBSTRUCTED`

* **Classification Taxonomy:** `B. object + spatial reasoning`.
* **Primary Implementation Strategy:**
  1. Detect `FIRE_HOSE_REEL` drum or cabinet.
  2. Detect `OBSTACLE` in front of cabinet door or swing arm.
  3. Calculate clearance perimeter violation.
* **Perception Dependencies:** `FIRE_HOSE_REEL`, `OBSTACLE`.
* **Reasoning Dependencies:** Spatial clearance rule.
* **OCR Dependencies:** None.
* **Current Data Support:** `INSUFFICIENT_DATA`.
  - Canonical dataset contains 918 hose reel annotations; verified obstruction pairs are lacking.
* **Production Readiness Status:** `PHASE_2_FUTURE`.
* **Officer Verification Protocol:** Officer verifies cabinet door opens $180^\circ$ and hose nozzle is deployable.
* **Failure & Uncertainty Behavior:** Suppressed from MVP automated pipeline.
