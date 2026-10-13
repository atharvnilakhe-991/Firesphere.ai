"""Tests for bounding box coordinate transformations between original and letterboxed model spaces."""

import pytest
from app.cv.transforms import BoundingBoxTransformer, TransformationMetadata
from app.schemas.finding import BoundingBox


def test_square_image_transformation():
    """Verifies that for a 1:1 square image scaled to 1:1 canvas, coordinates remain identical."""
    meta = TransformationMetadata(
        originalWidth=1000,
        originalHeight=1000,
        targetWidth=640,
        targetHeight=640,
        scaledWidth=640,
        scaledHeight=640,
        scaleFactor=0.64,
        padLeft=0,
        padRight=0,
        padTop=0,
        padBottom=0,
    )

    box = BoundingBox(xMin=0.2, yMin=0.3, xMax=0.6, yMax=0.7)

    # Original to Letterbox
    lb_box = BoundingBoxTransformer.original_to_letterbox(box, meta)
    assert lb_box.xMin == 0.2
    assert lb_box.yMin == 0.3
    assert lb_box.xMax == 0.6
    assert lb_box.yMax == 0.7

    # Letterbox back to Original (round-trip)
    recovered = BoundingBoxTransformer.letterbox_to_original(lb_box, meta)
    assert recovered.xMin == 0.2
    assert recovered.yMin == 0.3
    assert recovered.xMax == 0.6
    assert recovered.yMax == 0.7


def test_wide_aspect_ratio_transformation():
    """Verifies coordinate mapping for a wide image (aspect 2:1) with top and bottom letterbox padding."""
    # 800x400 image into 640x640 canvas
    # scale = 0.8 -> scaled to 640x320. padTop=160, padBottom=160, padLeft=0, padRight=0.
    meta = TransformationMetadata(
        originalWidth=800,
        originalHeight=400,
        targetWidth=640,
        targetHeight=640,
        scaledWidth=640,
        scaledHeight=320,
        scaleFactor=0.8,
        padLeft=0,
        padRight=0,
        padTop=160,
        padBottom=160,
    )

    # Box in original image: center box covering full height (yMin=0.0, yMax=1.0)
    orig_box = BoundingBox(xMin=0.25, yMin=0.0, xMax=0.75, yMax=1.0)

    lb_box = BoundingBoxTransformer.original_to_letterbox(orig_box, meta)

    # X coordinates should be unchanged since width is unpadded: 0.25 and 0.75
    assert lb_box.xMin == 0.25
    assert lb_box.xMax == 0.75
    # Y coordinates: in letterbox space, image occupies from y=160 to y=480 out of 640
    # 160 / 640 = 0.25, 480 / 640 = 0.75
    assert lb_box.yMin == 0.25
    assert lb_box.yMax == 0.75

    # Round-trip recovery
    recovered = BoundingBoxTransformer.letterbox_to_original(lb_box, meta)
    assert abs(recovered.xMin - orig_box.xMin) < 1e-3
    assert abs(recovered.yMin - orig_box.yMin) < 1e-3
    assert abs(recovered.xMax - orig_box.xMax) < 1e-3
    assert abs(recovered.yMax - orig_box.yMax) < 1e-3


def test_tall_aspect_ratio_transformation():
    """Verifies coordinate mapping for a tall image (aspect 1:2) with left and right letterbox padding."""
    # 400x800 image into 640x640 canvas
    # scale = 0.8 -> scaled to 320x640. padLeft=160, padRight=160, padTop=0, padBottom=0.
    meta = TransformationMetadata(
        originalWidth=400,
        originalHeight=800,
        targetWidth=640,
        targetHeight=640,
        scaledWidth=320,
        scaledHeight=640,
        scaleFactor=0.8,
        padLeft=160,
        padRight=160,
        padTop=0,
        padBottom=0,
    )

    orig_box = BoundingBox(xMin=0.0, yMin=0.1, xMax=1.0, yMax=0.9)
    lb_box = BoundingBoxTransformer.original_to_letterbox(orig_box, meta)

    # Y coordinates are unpadded: 0.1 and 0.9
    assert lb_box.yMin == 0.1
    assert lb_box.yMax == 0.9
    # X coordinates: image occupies x=160 to x=480 in canvas -> 0.25 to 0.75
    assert lb_box.xMin == 0.25
    assert lb_box.xMax == 0.75

    # Round-trip recovery
    recovered = BoundingBoxTransformer.letterbox_to_original(lb_box, meta)
    assert abs(recovered.xMin - orig_box.xMin) < 1e-3
    assert abs(recovered.yMin - orig_box.yMin) < 1e-3
    assert abs(recovered.xMax - orig_box.xMax) < 1e-3
    assert abs(recovered.yMax - orig_box.yMax) < 1e-3


def test_boundary_clamping():
    """Verifies that out-of-boundary predictions are safely clamped to [0.0, 1.0]."""
    meta = TransformationMetadata(
        originalWidth=500,
        originalHeight=500,
        targetWidth=640,
        targetHeight=640,
        scaledWidth=640,
        scaledHeight=640,
        scaleFactor=1.28,
        padLeft=0,
        padRight=0,
        padTop=0,
        padBottom=0,
    )

    # Coordinate at 0.0 and 1.0
    boundary_box = BoundingBox(xMin=0.0, yMin=0.0, xMax=1.0, yMax=1.0)
    recovered = BoundingBoxTransformer.letterbox_to_original(boundary_box, meta)

    assert recovered.xMin == 0.0
    assert recovered.yMin == 0.0
    assert recovered.xMax == 1.0
    assert recovered.yMax == 1.0
