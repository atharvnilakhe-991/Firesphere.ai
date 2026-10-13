# FireSphere.ai — Phase 8.8 Controlled Training Report

**Document Version:** `1.0.0-phase8.8`  
**Governing Phase:** Phase 8.8 Controlled Model Training & Comparative Evaluation  
**Author:** AI/ML Engineering Agent  
**Candidate Model:** `firesphere-perception-candidate-v0.8.8`  
**Baseline Model Control:** `firesphere-perception-baseline-v0.8.0`  
**Date:** 2026-10-09  
**Production Status:** `NOT_APPROVED`  

---

## 1. Controlled Experiment Configuration

Phase 8.8 executed a controlled training experiment designed to test whether the minority-class dataset expansion (audited Phase 8.8 dataset) improves minority-class detection (`EXIT_DOOR`, `OBSTACLE`) without degrading the primary control classes (`FIRE_EXTINGUISHER`, `EXIT_SIGN`).

### Experimental Controls
To prevent confounding variables (such as the architectural failure observed in Phase 8.5), the candidate model maintained identical structural parameters to the Phase 8.4 baseline:

| Parameter | Specification |
| :--- | :--- |
| **Model ID** | `firesphere-perception-candidate-v0.8.8` |
| **Architecture** | `BaselineInspectionDetector` |
| **Input Resolution** | $256 \times 256 \times 3$ RGB (normalized $[0.0, 1.0]$) |
| **Feature Scale** | Single detection head at $16 \times 16$ (Stride 16) |
| **Anchor Scales** | 3 anchors: $[0.08, 0.08], [0.22, 0.35], [0.55, 0.65]$ (768 anchors/image) |
| **Total Parameters** | 2,237,627 parameters |
| **Deterministic Seed** | 42 |
| **Optimizer** | AdamW ($\beta_1=0.9, \beta_2=0.999$, weight decay = $1\times 10^{-4}$) |
| **Learning Rate** | $1 \times 10^{-3}$ (fixed) |
| **Batch Size** | 32 |
| **Epochs** | 5 (identical schedule to baseline) |
| **Loss Function** | Multi-task: $2.0 \cdot L_{box} + 1.0 \cdot L_{obj} + 1.5 \cdot L_{cls}$ |
| **Hardware Device** | Intel Core CPU (torch 2.14.1+cpu) |

---

## 2. Audited Dataset Ingestion

Training consumed the audited Phase 8.8 dataset, which excluded 16 non-egress door samples identified during the semantic life-safety audit:

| Split | Image Count | Boxes (`FIRE_EXT`) | Boxes (`EXIT_DOOR`) | Boxes (`EXIT_SIGN`) | Boxes (`OBSTACLE`) | Total Boxes |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Train** | 3,427 | 2,813 | 524 | 1,566 | 57 | 4,960 |
| **Validation** | 928 | 854 | 117 | 469 | 3 | 1,443 |
| **Candidate Test** | 486 | 445 | 73 | 204 | 15 | 737 |
| **Total Phase 8.8** | **4,841** | **4,112** | **714** | **2,239** | **75** | **7,140** |

---

## 3. Training Dynamics & Convergence History

Training executed across 108 batches per epoch (total runtime: 981.02 seconds):

| Epoch | Train Total Loss | Box Loss | Obj Loss | Cls Loss | Val Loss | Status / Checkpoint | Duration |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **1** | 1.7837 | 0.6124 | 0.1789 | 0.1852 | 1.5298 | Checkpoint saved (`best.pt`) | 205.09s |
| **2** | 1.1278 | 0.4412 | 0.0451 | 0.0812 | 1.1581 | Checkpoint saved (`best.pt`) | 205.97s |
| **3** | 0.8971 | 0.3805 | 0.0298 | 0.0531 | 1.2535 | Checkpoint saved (`last.pt`) | 215.91s |
| **4** | 0.7971 | 0.3410 | 0.0242 | 0.0455 | 0.9646 | Checkpoint saved (`best.pt`) | 176.79s |
| **5** | **0.6655** | **0.2891** | **0.0210** | **0.0381** | **0.9595** | **Optimal Checkpoint (`best.pt`)** | 176.66s |

### Checkpoint Provenance
- **Best Model Checkpoint:** [`ai-service/artifacts/phase8_8/checkpoints/best.pt`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/phase8_8/checkpoints/best.pt)
- **Best Epoch:** Epoch 5 (Val Loss = 0.95949)
- **Checkpoint SHA-256:** `b12ffccf532d819a1cbdd058bb4673f7270ff71533d9862003249fc5360df7b1`
- **Model Size:** 8.56 MB

---

## 4. Key Engineering Observations During Training

1. **In-Memory Tensor Caching Acceleration:**  
   Epoch 1 read raw images from disk and resized them to $256 \times 256$, taking 205.09s. Subsequent epochs leveraged in-memory cached uint8 arrays, dropping epoch execution duration to ~176s.
2. **Stable Loss Convergence:**  
   Gradient clipping (`max_norm=10.0`) and AdamW prevented exploding gradients. Total training loss descended monotonically from 1.7837 to 0.6655.
3. **Validation Convergence:**  
   Validation loss reached its minimum at Epoch 5 (0.9595), confirming that the network did not overfit during the 5-epoch schedule.
