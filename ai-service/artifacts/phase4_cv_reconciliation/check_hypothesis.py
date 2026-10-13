import sys
from pathlib import Path
WORKSPACE_ROOT = Path("C:/Users/athar/OneDrive/Desktop/Firesphere.ai")
AI_SERVICE_DIR = WORKSPACE_ROOT / "ai-service"
sys.path.insert(0, str(AI_SERVICE_DIR))

import torch
from torch.utils.data import DataLoader
from app.models.baseline_detector import BaselineInspectionDetector, CLASS_NAMES
from app.models.nms import apply_non_maximum_suppression, compute_iou
from app.training.phase8_4_trainer import Phase84YOLODataset, collate_fn
from app.training.checkpoints import CheckpointManager

# Could it be evaluation IoU = 0.40 instead of 0.50? Or 0.45?
# Could it be image resizing without letterbox vs letterbox?
# Could it be matching against all GT or only matched classes?
# Could it be different confidence formula?
# In run_experiment.py:
#   confidence = obj_conf * best_p
# What if confidence was obj_conf alone?
# What if confidence was best_p alone?
# What if eval_iou was 0.40 or 0.45 or 0.50?

print("[*] Checking variations...")
