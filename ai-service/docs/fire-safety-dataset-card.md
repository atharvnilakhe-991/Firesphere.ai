# FireSphere.ai — Fire Safety Computer Vision Dataset Card

**Dataset Name:** `firesphere-fire-safety-dataset`  
**Dataset Version:** `0.8.1-phase8b`  
**Document Standard:** Hugging Face / Model Card Architecture Standard  
**Governance Status:** `DATASET_ACQUISITION_IN_PROGRESS`  
**Lead Authority:** Member 4 — AI/ML, Computer Vision & AI Productivity

---

## 1. Dataset Purpose & Description

The **FireSphere Fire Safety Dataset** is a curated computer vision benchmark designed to train, validate, and evaluate deep learning object detection and spatial defect models for municipal and commercial fire safety administration. It provides visual evidence-linked training data for locating fire safety equipment, verifying operational clearances, and identifying hazardous conditions during building inspections.

---

## 2. Verified Candidate Sources & Provenance

To eliminate unverified data risks, candidate datasets are vetted and recorded in [`datasets/fire_safety/dataset_sources.json`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/datasets/fire_safety/dataset_sources.json):

| Dataset ID | Candidate Source | Publisher / Author | Verified License | Provenance Status |
| :--- | :--- | :--- | :--- | :--- |
| `zenodo-fire-art-v1` | Zenodo (Fire-ART) | Cambridge / Macau (Qiao et al.) | `CC BY 4.0` | `VERIFIED_PUBLIC_OPEN_ACCESS` |
| `mendeley-fire-extinguisher-v1` | Mendeley Data | Chhor et al. | `CC BY 4.0` | `VERIFIED_PUBLIC_OPEN_ACCESS` |
| `zenodo-fse-infrastructure-v1` | Zenodo Open Access | Zenodo Research Consortium | `CC BY 4.0` | `VERIFIED_PUBLIC_OPEN_ACCESS` |
| `roboflow-emergency-exit-obstruction-v2` | Roboflow Universe | Building Safety AI Community | `CC BY 4.0` | `VERIFIED_PUBLIC_OPEN_ACCESS` |
| `kaggle-electrical-panel-hazard-v1` | Kaggle Open Data | Industrial Safety AI Group | `DbCL / Apache 2.0` | `VERIFIED_PUBLIC_OPEN_ACCESS` |
| `zenodo-sprinkler-head-defect-v1` | Zenodo (SHD-Bench) | Fire Protection Eng Lab | `CC BY-NC 4.0` | `RESTRICTED_NON_COMMERCIAL` (Quarantined) |
| `firesphere-field-inspection-pilot-v0` | Municipal Pilot Partner | FireSphere Operations & Fire Dept | Proprietary Pilot Agreement | `ACQUISITION_IN_PROGRESS` |

---

## 3. License Terms & Commercial Safety

1. **Permissive Open Access (`CC BY 4.0` & `Apache 2.0`)**: Candidates from Mendeley, Zenodo (FSE-ID), Roboflow, and Kaggle provide full commercial redistribution rights under appropriate attribution.
2. **Restricted Non-Commercial (`CC BY-NC 4.0`)**: The sprinkler defect dataset (`zenodo-sprinkler-head-defect-v1`) carries a non-commercial clause. It is approved solely for experimental staging and benchmark validation, and is quarantined from commercial SaaS deployment containers.
3. **Proprietary Municipal Pilot Agreement**: Field inspection imagery collected by municipal partners is subject to tenant data anonymization and strict multi-tenant context boundaries.

---

## 4. Target Classes & Detection Ontology

The dataset maps to the 10 FireSphere visual non-compliance inspection classes:

| Class ID | Class Name | Target Physical Defect | Operational Severity | Detectability Profile |
| :---: | :--- | :--- | :---: | :--- |
| `0` | `FIRE_EXTINGUISHER_MISSING` | Vacant mounting bracket or empty floor stand | `CRITICAL` | `PARTIALLY_VISUALLY_DETECTABLE` |
| `1` | `FIRE_EXTINGUISHER_BLOCKED` | Cylinder obstructed ($< 1\text{m}$ frontal clearance) | `HIGH` | `PARTIALLY_VISUALLY_DETECTABLE` |
| `2` | `FIRE_EXTINGUISHER_EXPIRED` | Pressure gauge in red zone or expired tag | `MEDIUM` | `NOT_RELIABLY_VISUALLY_DETECTABLE` |
| `3` | `EMERGENCY_EXIT_OBSTRUCTED` | Blocked egress door, passage, or exit corridor | `CRITICAL` | `DIRECTLY_VISUALLY_DETECTABLE` |
| `4` | `EXIT_SIGNAGE_MISSING` | Exit doorway lacking required illuminated sign | `HIGH` | `PARTIALLY_VISUALLY_DETECTABLE` |
| `5` | `SPRINKLER_HEAD_DEFECT` | Painted, bent, corroded, or obstructed sprinkler | `HIGH` | `PARTIALLY_VISUALLY_DETECTABLE` |
| `6` | `ELECTRICAL_PANEL_HAZARD` | Open breaker enclosure door or exposed terminals | `CRITICAL` | `DIRECTLY_VISUALLY_DETECTABLE` |
| `7` | `COMBUSTIBLE_MATERIAL_HAZARD` | Flammable pallets/cardboard near ignition source | `HIGH` | `DIRECTLY_VISUALLY_DETECTABLE` |
| `8` | `MANUAL_CALL_POINT_BLOCKED` | Fire alarm pull box obstructed by clutter | `HIGH` | `PARTIALLY_VISUALLY_DETECTABLE` |
| `9` | `HOSE_REEL_OBSTRUCTED` | Blocked fire hose cabinet or inaccessible reel | `HIGH` | `PARTIALLY_VISUALLY_DETECTABLE` |

---

## 5. Source-to-Target Class Mapping

Explicit semantic translation rules ensure that non-conforming classes are never artificially relabeled:

```text
[mendeley-fire-extinguisher-v1]
  fire_extinguisher       -> FIRE_EXTINGUISHER_BLOCKED (PARTIAL: localizes cylinder for spatial clearance)
  fire_extinguisher_sign  -> FIRE_EXTINGUISHER_MISSING (PARTIAL: cues vacant cradle)

[roboflow-emergency-exit-obstruction-v2]
  obstructed_door         -> EMERGENCY_EXIT_OBSTRUCTED (DIRECT)
  blocked_corridor        -> EMERGENCY_EXIT_OBSTRUCTED (DIRECT)
  clear_exit              -> UNSUPPORTED (Negative background sample)

[kaggle-electrical-panel-hazard-v1]
  electrical_panel_open   -> ELECTRICAL_PANEL_HAZARD (DIRECT)
  exposed_breaker         -> ELECTRICAL_PANEL_HAZARD (DIRECT)
  combustible_near_panel  -> COMBUSTIBLE_MATERIAL_HAZARD (DIRECT)
  electrical_panel_closed -> UNSUPPORTED (Compliant closed box)

[zenodo-sprinkler-head-defect-v1]
  sprinkler_corroded      -> SPRINKLER_HEAD_DEFECT (DIRECT)
  sprinkler_painted       -> SPRINKLER_HEAD_DEFECT (DIRECT)
  sprinkler_obstructed    -> SPRINKLER_HEAD_DEFECT (DIRECT)
  sprinkler_normal        -> UNSUPPORTED (Compliant sprinkler head)
```

---

## 6. Data Collection & Field Operational Context

* **Capture Devices**: Mobile smartphone cameras (iOS / Android) used by field fire safety officers during on-site inspections.
* **Image Format**: RGB photographs encoded as JPEG, PNG, or WebP.
* **Target Resolution**: Canvas standardized to $640 \times 640$ via aspect-preserving letterboxing.
* **Environment Types**:
  - Commercial retail complexes, shopping centers, and office buildings.
  - Institutional educational facilities, hospitals, and universities.
  - Industrial manufacturing warehouses, factories, and utility boiler rooms.

---

## 7. Known Limitations

1. **Non-Visual Dial Reading**: Pressure gauge needles ($15\text{mm}$ diameter) cannot be read from standard wide-angle photos ($640 \times 640$). Expiration verification requires macro high-resolution close-ups and OCR.
2. **2D Perspective Foreshortening**: Obstruction detection in 2D imagery may misinterpret foreground objects as blocking an extinguisher that is physically several meters in the background. Physical on-site depth verification by an officer is required.
3. **Absence Ambiguity**: Detecting missing equipment requires a visible fixture cue (empty bracket or wall sign). Without an anchor, the model cannot distinguish between a compliant blank wall and a wall missing an extinguisher.

---

## 8. Bias Considerations

* **Lighting Bias**: Baseline public datasets predominantly feature bright fluorescent office lighting. Dark stairwells, basements, and power-failure conditions require active flash/lighting augmentation.
* **Regional Equipment Variation**: Fire protection hardware varies internationally (e.g., European break-glass call points vs. North American T-bar pull stations). The dataset must incorporate multi-jurisdictional equipment form factors.
* **Occupancy Disparities**: Commercial offices are over-represented in public datasets, while hazardous chemical storage facilities are under-represented.

---

## 9. Intended Use & Regulatory Boundaries

### Authorized Intended Use:
* Computer vision assistance for authorized human Fire Safety Officers.
* Automated pre-screening of inspection evidence to highlight potential hazards for human review.
* Generation of advisory inspection report drafts.

### Explicitly Forbidden Non-Intended Use:
* **NEVER autonomously approve or reject a Fire NOC**.
* **NEVER execute statutory enforcement actions without human officer verification**.
* **NEVER treat AI detections as legal evidence in statutory proceedings without physical officer confirmation**.
