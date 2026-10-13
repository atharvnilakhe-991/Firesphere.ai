# FireSphere.ai — Computer Vision Model & Dataset Specification

**Document Version**: 0.3.0-phase3  
**Owner**: Member 4 — AI/ML, Computer Vision & AI Productivity  
**Governance Constraint**: The AI model is strictly an advisory visual detection assistant. It NEVER makes autonomous regulatory or NOC approval/rejection decisions.

---

## 1. Model Identification & Architecture

| Parameter | Specification |
| :--- | :--- |
| **Model Name** | `firesphere-inspection-cv` |
| **Model Version** | `0.3.0-phase3` |
| **Target Architecture** | Ultralytics YOLOv8 / YOLOv11 Nano/Small (Inspection Adapter) |
| **Current Baseline** | `CVModelAdapter` (Case C: Model Interface & Inference Contract) |
| **Artifact Format** | PyTorch (`.pt`), TorchScript, or ONNX (`.onnx`) |
| **Input Tensor Format** | Normalized RGB float32 `[0.0, 1.0]`, batch shape `(1, 3, 640, 640)` |
| **Input Canvas Dimensions** | $640 \times 640$ pixels (letterbox padded with neutral grey `114`) |
| **Inference Hardware Target**| NVIDIA GeForce RTX 4050 (CUDA 13.1) with automatic CPU fallback |
| **Confidence Threshold** | `0.50` (Configurable via `AI_CONFIDENCE_THRESHOLD`) |
| **NMS IoU Threshold** | `0.45` (Configurable via `AI_NMS_IOU_THRESHOLD`) |

---

## 2. Dataset Availability & Provenance

Per the Phase 3 Decision Gate inspection:
* **Training Dataset**: **NOT AVAILABLE / NOT YET ESTABLISHED**
* **Validation Dataset**: **NOT AVAILABLE / NOT YET ESTABLISHED**
* **Test / Benchmark Dataset**: **NOT AVAILABLE / NOT YET ESTABLISHED**
* **Dataset Version**: **NOT AVAILABLE**
* **Annotation Format**: Target YOLO format (`class_id x_center y_center width height` normalized `[0, 1]`)
* **Training Status**: **NOT YET TRAINED (Untrained Adapter Baseline)**
* **Validation Status**: **NOT YET VALIDATED ON REAL ANNOTATED DATA**
* **Test Status**: **NOT YET EVALUATED ON BENCHMARK**

> [!IMPORTANT]
> In accordance with Phase 3 Governance Rules, **no fake model weights or synthetic training data have been manufactured**.
> The service exposes the production-grade model interface, tensor ingestion pipeline, NMS engine, coordinate projection, and evaluation framework. Once a verified fire inspection dataset is collected and annotated, weights will be placed in the designated artifact repository.

---

## 3. Detection Class Registry

The following fire safety inspection non-compliance classes are established in the FireSphere schema. Their current operational status is explicitly recorded:

| Class ID | Class Identifier | Description | Training / Model Status |
| :--- | :--- | :--- | :--- |
| `0` | `FIRE_EXTINGUISHER_MISSING` | Vacant wall bracket or absent extinguisher | **NOT_SUPPORTED (Pending Dataset)** |
| `1` | `FIRE_EXTINGUISHER_BLOCKED` | Obstructed extinguisher station ($< 1\text{m}$ clearance) | **NOT_SUPPORTED (Pending Dataset)** |
| `2` | `FIRE_EXTINGUISHER_EXPIRED` | Expired pressure gauge or inspection tag | **NOT_SUPPORTED (Pending Dataset)** |
| `3` | `EMERGENCY_EXIT_OBSTRUCTED` | Blocked egress door, corridor, or exit stairwell | **NOT_SUPPORTED (Pending Dataset)** |
| `4` | `EXIT_SIGNAGE_MISSING` | Missing, dark, or obscured illuminated EXIT sign | **NOT_SUPPORTED (Pending Dataset)** |
| `5` | `SPRINKLER_HEAD_DEFECT` | Corroded, bent, painted, or obstructed sprinkler head | **NOT_SUPPORTED (Pending Dataset)** |
| `6` | `ELECTRICAL_PANEL_HAZARD` | Open breaker panel or combustible storage near panel | **NOT_SUPPORTED (Pending Dataset)** |
| `7` | `COMBUSTIBLE_MATERIAL_HAZARD` | Flammable goods stored in hazardous configuration | **NOT_SUPPORTED (Pending Dataset)** |
| `8` | `MANUAL_CALL_POINT_BLOCKED` | Fire alarm pull box or call point obstructed | **NOT_SUPPORTED (Pending Dataset)** |
| `9` | `HOSE_REEL_OBSTRUCTED` | Fire hose cabinet blocked or inaccessible | **NOT_SUPPORTED (Pending Dataset)** |

---

## 4. Model Artifact Strategy

1. **Storage Location**:
   * Local: `ai-service/artifacts/models/` (configured via `AI_MODEL_WEIGHTS_PATH`).
   * Remote: S3 / MinIO artifact bucket `firesphere-models/cv/` with SHA-256 integrity verification.
2. **Version Tracking**:
   * Semantic model versioning (`<major>.<minor>.<patch>-<checkpoint>`).
   * Metadata embedded in every generated finding.
3. **Artifact Discovery**:
   * On startup, the [ModelLoader](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/models/model_loader.py) checks `AI_MODEL_WEIGHTS_PATH`.
   * If weights are present, it loads the PyTorch / TorchScript / ONNX model into GPU or CPU memory.
   * If weights are absent, it operates in baseline mode, reporting `weightsLoaded=False` and preventing false detection claims.

---

## 5. Evaluation Metrics & Benchmarking

When an annotated test dataset is integrated, the [ModelEvaluator](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/app/evaluation/model_evaluator.py) provides:
* **Precision**: $TP / (TP + FP)$ per class.
* **Recall**: $TP / (TP + FN)$ per class.
* **F1 Score**: $2 \cdot (P \cdot R) / (P + R)$.
* **Mean Average Precision (mAP)**: Evaluated at $\text{IoU} = 0.50$ and $\text{IoU} = 0.50:0.95$.
* **Current Evaluation Status**: **NOT AVAILABLE (No benchmark dataset in repository)**.

---

## 6. Known Limitations

1. **No Production Weights in Phase 3**: Real inspection photographs produce `findings: []` until fine-tuned weights are trained and loaded.
2. **Synthetic Data Restriction**: Synthetic detections are strictly confined to automated integration tests (`testMode: true`) and clearly tagged with `isTestData: true`.
3. **Hardware Fallback**: Inference automatically utilizes CPU if an NVIDIA GPU/CUDA driver is not detected.
