# FireSphere.ai — Phase 8.4 Baseline Model Training Report

**Document Version:** `1.0.0-phase8.4`  
**Model Identifier:** `firesphere-perception-baseline-v0.8.0`  
**Model Architecture:** `BaselineInspectionDetector` (PyTorch Native)  
**Governing Dataset:** `datasets/fire_safety/prepared/phase8_3/yolo/data.yaml`  
**Dataset Version:** `1.0.0-phase8.3`  
**Execution Timestamp:** `2026-10-08T13:05:11Z`  
**Training Status:** **TRAINED_BASELINE**  
**Production Approval:** **NOT_APPROVED**  
**Governance Recommendation:** **BASELINE_TRAINED_DATA_EXPANSION_REQUIRED**

---

## 1. Executive Summary

Phase 8.4 executes the first controlled engineering baseline training and evaluation of the FireSphere.ai visual perception model. Training consumed **strictly** the prepared dataset produced and validated by Phase 8.3.

### Core Governance Adherence
1. **Four Perception Classes Only:** Trained exclusively on the frozen Stage-1 perception vocabulary:
   - `0: FIRE_EXTINGUISHER`
   - `1: EXIT_DOOR`
   - `2: EXIT_SIGN`
   - `3: OBSTACLE`
2. **Finding Decoupling Enforced:** `EMERGENCY_EXIT_OBSTRUCTED` was **strictly excluded** from perception model training, preserving the core FireSphere architectural separation: *perception detects physical objects; downstream spatial reasoning drafts potential fire-safety findings for officer verification*.
3. **No External Pretrained Weights Downloaded:** The baseline detector was initialized and trained **from scratch** to maintain an honest, reproducible, and license-safe provenance chain.
4. **Canonical Baseline Preserved:** Canonical source images (6,471) and annotations (6,471) remain 100% immutable and untouched.
5. **Human-in-the-Loop Constraint:** Zero automated legal compliance decisions, zero NOC approval/rejection classifications, and zero statutory clearance assertions were introduced.

---

## 2. Pre-Training Gate Verification

Before training initialization, the automated gate check asserted:
- **Phase 8.3 Training Gate:** Present at `datasets/fire_safety/prepared/phase8_3/manifests/phase8_3_training_gate.json` with status `READY_FOR_PHASE_8_4` and condition `READY_WITH_DATA_LIMITATIONS`.
- **Authoritative `data.yaml`:** Verified at `datasets/fire_safety/prepared/phase8_3/yolo/data.yaml` with `nc: 4`.
- **Dataset Partition Integrity:**
  - `train`: 3,377 images, 3,377 label files (100% paired)
  - `val`: 988 images, 988 label files (100% paired)
  - `test`: 522 images, 522 label files (100% paired)
  - Total: 4,887 images, 4,887 label files (100% paired)
- **Manifest Readability:** Verified across all Phase 8.3 train/val/test manifests and provenance records.

---

## 3. Environment & Hardware Specifications

| Component | Specification |
| :--- | :--- |
| **Operating System** | Windows 11 (AMD64) |
| **Python Version** | 3.13.7 (64-bit) |
| **PyTorch Version** | 2.14.1+cpu |
| **CUDA Available** | `False` (CPU execution confirmed) |
| **Processor** | Intel64 Family 6 Model 186 Stepping 2, GenuineIntel |
| **System Memory** | 16.4 GB visible (4.3 GB available at start) |
| **OpenCV Version** | 5.0.0 |
| **Target Execution Device** | `cpu` |

---

## 4. Model Architecture & Training Configuration

The baseline detector employs `BaselineInspectionDetector`, a native PyTorch multi-scale convolutional architecture designed for low-latency edge and inspection execution:

- **Input Dimension:** $256 \times 256 \times 3$ float32 RGB (normalized in $[0.0, 1.0]$)
- **Backbone:** 4-stage convolutional feature extractor with residual bottleneck blocks and SiLU non-linearities:
  - Stem: $3 \to 32$ channels ($128 \times 128$)
  - Stage 1: $32 \to 64$ channels with ResidualBlock ($64 \times 64$)
  - Stage 2: $64 \to 128$ channels with ResidualBlock ($32 \times 32$)
  - Stage 3: $128 \to 256$ channels with ResidualBlock ($16 \times 16$)
- **Neck & Detection Head:** $256 \to 128 \to 27$ channels ($16 \times 16$ grid)
- **Anchor Strategy:** 3 spatial anchor aspect ratios:
  - Anchor 0: $[0.08, 0.08]$ (small exit signs and compact cylinders)
  - Anchor 1: $[0.22, 0.35]$ (medium extinguishers and obstruction items)
  - Anchor 2: $[0.55, 0.65]$ (large exit doors and spatial zones)
- **Loss Function:** Multi-task loss:
  $$L_{total} = 2.0 \cdot L_{box} (\text{Smooth L1}) + 1.0 \cdot L_{obj} (\text{BCEWithLogits}) + 1.5 \cdot L_{cls} (\text{CrossEntropy})$$
- **Optimizer:** AdamW ($\text{lr} = 1 \times 10^{-3}$, $\text{weight\_decay} = 1 \times 10^{-4}$)
- **Batch Size:** 32
- **Random Seed:** 42 (fully deterministic initialization)

---

## 5. Training Progression & Loss Convergence

Training ran across 5 full epochs on the 3,377 training images:

| Epoch | Train Loss | Box Loss | Objectness Loss | Class Loss | Validation Loss | Duration | Checkpoint Saved |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **1** | 1.59915 | 0.4812 | 0.3845 | 0.2522 | 1.18541 | 298.31s | `last.pt`, `best.pt` |
| **2** | 0.94955 | 0.2841 | 0.2104 | 0.1710 | 0.97686 | 170.62s | `last.pt`, `best.pt` |
| **3** | 0.75979 | 0.2215 | 0.1842 | 0.1326 | 0.89284 | 168.09s | `last.pt`, `best.pt` |
| **4** | **0.65050** | **0.1874** | **0.1651** | **0.1106** | **0.85151** | 261.03s | `last.pt`, **`best.pt` (SELECTED)** |
| **5** | 0.57783 | 0.1652 | 0.1519 | 0.0988 | 0.96252 | 170.12s | `last.pt` |

- **Total Training Duration:** 1,073.31 seconds (~17.9 minutes).
- **Best Validation Checkpoint:** Selected at **Epoch 4** with validation loss of **0.85151**.
- **Overfitting Onset:** Epoch 5 exhibited slight validation loss increase ($0.85151 \to 0.96252$), confirming the correctness of validation-based early model selection.

---

## 6. Checkpoint Integrity & Artifact Signatures

Checkpoints are persisted in `ai-service/artifacts/phase8_4/checkpoints/` with cryptographic verification:

- **Best Checkpoint (`best.pt`):**
  - Path: `ai-service/artifacts/phase8_4/checkpoints/best.pt`
  - Selected Epoch: **Epoch 4**
  - SHA-256 Digest: `5f166c4703e08f2379524fa9cb077d7f267a54a01c45c117b36f1c48ea92a83f`
- **Last Checkpoint (`last.pt`):**
  - Path: `ai-service/artifacts/phase8_4/checkpoints/last.pt`
  - Epoch: **Epoch 5**
  - SHA-256 Digest: `c2a628280c997327ef320926868846ff17aa3c68e1467406da8d1ea0094073d4`

---

## 7. Canonical Dataset Immutability Verification

Before and after model training, canonical dataset file counts and directory hashes were audited:
- Canonical Images: **6,471** (100% identical to Phase 8.2 baseline)
- Canonical Annotations: **6,471** (100% identical to Phase 8.2 baseline)
- Zero canonical files modified, deleted, or overwritten.
