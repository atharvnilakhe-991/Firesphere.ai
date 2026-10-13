# FireSphere.ai — Phase 8.1: Architecture & Scope Decision Records (ADRs)

**Document Version:** `1.0.0-phase8.1`  
**Classification:** `ARCHITECTURE_DECISION_RECORD`  
**Lifecycle Status:** `ACCEPTED & FROZEN`  
**Service Version:** `0.8.1-phase8.1`  
**Author:** Member 4 — AI/ML, Computer Vision & AI Productivity  
**Date:** 2026-10-08  

---

## Overview

This Architecture Decision Record (ADR) codifies the definitive architectural, algorithmic, and governance decisions established during **Phase 8.1: Production AI Scope Freeze**. These decisions govern all subsequent phases (Phase 8.2 Dataset Manifests, Phase 9 Model Training, and Phase 10 Production Deployment) of the FireSphere.ai computer vision service.

---

## ADR-01: Decouple Statutory Violations from Monolithic Object Detection

### Status
`ACCEPTED`

### Context
Early architectural proposals considered training a monolithic 10-class YOLO object detector where each class directly corresponded to one of the 10 statutory fire-safety violations (e.g., predicting `0: FIRE_EXTINGUISHER_MISSING`, `1: FIRE_EXTINGUISHER_BLOCKED`, `2: FIRE_EXTINGUISHER_EXPIRED`, etc., directly as bounding boxes).

### Decision
FireSphere.ai **strictly rejects** the monolithic single-model architecture. Violations shall not be modeled as direct atomic bounding boxes. Instead, the system implements a multi-stage architecture:
$$\text{Image} \xrightarrow{} \text{Stage 1: Atomic Object Perception} \xrightarrow{} \text{Stage 2: Spatial \& Rule Reasoning} \xrightarrow{} \text{Stage 3: Advisory Finding}$$

### Rationale & Consequences
* **Absence Failure:** Standard object detectors localize pixel clusters; predicting the *non-existence* of an object (`FIRE_EXTINGUISHER_MISSING`, `EXIT_SIGNAGE_MISSING`) yields unconstrained false positives on every blank wall.
* **Compound Relationships:** An obstruction (`FIRE_EXTINGUISHER_BLOCKED`, `EMERGENCY_EXIT_OBSTRUCTED`) is a spatial relationship between equipment and an encroaching obstacle, not a standalone visual object.
* **Resolution Disparity:** Extreme scale differences (macro corridor vs. micro sprinkler head or pressure gauge needle) destabilize unified convolutional/attention feature pyramids.

---

## ADR-02: Use Object Perception Paired with Deterministic Spatial Reasoning

### Status
`ACCEPTED`

### Context
Evaluating whether equipment or egress routes are obstructed requires assessing the 36-inch clearance envelope defined by NFPA 10 and NFPA 101.

### Decision
The vision pipeline localizes base physical equipment (`FIRE_EXTINGUISHER`, `EXIT_DOOR`) and physical clutter (`OBSTACLE`). The Phase 4 Spatial Clearance Engine (`app.intelligence.spatial`) then deterministically calculates 2D bounding-box IoU, centroid proximity, and ground-plane contact lines to determine whether a physical clearance violation exists.

### Rationale & Consequences
* **Auditability:** When an officer queries why an alert was triggered, the system provides transparent mathematical evidence (e.g., "Obstacle box encroaches within 15% IoU of extinguisher bounding footprint") rather than opaque black-box neural logits.
* **Tunability:** Clearance thresholds can be calibrated dynamically based on facility occupancy types (industrial warehouse vs. residential corridor) without retraining convolutional backbones.

---

## ADR-03: Isolate Fire Extinguisher Expiry to OCR and High-Resolution Macro Workflows

### Status
`ACCEPTED`

### Context
Statutory code mandates that fire extinguishers undergo annual maintenance inspections documented on affixed tags or certification collars (`FIRE_EXTINGUISHER_EXPIRED`).

### Decision
`FIRE_EXTINGUISHER_EXPIRED` is classified as a **Phase 2 / Future Multi-Modal Capability**. It shall **never** be predicted by wide-angle object detection models. When implemented, it will utilize a dedicated mobile macro workflow:
$$\text{Macro Close-Up Capture} \xrightarrow{} \text{Tag Segmentation} \xrightarrow{} \text{OCR Text Parsing} \xrightarrow{} \text{Date Normalization} \xrightarrow{} \text{Maintenance Schedule Logic}$$
If an inspection photograph lacks sufficient optical resolution to resolve the inspection punch date, the system must emit an explicit `UNVERIFIED_INSPECTION_TAG` uncertainty flag and instruct the officer to perform a physical tag check. The AI must **never fabricate dates or assume expiration**.

### Rationale & Consequences
* Standard wide-angle room photos do not contain the optical resolution required to read 3 mm punched dates or verify gauge needle deflection. Attempting to predict expiration from wide-angle images constitutes statistical hallucination.

---

## ADR-04: Strictly Prohibit Coercive Semantic Relabeling of Generic Equipment Datasets

### Status
`ACCEPTED`

### Context
Public fire safety datasets (e.g., Roboflow, Kaggle) predominantly contain annotations for generic equipment presence (`fire_extinguisher`, `emergency exit sign`, `fire hose reel`). In past exploratory phases, models attempted to map generic equipment labels directly to violation labels.

### Decision
FireSphere.ai establishes an immutable **Truth-in-AI Governance Boundary**:  
Generic equipment presence annotations must **NEVER** be coercively relabeled as violation labels:
$$\begin{aligned}
\text{fire\_extinguisher} &\centernot\xrightarrow{} \text{FIRE\_EXTINGUISHER\_BLOCKED} \\
\text{emergency exit door} &\centernot\xrightarrow{} \text{EMERGENCY\_EXIT\_OBSTRUCTED} \\
\text{generic extinguisher} &\centernot\xrightarrow{} \text{FIRE\_EXTINGUISHER\_EXPIRED}
\end{aligned}$$
Generic presence annotations may only be ingested as Stage 1 atomic object perception data (`FIRE_EXTINGUISHER`, `EXIT_DOOR`, `EXIT_SIGN`).

### Rationale & Consequences
* Coercive relabeling trains models to classify compliant, life-saving safety equipment as dangerous violations, creating catastrophic false-positive rates that destroy operational utility.

---

## ADR-05: Enforce Mandatory Human-in-the-Loop Officer Verification Boundaries

### Status
`ACCEPTED`

### Context
Deploying AI in statutory life-safety administration carries legal and life-safety implications. Unsupervised automated decisions could lead to wrongful citations, unaddressed fire hazards, or administrative liability.

### Decision
The AI service is strictly **advisory**. The authorized fire officer is the **sole statutory authority and final decision-maker**.  
The AI service is legally and programmatically barred from:
1. Autonomously approving or rejecting inspection reports.
2. Issuing statutory compliance certificates or occupancy permits.
3. Inventing fire code section citations, clearance dimensions, pressure values, fines, or cure deadlines.
4. Fabricating missing evidence.

Furthermore, **zero AI detections does NOT equal a compliant building**. Every inspection report draft generated by the AI requires explicit officer verification, comment, and digital sign-off before being finalized.

### Rationale & Consequences
* Guarantees legal compliance with municipal administrative codes and eliminates algorithmic liability.

---

## ADR-06: Stage Delivery via MVP Scope Freeze Rather than Blocking on Monolithic Perfection

### Status
`ACCEPTED`

### Context
Acquiring fully annotated, diverse, multi-scene datasets for all 10 statutory defect classes simultaneously is currently impossible without introducing unvalidated or pseudo-replicated data (such as Dataset 7's single-scene capture).

### Decision
Phase 8.1 formally **freezes the Production MVP Scope** to two empirically validated, high-confidence capabilities:
1. `EMERGENCY_EXIT_OBSTRUCTED` (Class 3) — Supported by Dataset 6 verified violation annotations.
2. `FIRE_EXTINGUISHER_BLOCKED` (Class 1) — Supported by canonical extinguisher perception, Dataset 8 auxiliary condition data, and spatial clearance reasoning.

The remaining 8 classes are systematically staged:
* 7 classes deferred to **Phase 2 / Future Releases** (awaiting multi-scene paired datasets and macro workflows).
* 1 class (`SPRINKLER_HEAD_DEFECT`) classified as **Strictly Unsupported by Vision** and relegated to manual officer checklists.

### Rationale & Consequences
* Unblocks production engineering and field deployment for Core MVP workflows without compromising safety or data integrity.

---

## ADR-07: Mandatory Group-Aware Cluster Locking for Split Partitioning

### Status
`ACCEPTED`

### Context
Public datasets frequently incorporate synthetic augmentations (e.g., Dataset 6 contains 52 scenes expanded 3x to 156 images) or video frame sequences (e.g., Dataset 8 contains multi-frame handheld walk clusters). Splitting augmented or sequential images randomly between `train`, `val`, and `test` causes severe data leakage, inflating evaluation metrics while crippling real-world generalization.

### Decision
All dataset split generators in the FireSphere pipeline must enforce **group-aware cluster locking**:
* All augmented variants and sequential video frames originating from the same physical scene cluster must be deterministically locked into the exact same partition (`train`, `val`, or `test`).
* Partitions must be evaluated with perceptual hash (dHash) cross-partition distance checks (requiring Hamming distance $\ge 5$).

### Rationale & Consequences
* Prevents data leakage and ensures reported validation metrics reflect genuine generalization to unseen physical facilities.

---

## ADR-08: Strict Quarantine and Exclusion of Non-Commercial (CC BY-NC) Datasets

### Status
`ACCEPTED`

### Context
The Zenodo SHD-Bench dataset for sprinkler head defects is distributed under the `CC BY-NC 4.0` license, which explicitly prohibits commercial use.

### Decision
Any dataset carrying a non-commercial restriction (`CC BY-NC 4.0`, `CC BY-NC-SA 4.0`, or proprietary academic restrictions) is **strictly quarantined and excluded** from the FireSphere.ai commercial production repository and training pipeline.

### Rationale & Consequences
* Protects the FireSphere commercial SaaS platform from copyright infringement and licensing litigation.

---

## ADR-09: Explicit Uncertainty Propagation Over Hallucinatory Prediction

### Status
`ACCEPTED`

### Context
Photographs submitted from the field frequently suffer from poor illumination, severe motion blur, occluded camera angles, or out-of-frame equipment.

### Decision
When image quality, optical resolution, or bounding-box confidence falls below calibrated operational thresholds, the AI service must return an explicit **Uncertainty State** (`ADVISORY_LOW_CONFIDENCE`, `REQUIRES_OFFICER_VERIFICATION`, or `UNVERIFIED_INSPECTION_TAG`). The model must never hallucinate certainty or force a classification when visual evidence is ambiguous.

### Rationale & Consequences
* Upholds the core principle of Truth-in-AI and preserves officer trust in automated alerts.
