"""FireSphere.ai — Phase 8.9/8.10 Pretrained Visual Perception Detector.

Pretrained inspection detector architecture pairing a standard visual backbone (MobileNetV2)
with a decoupled bi-scale detection neck (P3: small objects, P4: medium/tall objects)
for the 4 frozen perception classes:
0: FIRE_EXTINGUISHER
1: EXIT_DOOR
2: EXIT_SIGN
3: OBSTACLE

Designed according to Phase 8.9 specification (docs/phase8_9_architecture.md)
to address the representation dilution observed in Phase 8.8 and the anchor
dilution observed in Phase 8.5.
"""

from typing import Any, Dict, List, Optional, Tuple
import math
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

from app.models.baseline_detector import CLASS_NAMES, CLASS_IDS


# Bi-Scale Anchors per Level [width, height] normalized in [0, 1]
BI_SCALE_ANCHORS = {
    "P3": [  # Stride 8 (32x32) - Small Objects (Exit signs, compact elements)
        [0.04, 0.04],
        [0.08, 0.08],
    ],
    "P4": [  # Stride 16 (16x16) - Medium & Tall Objects (Extinguishers, doors, obstacles)
        [0.22, 0.35],
        [0.20, 0.75],
    ],
}


class ConvBNReLU(nn.Sequential):
    """Standard Conv2d + BatchNorm2d + ReLU6 block used in MobileNetV2."""

    def __init__(
        self,
        in_planes: int,
        out_planes: int,
        kernel_size: int = 3,
        stride: int = 1,
        groups: int = 1,
    ):
        padding = (kernel_size - 1) // 2
        super().__init__(
            nn.Conv2d(
                in_planes,
                out_planes,
                kernel_size,
                stride,
                padding,
                groups=groups,
                bias=False,
            ),
            nn.BatchNorm2d(out_planes),
            nn.ReLU6(inplace=True),
        )


class InvertedResidual(nn.Module):
    """MobileNetV2 Inverted Residual Bottleneck Block."""

    def __init__(self, inp: int, oup: int, stride: int, expand_ratio: int):
        super().__init__()
        self.stride = stride
        assert stride in [1, 2]

        hidden_dim = int(round(inp * expand_ratio))
        self.use_res_connect = self.stride == 1 and inp == oup

        layers: List[nn.Module] = []
        if expand_ratio != 1:
            # Pointwise expand
            layers.append(ConvBNReLU(inp, hidden_dim, kernel_size=1))
        layers.extend([
            # Depthwise
            ConvBNReLU(hidden_dim, hidden_dim, stride=stride, groups=hidden_dim),
            # Pointwise linear project
            nn.Conv2d(hidden_dim, oup, 1, 1, 0, bias=False),
            nn.BatchNorm2d(oup),
        ])
        self.conv = nn.Sequential(*layers)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        if self.use_res_connect:
            return x + self.conv(x)
        return self.conv(x)


class MobileNetV2Backbone(nn.Module):
    """Native PyTorch MobileNetV2 feature extractor matching official torchvision layer naming."""

    def __init__(self, width_mult: float = 1.0):
        super().__init__()
        # Inverted residual setting: [expand_ratio, out_channels, num_blocks, stride]
        self.cfgs = [
            # t, c, n, s
            [1, 16, 1, 1],
            [6, 24, 2, 2],
            [6, 32, 3, 2],   # Stage 3 -> Stride 8 (C3 feature extraction point: block 6, out 32)
            [6, 64, 4, 2],   # Stage 4 -> Stride 16
            [6, 96, 3, 1],   # Stage 4 continued (C4 feature extraction point: block 13, out 96)
            [6, 160, 3, 2],  # Stage 5 -> Stride 32
            [6, 320, 1, 1],
        ]

        input_channel = int(32 * width_mult)
        features: List[nn.Module] = [ConvBNReLU(3, input_channel, stride=2)]

        # Build inverted residual blocks
        for t, c, n, s in self.cfgs:
            output_channel = int(c * width_mult)
            for i in range(n):
                stride = s if i == 0 else 1
                features.append(InvertedResidual(input_channel, output_channel, stride, expand_ratio=t))
                input_channel = output_channel

        # Final stem expansion layer matching standard MobileNetV2 (features[18])
        last_channel = int(1280 * width_mult)
        features.append(ConvBNReLU(input_channel, last_channel, kernel_size=1))

        self.features = nn.Sequential(*features)
        # C3 is at end of block 6 (features[0..6] -> stride 8, 32 channels)
        # C4 is at end of block 13 (features[7..13] -> stride 16, 96 channels)
        self.c3_idx = 6
        self.c4_idx = 13

    def forward_features(self, x: torch.Tensor) -> Tuple[torch.Tensor, torch.Tensor]:
        """Extract multi-scale features C3 (stride 8) and C4 (stride 16)."""
        c3 = None
        c4 = None
        feat = x
        for i, layer in enumerate(self.features):
            feat = layer(feat)
            if i == self.c3_idx:
                c3 = feat
            elif i == self.c4_idx:
                c4 = feat
                break
        return c3, c4

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.features(x)


class DecoupledBiScaleNeck(nn.Module):
    """Lateral feature pyramid neck extracting P3 (stride 8, 64-ch) and P4 (stride 16, 64-ch)."""

    def __init__(self, c3_channels: int = 32, c4_channels: int = 96, neck_channels: int = 64):
        super().__init__()
        self.lateral_c4 = nn.Sequential(
            nn.Conv2d(c4_channels, neck_channels, kernel_size=1, bias=False),
            nn.BatchNorm2d(neck_channels),
            nn.SiLU(inplace=True),
        )
        self.smooth_p4 = nn.Sequential(
            nn.Conv2d(neck_channels, neck_channels, kernel_size=3, padding=1, bias=False),
            nn.BatchNorm2d(neck_channels),
            nn.SiLU(inplace=True),
        )
        self.lateral_c3 = nn.Sequential(
            nn.Conv2d(c3_channels, neck_channels, kernel_size=1, bias=False),
            nn.BatchNorm2d(neck_channels),
            nn.SiLU(inplace=True),
        )
        self.smooth_p3 = nn.Sequential(
            nn.Conv2d(neck_channels, neck_channels, kernel_size=3, padding=1, bias=False),
            nn.BatchNorm2d(neck_channels),
            nn.SiLU(inplace=True),
        )

    def forward(self, c3: torch.Tensor, c4: torch.Tensor) -> Tuple[torch.Tensor, torch.Tensor]:
        p4_lat = self.lateral_c4(c4)
        p4 = self.smooth_p4(p4_lat)

        # Top-down pathway: upsample P4 to match C3 spatial resolution
        p4_upsampled = F.interpolate(p4_lat, size=c3.shape[-2:], mode="nearest")
        p3_lat = self.lateral_c3(c3) + p4_upsampled
        p3 = self.smooth_p3(p3_lat)

        return p3, p4


class PretrainedInspectionDetector(nn.Module):
    """FireSphere Phase 8.9 Pretrained Transfer-Learning Inspection Detector.

    Backbone: MobileNetV2 (pretrained ImageNet features)
    Neck: Decoupled Bi-Scale FPN (P3: Stride 8, P4: Stride 16)
    Heads:
      - Head P3: 2 anchors per cell, 32x32 grid (small objects / EXIT_SIGN) -> 2,048 anchors
      - Head P4: 2 anchors per cell, 16x16 grid (medium & tall objects / EXTINGUISHER, DOOR, OBSTACLE) -> 512 anchors
      Total anchors = 2,560
    """

    def __init__(
        self,
        num_classes: int = 4,
        anchors: Optional[Dict[str, List[List[float]]]] = None,
        input_size: Tuple[int, int] = (256, 256),
    ):
        super().__init__()
        self.num_classes = num_classes
        self.anchors = anchors or BI_SCALE_ANCHORS
        self.input_size = input_size

        self.backbone = MobileNetV2Backbone()
        self.neck = DecoupledBiScaleNeck(c3_channels=32, c4_channels=96, neck_channels=64)

        # Output channels per anchor: 5 (tx, ty, tw, th, obj) + num_classes
        self.out_channels_per_anchor = 5 + self.num_classes

        self.num_anchors_p3 = len(self.anchors["P3"])
        self.head_p3 = nn.Conv2d(
            64,
            self.num_anchors_p3 * self.out_channels_per_anchor,
            kernel_size=1,
        )

        self.num_anchors_p4 = len(self.anchors["P4"])
        self.head_p4 = nn.Conv2d(
            64,
            self.num_anchors_p4 * self.out_channels_per_anchor,
            kernel_size=1,
        )

        self.freeze_stage = "unfrozen"

    def set_freeze_stage(self, stage: str) -> Dict[str, int]:
        """Configures transfer learning parameter freezing.

        stage == 'stage_a': Backbone fully frozen, Neck + Heads trainable.
        stage == 'stage_b': Stem & early stages frozen (features[:7]), deep backbone (features[7:]) + Neck + Heads trainable.
        stage == 'unfrozen': All layers trainable.
        """
        self.freeze_stage = stage
        if stage == "stage_a":
            for param in self.backbone.parameters():
                param.requires_grad = False
            for param in self.neck.parameters():
                param.requires_grad = True
            for param in self.head_p3.parameters():
                param.requires_grad = True
            for param in self.head_p4.parameters():
                param.requires_grad = True
        elif stage == "stage_b":
            # Freeze features 0..6 (stem + stages 1-3)
            for i in range(7):
                for param in self.backbone.features[i].parameters():
                    param.requires_grad = False
            # Unfreeze features 7.. (stage 4 deep layers)
            for i in range(7, len(self.backbone.features)):
                for param in self.backbone.features[i].parameters():
                    param.requires_grad = True
            for param in self.neck.parameters():
                param.requires_grad = True
            for param in self.head_p3.parameters():
                param.requires_grad = True
            for param in self.head_p4.parameters():
                param.requires_grad = True
        elif stage == "unfrozen":
            for param in self.parameters():
                param.requires_grad = True
        else:
            raise ValueError(f"Unknown freeze stage: {stage}")

        trainable = sum(p.numel() for p in self.parameters() if p.requires_grad)
        frozen = sum(p.numel() for p in self.parameters() if not p.requires_grad)
        total = trainable + frozen
        return {"trainable": trainable, "frozen": frozen, "total": total}

    def get_parameter_groups(
        self, lr_head: float = 1e-3, lr_backbone: float = 1e-4, weight_decay: float = 1e-4
    ) -> List[Dict[str, Any]]:
        """Returns parameter groups with decoupled learning rates for transfer learning."""
        backbone_params = [p for p in self.backbone.parameters() if p.requires_grad]
        head_neck_params = [
            p
            for m in [self.neck, self.head_p3, self.head_p4]
            for p in m.parameters()
            if p.requires_grad
        ]
        groups = []
        if backbone_params:
            groups.append({
                "params": backbone_params,
                "lr": lr_backbone,
                "weight_decay": weight_decay,
                "name": "backbone",
            })
        if head_neck_params:
            groups.append({
                "params": head_neck_params,
                "lr": lr_head,
                "weight_decay": weight_decay,
                "name": "heads_neck",
            })
        return groups

    def load_pretrained_backbone(self, state_dict: Dict[str, torch.Tensor], strict: bool = False) -> Dict[str, Any]:
        """Loads pretrained weights into backbone features."""
        # Handle state_dict with or without 'features.' prefix
        backbone_dict = {}
        for k, v in state_dict.items():
            if k.startswith("features."):
                backbone_dict[k] = v
            elif k.startswith("backbone.features."):
                backbone_dict[k.replace("backbone.", "")] = v
            elif not any(k.startswith(p) for p in ["classifier", "head", "neck"]):
                # If pure features state_dict
                backbone_dict[f"features.{k}"] = v

        missing, unexpected = self.backbone.load_state_dict(backbone_dict, strict=strict)
        return {
            "loaded_tensors": len(backbone_dict),
            "missing_keys": len(missing),
            "unexpected_keys": len(unexpected),
        }

    def forward(self, x: torch.Tensor) -> Dict[str, torch.Tensor]:
        """Forward pass.
        Args:
            x: Tensor of shape (B, 3, H, W)
        Returns:
            Dict containing:
              - 'P3': (B, num_anchors_p3, 5 + C, 32, 32)
              - 'P4': (B, num_anchors_p4, 5 + C, 16, 16)
        """
        b = x.size(0)
        c3, c4 = self.backbone.forward_features(x)
        p3, p4 = self.neck(c3, c4)

        out_p3 = self.head_p3(p3)
        out_p4 = self.head_p4(p4)

        s3 = out_p3.size(-1)
        out_p3 = out_p3.view(b, self.num_anchors_p3, self.out_channels_per_anchor, s3, s3)

        s4 = out_p4.size(-1)
        out_p4 = out_p4.view(b, self.num_anchors_p4, self.out_channels_per_anchor, s4, s4)

        return {"P3": out_p3, "P4": out_p4}

    def compute_loss(
        self,
        pyramid_outputs: Dict[str, torch.Tensor],
        targets: List[torch.Tensor],
        device: torch.device,
    ) -> Tuple[torch.Tensor, Dict[str, float]]:
        """Multi-scale loss calculation assigned across P3 (small) and P4 (medium/tall)."""
        b = next(iter(pyramid_outputs.values())).size(0)

        # Flat anchors list for global anchor matching
        # 0, 1 -> P3; 2, 3 -> P4
        all_anchors = self.anchors["P3"] + self.anchors["P4"]

        target_obj = {
            "P3": torch.zeros((b, self.num_anchors_p3, 32, 32), device=device, dtype=torch.float32),
            "P4": torch.zeros((b, self.num_anchors_p4, 16, 16), device=device, dtype=torch.float32),
        }

        total_box_loss = torch.tensor(0.0, device=device)
        total_cls_loss = torch.tensor(0.0, device=device)
        total_pos = 0

        for b_idx in range(b):
            gt_boxes = targets[b_idx]
            if len(gt_boxes) == 0:
                continue

            for box in gt_boxes:
                cid = int(box[0].item())
                if cid >= self.num_classes:
                    continue
                xc, yc, w, h = box[1].item(), box[2].item(), box[3].item(), box[4].item()

                # Find best anchor across all anchors
                box_wh = np.array([w, h])
                anc_wh = np.array(all_anchors)
                diffs = np.sum(np.abs(np.log(np.maximum(box_wh, 1e-4) / np.maximum(anc_wh, 1e-4))), axis=1)
                best_global_anchor = int(np.argmin(diffs))

                if best_global_anchor < self.num_anchors_p3:
                    level = "P3"
                    a_idx = best_global_anchor
                    s = 32
                else:
                    level = "P4"
                    a_idx = best_global_anchor - self.num_anchors_p3
                    s = 16

                col = min(int(xc * s), s - 1)
                row = min(int(yc * s), s - 1)

                target_obj[level][b_idx, a_idx, row, col] = 1.0

                gt_tx = max(1e-4, min(1.0 - 1e-4, xc * s - col))
                gt_ty = max(1e-4, min(1.0 - 1e-4, yc * s - row))
                gt_logit_tx = np.log(gt_tx / (1.0 - gt_tx))
                gt_logit_ty = np.log(gt_ty / (1.0 - gt_ty))

                anc_w, anc_h = self.anchors[level][a_idx]
                gt_tw = np.log(max(w, 1e-4) / anc_w)
                gt_th = np.log(max(h, 1e-4) / anc_h)

                target_coord = torch.tensor(
                    [gt_logit_tx, gt_logit_ty, gt_tw, gt_th],
                    device=device,
                    dtype=torch.float32,
                )

                out_lvl = pyramid_outputs[level]
                pred_coord = torch.stack([
                    out_lvl[b_idx, a_idx, 0, row, col],
                    out_lvl[b_idx, a_idx, 1, row, col],
                    out_lvl[b_idx, a_idx, 2, row, col],
                    out_lvl[b_idx, a_idx, 3, row, col],
                ])

                total_box_loss = total_box_loss + F.smooth_l1_loss(pred_coord, target_coord)

                cls_pred = out_lvl[b_idx, a_idx, 5:, row, col].unsqueeze(0)
                total_cls_loss = total_cls_loss + F.cross_entropy(
                    cls_pred,
                    torch.tensor([cid], device=device, dtype=torch.long),
                )
                total_pos += 1

        # Objectness loss across both levels
        pos_weight = torch.tensor([2.0], device=device)
        obj_loss_p3 = F.binary_cross_entropy_with_logits(
            pyramid_outputs["P3"][:, :, 4, :, :],
            target_obj["P3"],
            pos_weight=pos_weight,
        )
        obj_loss_p4 = F.binary_cross_entropy_with_logits(
            pyramid_outputs["P4"][:, :, 4, :, :],
            target_obj["P4"],
            pos_weight=pos_weight,
        )
        total_obj_loss = 0.5 * (obj_loss_p3 + obj_loss_p4)

        if total_pos > 0:
            total_box_loss = total_box_loss / total_pos
            total_cls_loss = total_cls_loss / total_pos

        total_loss = 2.0 * total_box_loss + 1.0 * total_obj_loss + 1.5 * total_cls_loss

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
        """Decodes bi-scale detections (P3, P4) and applies greedy NMS."""
        from app.models.nms import apply_non_maximum_suppression

        b = next(iter(pyramid_outputs.values())).size(0)
        batch_candidates: List[List[Dict[str, Any]]] = [[] for _ in range(b)]

        for level_name, output in pyramid_outputs.items():
            _, num_anchors, _, s, _ = output.shape
            anchors = self.anchors[level_name]
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

        final_results = []
        for b_idx in range(b):
            filtered = apply_non_maximum_suppression(batch_candidates[b_idx], iou_threshold=iou_threshold)
            final_results.append(filtered)

        return final_results

