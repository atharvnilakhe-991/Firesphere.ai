# FireSphere.ai — Phase 8.9 Formal Governance Decision Record

**Document Version:** `2.0.0-phase8.9`  
**Governing Phase:** Phase 8.9 Pretrained Transfer-Learning Experiment & Controlled Evaluation  
**Author:** AI/ML Engineering Agent  
**Baseline Model Control:** `firesphere-perception-baseline-v0.8.0` (Phase 8.4)  
**Date:** 2026-10-09  

---

## 1. Formal Phase 8.9 Decision Record

```json
{
  "phase": "8.9",
  "gate_decision": "CANDIDATE_REGRESSED",
  "production_status": "NOT_APPROVED",
  "provenance_limitation": "Full SHA-256 not officially published by PyTorch; research authorization granted by user.",
  "authoritative_baseline_preserved": "firesphere-perception-baseline-v0.8.0",
  "baseline_checkpoint_sha256": "5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666",
  "candidate_model": "firesphere-perception-pretrained-v0.8.9",
  "candidate_checkpoint_sha256": "abb5bab4a4eb3de7d2275c5a946238804f3f453cc1419a3906b888fe52d88d36",
  "test_split_integrity": "UNTOUCHED_522_IMAGES",
  "canonical_dataset_integrity": "UNCHANGED"
}
```

---

## 2. Evaluation Against Phase 8.9 Decision Gate Criteria

Section 5 and Section 21 of the governing specification define the decision vocabulary and exit conditions:

| Decision Gate Candidate | Qualification Condition | Empirical Assessment | Gate Verdict |
| :--- | :--- | :--- | :---: |
| **`PRETRAINED_EXPERIMENT_BLOCKED`** | Suitable pretrained weights are unavailable locally in the environment. | Overridden by explicit user authorization to proceed with existing verified MobileNetV2 checkpoint. | Inactive |
| **`CANDIDATE_REGRESSED`** | Candidate model is materially worse than Phase 8.4 on the primary safety class (`FIRE_EXTINGUISHER` AP drop $> 0.05$). | **Extinguisher AP@50 dropped by -0.0644 (from 0.1973 to 0.1329) relative to Phase 8.4 baseline.** | **CONFIRMED & ENACTED** |
| **`CANDIDATE_NOT_IMPROVED`** | No meaningful improvement demonstrated across the model. | Overridden by regression threshold. | Inactive |
| **`CANDIDATE_PROMISING_NOT_APPROVED`** | Meaningful improvement with stable primary class performance. | Cannot be awarded due to primary extinguisher regression. | Inactive |
| **`CONTROLLED_VALIDATION_READY`** | Candidate clearly outperforms Phase 8.4 on all dimensions. | Did not outperform Phase 8.4 on primary compliance class. | Inactive |

**FINAL FORMAL GATE VERDICT:** **`CANDIDATE_REGRESSED`**  
**PRODUCTION STATUS:** **`NOT_APPROVED`**  

---

## 3. Decision Rationale & Engineering Evidence

1. **Primary Compliance Class Priority:**  
   In fire safety inspection, `FIRE_EXTINGUISHER` is the primary compliance detection class. A model that drops extinguisher AP@50 by -6.44 percentage points (from 0.1973 down to 0.1329) cannot replace the production reference baseline, regardless of secondary gains.
2. **Recognition of Exit Sign Breakthrough:**  
   The experiment achieved platform-record exit sign performance (AP@50 jumped from 0.0084 to 0.0684, an 8.1x gain). This proves that high-resolution feature taps (stride 8) and pretrained convolutional representations are scientifically valid for compact objects.
3. **Phase 8.4 Authoritative Baseline Retained:**  
   In accordance with stop condition rules, `firesphere-perception-baseline-v0.8.0` remains the authoritative, unbroken reference baseline.
4. **Historical Provenance Preserved:**  
   Phase 8.10 (`CHECKPOINT_INTEGRITY_FAILED`) and Phase 8.10A (`CHECKPOINT_HASH_REQUIRES_MANUAL_VERIFICATION`) records remain intact and unchanged. The provenance limitation of the MobileNetV2 checkpoint is explicitly recorded.
5. **No Automatic Phase Progression:**  
   Phase 8.9 work stops here. No model registration, deployment, or automatic advancement to Phase 9 will occur.
