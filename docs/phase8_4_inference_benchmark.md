# FireSphere.ai — Phase 8.4 Inference Benchmark Report

**Document Version:** `1.0.0-phase8.4`  
**Model Identifier:** `firesphere-perception-baseline-v0.8.0`  
**Target Hardware:** Intel CPU (Windows 11)  
**Execution Timestamp:** `2026-10-08T13:05:11Z`  
**Benchmarking Protocol:** 10 Warmup Iterations + 100 Timed Evaluation Runs  

---

## 1. Benchmarking Testbed

| Parameter | Value |
| :--- | :--- |
| **Processor** | Intel64 Family 6 Model 186 Stepping 2, GenuineIntel |
| **OS** | Windows 11 (64-bit AMD64) |
| **Execution Device** | `cpu` |
| **PyTorch Mode** | `torch.inference_mode()` |
| **Input Dimensions** | $256 \times 256 \times 3$ RGB float32 |
| **Batch Size** | 1 (Single-image real-time inspection simulation) |
| **Samples Benchmarked** | 100 test images |

---

## 2. Latency Breakdown

| Phase | Average Duration (ms) | Percentage of Total Latency |
| :--- | :---: | :---: |
| **Model Cold Load Time** | 50.42 ms | (One-time startup overhead) |
| **Preprocessing (Read + Resize + Float Tensor)** | 17.86 ms | 33.7% |
| **Neural Forward Inference** | 12.64 ms | 23.8% |
| **Postprocessing (NMS + Decode + Filtering)** | 22.49 ms | 42.5% |
| **Total Per-Image Latency** | **53.00 ms** | **100.0%** |

---

## 3. Statistical Distribution & Throughput

- **Average Latency:** **53.00 ms** per image
- **Median Latency (p50):** **39.03 ms** per image
- **95th Percentile Latency (p95):** **113.50 ms** per image
- **Throughput (FPS):** **18.87 images / second** on CPU

---

## 4. Engineering Disclaimer

> [!NOTE]
> These measurements represent engineering baseline observations on CPU hardware. They do not constitute a formal contractual production Service Level Agreement (SLA). GPU deployment or ONNX Runtime export will further decrease latency.
