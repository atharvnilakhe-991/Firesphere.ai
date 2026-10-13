# FireSphere.ai — Phase 8.4 Structured Failure & Error Analysis Report

**Document Version:** `1.0.0-phase8.4`  
**Model Identifier:** `firesphere-perception-baseline-v0.8.0`  
**Governing Gate Decision:** `BASELINE_TRAINED_DATA_EXPANSION_REQUIRED`  
**Execution Timestamp:** `2026-10-08T13:05:11Z`  

---

## 1. Overview & Objectives

In a safety-critical application like FireSphere.ai, error analysis must be rigorous, transparent, and grounded in empirical reality. This document dissects representative false positives, false negatives, scale issues, and class failure modes observed during Phase 8.4 baseline evaluation.

---

## 2. Dedicated Data-Limited Class Analysis

### 2.1 `EXIT_DOOR` (Class 1)
- **Prepared Dataset Reality:**
  - Total prepared annotations: 42 boxes (33 train / 3 val / 6 test)
  - Total unique physical scenes: 14 scenes (all sourced exclusively from Dataset 6)
- **Empirical Evaluation Metrics:**
  - Test Positives (Support): 6
  - True Positives: 0
  - False Negatives: 6 (100% missed)
  - False Positives: 0
  - AP@50: **0.0000**
- **Root Cause & Visual Failure Patterns:**
  1. *Severe Data Starvation:* At 33 training samples out of 3,377 images, doors represent <1% of the training signal. The network minimizes loss simply by ignoring the class.
  2. *Extreme Optical Scale Disparity:* Doors typically span 40% to 85% of total image height, whereas extinguishers and signs span 2% to 15%. The single-scale feature grid (16x16) lacks large receptive field integration for full-height doors.
  3. *Contextual Ambiguity:* Doors in standard room photos share low-level visual features with interior walls, elevators, and corridors.
- **Production Implication:** **NOT_READY_FOR_PRODUCTION_USE**. Attempting automated exit door localization with this baseline would result in complete operational failure.

---

### 2.2 `OBSTACLE` (Class 3)
- **Prepared Dataset Reality:**
  - Total prepared annotations: 93 boxes (69 train / 6 val / 18 test)
  - Total unique physical scenes: 30 scenes (all sourced exclusively from Dataset 6)
- **Empirical Evaluation Metrics:**
  - Test Positives (Support): 18
  - True Positives: 0
  - False Negatives: 18 (100% missed)
  - False Positives: 0
  - AP@50: **0.0000**
- **Root Cause & Visual Failure Patterns:**
  1. *Taxonomic Diversity Disparity:* Unlike a fire extinguisher, which has a distinct cylindrical silhouette and valve structure, an "obstacle" can be a cardboard box, wooden pallet, metal cart, cleaning trolley, or furniture.
  2. *Contextual Grounding:* An object is an "obstacle" only in relation to an exit door or clear corridor. Treating generic boxes as visual object class 3 without spatial context created high intra-class variance that 69 training samples could not resolve.
- **Production Implication:** **NOT_READY_FOR_PRODUCTION_USE**. Obstruction assessment must rely on compound spatial reasoning (Stage-2) rather than raw uncontextualized perception object detection.

---

## 3. Analysis of Abundant Perception Classes

### 3.1 `FIRE_EXTINGUISHER` (Class 0)
- **Support:** 452 test objects | **TP:** 195 | **FP:** 185 | **FN:** 257
- **Precision:** 51.32% | **Recall:** 43.14% | **AP@50:** 22.36%
- **Failure Modes:**
  1. *Small-Scale Instances (<1% image area):* Extinguishers mounted far down hallways or captured in wide-angle room overviews were consistently missed by the 16x16 feature grid.
  2. *Recessed Cabinets & Glass Encasements:* Extinguishers mounted inside metal/glass cabinets exhibited lower confidence scores due to glare and frame reflections.
  3. *False Positives on Red Cylindrical Equipment:* The model fired false alarms on red fire alarm pull station boxes, red fire hose nozzles, and red pipe valves.

### 3.2 `EXIT_SIGN` (Class 2)
- **Support:** 320 test objects | **TP:** 6 | **FP:** 9 | **FN:** 314
- **Precision:** 40.00% | **Recall:** 1.87% | **AP@50:** 0.79%
- **Failure Modes:**
  1. *Anchor Size Mismatch:* Emergency exit signs in real inspection photographs are frequently ceiling-mounted and small (often <15x15 px on downscaled 256x256 canvases). The smallest anchor (0.08x0.08) was too coarse for distant exit signs.
  2. *High Contrast & Backlighting:* Illuminated signs against dark backgrounds or direct fluorescent lighting caused high false negative rates.

---

## 4. Failure-Safe Inference Verification

To guarantee that the model behaves safely in production inspection pipelines:
1. **Malformed Input Robustness:** Tested with zero-valued tensors, empty arrays, and unexpected aspect ratios. The model returned empty detections without throwing unhandled exceptions or crashing the service.
2. **Distinguishability of Failure:** Model failure remains clearly distinct from "no hazards found".
3. **Absence of Prohibited Legal Assertions:** Confirmed that the model outputs **zero** legal findings, zero compliance statuses, zero statutory clearance measurements, and zero automated pass/fail judgments.
