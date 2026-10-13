# FireSphere.ai — Phase 8.8 Formal Governance Decision Record

**Document Version:** `1.0.0-phase8.8`  
**Governing Phase:** Phase 8.8 Controlled Model Training & Comparative Evaluation  
**Author:** AI/ML Engineering Agent  
**Candidate Model:** `firesphere-perception-candidate-v0.8.8`  
**Baseline Model Control:** `firesphere-perception-baseline-v0.8.0`  
**Date:** 2026-10-09  

---

## 1. Formal Phase 8.8 Decision

```json
{
  "phase": "8.8",
  "gate_decision": "CANDIDATE_REGRESSED",
  "production_status": "NOT_APPROVED",
  "authoritative_baseline_preserved": "firesphere-perception-baseline-v0.8.0",
  "baseline_checkpoint_sha256": "5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666",
  "candidate_checkpoint_sha256": "b12ffccf532d819a1cbdd058bb4673f7270ff71533d9862003249fc5360df7b1",
  "test_split_integrity": "UNTOUCHED_522_IMAGES",
  "canonical_dataset_integrity": "UNCHANGED"
}
```

---

## 2. Evaluation Against Phase 8.8 Decision Rules

Section 17 of the governing specification established five possible outcomes:

| Candidate Decision Rule | Qualification Condition | Empirical Assessment | Decision Outcome |
| :--- | :--- | :--- | :---: |
| **`CANDIDATE_IMPROVED_NOT_APPROVED`** | Model demonstrates clear improvements without significant regressions. | Extinguisher AP dropped by -47.9% and overall mAP dropped from 0.0514 to 0.0276. | **REJECTED** |
| **`CANDIDATE_NOT_IMPROVED`** | New data provides zero change across all metrics. | Class metrics experienced substantial divergence. | **REJECTED** |
| **`CANDIDATE_REGRESSED`** | Candidate causes unacceptable regression against Phase 8.4 control. | **FIRE_EXTINGUISHER AP dropped from 0.1973 to 0.1028; mAP@50 dropped from 0.0514 to 0.0276.** | **CONFIRMED** |
| **`DATA_EXPANSION_STILL_REQUIRED`** | Minority classes remain data-starved. | True, but secondary to the regression rule. | Supported |
| **`CONTROLLED_CANDIDATE_READY_FOR_NEXT_VALIDATION`** | Improvements justify progressing candidate. | Candidate is regressed against baseline control. | **REJECTED** |

**FINAL GATE VERDICT:** **`CANDIDATE_REGRESSED`**

---

## 3. Decision Rationale & Engineering Findings

1. **Fire Extinguisher Control Degradation:**  
   Under Section 12.A of the governing prompt: *"A minority-class improvement is NOT acceptable if it causes substantial degradation of this control class."* The candidate's extinguisher AP@50 fell by -0.0945 (-47.9% relative drop, with true positives dropping from 220 to 144).
2. **Representation Competition in Scratch-Trained Backbones:**  
   Training a 2.2M CNN from random initialization on 3,427 images forces the shared convolutional layers to allocate features among conflicting spatial geometries (compact extinguisher cylinders vs full-height rectangular doors). Without pre-trained weights (e.g. ImageNet/COCO backbone initialization), introducing 524 doors degraded extinguisher feature specialization.
3. **Domain Gap on Held-Out Test Doors:**  
   The held-out Phase 8.3 test set contains only 6 door annotations from Dataset 6. The 529 new doors from `fire_safety_multi` did not produce cross-domain transfer to those 6 specific test doors at IoU 0.50.
4. **Phase 8.4 Baseline Retains Authority:**  
   `firesphere-perception-baseline-v0.8.0` remains the authoritative baseline model. The Phase 8.8 candidate is archived as an informative controlled experiment.

---

## 4. Statutory Product Constraints Preserved

- The model is **NOT APPROVED** for production.
- Model weights will **NOT** be registered into production or deployed to the backend.
- FireSphere.ai maintains strict Human-in-the-Loop governance: all findings remain advisory and require Fire Inspection Officer verification.
