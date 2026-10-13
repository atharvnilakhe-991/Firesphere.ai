# FireSphere.ai — Phase 9 Formal Model Selection Record

**Document Version:** `1.0.0-phase9`  
**Governing Phase:** Phase 9 — Final AI Model Selection, Calibration, Evaluation & Validation  
**Author:** AI/ML Engineering Agent  
**Selected Reference Baseline:** `firesphere-perception-baseline-v0.8.0` (Phase 8.4)  
**Production Inference Status:** Strictly `NOT_APPROVED`  
**Date:** 2026-10-09  

---

## 1. Executive Summary & Selection Context

Phase 9 establishes the definitive, evidence-grounded model selection across all evaluated perception candidates. In strict accordance with the project roadmap freeze:
- Roadmap is permanently locked: Phase 9 (Selection & Validation) $\rightarrow$ Phase 10 (Production Integration) $\rightarrow$ M4 completion.
- No further open-ended dataset searches, model architecture redesigns, or retraining runs are permitted.
- Model selection completion does **NOT** imply production approval.
- The authoritative reference model is selected for controlled baseline reference only. Production inference gates remain closed.

---

## 2. Comparative Evidence Across Candidates

All models were evaluated on the identical, untouched Phase 8.3 external test set (522 images, 796 ground truth annotations):

| Model Candidate | Architecture | Checkpoint Hash (SHA-256) | mAP@50 | mAP@50:95 | Mean Precision | Mean Recall | Mean F1 | Extinguisher AP@50 | Exit Sign AP@50 | CPU Latency (Total) | Throughput (FPS) | Checkpoint Disk Size |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Phase 8.4 Baseline** | Native ConvNet (Scratch) | `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666` | **0.0514** | 0.0172 | 0.1545 | **0.1303** | 0.1256 | **0.1973** | 0.0084 | **33.52 ms** | **29.84 FPS** | 25.66 MB |
| **Phase 8.8 Candidate** | Native ConvNet (Scratch) | `b12ffccf532d819a1cbdd058bb4673f7270ff71533d9862003249fc5360df7b1` | 0.0276 | 0.0097 | 0.1277 | 0.0882 | 0.0949 | 0.1028 | 0.0074 | 35.10 ms | 28.49 FPS | 25.66 MB |
| **Phase 8.9 Candidate** | MobileNetV2 + FPN (Pretrained) | `abb5bab4a4eb3de7d2275c5a946238804f3f453cc1419a3906b888fe52d88d36` | 0.0503 | **0.0195** | **0.2064** | 0.1197 | **0.1482** | 0.1329 | **0.0684** | 170.47 ms | 5.87 FPS | **13.45 MB** |

---

## 3. Benchmark Methodology & Latency Profile

Profiling was performed under identical conditions on CPU hardware (Batch Size = 1, $256 \times 256 \times 3$ RGB, 100 test samples):
1. **Phase 8.4 Inference Profile:**  
   - Mean Pure Inference: 20.88 ms (p50 = 20.77 ms, p95 = 23.56 ms).
   - Mean Total Pipeline (Decode + Preprocess + Forward + NMS): 33.52 ms.
   - Operates near real-time edge processing speeds (29.84 FPS).
2. **Phase 8.9 Inference Profile:**  
   - Mean Pure Inference: 154.99 ms (p50 = 77.73 ms, p95 = 294.62 ms).
   - Mean Total Pipeline: 170.47 ms (p50 = 126.46 ms, p95 = 301.85 ms).
   - Operates at batch inspection speeds (5.87 FPS).
3. **Engineering Root Cause:**  
   MobileNetV2 inverted residual bottlenecks utilize depthwise separable convolutions. While computationally efficient in FLOP count, each inverted residual block requires multiple individual PyTorch CPU kernel dispatches ($3\times 3$ depthwise conv followed by $1\times 1$ pointwise conv). On single-threaded CPU backends without fused runtime operators or INT8 quantization, dispatch latency multiplies significantly compared to standard convolutional layers.

---

## 4. Formal Selection Determination

### Selected Reference Model: `firesphere-perception-baseline-v0.8.0` (Phase 8.4)
- **Checkpoint Location:** [`ai-service/artifacts/phase8_4/checkpoints/best.pt`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/phase8_4/checkpoints/best.pt)
- **Verified SHA-256 Digest:** `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666`
- **Role in Platform:** **REFERENCE BASELINE CONTROL**.

### Selection Rationale:
1. **Primary Compliance Accuracy:** `FIRE_EXTINGUISHER` is the central compliance asset in commercial fire inspections. Phase 8.4 achieved the highest AP@50 (0.1973) and recall (0.4867) on this class.
2. **Phase 8.9 Retained as Regressed:** Although Phase 8.9 achieved a breakthrough on `EXIT_SIGN` (AP@50 = 0.0684 vs 0.0084), its extinguisher performance regressed by -0.0644 (-32.6% relative loss) due to feature competition. Phase 8.9 remains historically classified as `CANDIDATE_REGRESSED`.
3. **Runtime Execution Suitability:** Phase 8.4 delivers ~5x faster CPU inference (33.52 ms vs 170.47 ms) and 29.84 FPS throughput.

### Production Guardrail:
- **Production Status:** Strictly **`NOT_APPROVED`**.
- This selection is an architectural reference baseline for platform integration testing.
- The model **MUST NOT** be automatically loaded into production live inspection traffic while the production model gate remains closed.
