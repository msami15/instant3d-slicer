# Estimator Validation and Slicer Calibration

## Status

No Bambu Studio or OrcaSlicer execution was available while this validation work was prepared. No comparison values below are claimed. Fill in the results tables after running the procedure.

## Repeatable baseline

1. Use one set of STL files for every run and both slicers. Generate them once from the dimensions below, save the exact files, and record their SHA-256 hashes. Do not re-export or repair them between applications.
2. In Instant3D Slicer and both slicers, use the same printer build volume and orientation. Baseline: Bambu Lab A1, model resting on Z=0, no rotation after import.
3. Use one material consistently: PLA, density 1.240 g/cm³, filament diameter 1.75 mm. Record the exact material profile and application versions.
4. Configure 0.40 mm nozzle, 0.20 mm layer height, 2 wall loops/perimeters, 15% gyroid infill, 4 top layers, 4 bottom layers. Disable supports for the first three models.
5. Disable skirt, brim, raft, purge/flush, prime tower, and other non-model extrusion. Use one object and one filament. If a slicer cannot exclude an item from its displayed mass, record that fact and report the displayed value separately.
6. Slice each model and record the slicer's model-filament mass (not packaging/spool usage). In Instant3D Slicer, use the same input values. Capture estimated grams and the breakdown.
7. Repeat the comparison with supports enabled on the overhang model. Set the slicer's overhang threshold as close as possible to 45°; record the exact support style, density, interface, and threshold because these do not map one-to-one to the approximation.
8. For each slicer, record model mass, app estimate, absolute error, and relative error:
   - Absolute error (g) = |app estimate (g) − slicer model mass (g)|
   - Relative error (%) = absolute error ÷ slicer model mass × 100
9. Compare Bambu Studio and OrcaSlicer separately. Preserve their project files and screenshots/exported reports with the results.

## Calibration geometries

Use these exact dimensions and coordinate placement; all dimensions are mm.

| ID | Geometry | Construction |
|---|---|---|
| G1 | Solid cube | 20 × 20 × 20 |
| G2 | Rectangular block | 30 × 20 × 10 |
| G3 | Hollow vertical cylinder | Outer radius 10, inner radius 8, height 20, 96-sided polygonal rings; bottom at Z=0 |
| G4 | Sloped overhang prism | Extrude 20 mm along Y the X-Z polygon (0,10), (20,0), (20,20), (0,20); place Y from 0 to 20 |

Known analytic/polygonal model volumes: G1 = 8,000 mm³; G2 = 6,000 mm³; G3 = [96/2 × sin(2π/96) × (10²−8²)] × 20 mm³; G4 = 6,000 mm³. G3 is intentionally a polygonal tube so both the STL and expected volume are reproducible. The application’s mesh volume should be recorded to confirm the exact test input before comparing estimates.

For G4 support runs, keep the sloped underside facing down, with the low end at Z=0. The estimator uses the mesh facet normals and a 45° threshold. Slicer support paths and interface layers will differ by implementation.

## Results template

Record versions and profiles before adding measurements:

- Test date:
- Instant3D Slicer branch/commit:
- Bambu Studio version:
- OrcaSlicer version:
- Printer profile / build volume:
- Material profile (density, filament diameter):
- STL SHA-256 hashes:
- Slicer line width / flow / support-interface details:
- Disabled extras (skirt, brim, raft, purge, prime tower):

| Geometry | Slicer | Slicer grams | App grams | Absolute error (g) | Relative error (%) | Notes |
|---|---|---:|---:|---:|---:|---|
| G1 cube | Bambu Studio |  |  |  |  |  |
| G1 cube | OrcaSlicer |  |  |  |  |  |
| G2 block | Bambu Studio |  |  |  |  |  |
| G2 block | OrcaSlicer |  |  |  |  |  |
| G3 tube | Bambu Studio |  |  |  |  |  |
| G3 tube | OrcaSlicer |  |  |  |  |  |
| G4 overhang, supports off | Bambu Studio |  |  |  |  |  |
| G4 overhang, supports off | OrcaSlicer |  |  |  |  |  |
| G4 overhang, supports on | Bambu Studio |  |  |  |  |  |
| G4 overhang, supports on | OrcaSlicer |  |  |  |  |  |

## Expected sources of discrepancy to investigate

- Shell area in the estimator uses side-facet area for walls and the XY bounding rectangle for top/bottom shells; actual slicers calculate layer-specific contours and offset paths.
- Thin walls, holes, narrow regions, curved facets, sloped surfaces, and shell overlap can change perimeter counts or cause slicer wall generation to differ.
- Infill is represented as a percentage of estimated remaining core volume. Pattern-specific path lengths, overlap, gap fill, and solid-infill regions are not modeled.
- Supports are projected columns under downward-facing mesh facets, scaled by density and capped to the empty model bounding envelope. Actual tree/normal supports, support blockers, interface layers, and support/model intersections are not modeled.
- Slicers may include extrusion outside the model (purge, prime, skirt, or support interface) and may calculate filament mass from different material-density conventions.
- The selected orientation and first-layer/contact behavior affect the sliced result; estimator checks the imported axis-aligned bounds and does not rotate or optimize placement.

Do not tune constants to a single part. If calibration changes are considered, evaluate each geometry and both slicers, reserve at least one geometry as a holdout, and document any material/printer-specific calibration.
