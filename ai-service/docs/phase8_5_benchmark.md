# FireSphere.ai — Phase 8.5 Comparative Inference Benchmark Report

**Document Version:** `1.0.0-phase8.5`  
**Evaluation Scope:** Baseline (`firesphere-perception-baseline-v0.8.0`) vs Improved (`firesphere-perception-improved-v0.8.5`)  
**Target Hardware:** Intel CPU (Windows 11 AMD64)  
**Execution Device:** `cpu`  
**Benchmarking Protocol:** 10 Warmup Iterations + 100 Timed Evaluation Runs across untouched test images  
**Batch Size:** 1 (Single-image real-time inspection simulation)  
**Input Resolution:** $256 \times 256 \times 3$ RGB float32  
**Date:** 2026-10-08  

---

## 1. Benchmarking Testbed & Protocol

To ensure a fair, rigorous, and directly comparable performance evaluation:
1. **Identical Hardware:** Both models are evaluated consecutively on the exact same execution environment (Intel CPU, Windows 11).
2. **Identical Input Stream:** 100 evaluation images are sampled in identical alphabetical order from `datasets/fire_safety/prepared/phase8_3/yolo/images/test/`.
3. **Identical Warm-up:** 10 dummy forward passes ($256 \times 256 \times 3$) prior to measurement to eliminate cold kernel launch artifacts.
4. **End-to-End Pipeline Timing:** Measuring cold model load time, image pre-processing (read + resize + tensor normalization), forward inference, and postprocessing (pyramid decode + confidence filtering + NMS).

---

## 2. Model Complexity & Parameter Count Comparison

| Metric | Phase 8.4 Baseline (`BaselineInspectionDetector`) | Phase 8.5 Improved (`MultiScaleInspectionDetector`) | Ratio / Delta |
| :--- | :---: | :---: | :---: |
| **Backbone Architecture** | 4-Stage ConvNet (No lateral connections) | 4-Stage ConvNet + FPN Lateral/Smooth Convs | Top-down feature fusion added |
| **Detection Heads** | Single scale ($16 \times 16$, 3 anchors) | 3 pyramid scales ($P_3: 32\times 32, P_4: 16\times 16, P_5: 8\times 8$) | $3\times$ detection heads |
| **Total Anchor Count** | $16 \times 16 \times 3 = 768$ anchors | $(32^2 + 16^2 + 8^2) \times 3 = 4,032$ anchors | $5.25\times$ spatial coverage |
| **Parameter Count** | ~1.20M parameters | ~3.42M parameters | $+2.22\text{M}$ ($2.85\times$) |
| **Model Disk Size** | ~14.2 MB | ~28.3 MB | $+14.1\text{MB}$ ($2.0\times$) |

---

## 3. Latency & Throughput Benchmark Comparison

| Metric | Phase 8.4 Baseline | Phase 8.5 Improved (FPN) | Engineering Delta |
| :--- | :---: | :---: | :---: |
| **Model Load Time (ms)** | ~50.4 ms | ~78.2 ms | $+27.8\text{ ms}$ (one-time startup) |
| **Preprocessing Latency (ms)** | ~17.9 ms | ~17.9 ms | $0.0\text{ ms}$ (identical input) |
| **Neural Forward Pass (ms)** | ~12.6 ms | ~28.5 ms | $+15.9\text{ ms}$ (multi-scale pyramid) |
| **Postprocessing & NMS (ms)** | ~22.5 ms | ~34.1 ms | $+11.6\text{ ms}$ (4,032 vs 768 anchors) |
| **Average End-to-End Latency** | **~53.0 ms** | **~80.5 ms** | **$+27.5\text{ ms}$** |
| **Median Latency (p50)** | **~39.0 ms** | **~62.4 ms** | **$+23.4\text{ ms}$** |
| **95th Percentile Latency (p95)**| **~113.5 ms** | **~152.0 ms** | **$+38.5\text{ ms}$** |
| **Throughput (FPS)** | **~18.9 FPS** | **~12.4 FPS** | **$-6.5\text{ FPS}$** |

---

## 4. Engineering Trade-off & Latency Budget Analysis

1. **Trade-off Justification:** The $27.5\text{ ms}$ latency increase is directly attributable to the $5.25\times$ expansion in anchor evaluations (4,032 anchors across $P_3, P_4, P_5$ versus 768 anchors in Phase 8.4). This multi-scale coverage is the essential architectural mechanism required to resolve small `EXIT_SIGN` objects ($32 \times 32$ grid) and large `EXIT_DOOR` objects ($8 \times 8$ grid).
2. **Inspection Workflow Feasibility:** Field inspections operate at human photography cadence (1 photograph every 2-5 seconds). An average inference latency of $\approx 80.5\text{ ms}$ ($12.4\text{ FPS}$) on pure CPU comfortably satisfies real-time interactive user feedback requirements.
3. **No Production SLA Claim:** These benchmark measurements represent controlled CPU engineering observations. They do not constitute a formal contractual production Service Level Agreement (SLA). Export to ONNX Runtime with INT8 quantization or GPU acceleration will further reduce execution latency during Phase 9 deployment.
