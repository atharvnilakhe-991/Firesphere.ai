"""
FireSphere.ai — Phase 8.4 Baseline 4-Class Perception Detector
Native PyTorch lightweight object detection model architecture.
Frozen Perception Vocabulary:
0: FIRE_EXTINGUISHER
1: EXIT_DOOR
2: EXIT_SIGN
3: OBSTACLE
"""

from typing import Any, Dict, List, Optional, Tuple
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

CLASS_NAMES = {
    0: "FIRE_EXTINGUISHER",
    1: "EXIT_DOOR",
    2: "EXIT_SIGN",
    3: "OBSTACLE",
}

CLASS_IDS = {v: k for k, v in CLASS_NAMES.items()}

# Default Anchor Scales [width, height] normalized in [0, 1]
DEFAULT_ANCHORS = [
    [0.08, 0.08],  # Anchor 0: Small (signs, compact extinguishers)
    [0.22, 0.35],  # Anchor 1: Medium (extinguishers, obstacles)
    [0.55, 0.65],  # Anchor 2: Large (doors, large scene objects)
]


class ResidualBlock(nn.Module):
    """Residual convolutional block with SiLU activation."""

    def __init__(self, channels: int):
        super().__init__()
        self.conv1 = nn.Conv2d(channels, channels, kernel_size=3, padding=1, bias=False)
        self.bn1 = nn.BatchNorm2d(channels)
        self.conv2 = nn.Conv2d(channels, channels, kernel_size=3, padding=1, bias=False)
        self.bn2 = nn.BatchNorm2d(channels)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        res = x
        out = F.silu(self.bn1(self.conv1(x)))
        out = self.bn2(self.conv2(out))
        return F.silu(out + res)


class BaselineInspectionDetector(nn.Module):
    """Native PyTorch 4-class object detection neural network for FireSphere baseline."""

    def __init__(
        self,
        num_classes: int = 4,
        anchors: Optional[List[List[float]]] = None,
        input_size: Tuple[int, int] = (256, 256),
    ):
        super().__init__()
        self.num_classes = num_classes
        self.anchors = anchors or DEFAULT_ANCHORS
        self.num_anchors = len(self.anchors)
        self.input_size = input_size

        # Backbone: Lightweight CNN
        # Stem: 256x256 -> 128x128
        self.stem = nn.Sequential(
            nn.Conv2d(3, 32, kernel_size=3, stride=2, padding=1, bias=False),
            nn.BatchNorm2d(32),
            nn.SiLU(inplace=True),
        )

        # Stage 1: 128x128 -> 64x64
        self.stage1 = nn.Sequential(
            nn.Conv2d(32, 64, kernel_size=3, stride=2, padding=1, bias=False),
            nn.BatchNorm2d(64),
            nn.SiLU(inplace=True),
            ResidualBlock(64),
        )

        # Stage 2: 64x64 -> 32x32
        self.stage2 = nn.Sequential(
            nn.Conv2d(64, 128, kernel_size=3, stride=2, padding=1, bias=False),
            nn.BatchNorm2d(128),
            nn.SiLU(inplace=True),
            ResidualBlock(128),
        )

        # Stage 3: 32x32 -> 16x16
        self.stage3 = nn.Sequential(
            nn.Conv2d(128, 256, kernel_size=3, stride=2, padding=1, bias=False),
            nn.BatchNorm2d(256),
            nn.SiLU(inplace=True),
            ResidualBlock(256),
        )

        # Neck & Head (Detection map at 16x16)
        self.neck = nn.Sequential(
            nn.Conv2d(256, 128, kernel_size=3, padding=1, bias=False),
            nn.BatchNorm2d(128),
            nn.SiLU(inplace=True),
        )

        # Output channels = num_anchors * (5 + num_classes)
        # 5 = [tx, ty, tw, th, obj]
        self.out_channels_per_anchor = 5 + self.num_classes
        self.head = nn.Conv2d(
            128,
            self.num_anchors * self.out_channels_per_anchor,
            kernel_size=1,
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """Forward pass.
        Args:
            x: Tensor of shape (B, 3, H, W)
        Returns:
            Tensor of shape (B, num_anchors, out_channels_per_anchor, S, S)
        """
        b = x.size(0)
        feat = self.stem(x)
        feat = self.stage1(feat)
        feat = self.stage2(feat)
        feat = self.stage3(feat)
        neck_feat = self.neck(feat)
        out = self.head(neck_feat)  # (B, num_anchors * out_ch, S, S)

        s = out.size(-1)
        out = out.view(b, self.num_anchors, self.out_channels_per_anchor, s, s)
        return out

    def compute_loss(
        self,
        outputs: torch.Tensor,
        targets: List[torch.Tensor],
        device: torch.device,
    ) -> Tuple[torch.Tensor, Dict[str, float]]:
        """Computes multi-task detection loss: box regression, objectness, and classification.
        Args:
            outputs: (B, num_anchors, 5 + C, S, S)
            targets: List of Tensors for each batch item, shape (N_boxes, 5) -> [class_id, xc, yc, w, h]
            device: execution device
        """
        b, num_anchors, out_ch, s, _ = outputs.shape
        anchor_tensor = torch.tensor(self.anchors, device=device, dtype=torch.float32)  # (A, 2)

        # Separate predicted components
        pred_tx = outputs[:, :, 0, :, :]
        pred_ty = outputs[:, :, 1, :, :]
        pred_tw = outputs[:, :, 2, :, :]
        pred_th = outputs[:, :, 3, :, :]
        pred_obj = outputs[:, :, 4, :, :]
        pred_cls = outputs[:, :, 5:, :, :]  # (B, A, C, S, S)

        # Create target tensors
        target_obj = torch.zeros((b, num_anchors, s, s), device=device, dtype=torch.float32)
        box_loss = torch.tensor(0.0, device=device)
        cls_loss = torch.tensor(0.0, device=device)
        num_pos = 0

        for b_idx in range(b):
            gt_boxes = targets[b_idx]
            if len(gt_boxes) == 0:
                continue

            for box in gt_boxes:
                cid, xc, yc, w, h = int(box[0].item()), box[1].item(), box[2].item(), box[3].item(), box[4].item()
                if cid >= self.num_classes:
                    continue

                col = min(int(xc * s), s - 1)
                row = min(int(yc * s), s - 1)

                # Match best anchor by area difference
                box_wh = np.array([w, h])
                anchor_wh = np.array(self.anchors)
                # Compute aspect ratio similarity
                diffs = np.sum(np.abs(np.log(np.maximum(box_wh, 1e-4) / np.maximum(anchor_wh, 1e-4))), axis=1)
                best_anchor = int(np.argmin(diffs))

                target_obj[b_idx, best_anchor, row, col] = 1.0

                # Target coordinates in cell space
                gt_tx = xc * s - col
                gt_ty = yc * s - row
                # Clamp to avoid numerical issues
                gt_tx = max(1e-4, min(1.0 - 1e-4, gt_tx))
                gt_ty = max(1e-4, min(1.0 - 1e-4, gt_ty))
                gt_logit_tx = np.log(gt_tx / (1.0 - gt_tx))
                gt_logit_ty = np.log(gt_ty / (1.0 - gt_ty))

                anc_w, anc_h = self.anchors[best_anchor]
                gt_tw = np.log(max(w, 1e-4) / anc_w)
                gt_th = np.log(max(h, 1e-4) / anc_h)

                target_coord = torch.tensor(
                    [gt_logit_tx, gt_logit_ty, gt_tw, gt_th],
                    device=device,
                    dtype=torch.float32,
                )
                pred_coord = torch.stack([
                    pred_tx[b_idx, best_anchor, row, col],
                    pred_ty[b_idx, best_anchor, row, col],
                    pred_tw[b_idx, best_anchor, row, col],
                    pred_th[b_idx, best_anchor, row, col],
                ])

                box_loss = box_loss + F.smooth_l1_loss(pred_coord, target_coord)

                # Classification target
                pred_class_logits = pred_cls[b_idx, best_anchor, :, row, col].unsqueeze(0)
                target_class_tensor = torch.tensor([cid], device=device, dtype=torch.long)
                cls_loss = cls_loss + F.cross_entropy(pred_class_logits, target_class_tensor)

                num_pos += 1

        # Objectness loss with negative downweighting (0.5 for non-object)
        pos_weight = torch.tensor([2.0], device=device)
        obj_loss = F.binary_cross_entropy_with_logits(
            pred_obj,
            target_obj,
            pos_weight=pos_weight,
        )

        if num_pos > 0:
            box_loss = box_loss / num_pos
            cls_loss = cls_loss / num_pos

        total_loss = 2.0 * box_loss + 1.0 * obj_loss + 1.5 * cls_loss

        metrics = {
            "total_loss": round(total_loss.item(), 5),
            "box_loss": round(box_loss.item(), 5),
            "obj_loss": round(obj_loss.item(), 5),
            "cls_loss": round(cls_loss.item(), 5),
            "pos_targets": num_pos,
        }
        return total_loss, metrics

    def decode_predictions(
        self,
        output: torch.Tensor,
        conf_threshold: float = 0.20,
        iou_threshold: float = 0.45,
    ) -> List[List[Dict[str, Any]]]:
        """Decodes model output tensor into list of detections per batch image.
        Args:
            output: (B, num_anchors, 5 + C, S, S)
            conf_threshold: minimum confidence filter
            iou_threshold: NMS overlap threshold
        Returns:
            List of detection lists for each batch item.
        """
        b, num_anchors, _, s, _ = output.shape
        batch_results: List[List[Dict[str, Any]]] = []

        device = output.device
        pred_tx = torch.sigmoid(output[:, :, 0, :, :])  # (B, A, S, S)
        pred_ty = torch.sigmoid(output[:, :, 1, :, :])
        pred_tw = torch.clamp(output[:, :, 2, :, :], -3.0, 3.0)
        pred_th = torch.clamp(output[:, :, 3, :, :], -3.0, 3.0)
        pred_obj = torch.sigmoid(output[:, :, 4, :, :])
        pred_cls_logits = output[:, :, 5:, :, :]  # (B, A, C, S, S)
        pred_cls_probs = F.softmax(pred_cls_logits, dim=2)

        # Coordinate grid
        col_grid = torch.arange(s, device=device).view(1, 1, 1, s).expand(b, num_anchors, s, s)
        row_grid = torch.arange(s, device=device).view(1, 1, s, 1).expand(b, num_anchors, s, s)

        # Center coordinates
        pred_xc = (col_grid + pred_tx) / float(s)
        pred_yc = (row_grid + pred_ty) / float(s)

        # Anchor sizes
        anchor_tensor = torch.tensor(self.anchors, device=device, dtype=torch.float32)
        anc_w = anchor_tensor[:, 0].view(1, num_anchors, 1, 1)
        anc_h = anchor_tensor[:, 1].view(1, num_anchors, 1, 1)

        pred_w = torch.clamp(anc_w * torch.exp(pred_tw), 0.005, 1.0)
        pred_h = torch.clamp(anc_h * torch.exp(pred_th), 0.005, 1.0)

        for b_idx in range(b):
            image_detections: List[Dict[str, Any]] = []

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
                                image_detections.append({
                                    "classId": best_cid,
                                    "className": CLASS_NAMES.get(best_cid, "UNKNOWN"),
                                    "confidence": round(confidence, 4),
                                    "boundingBox": {
                                        "xMin": round(xmin, 4),
                                        "yMin": round(ymin, 4),
                                        "xMax": round(xmax, 4),
                                        "yMax": round(ymax, 4),
                                    },
                                })

            # Apply NMS
            from app.models.nms import apply_non_maximum_suppression
            filtered = apply_non_maximum_suppression(image_detections, iou_threshold=iou_threshold)
            batch_results.append(filtered)

        return batch_results
