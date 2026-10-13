# FireSphere.ai — Phase 8.8 Inference & Resource Benchmark Report

**Document Version:** `1.0.0-phase8.8`  
**Governing Phase:** Phase 8.8 Controlled Model Training & Comparative Evaluation  
**Author:** AI/ML Engineering Agent  
**Candidate Model:** `firesphere-perception-candidate-v0.8.8`  
**Baseline Control:** `firesphere-perception-baseline-v0.8.0`  
**Date:** 2026-10-09  

---

## 1. Benchmarking Environment & Protocol

To ensure 100% rigorous comparability, both the Phase 8.4 baseline and Phase 8.8 candidate were evaluated under identical execution protocols:
- **Device:** Local Host CPU (Intel Core, PyTorch 2.14.1+cpu)
- **Input Resolution:** $256 \times 256 \times 3$ RGB (float32 normalized $[0.0, 1.0]$)
- **Batch Size:** 1 (standard single-image mobile inspection workflow)
- **Benchmark Corpus:** 100 images sampled deterministically from the held-out test split
- **Warmup:** 5 warmup inferences prior to measurement
- **Metric Definitions:**
  - *Inference Latency:* Pure model forward pass (`model(tensor)`)
  - *Total Latency:* Full pipeline traversal including OpenCV image decode, color space conversion, resizing, tensor conversion, forward pass, decoding, and greedy NMS

---

## 2. Direct Comparative Latency Table

| Benchmark Metric | Phase 8.4 Baseline Control | Phase 8.8 Candidate | Delta ($\Delta$) | Operational Assessment |
| :--- | :---: | :---: | :---: | :--- |
| **Model Parameter Count** | 2,237,627 | 2,237,627 | 0 | Identical architecture |
| **Model Checkpoint Size** | 25.66 MB | 8.56 MB | -17.10 MB | **-66.6% file size** (optimized serialization) |
| **Pure Inference Latency (Mean)** | 36.32 ms | 36.50 ms | +0.18 ms | Indistinguishable (<0.5%) |
| **Pure Inference Latency (p50)** | 35.37 ms | 35.83 ms | +0.46 ms | Stable median |
| **Pure Inference Latency (p95)** | 38.45 ms | 41.08 ms | +2.63 ms | Tightly bounded variance |
| **Total Pipeline Latency (Mean)** | 54.60 ms | 55.65 ms | +1.05 ms | Low total latency |
| **Total Pipeline Latency (p50)** | 39.74 ms | 40.17 ms | +0.43 ms | Highly responsive |
| **Total Pipeline Latency (p95)** | 113.99 ms | 118.91 ms | +4.92 ms | No CPU thrashing |
| **Throughput (FPS)** | **18.32 FPS** | **17.97 FPS** | -0.35 FPS | **Real-time capable on CPU** |

---

## 3. Operational & Edge Deployment Suitability

1. **Lightweight Footprint:** At only 8.56 MB and 2.24M parameters, the candidate model is well within the threshold for on-device deployment (target: Google Play inspection app, target size < 50 MB).
2. **Real-Time Frame Rate:** With total per-image latency averaging ~55.6 ms (17.97 FPS on a standard laptop CPU), the detector comfortably satisfies the interactive inspection guidance requirement (>10 FPS).
3. **Conclusion on Hardware Viability:** Both models demonstrate excellent computational and memory efficiency; performance bottlenecks are entirely statistical and representation-driven, not computational.
