"""
Investigate what produces NMS sweep FP:
IoU=0.35 -> 377
IoU=0.40 -> 379
IoU=0.45 -> 381
IoU=0.50 -> 385
IoU=0.55 -> 387

vs

IoU=0.35 -> 324
IoU=0.40 -> 351
IoU=0.45 -> 381
IoU=0.50 -> 430
IoU=0.55 -> 472
"""
import sys
from pathlib import Path
WORKSPACE_ROOT = Path("C:/Users/athar/OneDrive/Desktop/Firesphere.ai")
AI_SERVICE_DIR = WORKSPACE_ROOT / "ai-service"
sys.path.insert(0, str(AI_SERVICE_DIR))

import json
from app.models.nms import apply_non_maximum_suppression, compute_iou

# Let's inspect how apply_non_maximum_suppression works in app.models.nms
import inspect
from app.models import nms
print(inspect.getsource(nms))
