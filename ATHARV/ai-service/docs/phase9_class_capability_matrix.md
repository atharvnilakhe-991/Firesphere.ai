# FireSphere.ai — Phase 9 Per-Class Capability Matrix & Operational Boundaries

**Document Version:** `1.0.0-phase9`  
**Governing Phase:** Phase 9 — Final AI Model Selection, Calibration, Evaluation & Validation  
**Author:** AI/ML Engineering Agent  
**Operational Framework:** Human-in-the-Loop Decision Support Only  
**Date:** 2026-10-09  

---

## 1. Executive Summary & Gating Taxonomy

To prevent premature deployment or misleading claims of readiness, each perception class is independently evaluated against empirical test evidence. Operational capability is categorized into three strict tiers:
- **`ADVISORY_ONLY`:** Model exhibits detectable visual features on held-out test data with documented precision and recall. Detections serve as advisory candidate cues to assist a human inspector; they are **NOT** autonomous legal compliance certifications.
- **`EXPERIMENTAL_ONLY`:** Model learns partial representations on training data, but generalization to external domains is weak or unstable. Permitted only in offline research environments.
- **`UNSUPPORTED`:** Model achieves zero true positives on held-out test data due to severe data scarcity, perspective variance, or semantic heterogeneity. Inoperable for operational inspection workflows.

---

## 2. Per-Class Operational Capability Matrix

| Class ID | Class Name | Training Support | Validation Support | External Test Support | Test TP / FP / FN | Test Precision | Test Recall | Baseline AP@50 | Candidate AP@50 | Operational Status | Production Ready? |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **0** | `FIRE_EXTINGUISHER` | 2,813 | 854 | 452 | 143 / 198 / 309 | 0.4194 | 0.3164 | 0.1973 | 0.1329 | **`ADVISORY_ONLY`** | **NO** |
| **1** | `EXIT_DOOR` | 524 | 117 | **6** | 0 / 0 / 6 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | **`UNSUPPORTED`** | **NO** |
| **2** | `EXIT_SIGN` | 1,566 | 469 | 320 | 52 / 76 / 268 | 0.4062 | 0.1625 | 0.0084 | 0.0684 | **`ADVISORY_ONLY`** | **NO** |
| **3** | `OBSTACLE` | 57 | 3 | **18** | 0 / 0 / 18 | 0.0000 | 0.0000 | 0.0000 | 0.0000 | **`UNSUPPORTED`** | **NO** |

---

## 3. Deep-Dive Per-Class Capability Analysis

### 3.1 Class 0: `FIRE_EXTINGUISHER`
- **Empirical Status:** **`ADVISORY_ONLY`** (Human-in-the-Loop Assistance).
- **Supported Operational Use:** Detecting visible wall-mounted red fire extinguishers in common indoor corridors and offices to highlight candidate locations for officer review.
- **Strict Operational Boundaries:**
  1. **Generic Detection $\neq$ Fire Code Compliance:** Detecting an extinguisher cylinder does **NOT** indicate that the installation complies with fire safety regulations. The model cannot inspect pressure gauge needles, hydro-test expiration dates, mounting bracket height, or tamper seals.
  2. **Non-Detection $\neq$ Violation:** A missed detection (FN = 309) must never automatically generate a statutory fine or citation for "Missing Extinguisher."
  3. **False Positive Clutter:** Precision of 41.94% means ~58% of candidate bounding boxes represent visual false alarms (red alarm bells, emergency pull stations, red hose reel cabinets). Every box requires officer confirmation.

---

### 3.2 Class 1: `EXIT_DOOR`
- **Empirical Status:** **`UNSUPPORTED`** (Completely Inoperable for Operational Decisions).
- **External Test Support:** **Only 6 ground truth instances** exist in the held-out test split.
- **Empirical Findings:** Both Phase 8.4 and Phase 8.9 achieved **0 True Positives**, 0 Precision, 0 Recall, and 0.0000 AP@50.
- **Engineering Root Cause:** High aspect ratio variance (ranging from $1.5$ to $4.5$), extreme oblique viewing angles in narrow corridors, and varying illumination prevent bounding box regression with IoU $\ge 0.50$.
- **Strict Prohibition:** The AI service must **NOT** make any claim or inference regarding exit door presence, clearance, or functionality.

---

### 3.3 Class 2: `EXIT_SIGN`
- **Empirical Status:** **`ADVISORY_ONLY`** (Controlled Auxiliary Cues).
- **Empirical Findings:** Pretrained features at stride 8 (P3 head) delivered an 8.1x AP@50 improvement (0.0684 vs 0.0084), increasing True Positives from 11 to 52 with 40.62% precision.
- **Operational Boundaries:**
  1. Recall of 16.25% means >83% of exit signs are missed. The system cannot certify that an emergency exit route possesses adequate signage.
  2. Model detects physical rectangular illuminated plaques; it cannot verify electrical backup battery power, emergency illumination lux levels, or regulatory wording compliance.
  3. False positives on overhead fluorescent diffusers and HVAC vent covers require human validation.

---

### 3.4 Class 3: `OBSTACLE`
- **Empirical Status:** **`UNSUPPORTED`** (Completely Inoperable for Operational Decisions).
- **Dataset Support:** **Severe scarcity** — only 57 training instances across 3,427 corpus images, and **only 18 instances** in the external test split.
- **Empirical Findings:** **0 True Positives**, 0 Precision, 0 Recall, and 0.0000 AP@50 across all models.
- **Engineering Root Cause:** "Obstacle" is an open-world semantic category encompassing chairs, boxes, trash bins, pallets, ladders, and carts. Without thousands of dedicated multi-domain annotations, visual models cannot converge on a unified feature manifold.
- **Strict Prohibition:** Generic clutter, shadows, or corridor furniture must **NEVER** be automatically cited as legal egress obstructions under statutory building codes.
