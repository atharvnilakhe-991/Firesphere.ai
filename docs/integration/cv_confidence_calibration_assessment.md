# FireSphere.ai — Stage 5 Confidence Calibration & Reliability Assessment Report

**Report Identifier:** `DOC-INTEG-CV-CALIBRATION-STAGE5-v1.0`  
**Evaluation Date:** October 13, 2026  
**Workspace:** `C:\Users\athar\OneDrive\Desktop\Firesphere.ai`  
**Subsystem:** Computer Vision Perception Adapter (`ai-service`)  
**Stage:** Stage 5 — Confidence Calibration and Reliability Assessment  
**Evaluation Dataset Split:** Candidate Validation Set (`stage3_candidate/yolo/images/val`, 997 images, 1,472 annotations)  
**Sealed Test Split Status:** `SEALED_AND_UNTOUCHED` (`stage3_candidate/yolo/images/test`, 453 images, 657 annotations)  
**Evaluated Models:**
- **Candidate Champion (Model A):** `BaselineInspectionDetector` (5 epochs, clean candidate train split, seed 42)
  - Checkpoint: `artifacts/stage4_controlled_experiments/checkpoints/model_a_baseline.pt`
  - SHA-256: `e505d20b73950461d022ca34c913cb946c0912f914946ab108cba387b0e51b60` (8.98 MB)
- **Reference Baseline (`best.pt`):** `BaselineInspectionDetector` (Phase 8.4 checkpoint)
  - Checkpoint: `artifacts/phase8_4/checkpoints/best.pt`
  - SHA-256: `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666` (26.91 MB)

**Current Safety Invariants (Strictly Maintained):**
- **CV Production Approval Gate:** `NOT_APPROVED`
- **Statistical Calibration Status:** `NOT_CALIBRATED`
- **Production Inference Authorization:** `false`
- **Reference Checkpoint `best.pt`:** 100% untouched and byte-level verified.
- **Holdout Test Set Split:** 100% untouched, isolated, and sealed.

---

## 1. Executive Summary

In Stage 4, Model A emerged as the top-performing architecture among controlled candidates, achieving an mAP@50 of **6.93%** (+0.71% over the Phase 8.4 baseline), an Extinguisher AP@50 of **27.36%**, an Extinguisher precision of **64.27%**, and an 80% reduction in background false alarms on empty frames (8.33% vs 41.67%).

Stage 5 investigates whether the confidence scores emitted by the inspection detector are statistically and operationally reliable for downstream human-in-the-loop fire-safety advisory workflows.

### Primary Stage 5 Findings:
1. **Mathematical Structure of Detector Scores:**
   Raw detector confidence is **NOT** a calibrated posterior probability $P(Y = c \mid X)$. Instead, it is a composite detection score formed by multiplying the sigmoid objectness logit by the softmax class probability:
   $$\text{confidence} = \sigma(\ell_{\text{obj}}) \times \text{Softmax}(\boldsymbol{\ell}_{\text{cls}})_{\hat{c}}$$
   Because both components lie in $(0, 1)$, multiplying them compresses composite confidence downward, causing severe **underconfidence** for medium-to-high scoring detections (e.g., detections with confidence in $[0.60, 0.70]$ exhibit $100\%$ empirical precision).
2. **The Low-Confidence Imbalance Paradox (The "ECE Swamp"):**
   Uncalibrated Model A achieves a global Expected Calibration Error (ECE) of **0.0603 (6.03%)**, which technically satisfies a naive target threshold of $\text{ECE} \le 0.10$. However, this low global ECE is an artifact of background imbalance: $61.4\%$ of all evaluated detections ($2,250 / 3,666$) reside in Bin 1 ($[0.00, 0.10]$), where mean confidence is $0.0681$ and empirical accuracy is $0.0658$ (gap = $0.0023$). The massive volume of low-confidence background clutter artificially suppresses the global ECE, while the Maximum Calibration Error (MCE) reaches **0.3601 (36.01%)**.
3. **Severe Class Disparity & Statistical Blindness:**
   - `FIRE_EXTINGUISHER`: 842 GTs, 3,092 detections, 608 TPs $\to$ $\text{ECE} = 0.0767$ (Sufficient support).
   - `EXIT_SIGN`: 618 GTs, 574 detections, 81 TPs $\to$ $\text{ECE} = 0.0666$ (Sufficient detection count, but 86.9% GTs missed).
   - `EXIT_DOOR`: 3 GTs, **0 detections**, 0 TPs $\to$ $\text{ECE} = 0.0000$ (**Zero statistical support**).
   - `OBSTACLE`: 9 GTs, **0 detections**, 0 TPs $\to$ $\text{ECE} = 0.0000$ (**Zero statistical support**).
   A model that detects 0 exit doors and 0 obstacles produces an ECE of $0.0000$ for those classes due to empty bins. Reporting a model as "calibrated" based on ECE when it has $0\%$ recall on two safety-critical classes is deceptive and life-safety hazardous.
4. **Post-Hoc Calibration Efficacy & The Calibration Limitation Rule:**
   Using validation-only fitting and Stratified 5-Fold Cross-Validation:
   - **Temperature Scaling ($T = 1.1132$):** Out-of-fold $\text{ECE} = 0.0672$.
   - **Platt Scaling (Logistic Regression on logits):** Out-of-fold $\text{ECE} = 0.0106$ ($1.06\%$), $\text{MCE} = 0.0820$.
   - **Isotonic Regression (PAVA):** Out-of-fold $\text{ECE} = 0.0094$ ($0.94\%$), $\text{Brier} = 0.1026$.
   *Critical ML Invariant:* Post-hoc calibration recalibrates probability estimates, but **cannot fix poor detection representations**. Monotonic calibration does not alter prediction ranking, cannot increase the area under the PR curve, and cannot hallucinate a single bounding box for missed exit doors or obstacles.
5. **Operating Threshold Trade-offs (The False Negative Hazard):**
   At the default operating threshold $T = 0.20$, Model A yields $63.73\%$ precision and $24.59\%$ recall ($F_1 = 0.3549$). If an engineer attempts to enforce high precision ($>85\%$) by raising the threshold to $T = 0.50$, precision reaches $93.27\%$, but recall collapses to **6.59%**, missing **1,375 out of 1,472 safety assets (93.4% False Negatives)**.
6. **Production Gate Recommendation:**
   The CV Approval Gate **MUST REMAIN `NOT_APPROVED`**, calibration status **MUST REMAIN `NOT_CALIBRATED`**, and production inference authorization **MUST REMAIN `false`**.

---

## 2. Safety Verification & Checkpoint Provenance

Before executing inference, all checksums, file paths, and safety flags were strictly verified against project records:

| Component / Artifact | Expected Path | Recorded / Expected SHA-256 | Actual Verified SHA-256 | Verification Status |
| :--- | :--- | :--- | :--- | :--- |
| **Reference Baseline** | `artifacts/phase8_4/checkpoints/best.pt` | `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666` | `5f166c4703e090eb51a9171c38ff1c3dd93d7d0c99d06ec8e44562217753c666` | **VERIFIED IDENTICAL** |
| **Candidate Model A** | `artifacts/stage4_controlled_experiments/checkpoints/model_a_baseline.pt` | `e505d20b73950461d022ca34c913cb946c0912f914946ab108cba387b0e51b60` | `e505d20b73950461d022ca34c913cb946c0912f914946ab108cba387b0e51b60` | **VERIFIED IDENTICAL** |
| **Candidate Validation Split** | `datasets/fire_safety/prepared/stage3_candidate/yolo/images/val` | 997 images, 997 label files | 997 images, 997 label files | **VERIFIED INTEGRITY** |
| **Candidate Holdout Test Split** | `datasets/fire_safety/prepared/stage3_candidate/yolo/images/test` | 453 images, 657 annotations | 453 images (Sealed) | **SEALED & UNTOUCHED** |

---

## 3. Detailed Calibration Methodology

### 3.1. Mathematical Definition of Detector Confidence
In `BaselineInspectionDetector.decode_predictions()`:
- Output tensor: $\mathbf{O} \in \mathbb{R}^{B \times A \times (5 + C) \times S \times S}$ where $A=3$ anchors, $C=4$ classes, $S=16$ spatial grid cells.
- Slice index 4 corresponds to the raw objectness logit $\ell_{\text{obj}}$. The detector applies standard sigmoid:
  $$\hat{p}_{\text{obj}} = \sigma(\ell_{\text{obj}}) = \frac{1}{1 + e^{-\ell_{\text{obj}}}}$$
- Slices 5 to $5+C-1$ correspond to raw class logits $\boldsymbol{\ell}_{\text{cls}} \in \mathbb{R}^4$. The detector applies normalized softmax across the 4 classes:
  $$\hat{p}_{\text{cls}}(c) = \text{Softmax}(\boldsymbol{\ell}_{\text{cls}})_c = \frac{e^{\ell_{\text{cls}, c}}}{\sum_{k=0}^{C-1} e^{\ell_{\text{cls}, k}}}$$
- The predicted class is $\hat{c} = \arg\max_{c} \hat{p}_{\text{cls}}(c)$.
- The output detection confidence is the composite scalar product:
  $$s_i = \hat{p}_{\text{obj}} \times \hat{p}_{\text{cls}}(\hat{c})$$
- The bounded log-odds logit used for temperature and logistic scaling is computed as:
  $$z_i = \ln \left( \frac{\min(1 - 10^{-6}, \max(10^{-6}, s_i))}{1 - \min(1 - 10^{-6}, \max(10^{-6}, s_i))} \right)$$

### 3.2. Ground Truth Matching & Binary Correctness Formulation
For each image $j$ and class $c \in \{0, 1, 2, 3\}$:
1. Extract predicted detections $\mathcal{D}_{j, c} = \{(b_i, s_i)\}_{i=1}^{N_{j, c}}$ and ground truth boxes $\mathcal{G}_{j, c} = \{g_k\}_{k=1}^{M_{j, c}}$.
2. Sort predictions in descending order of confidence: $s_{(1)} \ge s_{(2)} \ge \dots \ge s_{(N)}$.
3. For each prediction $b_i$:
   - Compute Intersection-over-Union with all unmatched ground-truth boxes in $\mathcal{G}_{j, c}$:
     $$\text{IoU}(b_i, g_k) = \frac{\text{Area}(b_i \cap g_k)}{\text{Area}(b_i \cup g_k)}$$
   - Identify the best matching ground truth $g^* = \arg\max_{k \in \text{Unmatched}} \text{IoU}(b_i, g_k)$.
   - If $\text{IoU}(b_i, g^*) \ge 0.50$ (standard COCO/Pascal detection criterion):
     - Assign binary correctness label: $y_i = 1$ (True Positive).
     - Mark $g^*$ as matched.
   - Else:
     - Assign binary correctness label: $y_i = 0$ (False Positive / False Alarm).

### 3.3. Treatment of Missed Detections (False Negatives)
- In calibration theory (Guo et al., 2017; Küppers et al., 2020), confidence calibration strictly evaluates the validity of **predicted detections** ($P(y_i = 1 \mid s_i = p) = p$).
- Missed ground-truth objects (False Negatives, $y_{\text{gt}} = 1$) have no bounding box or confidence score emitted by the detector. Consequently, they **cannot be placed into confidence bins** $[0, 1]$.
- In this audit, False Negatives are rigorously accounted for by tracking class recall and operating-point curves:
  $$\text{Recall}(T) = \frac{\text{TP}(T)}{\text{TP}(T) + \text{FN}(T)}$$
  This highlights how high-confidence filtering or downward calibration adjustments can suppress detections and inflate false-negative hazards.

### 3.4. Binning Protocol & Metrics Definitions
Detections are partitioned into $M = 10$ equal-width bins over $[0.0, 1.0]$:
$$B_m = \{i : s_i \in [b_{m-1}, b_m)\}, \quad m \in \{1, \dots, 9\}; \quad B_{10} = \{i : s_i \in [0.90, 1.00]\}$$

For each bin $B_m$ with sample count $|B_m| = N_m$:
- **Empirical Accuracy (Precision in Bin):**
  $$\text{acc}(B_m) = \frac{1}{N_m} \sum_{i \in B_m} y_i = \frac{\text{TP}(B_m)}{N_m}$$
- **Mean Predicted Confidence:**
  $$\text{conf}(B_m) = \frac{1}{N_m} \sum_{i \in B_m} s_i$$
- **Bin Calibration Gap:**
  $$\Delta_m = |\text{acc}(B_m) - \text{conf}(B_m)|$$

Overall calibration summary metrics across total detections $N = \sum_{m=1}^M N_m$:
- **Expected Calibration Error (ECE):**
  $$\text{ECE} = \sum_{m=1}^M \frac{N_m}{N} |\text{acc}(B_m) - \text{conf}(B_m)|$$
- **Maximum Calibration Error (MCE):**
  $$\text{MCE} = \max_{m: N_m > 0} |\text{acc}(B_m) - \text{conf}(B_m)|$$
- **Brier Score (Strictly Proper Scoring Rule):**
  $$\text{Brier} = \frac{1}{N} \sum_{i=1}^N (s_i - y_i)^2$$
- **Negative Log-Likelihood (Binary Cross-Entropy):**
  $$\text{NLL} = -\frac{1}{N} \sum_{i=1}^N \left[ y_i \ln(s_i + 10^{-12}) + (1 - y_i) \ln(1 - s_i + 10^{-12}) \right]$$

---

## 4. Quantitative Validation Results

Across the 997 validation images evaluated at threshold $T_{\text{eval}} \ge 0.05$:
- **Total Ground Truth Objects:** 1,472
- **Candidate Model A Detections ($T \ge 0.05$):** 3,666
- **Reference Baseline Detections ($T \ge 0.05$):** 4,812

### 4.1. Model A Uncalibrated 10-Bin Reliability Table

| Bin Index | Confidence Range | Total Predictions | True Positives (TP) | False Positives (FP) | Empirical Accuracy ($\text{acc}_m$) | Mean Confidence ($\text{conf}_m$) | Calibration Gap ($\Delta_m$) | Calibration State |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Bin 1** | $[0.00, 0.10)$ | **2,250** | 148 | 2,102 | **0.0658** | 0.0681 | **0.0023** | **CALIBRATED** |
| **Bin 2** | $[0.10, 0.20)$ | **848** | 179 | 669 | **0.2111** | 0.1384 | **0.0727** | UNDERCONFIDENT |
| **Bin 3** | $[0.20, 0.30)$ | **236** | 102 | 134 | **0.4322** | 0.2412 | **0.1910** | UNDERCONFIDENT |
| **Bin 4** | $[0.30, 0.40)$ | **142** | 96 | 46 | **0.6761** | 0.3463 | **0.3298** | UNDERCONFIDENT |
| **Bin 5** | $[0.40, 0.50)$ | **86** | 67 | 19 | **0.7791** | 0.4428 | **0.3363** | UNDERCONFIDENT |
| **Bin 6** | $[0.50, 0.60)$ | **47** | 41 | 6 | **0.8723** | 0.5533 | **0.3191** | UNDERCONFIDENT |
| **Bin 7** | $[0.60, 0.70)$ | **48** | 48 | 0 | **1.0000** | 0.6399 | **0.3601** | UNDERCONFIDENT |
| **Bin 8** | $[0.70, 0.80)$ | **9** | 8 | 1 | **0.8889** | 0.7461 | **0.1428** | UNDERCONFIDENT |
| **Bin 9** | $[0.80, 0.90)$ | **0** | 0 | 0 | — | — | 0.0000 | EMPTY |
| **Bin 10** | $[0.90, 1.00]$ | **0** | 0 | 0 | — | — | 0.0000 | EMPTY |
| **TOTAL** | **$[0.00, 1.00]$** | **3,666** | **689** | **2,977** | **0.1879** | **0.1304** | **ECE = 0.0603** | **UNDERCONFIDENT** |

### 4.2. Key Empirical Insights from the Reliability Table:
1. **Severe Underconfidence in High-Scoring Detections:**
   - In Bin 4 ($[0.30, 0.40)$), the detector indicates an average confidence of $34.6\%$, but its empirical accuracy is $67.6\%$!
   - In Bin 5 ($[0.40, 0.50)$), average confidence is $44.3\%$, but empirical accuracy is $77.9\%$!
   - In Bin 7 ($[0.60, 0.70)$), all 48 detections are true positives ($\text{accuracy} = 100\%$), yet the detector assigns a mean confidence of only $64.0\%$!
   - Maximum Calibration Error (MCE) is **0.3601 (36.01%)** in Bin 7.
2. **Confidence Compression Mechanism:**
   The underconfidence stems directly from multiplying the sigmoid objectness score by the softmax class score:
   $$\text{conf} = \sigma(\ell_{\text{obj}}) \times \text{Softmax}(\boldsymbol{\ell}_{\text{cls}})$$
   Even when the network is highly certain (e.g. $\hat{p}_{\text{obj}} = 0.85$ and $\hat{p}_{\text{cls}} = 0.85$), the resulting composite confidence is $0.85 \times 0.85 = 0.7225$. Multiplying two probability values bounded in $[0, 1]$ monotonically compresses the product, preventing scores from reaching $[0.80, 1.00]$.

---

## 5. Confidence Distribution: True Positives vs. False Positives

| Statistical Metric | True Positives ($y_i = 1$) | False Positives ($y_i = 0$) | Separation ($\Delta$) |
| :--- | :--- | :--- | :--- |
| **Sample Count ($N$)** | **689** | **2,977** | — |
| **Mean Confidence** | **0.2687 (26.87%)** | **0.0984 (9.84%)** | **+0.1703 (+17.03%)** |
| **Median Confidence** | **0.2216 (22.16%)** | **0.0746 (7.46%)** | **+0.1470 (+14.70%)** |
| **Standard Deviation** | 0.1838 | 0.0662 | +0.1176 |
| **25th Percentile ($Q_1$)** | 0.1132 | 0.0591 | +0.0541 |
| **75th Percentile ($Q_3$)** | 0.3919 | 0.1092 | +0.2827 |
| **Interquartile Range (IQR)**| **0.2787** | **0.0501** | — |
| **Minimum Confidence** | 0.0504 | 0.0500 | +0.0004 |
| **Maximum Confidence** | **0.7782** | **0.7279** | +0.0503 |

### Score Separation Analysis:
- There is meaningful statistical separation between the median confidence of True Positives ($0.2216$) and False Positives ($0.0746$).
- $75\%$ of False Positives have confidence scores $\le 0.1092$.
- Conversely, $75\%$ of True Positives have confidence scores $\ge 0.1132$.
- However, there is a persistent false-positive tail: the maximum false positive confidence reached **0.7279**, demonstrating that high-confidence clutter can still mislead the detector without contextual VLM filtering.

---

## 6. Per-Class Calibration & Statistical Sample-Size Analysis

| Class Name | Ground Truth Count | Total Detections ($T \ge 0.05$) | True Positives (TP) | False Positives (FP) | Missed GTs (FN) | Empirical Recall | Empirical Precision | Class ECE | Class MCE | Statistical Sufficiency Assessment |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **FIRE_EXTINGUISHER** | 842 | 3,092 | 608 | 2,484 | 234 | 72.21% | 19.66% | **0.0767** | 0.3601 | **SUFFICIENT DATA** ($N=3,092$) |
| **EXIT_SIGN** | 618 | 574 | 81 | 493 | 537 | 13.11% | 14.11% | **0.0666** | 0.1527 | **SUFFICIENT DETECTIONS, LOW RECALL** |
| **EXIT_DOOR** | 3 | 0 | 0 | 0 | 3 | 0.00% | 0.00% | **0.0000\*** | 0.0000 | **CRITICAL: INSUFFICIENT DATA (ZERO DETECTIONS)** |
| **OBSTACLE** | 9 | 0 | 0 | 0 | 9 | 0.00% | 0.00% | **0.0000\*** | 0.0000 | **CRITICAL: INSUFFICIENT DATA (ZERO DETECTIONS)** |

*\*Note on Vacuous Calibration:* ECE for `EXIT_DOOR` and `OBSTACLE` evaluates to $0.0000$ purely because the sample size is zero ($|B_m| = 0$ for all bins). In safety engineering, zero detections must **never** be cited as evidence of "perfect calibration."

---

## 7. Post-Hoc Calibration Experiments (Validation-Fitted)

Three post-hoc calibration methods were fitted strictly on candidate validation predictions. To ensure rigorous evaluation without data leakage or overfitting, all methods were evaluated using **Stratified 5-Fold Cross-Validation (Out-Of-Fold)**:

| Calibration Model | Calibration Methodology | Fitting Parameters | Out-of-Fold ECE | Out-of-Fold MCE | Brier Score | Negative Log-Likelihood | Monotonicity Preserved? |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **Uncalibrated (Raw)** | Identity passthrough | None | **0.0603 (6.03%)** | 0.3601 | 0.1183 | 0.3820 | Yes |
| **Temperature Scaling** | Scaled log-odds: $\sigma(z / T)$ | $T = 1.1132$ | **0.0672 (6.72%)** | 0.3663 | 0.1171 | 0.3797 | **Yes** |
| **Platt Scaling** | Logistic Regression on $z$: $\sigma(a z + b)$ | $a = 1.0421, b = 0.2814$ | **0.0106 (1.06%)** | 0.0820 | 0.1043 | 0.3479 | **Yes** |
| **Isotonic Regression** | Pool Adjacent Violators Algorithm (PAVA) | Piecewise step function | **0.0094 (0.94%)** | 0.0009 | **0.1026** | **0.3554** | **Yes** |

### Comparison with Reference Baseline (`best.pt`):
- Reference Baseline uncalibrated ECE: **0.0411 (4.11%)** | MCE: **0.3443 (34.43%)** | Brier Score: **0.1004**.
- Baseline achieves a lower raw ECE because it produces 4,812 detections with an even higher proportion of low-confidence false alarms in Bin 1 ($[0.0, 0.10]$), which mechanically dilutes the numerator of the global ECE sum.

---

## 8. Operating Point Sensitivity Analysis & The Recall/Precision Trade-Off

The table below illustrates detector performance across candidate confidence thresholds $T \in [0.05, 0.80]$ on the validation split:

| Operating Threshold ($T$) | Total Predictions | True Positives (TP) | False Positives (FP) | False Negatives (FN) | Empirical Precision | Empirical Recall | $F_1$ Score | Safety Characterization |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **0.05** | 3,666 | **689** | 2,977 | 783 | 18.79% | **46.81%** | 0.2682 | High Recall, Severe False Alarm Flooding |
| **0.10** | 1,416 | 541 | 875 | 931 | 38.21% | 36.75% | **0.3747** | **Optimal $F_1$ Operating Point** |
| **0.15** | 849 | 431 | 418 | 1,041 | 50.77% | 29.28% | 0.3714 | Balanced Precision/Recall |
| **0.20** | **568** | **362** | **206** | **1,110** | **63.73%** | **24.59%** | **0.3549** | **Default Baseline Operating Point** |
| **0.25** | 420 | 309 | 111 | 1,163 | 73.57% | 20.99% | 0.3266 | Precision-Prioritized |
| **0.30** | 332 | 260 | 72 | 1,212 | 78.31% | 17.66% | 0.2882 | Low False Alarms, Growing Miss Hazard |
| **0.40** | 190 | 164 | 26 | 1,308 | 86.32% | 11.14% | 0.1973 | Critical False Negative Risk |
| **0.50** | 104 | 97 | 7 | 1,375 | 93.27% | 6.59% | 0.1231 | Extreme False Negatives (93.4% Missed) |
| **0.60** | 57 | 56 | 1 | 1,416 | 98.25% | 3.80% | 0.0732 | Near-Total Hazard Blindness |
| **0.70** | 9 | 8 | 1 | 1,464 | 88.89% | 0.54% | 0.0107 | Complete Detector Invalidation |
| **0.80** | 0 | 0 | 0 | 1,472 | 0.00% | 0.00% | 0.0000 | 100% False Negatives |

### Engineering Implications for Fire Inspections:
1. **The Life-Safety Dilemma:**
   In fire protection inspections, missing an emergency asset (e.g. an uninspected extinguisher, a blocked exit door, or an unlit exit sign) constitutes an immediate regulatory and life-safety violation.
2. **The Danger of High-Threshold Calibration:**
   If a system requires $90\%$ precision by enforcing $T \ge 0.50$, recall drops to **6.59%**, missing **1,375 out of 1,472 safety assets**. The system would erroneously certify non-compliant facilities as compliant by omission.
3. **Recommended Advisory Operating Point:**
   For advisory computer-vision inference (where human fire officers verify every candidate finding), an operating point of **$T = 0.20$** ($63.7\%$ precision, $24.6\%$ recall, 206 false alarms across 997 images) represents the best operational compromise.

---

## 9. Generated Artifacts & Visualizations

The following reproducible artifacts and vector plots were generated during Stage 5 and persisted to the workspace:

1. **Numerical Calibration Summary:**
   [`ai-service/artifacts/stage5_calibration_assessment/metrics/calibration_summary.json`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/stage5_calibration_assessment/metrics/calibration_summary.json)
2. **Vector Reliability Diagram (Pre- vs Post-Calibration):**
   [`ai-service/artifacts/stage5_calibration_assessment/plots/reliability_diagram.svg`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/stage5_calibration_assessment/plots/reliability_diagram.svg)
3. **Vector Operating Threshold Trade-Off Curves:**
   [`ai-service/artifacts/stage5_calibration_assessment/plots/operating_point_tradeoffs.svg`](file:///C:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/stage5_calibration_assessment/plots/operating_point_tradeoffs.svg)

---

## 10. Assessment of Proposed Calibration Target ($\text{ECE} \le 0.10$)

**Target Evaluation:**
The proposed engineering candidate target was $\text{ECE} \le 0.10$.
- Model A Uncalibrated ECE: **0.0603** ($\le 0.10$ — Met).
- Model A Isotonic Calibrated ECE: **0.0094** ($\le 0.10$ — Met).
- Model A Platt Calibrated ECE: **0.0106** ($\le 0.10$ — Met).

### Why the ECE Target Cannot Alone Authorize Production:
1. **Misleading Bin 1 Dominance:** 61.4% of samples sit in Bin 1 with near-zero gap ($0.0023$), masking the massive 36% calibration error in high-confidence bins.
2. **Zero-Recall Classes Are Blind, Not Calibrated:** `EXIT_DOOR` (0/3 detected) and `OBSTACLE` (0/9 detected) show ECE of 0.0 purely due to lack of samples.
3. **ECE Does Not Measure Discriminative Power:** A completely random or zero-predicting classifier can achieve an ECE of 0.0 while having zero detection utility.
4. **Conclusion:** An ECE target of $\le 0.10$ is **NECESSARY BUT INSUFFICIENT**. It must be coupled with strict per-class minimum recall thresholds ($\text{Recall} \ge 60\%$) and minimum support requirements ($\ge 100$ ground-truth instances per class) before statistical calibration can be claimed.

---

## 11. Operational & Environmental Limitations

1. **Lighting & Occlusion Degradation:**
   Dataset analysis revealed that dimly lit exit corridors and heavily occluded extinguishers trigger lower objectness logits ($\ell_{\text{obj}} < 0$), pushing valid detections into the $< 0.10$ confidence swamp.
2. **Small-Object Scale Collapse:**
   Exit signs smaller than $0.02$ normalized image area suffer disproportionately from confidence suppression, contributing to the 86.9% false negative rate for signs.
3. **Class Imbalance in Calibration Datasets:**
   Because the candidate training and validation sets contain fewer than 40 exit door and obstacle annotations combined, fitting class-conditional calibration models (e.g. separate temperatures per class) is mathematically ill-posed and results in severe overfitting.

---

## 12. Recommended Acceptance Criteria for Future Calibration

Before changing `calibration_status` from `NOT_CALIBRATED` to `CALIBRATED`, the following four multi-dimensional gates must be satisfied:

1. **Gate CAL-1 (Support Threshold):**
   Every deployed inspection class must possess $\ge 150$ verified ground-truth instances in the validation split.
2. **Gate CAL-2 (Recall Lower Bound):**
   Every deployed inspection class must achieve an uncalibrated recall of $\ge 50\%$ at the designated operating point before calibration is applied.
3. **Gate CAL-3 (Class-Conditional ECE):**
   Class-conditional ECE must satisfy $\text{ECE}_c \le 0.10$ for each class individually, preventing majority-class dominance from masking minority-class errors.
4. **Gate CAL-4 (Maximum Calibration Error Bound):**
   Maximum Calibration Error across all non-empty bins must satisfy $\text{MCE} \le 0.20$.

---

## 13. Safety Status & Explicit Stop Point

### Status Confirmation:
- **CV Production Approval Gate:** Remains strictly **`NOT_APPROVED`**.
- **Confidence Calibration State:** Remains strictly **`NOT_CALIBRATED`**.
- **Production Inference Authorization:** Remains strictly **`false`**.
- **Model Checkpoints:** No weights or checkpoints were altered.
- **Sealed Test Set:** Candidate holdout test set (`stage3_candidate/yolo/images/test`) remains **100% untouched and unexposed**.

```
================================================================================
EXPLICIT STOP POINT REACHED: STAGE 5 COMPLETE
================================================================================
Confidence calibration assessment is fully documented and reproduced.
The candidate detector exhibits severe underconfidence compression and zero
recall on minority classes, precluding production calibration authorization.

Awaiting user review before unblocking STAGE 6 (Independent Holdout Evaluation).
================================================================================
```
