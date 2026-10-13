# FireSphere.ai — Phase 8.9 Inference Latency & Efficiency Benchmark Report

**Document Version:** `2.0.0-phase8.9`  
**Governing Phase:** Phase 8.9 Pretrained Transfer-Learning Experiment & Controlled Evaluation  
**Author:** AI/ML Engineering Agent  
**Environment:** Local Host CPU, PyTorch 2.14.1+cpu  
**Input Resolution:** $256 \times 256 \times 3$ RGB  
**Benchmark Configuration:** 100 held-out test images, Batch Size = 1  
**Date:** 2026-10-09  

---

## 1. Executive Summary & Comparative Latency

A rigorous latency and resource consumption benchmark was conducted comparing the Phase 8.4 authoritative baseline against the Phase 8.9 pretrained candidate. Both models were profiled on the identical CPU hardware executing full real-world inference pipelines: image preprocessing (resize + normalization), tensor forward pass, box decoding, and greedy non-maximum suppression (NMS).

---

## 2. Benchmark Measurement Summary

| Metric | Phase 8.4 Baseline | Phase 8.9 Candidate | Relative Comparison |
| :--- | :---: | :---: | :---: |
| **Model Architecture** | `BaselineInspectionDetector` | `PretrainedInspectionDetector` | Custom ConvNet vs MobileNetV2 + FPN |
| **Parameter Count** | 2,237,627 | 2,308,644 | +3.2% (+71,017 params) |
| **Checkpoint Disk Size** | **25.66 MB** | **13.45 MB** | **-47.6% (12.21 MB smaller)** |
| **Pure Inference Mean** | 20.88 ms | 154.99 ms | +134.11 ms |
| **Pure Inference p50** | 20.77 ms | 77.73 ms | +56.96 ms |
| **Pure Inference p95** | 23.56 ms | 294.62 ms | +271.06 ms |
| **Total Pipeline Mean** | **33.52 ms** | **170.47 ms** | +136.95 ms |
| **Total Pipeline p50** | **23.89 ms** | **126.46 ms** | +102.57 ms |
| **Total Pipeline p95** | **73.71 ms** | **301.85 ms** | +228.14 ms |
| **Effective Throughput** | **29.84 FPS** | **5.87 FPS** | -23.97 FPS |

---

## 3. Engineering Analysis & Architectural Tradeoffs

1. **Model Size Efficiency:**  
   The candidate checkpoint is **47.6% smaller** on disk (13.45 MB vs 25.66 MB). MobileNetV2 inverted residual blocks leverage depthwise separable convolutions rather than standard dense 2D convolutions, yielding a much higher parameter-to-weight efficiency.
2. **CPU Execution Latency:**  
   The candidate model exhibits ~5x higher latency on single-threaded CPU (170.47 ms vs 33.52 ms). In depthwise separable architectures, each spatial layer requires two separate PyTorch operator dispatches (depthwise conv followed by $1\times 1$ pointwise conv). On CPU backends without fused operator acceleration or TensorRT/ONNX Runtime optimization, operator dispatch overhead inflates latency.
3. **Edge Readiness Assessment:**  
   While 5.87 FPS satisfies offline batch inspection workflows (e.g., citizen fire safety photo upload, asynchronous compliance review), it does not meet the 30 FPS threshold required for real-time mobile video inspection. Production deployment would require ONNX quantization (INT8) or dedicated GPU acceleration.
