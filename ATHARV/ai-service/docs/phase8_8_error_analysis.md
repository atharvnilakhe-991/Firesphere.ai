# FireSphere.ai — Phase 8.8 Visual Error Analysis Report

**Document Version:** `1.0.0-phase8.8`  
**Governing Phase:** Phase 8.8 Controlled Model Training & Comparative Evaluation  
**Author:** AI/ML Engineering Agent  
**Candidate Model:** `firesphere-perception-candidate-v0.8.8`  
**Date:** 2026-10-09  

---

## 1. Executive Summary & Methodology

Visual failure analysis was conducted by auditing prediction overlays and ground-truth bounding boxes across test images from the held-out Phase 8.3 test set.

Saved visual artifacts are stored in:
- True Positives: [`ai-service/artifacts/phase8_8/visualizations/tp/`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/phase8_8/visualizations/tp)
- False Positives: [`ai-service/artifacts/phase8_8/visualizations/fp/`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/phase8_8/visualizations/fp)
- False Negatives: [`ai-service/artifacts/phase8_8/visualizations/fn/`](file:///c:/Users/athar/OneDrive/Desktop/Firesphere.ai/ai-service/artifacts/phase8_8/visualizations/fn)

---

## 2. Failure Mode Taxonomy

### Failure Mode 1: Extreme Aspect Ratio Anchor Quantization (`EXIT_DOOR`)
- **Visual Manifestation:** Full-height doors in corridors occupy an aspect ratio (height / width) ranging from $3.5$ to $8.0$.
- **Mechanism:** In `BaselineInspectionDetector`, Anchor 2 has width $0.55$ and height $0.65$ (aspect ratio $1.18$). When a door box has width $0.15$ and height $0.85$, the log-ratio regression targets are:
  $$t_w = \ln(0.15 / 0.55) \approx -1.30, \quad t_h = \ln(0.85 / 0.65) \approx +0.27$$
  Because the network outputs are clamped to $[-3.0, 3.0]$ and pooled on a stride-16 grid, horizontal localization error by just 1 grid cell ($16\text{px}$) shifts the predicted box sufficiently that $\text{IoU}$ drops below the $0.50$ threshold.
- **Visual Evidence:** Predicted door boxes encompass the door vertical span but miss the horizontal margin, resulting in IoU values between $0.25$ and $0.42$ (counted as False Negatives under standard COCO evaluation).

### Failure Mode 2: Sub-Feature Grid Collapse (`EXIT_SIGN`)
- **Visual Manifestation:** Directional exit signs mounted above doors or suspended from high ceilings occupy areas smaller than $0.01$ (e.g. $18 \times 12$ pixels on a $256 \times 256$ input).
- **Mechanism:** At stride 16, an $18 \times 12$ pixel sign maps to approximately $1.1 \times 0.75$ feature cells. Information about green running-man icons or red lettering is obliterated by the receptive field pooling of 4 consecutive convolutional stages.
- **Mitigation Demonstrated:** As shown in the threshold sweep, lowering $\tau$ to $0.05$ recovers recall to $13.2\%$ on large illuminated signs, but small signs require higher input resolution ($512\times 512$) or multi-scale lateral connections (P3 head).

### Failure Mode 3: Semantic Distraction on Red Indoor Fixtures (`FIRE_EXTINGUISHER`)
- **Visual Manifestation:** Red fire alarm pull stations, fire hose reels, and red exit signs generate false positive detections for `FIRE_EXTINGUISHER`.
- **Mechanism:** In the absence of pre-trained semantic weights, the network's early filters rely heavily on red chrominance and vertical rectangular edge contrast.

### Failure Mode 4: Open-Vocabulary Geometric Diversity (`OBSTACLE`)
- **Visual Manifestation:** Ground-truth obstacles include cardboard shipping cartons, wooden pallets, metal utility carts, and discarded office chairs.
- **Mechanism:** Without pre-trained representations, 57 training boxes across 30 scenes cannot span the structural variance of random physical clutter.

---

## 3. Summary of Visual Audit Counts

- **True Positives Generated:** 3 verified detections (clear extinguisher cylinders and large illuminated exit signs).
- **False Positives Generated:** 10 samples (red wall signs, fire alarm boxes, door frame margins).
- **False Negatives Generated:** 10 samples (distant exit signs, obstructed door portals, cardboard boxes).
