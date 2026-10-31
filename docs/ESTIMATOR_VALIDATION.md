# Estimator Validation and Slicer Calibration

## Status and scope

The user has supplied two earlier Bambu Studio comparisons and a six-point infill sweep for the same calibration cube. The separate earlier 100% infill STL remains distinct from the 100% cube sweep row. Missing settings and patterns remain explicitly unknown.

These measurements were not independently reproduced here. No OrcaSlicer measurements are available. The results below are recorded, not used to tune the estimator.

## Reported real-world results

Error convention used in every table (as requested): “Absolute error” is the signed difference, not an absolute-value magnitude.

- Absolute error (g) = Instant3D Slicer grams − Bambu Studio grams
- Percentage error (%) = (Instant3D Slicer grams − Bambu Studio grams) ÷ Bambu Studio grams × 100

| Test | Geometry | Infill | Pattern | Walls | Top / bottom | Layer height | Bambu Studio (g) | Instant3D Slicer (g) | Absolute error (App−Bambu, g) | Percentage error | Notes |
|---|---|---:|---|---:|---|---:|---:|---:|---:|---:|---|
| 1 | 20 × 20 × 20 mm cube | 15% | Gyroid | 2 | 4 / 4 | 0.2 mm | 3.96 | 3.71 | -0.25 | -6.31% | Other profile/version details, nozzle, material density and support setting were not included in the report. |
| 2 | Different STL; geometry not documented | 100% | Not documented | Not documented | Not documented | Not documented | 5.62 | 5.81 | +0.19 | +3.38% | This is not the cube from Test 1. Other settings and geometry are unknown. |


## Calibration cube — infill sweep

The following are reported measurements for the same 20 × 20 × 20 mm calibration cube. The pattern is confirmed only for the 15% Gyroid and 50% Grid rows. No other settings, profile values, app/slicer versions, support state, or STL hash were supplied for this sweep; the repeated 15% readings match Test 1.

| Infill | Pattern | Bambu Studio (g) | Instant3D Slicer (g) | Absolute difference (g) | Signed error (App−Bambu, g) | Percentage error |
|---:|---|---:|---:|---:|---:|---:|
| 5% | Unknown | 3.44 | 3.01 | 0.43 | -0.43 | -12.50% |
| 15% | Gyroid | 3.96 | 3.71 | 0.25 | -0.25 | -6.31% |
| 25% | Unknown | 4.59 | 4.41 | 0.18 | -0.18 | -3.92% |
| 50% | Grid | 6.27 | 6.16 | 0.11 | -0.11 | -1.75% |
| 75% | Unknown | 7.90 | 7.91 | 0.01 | +0.01 | +0.13% |
| 100% | Unknown | 9.55 | 9.66 | 0.11 | +0.11 | +1.15% |

The provisional mean absolute percentage error (unweighted arithmetic mean of the six absolute percentage errors) is **4.29%**. It is provisional: patterns are unknown for four rows, other settings/profile data are missing, and the runs have not been independently reproduced.

The signed error moves from -0.43 g at 5% to -0.11 g at 50%, is nearly zero at 75%, then rises to +0.11 g at 100%. Thus the discrepancy generally narrows through 75%, then increases slightly in the positive direction at full infill. If every setting except infill were held fixed, that progression would suggest a fixed shell/wall shortfall at low infill combined with an infill-dependent estimate that grows somewhat faster than Bambu Studio's reported mass. That is only a hypothesis: changing infill pattern (15% Gyroid versus 50% Grid, with other patterns unknown) and unrecorded settings confounds this interpretation.

The existing core formula is linear in infill percentage: estimated infill volume = remaining core volume × infill fraction. This sweep alone does not establish that formula is wrong. A controlled sweep with one confirmed pattern and identical non-infill settings is needed to separate core-volume error from pattern/path behavior.

### Interpretation limits

The earlier comparisons have opposite error signs, and the cube sweep crosses from underestimation to slight overestimation. These observations do not support applying a single global correction. They do not identify the cause of either error because Test 2 lacks geometry and settings, and Test 1 lacks selected material density, nozzle, support setting, application versions, and file/profile identifiers.

There is also an unresolved reproducibility gap for Test 1: with the current code, a 20 mm cube, 0.4 mm nozzle, 2 walls, 4/4 shell layers, 0.2 mm layers, 15% infill, supports off, and built-in PLA density 1.24 g/cm³ produces 2,832 mm³ and 3.51168 g. The reported app result is 3.71 g. A different selected density could explain that app/source gap, but the density was not reported, so this is unconfirmed.

Likely explanation for the negative slicer error is that the geometric model omits real extrusion-path effects such as overlap, line width differing from nozzle diameter, and gap/skin fill. For this cube the XY footprint is exact, but the simplified wall and shell proxies still do not reproduce sliced toolpaths. The missing material/profile/version details prevent attributing the full -0.25 g to any one factor.

For Test 2, 100% infill makes the current no-support model-material estimate approach the STL mesh volume. An app estimate above Bambu Studio could result from the mesh-volume × density estimate exceeding the slicer's realized path mass, from different density or profile settings, or from differences in what each displayed mass includes. Because the second model's geometry and nearly all settings are unknown, none of these causes can be confirmed. The opposite error signs are consistent with geometry- and setting-dependent error, but two observations are not enough to establish that trend.

## Review of the current mathematics

### Walls

The current engine estimates wall volume as:

`min(model volume, side-facet area × wall count × nozzle diameter)`

Horizontal facets are excluded from side-facet area so top and bottom faces are not counted again as walls. This fixes the prior total-surface-area overlap with shell material. Wall volume is capped at model volume.

This remains a surface-area proxy. It does not offset contours or resolve corners, thin walls, holes, variable perimeter counts, overlap, or actual extrusion line width. The engine assumes line width equals nozzle diameter; real profiles can use a different line width. These are accuracy limits, not a confirmed unit-conversion error.

### Top, bottom, core, and infill

Each raw shell estimate is:

`XY bounding-box area × shell layer count × layer height`

If both shells exceed the model volume remaining after walls, their volumes are scaled proportionally so neither shell receives arbitrary priority and the core cannot become negative. Infill is:

`remaining core volume × infill percentage / 100`

The box footprint can overstate shell coverage for hollow, curved, angled, or irregular layers. The engine also does not know which individual layers become solid or how a slicer handles gaps and skin overlap. At 100% infill with supports off, the model-material volume approaches the mesh volume by construction; the slicer's path volume can still differ.

Layer height changes the shell thickness estimate. It does not otherwise change modeled wall or infill paths. Nozzle diameter is used as a wall-width proxy; it is not a separate extrusion-width setting.

### Supports

For downward-facing facets beyond the 45° threshold, the engine estimates projected facet area × centroid height above the build plate, then scales by support density. It corrects facet normals for globally reversed winding and caps support volume to the density-scaled free portion of the model bounding envelope.

This is not support generation. Overlapping projected facets are not unioned before the cap; tree supports, interface layers, blockers, contact distances, build-plate-only options, and intersections with the model are not calculated. Support error cannot be assessed from either reported test because their support settings/results are not documented.

### Weight, filament length, and cost

The unit conversions are dimensionally consistent:

- Material grams = total mm³ ÷ 1,000 × density in g/cm³.
- Filament length in metres = total mm³ ÷ [π × (filament diameter in mm ÷ 2)²] ÷ 1,000.
- Material cost in PKR = estimated grams ÷ spool grams × spool price in PKR.

They assume the configured density, nominal filament diameter, spool mass, and spool price are accurate. Actual filament diameter, density, flow calibration, and slicer extrusion behavior can differ. Extreme or non-finite results are rejected.

### Confirmed defects versus limitations

No additional confirmed mathematical or unit-conversion defect was established from the two reported measurements. Existing Phase 7 fixes already avoid counting horizontal surface area in both walls and shells, normalize top/bottom caps against the remaining volume, account for global facet winding in support detection, and reject impossible mesh volume beyond its bounding envelope.

The remaining issues described above are approximation limits. Neither supplied result has enough shared metadata to justify changing a formula. No multiplier, hardcoded correction, or result-specific adjustment has been applied.

## Repeatable controlled comparison procedure

1. Use identical STL bytes in Instant3D Slicer, Bambu Studio, and OrcaSlicer. Generate each calibration geometry once, record its SHA-256 hash, and do not repair/re-export it between runs.
2. Record each app version, Instant3D Slicer commit, printer profile, material profile, and STL hash.
3. For a baseline, use one printer, 0.4 mm nozzle, 0.2 mm layer height, 2 walls, 15% Gyroid, 4 top / 4 bottom layers, supports off, PLA density 1.240 g/cm³, and 1.75 mm filament. Set matching values in each application.
4. Keep the model orientation fixed with its base at Z=0. Disable or separately record skirt, brim, raft, purge/flush, prime tower, and other non-model extrusion. Use one object and one filament.
5. Change one tested variable at a time. Preserve all other settings and the exact STL. For supports, record threshold, support type, density, interface, and build-plate-only setting.
6. Compare the slicer's model-filament grams with the app estimate in grams. If a slicer only provides whole-job mass, record that and list any included purge/support/interface material.
7. Compute signed error and percentage error using the convention at the top of this document. Keep Bambu Studio and OrcaSlicer results in separate rows.
8. Preserve slicer project files and screenshots/exported reports. Do not calibrate constants against only one geometry; reserve at least one geometry as a holdout.

### Calibration geometries

All dimensions are millimetres.

| ID | Geometry | Construction | Known mesh volume |
|---|---|---|---:|
| G1 | Calibration cube | 20 × 20 × 20 | 8,000 mm³ |
| G2 | Rectangular block | 30 × 20 × 10 | 6,000 mm³ |
| G3 | Hollow vertical cylinder | Outer radius 10, inner radius 8, height 20, 96-sided polygonal rings, base at Z=0 | `[96/2 × sin(2π/96) × (10²−8²)] × 20` mm³ |
| G4 | Sloped overhang prism | Extrude 20 mm along Y the X-Z polygon (0,10), (20,0), (20,20), (0,20); Y=0…20 | 6,000 mm³ |

The G3 polygon construction is intentional: it makes the hollow geometry deterministic. Record the app's analyzed mesh volume before comparing filament estimates.

## Test matrix

For each sweep below, keep all other baseline settings fixed: G1 cube, 0.4 mm nozzle, 0.2 mm layer height, 2 walls, 4/4 shells, 15% Gyroid, supports off, PLA 1.240 g/cm³, 1.75 mm filament. In the geometry sweep, use those same settings for each model. Do not infer values for unrecorded tests.

| Sweep | Geometry | Infill / pattern | Walls | Top / bottom | Bambu Studio (g) | Instant3D Slicer (g) | Absolute error (App−Bambu, g) | Percentage error |
|---|---|---|---:|---|---:|---:|---:|---:|
| Infill | G1 cube | 10% Gyroid | 2 | 4 / 4 |  |  |  |  |
| Infill | G1 cube | 15% Gyroid | 2 | 4 / 4 |  |  |  |  |
| Infill | G1 cube | 20% Gyroid | 2 | 4 / 4 |  |  |  |  |
| Infill | G1 cube | 30% Gyroid | 2 | 4 / 4 |  |  |  |  |
| Infill | G1 cube | 100% (record pattern) | 2 | 4 / 4 |  |  |  |  |
| Wall count | G1 cube | 15% Gyroid | 2 | 4 / 4 |  |  |  |  |
| Wall count | G1 cube | 15% Gyroid | 3 | 4 / 4 |  |  |  |  |
| Wall count | G1 cube | 15% Gyroid | 4 | 4 / 4 |  |  |  |  |
| Shell layers | G1 cube | 15% Gyroid | 2 | 3 / 3 |  |  |  |  |
| Shell layers | G1 cube | 15% Gyroid | 2 | 4 / 4 |  |  |  |  |
| Shell layers | G1 cube | 15% Gyroid | 2 | 5 / 5 |  |  |  |  |
| Geometry | G1 cube | 15% Gyroid | 2 | 4 / 4 |  |  |  |  |
| Geometry | G2 block | 15% Gyroid | 2 | 4 / 4 |  |  |  |  |
| Geometry | G3 hollow cylinder | 15% Gyroid | 2 | 4 / 4 |  |  |  |  |
| Geometry | G4 overhang prism | 15% Gyroid | 2 | 4 / 4 |  |  |  |  |

Run an additional G4 support-on comparison as a separate test. Record support settings rather than assuming they match the engine's projected-column approximation.

## Earlier user-supplied results (kept separate)

Test 2 below is a different STL from the calibration cube. It is not included in the cube-sweep MAPE.

These rows are repeated here so the test matrix remains reserved for complete controlled runs.

| Test | Bambu Studio (g) | Instant3D Slicer (g) | Error (g) | Percentage error |
|---|---:|---:|---:|---:|
| Test 1 — 15% Gyroid cube | 3.96 | 3.71 | -0.25 | -6.31% |
| Test 2 — separate STL, 100% infill; other settings/geometry incomplete | 5.62 | 5.81 | +0.19 | +3.38% |

## Preliminary quotation suitability and next measurements

The estimator is suitable only for a clearly labeled preliminary material-use indication, not a firm or slicer-equivalent quote. The current real-world samples do not establish a reliable error range: the six cube-sweep rows have incomplete setup metadata and the separate STL result lacks its geometry/settings. For customer-facing fixed quotes, verify with a slicer until the matrix has several controlled geometries and settings.

For both reported tests, collect the exact STL hash, app commit/version, selected printer, material name and density, filament diameter, nozzle diameter and line width, support settings, model orientation, and whether displayed grams include support/interface or other job extrusion. For Test 2, first record geometry, wall count, top/bottom layers, layer height, infill pattern, printer/material profiles, and support settings.

Do not fit a global multiplier to these two values. Any future calibration should be justified by repeated controlled comparisons, evaluated across the full matrix, and checked against held-out geometries.
