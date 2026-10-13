# FireSphere.ai — Fire Safety Annotation Guidelines & Handbook

**Document Version:** `1.0.0-phase8`  
**Standard:** YOLO Normalized Bounding Box Annotation Protocol  
**Audience:** Human Data Annotators, Quality Assurance Reviewers, Lead AI Engineers

---

## 1. General Bounding Box Annotation Principles

1. **Tight Boundary Rule**:
   Draw the bounding box around the tightest rectangle containing all visible pixels of the target object. Do NOT include excessive background padding, wall areas, or irrelevant surroundings.
2. **Normalized Coordinates**:
   Annotations must follow the standard YOLO format:
   ```text
   <class_id> <x_center> <y_center> <width> <height>
   ```
   All coordinate values must be floating-point numbers normalized strictly in $[0.0, 1.0]$.
3. **Truncation & Image Edges**:
   If a target object is partially cut off by the camera frame, bound only the portion that is visible inside the canvas. The bounding box must never extend outside the image ($< 0.0$ or $> 1.0$).
4. **Minimum Visibility Threshold**:
   * If $\ge 20\%$ of the target object is visible, draw a bounding box.
   * If $< 20\%$ is visible, do NOT annotate (mark as non-identifiable).
5. **Severe Occlusion**:
   When an object is occluded by another object (e.g., an extinguisher behind a pallet):
   * For equipment detection: Draw the box around the full visible footprint of the equipment.
   * For obstruction detection: If annotating compound hazard classes, the bounding box must enclose the interaction zone between the equipment and the obstructing clutter.

---

## 2. Detailed Class-by-Class Annotation Criteria

### Class 0: `FIRE_EXTINGUISHER_MISSING` (Class ID `0`)
* **Qualifies**:
  - A wall-mounted extinguisher bracket or hanging cradle that is completely vacant.
  - A dedicated red floor stand or extinguisher cabinet with an absent cylinder.
  - A wall-mounted "FIRE EXTINGUISHER" sign where the wall directly below is visibly unequipped.
* **Does NOT Qualify**:
  - Generic wall hooks (coat hooks, tool pegs, cable clamps).
  - Unmarked bare walls where no mounting bracket or sign exists.
* **Bounding Box Boundary**:
  Enclose the vacant mounting bracket, empty cradle, or the empty cabinet interior.

---

### Class 1: `FIRE_EXTINGUISHER_BLOCKED` (Class ID `1`)
* **Qualifies**:
  - Any fire extinguisher cylinder where objects (cardboard boxes, pallets, furniture, carts, equipment) are placed directly in front or within $1\text{ meter}$ of the cylinder, preventing direct physical retrieval.
* **Does NOT Qualify**:
  - Extinguishers in open corridors with $> 1\text{ meter}$ unobstructed clearance.
  - Obstacles positioned clearly to the side that do not impede direct forward access.
* **Bounding Box Boundary**:
  Draw the bounding box to enclose the extinguisher cylinder AND the immediately adjacent obstructing obstacle.

---

### Class 2: `FIRE_EXTINGUISHER_EXPIRED` (Class ID `2`)
* **Governance Notice**:
  * **DO NOT ANNOTATE on wide-angle room photos ($640 \times 640$)**.
  * Annotate ONLY on designated macro close-up photographs.
* **Qualifies**:
  - Macro photograph of pressure gauge where the indicator needle clearly points into the red "DISCHARGE" or "OVERCHARGED" zone.
  - Macro photograph of an inspection tag where the punched annual inspection date is expired.
* **Does NOT Qualify**:
  - Blurry dial faces where the needle position cannot be resolved with certainty.
* **Bounding Box Boundary**:
  Enclose the pressure gauge dial or the annual inspection tag.

---

### Class 3: `EMERGENCY_EXIT_OBSTRUCTED` (Class ID `3`)
* **Qualifies**:
  - Any emergency exit door, final exit door, or fire escape route blocked by stacked boxes, pallets, furniture, trash bins, or construction materials.
  - Exit doors secured with unauthorized padlocks, chains, or bicycle locks across panic push-bars.
  - Blocked corridors where egress width is reduced to $< 1.0\text{ meter}$.
* **Does NOT Qualify**:
  - Open, unobstructed exit doors and corridors.
  - Temporarily held doors where an active moving worker is visibly transporting goods.
* **Bounding Box Boundary**:
  Enclose the blocked doorway and the physical obstruction in a unified defect bounding box.

---

### Class 4: `EXIT_SIGNAGE_MISSING` (Class ID `4`)
* **Qualifies**:
  - A designated emergency exit doorway or egress stairwell entrance where the overhead lintel or bulkhead visibly lacks required illuminated EXIT signage.
  - An EXIT sign fixture that is visibly unpowered, dark, or physically broken during an illuminated emergency lighting test.
* **Does NOT Qualify**:
  - Interior non-exit doors (restrooms, private offices, janitorial closets).
  - Doorways where signage is positioned on an adjacent perpendicular wall within statutory sightlines.
* **Bounding Box Boundary**:
  Enclose the upper doorway lintel where the missing sign is expected.

---

### Class 5: `SPRINKLER_HEAD_DEFECT` (Class ID `5`)
* **Qualifies**:
  - Ceiling fire sprinkler head coated in paint over-spray from ceiling redecoration.
  - Sprinkler head with visible rust corrosion, mineral scaling, or bent deflector plate.
  - Storage items, wires, or banners hung directly from sprinkler piping or within $0.5\text{ meters}$ below the head.
* **Does NOT Qualify**:
  - Clean, unpainted, undamaged sprinkler heads.
  - Normal, light surface dust that does not impede deflector pattern or thermal bulb.
* **Bounding Box Boundary**:
  Enclose the complete sprinkler head assembly (frame arms, deflector, and thermal bulb).

---

### Class 6: `ELECTRICAL_PANEL_HAZARD` (Class ID `6`)
* **Qualifies**:
  - Electrical distribution board or breaker panel with the cabinet door open, unlatched, or missing.
  - Panel with removed deadfront cover exposing live electrical busbars or wiring terminals.
  - Storage clutter, boxes, or pallets stacked directly in front of the panel ($< 1\text{ meter}$ working clearance).
* **Does NOT Qualify**:
  - Fully closed, latched, and unobstructed electrical panels.
  - Panels undergoing authorized maintenance with an active certified technician present.
* **Bounding Box Boundary**:
  Enclose the open panel enclosure or the clutter encroaching on the panel's clearance zone.

---

### Class 7: `COMBUSTIBLE_MATERIAL_HAZARD` (Class ID `7`)
* **Qualifies**:
  - Stacks of cardboard boxes, wooden pallets, plastic wrap, or chemical drums placed in utility rooms, electrical closets, boiler rooms, or beneath stairwells.
  - Flammable goods stored directly adjacent to space heaters, exposed wiring, or heat sources.
* **Does NOT Qualify**:
  - Lawful storage in designated warehouse storage bays or sprinkler-protected pallet racks.
* **Bounding Box Boundary**:
  Enclose the accumulated stack of combustible materials.

---

### Class 8: `MANUAL_CALL_POINT_BLOCKED` (Class ID `8`)
* **Qualifies**:
  - Wall-mounted red manual call point or pull station blocked by filing cabinets, furniture, vending machines, or advertising banners ($< 1\text{ meter}$ access clearance).
* **Does NOT Qualify**:
  - Unobstructed call points with free direct frontal approach.
* **Bounding Box Boundary**:
  Enclose the call point and the obstructing foreground item.

---

### Class 9: `HOSE_REEL_OBSTRUCTED` (Class ID `9`)
* **Qualifies**:
  - Fire hose reel drum or red cabinet blocked by pallets, storage racks, machinery, or locked unauthorized padlocks preventing the cabinet door from swinging open $180^\circ$.
* **Does NOT Qualify**:
  - Clear, accessible hose reel cabinets in open corridors.
* **Bounding Box Boundary**:
  Enclose the hose reel cabinet and the obstructing objects.

---

## 3. Negative Samples & Background Controls

To prevent false alarms in operational deployment, the dataset must include **negative samples** (images containing no violations):
1. Fully compliant, unobstructed corridors.
2. Fully compliant, mounted, tagged fire extinguishers.
3. Fully compliant, closed, latched electrical panels.
4. Fully compliant, unpainted ceiling sprinkler heads.

**Annotation format for negative samples**: Leave the `.txt` annotation file empty ($0$ bytes). Do NOT create dummy bounding boxes.

---

## 4. Quality Control & Annotator Inter-Rater Reliability

1. **Dual-Annotation Auditing**: $10\%$ of all images are independently labeled by two separate annotators.
2. **IoU Consistency Threshold**: Two bounding boxes are considered consistent if their Intersection-over-Union ($\text{IoU}$) $\ge 0.70$ and their class labels match.
3. **Dispute Resolution**: Any class discrepancy or $\text{IoU} < 0.70$ is reviewed and arbitrated by a certified Fire Safety Specialist.
