"""
FireSphere.ai — Phase 8.5 Multi-Scale Feature Pyramid Network Perception Detector
Native PyTorch Multi-Scale FPN architecture designed for small signs (P3),
medium extinguishers (P4), and large doors/obstacles (P5).
"""

from typing import Any, Dict, List, Optional, Tuple
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

from app.models.baseline_detector import CLASS_NAMES, CLASS_IDS

# Multi-Scale Anchors per Pyramid Level [width, height] normalized in [0, 1]
PYRAMID_ANCHORS = {
    "P3": [  # Stride 8 (32x32) - Small Objects (Exit signs, compact cylinders)
        [0.03, 0.03],
        [0.06, 0.06],
        [0.08, 0.12],
    ],
    "P4": [  # Stride 16 (16x16) - Medium Objects (Extinguishers, medium clutter)
        [0.12, 0.25],
        [0.20, 0.40],
        [0.30, 0.30],
    ],
    "P5": [  # Stride 32 (8x8) - Large Objects (Full-height exit doors, large obstacles)
        [0.40, 0.65],
        [0.55, 0.85],
        [0.80, 0.90],
    ],
}


class ConvBNAct(nn.Module):
    """Standard Conv2D + BatchNorm + SiLU block."""

    def __init__(self, in_ch: int, out_ch: int, kernel_size: int = 3, stride: int = 1, padding: int = 1):
        super().__init__()
        self.conv = nn.Conv2d(in_ch, out_ch, kernel_size=kernel_size, stride=stride, padding=padding, bias=False)
        self.bn = nn.BatchNorm2d(out_ch)
        self.act = nn.SiLU(inplace=True)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.act(self.bn(self.conv(x)))


class ResidualBlock(nn.Module):
    """Residual convolutional bottleneck."""

    def __init__(self, channels: int):
        super().__init__()
        self.conv1 = ConvBNAct(channels, channels, kernel_size=3, padding=1)
        self.conv2 = nn.Conv2d(channels, channels, kernel_size=3, padding=1, bias=False)
        self.bn2 = nn.BatchNorm2d(channels)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        res = x
        out = self.bn2(self.conv2(self.conv1(x)))
        return F.silu(out + res)


class MultiScaleInspectionDetector(nn.Module):
    """Native PyTorch Multi-Scale Feature Pyramid Object Detector for FireSphere."""

    def __init__(
        self,
        num_classes: int = 4,
        pyramid_anchors: Optional[Dict[str, List[List[float]]]] = None,
        fpn_channels: int = 96,
    ):
        super().__init__()
        self.num_classes = num_classes
        self.pyramid_anchors = pyramid_anchors or PYRAMID_ANCHORS
        self.fpn_channels = fpn_channels

        # --- Backbone ---
        # Stem: 256x256 -> 128x128
        self.stem = ConvBNAct(3, 32, kernel_size=3, stride=2, padding=1)

        # Stage 1: 128x128 -> 64x64
        self.stage1 = nn.Sequential(
            ConvBNAct(32, 64, kernel_size=3, stride=2, padding=1),
            ResidualBlock(64),
        )

        # Stage 2 (C3): 64x64 -> 32x32 (Stride 8)
        self.stage2 = nn.Sequential(
            ConvBNAct(64, 64, kernel_size=3, stride=2, padding=1),
            ResidualBlock(64),
        )

        # Stage 3 (C4): 32x32 -> 16x16 (Stride 16)
        self.stage3 = nn.Sequential(
            ConvBNAct(64, 128, kernel_size=3, stride=2, padding=1),
            ResidualBlock(128),
        )

        # Stage 4 (C5): 16x16 -> 8x8 (Stride 32)
        self.stage4 = nn.Sequential(
            ConvBNAct(128, 256, kernel_size=3, stride=2, padding=1),
            ResidualBlock(256),
        )

        # --- FPN Neck ---
        # Lateral 1x1 convs
        self.lat5 = nn.Conv2d(256, fpn_channels, kernel_size=1)
        self.lat4 = nn.Conv2d(128, fpn_channels, kernel_size=1)
        self.lat3 = nn.Conv2d(64, fpn_channels, kernel_size=1)

        # Smooth 3x3 convs
        self.smooth5 = ConvBNAct(fpn_channels, fpn_channels, kernel_size=3, padding=1)
        self.smooth4 = ConvBNAct(fpn_channels, fpn_channels, kernel_size=3, padding=1)
        self.smooth3 = ConvBNAct(fpn_channels, fpn_channels, kernel_size=3, padding=1)

        # --- Detection Heads ---
        # Each anchor predicts 5 + num_classes parameters: [tx, ty, tw, th, obj, c0, c1, c2, c3]
        out_per_anchor = 5 + num_classes
        self.head3 = nn.Conv2d(fpn_channels, len(self.pyramid_anchors["P3"]) * out_per_anchor, kernel_size=1)
        self.head4 = nn.Conv2d(fpn_channels, len(self.pyramid_anchors["P4"]) * out_per_anchor, kernel_size=1)
        self.head5 = nn.Conv2d(fpn_channels, len(self.pyramid_anchors["P5"]) * out_per_anchor, kernel_size=1)

    def forward(self, x: torch.Tensor) -> Dict[str, torch.Tensor]:
        """Forward pass.
        Returns:
            Dict with keys 'P3', 'P4', 'P5', each shape (B, num_anchors, 5+C, S, S)
        """
        b = x.size(0)

        # Backbone bottom-up
        x_stem = self.stem(x)
        c2 = self.stage1(x_stem)
        c3 = self.stage2(c2)    # (B, 64, 32, 32)
        c4 = self.stage3(c3)    # (B, 128, 16, 16)
        c5 = self.stage4(c4)    # (B, 256, 8, 8)

        # FPN top-down
        p5 = self.smooth5(self.lat5(c5))                                       # (B, 96, 8, 8)
        p4 = self.smooth4(self.lat4(c4) + F.interpolate(p5, size=c4.shape[-2:], mode="nearest"))  # (B, 96, 16, 16)
        p3 = self.smooth3(self.lat3(c3) + F.interpolate(p4, size=c3.shape[-2:], mode="nearest"))  # (B, 96, 32, 32)

        # Heads
        out3 = self.head3(p3)  # (B, A*ch, 32, 32)
        out4 = self.head4(p4)  # (B, A*ch, 16, 16)
        out5 = self.head5(p5)  # (B, A*ch, 8, 8)

        out_ch = 5 + self.num_classes
        a3 = len(self.pyramid_anchors["P3"])
        a4 = len(self.pyramid_anchors["P4"])
        a5 = len(self.pyramid_anchors["P5"])

        return {
            "P3": out3.view(b, a3, out_ch, out3.size(-2), out3.size(-1)),
            "P4": out4.view(b, a4, out_ch, out4.size(-2), out4.size(-1)),
            "P5": out5.view(b, a5, out_ch, out5.size(-2), out5.size(-1)),
        }

    def compute_loss(
        self,
        pyramid_outputs: Dict[str, torch.Tensor],
        targets: List[torch.Tensor],
        device: torch.device,
    ) -> Tuple[torch.Tensor, Dict[str, float]]:
        """Multi-scale loss calculation assigned across P3, P4, P5 by bounding-box area."""
        total_loss = torch.tensor(0.0, device=device)
        total_box_loss = torch.tensor(0.0, device=device)
        total_obj_loss = torch.tensor(0.0, device=device)
        total_cls_loss = torch.tensor(0.0, device=device)
        total_pos = 0

        # Scale assignment thresholds by area
        # Small (< 0.02) -> P3 (32x32)
        # Medium (0.02 - 0.15) -> P4 (16x16)
        # Large (> 0.15) -> P5 (8x8)
        def assign_level(area: float) -> str:
            if area < 0.02:
                return "P3"
            elif area <= 0.15:
                return "P4"
            else:
                return "P5"

        b = next(iter(pyramid_outputs.values())).size(0)

        for level_name, outputs in pyramid_outputs.items():
            _, num_anchors, out_ch, s, _ = outputs.shape
            anchors = self.pyramid_anchors[level_name]

            pred_tx = outputs[:, :, 0, :, :]
            pred_ty = outputs[:, :, 1, :, :]
            pred_tw = outputs[:, :, 2, :, :]
            pred_th = outputs[:, :, 3, :, :]
            pred_obj = outputs[:, :, 4, :, :]
            pred_cls = outputs[:, :, 5:, :, :]

            target_obj = torch.zeros((b, num_anchors, s, s), device=device, dtype=torch.float32)
            lvl_box_loss = torch.tensor(0.0, device=device)
            lvl_cls_loss = torch.tensor(0.0, device=device)
            lvl_pos = 0

            for b_idx in range(b):
                gt_boxes = targets[b_idx]
                if len(gt_boxes) == 0:
                    continue

                for box in gt_boxes:
                    cid = int(box[0].item())
                    xc = box[1].item()
                    yc = box[2].item()
                    w = box[3].item()
                    h = box[4].item()
                    area = w * h

                    if assign_level(area) != level_name:
                        continue

                    col = min(int(xc * s), s - 1)
                    row = min(int(yc * s), s - 1)

                    box_wh = np.array([w, h])
                    anc_wh = np.array(anchors)
                    diffs = np.sum(np.abs(np.log(np.maximum(box_wh, 1e-4) / np.maximum(anc_wh, 1e-4))), axis=1)
                    best_a = int(np.argmin(diffs))

                    target_obj[b_idx, best_a, row, col] = 1.0

                    gt_tx = max(1e-4, min(1.0 - 1e-4, xc * s - col))
                    gt_ty = max(1e-4, min(1.0 - 1e-4, yc * s - row))
                    gt_logit_tx = np.log(gt_tx / (1.0 - gt_tx))
                    gt_logit_ty = np.log(gt_ty / (1.0 - gt_ty))

                    anc_w, anc_h = anchors[best_a]
                    gt_tw = np.log(max(w, 1e-4) / anc_w)
                    gt_th = np.log(max(h, 1e-4) / anc_h)

                    target_coord = torch.tensor([gt_logit_tx, gt_logit_ty, gt_tw, gt_th], device=device, dtype=torch.float32)
                    pred_coord = torch.stack([
                        pred_tx[b_idx, best_a, row, col],
                        pred_ty[b_idx, best_a, row, col],
                        pred_tw[b_idx, best_a, row, col],
                        pred_th[b_idx, best_a, row, col],
                    ])

                    lvl_box_loss = lvl_box_loss + F.smooth_l1_loss(pred_coord, target_coord)
                    cls_pred = pred_cls[b_idx, best_a, :, row, col].unsqueeze(0)
                    lvl_cls_loss = lvl_cls_loss + F.cross_entropy(cls_pred, torch.tensor([cid], device=device, dtype=torch.long))
                    lvl_pos += 1

            pos_weight = torch.tensor([2.0], device=device)
            lvl_obj_loss = F.binary_cross_entropy_with_logits(pred_obj, target_obj, pos_weight=pos_weight)

            if lvl_pos > 0:
                lvl_box_loss = lvl_box_loss / lvl_pos
                lvl_cls_loss = lvl_cls_loss / lvl_pos

            sub_total = 2.0 * lvl_box_loss + 1.0 * lvl_obj_loss + 1.5 * lvl_cls_loss
            total_loss = total_loss + sub_total
            total_box_loss = total_box_loss + lvl_box_loss
            total_obj_loss = total_obj_loss + lvl_obj_loss
            total_cls_loss = total_cls_loss + lvl_cls_loss
            total_pos += lvl_pos

        metrics = {
            "total_loss": round(total_loss.item(), 5),
            "box_loss": round(total_box_loss.item(), 5),
            "obj_loss": round(total_obj_loss.item(), 5),
            "cls_loss": round(total_cls_loss.item(), 5),
            "pos_targets": total_pos,
        }
        return total_loss, metrics

    def decode_predictions(
        self,
        pyramid_outputs: Dict[str, torch.Tensor],
        conf_threshold: float = 0.15,
        iou_threshold: float = 0.45,
    ) -> List[List[Dict[str, Any]]]:
        """Decodes multi-scale pyramid detections and applies NMS across all levels."""
        from app.models.nms import apply_non_maximum_suppression

        b = next(iter(pyramid_outputs.values())).size(0)
        batch_candidates: List[List[Dict[str, Any]]] = [[] for _ in range(b)]

        for level_name, output in pyramid_outputs.items():
            _, num_anchors, _, s, _ = output.shape
            anchors = self.pyramid_anchors[level_name]
            device = output.device

            pred_tx = torch.sigmoid(output[:, :, 0, :, :])
            pred_ty = torch.sigmoid(output[:, :, 1, :, :])
            pred_tw = torch.clamp(output[:, :, 2, :, :], -3.0, 3.0)
            pred_th = torch.clamp(output[:, :, 3, :, :], -3.0, 3.0)
            pred_obj = torch.sigmoid(output[:, :, 4, :, :])
            pred_cls_probs = F.softmax(output[:, :, 5:, :, :], dim=2)

            col_grid = torch.arange(s, device=device).view(1, 1, 1, s).expand(b, num_anchors, s, s)
            row_grid = torch.arange(s, device=device).view(1, 1, s, 1).expand(b, num_anchors, s, s)

            pred_xc = (col_grid + pred_tx) / float(s)
            pred_yc = (row_grid + pred_ty) / float(s)

            anchor_tensor = torch.tensor(anchors, device=device, dtype=torch.float32)
            anc_w = anchor_tensor[:, 0].view(1, num_anchors, 1, 1)
            anc_h = anchor_tensor[:, 1].view(1, num_anchors, 1, 1)

            pred_w = torch.clamp(anc_w * torch.exp(pred_tw), 0.005, 1.0)
            pred_h = torch.clamp(anc_h * torch.exp(pred_th), 0.005, 1.0)

            for b_idx in range(b):
                for a_idx in range(num_anchors):
                    for r in range(s):
                        for c in range(s):
                            obj_conf = float(pred_obj[b_idx, a_idx, r, c].item())
                            cls_probs = pred_cls_probs[b_idx, a_idx, :, r, c].detach().cpu().numpy()
                            best_cid = int(np.argmax(cls_probs))
                            best_cls_prob = float(cls_probs[best_cid])
                            confidence = obj_conf * best_cls_prob

                            if confidence >= conf_threshold:
                                xc = float(pred_xc[b_idx, a_idx, r, c].item())
                                yc = float(pred_yc[b_idx, a_idx, r, c].item())
                                w = float(pred_w[b_idx, a_idx, r, c].item())
                                h = float(pred_h[b_idx, a_idx, r, c].item())

                                xmin = max(0.0, xc - w / 2.0)
                                ymin = max(0.0, yc - h / 2.0)
                                xmax = min(1.0, xc + w / 2.0)
                                ymax = min(1.0, yc + h / 2.0)

                                if xmax > xmin and ymax > ymin:
                                    batch_candidates[b_idx].append({
                                        "classId": best_cid,
                                        "className": CLASS_NAMES.get(best_cid, "UNKNOWN"),
                                        "confidence": round(confidence, 4),
                                        "boundingBox": {
                                            "xMin": round(xmin, 4),
                                            "yMin": round(ymin, 4),
                                            "xMax": round(xmax, 4),
                                            "yMax": round(ymax, 4),
                                        },
                                        "level": level_name,
                                    })

        # Apply multi-class NMS
        final_results = []
        for b_idx in range(b):
            filtered = apply_non_maximum_suppression(batch_candidates[b_idx], iou_threshold=iou_threshold)
            final_results.append(filtered)

        return final_results
