# Estimator Validation and Slicer Calibration

## Status and scope

The user has supplied two earlier Bambu Studio comparisons and a six-point infill sweep for the same calibration cube. The separate earlier 100% infill STL remains distinct from the 100% cube sweep row.

The user confirms that all six cube tests used the exact same calibration cube and Grid infill, with every other slicing setting held identical; only infill percentage changed. The specific values of many fixed settings, profile/version details, and the STL hash were not recorded. These measurements have not been independently reproduced here. No OrcaSlicer measurements are available. Results are recorded, not used to tune the estimator.

## Reported real-world results

Error convention used in every table: “Absolute error” is the signed difference, not an absolute-value magnitude.

- Absolute error (g) = Instant3D Slicer grams − Bambu Studio grams
- Percentage error (%) = (Instant3D Slicer grams − Bambu Studio grams) ÷ Bambu Studio grams × 100

| Test | Geometry | Infill | Pattern | Walls | Top / bottom | Layer height | Bambu Studio (g) | Instant3D Slicer (g) | Absolute error (App−Bambu, g) | Percentage error | Notes |
|---|---|---:|---|---:|---|---:|---:|---:|---:|---:|---|
| 1 | 20 × 20 × 20 mm cube | 15% | Grid | 2 | 4 / 4 | 0.2 mm | 3.96 | 3.71 | -0.25 | -6.31% | These settings were reported for Test 1. Other profile/version details, nozzle, material density and support setting were not included. |
| 2 | Different STL; geometry not documented | 100% | Not documented | Not documented | Not documented | Not documented | 5.62 | 5.81 | +0.19 | +3.38% | This is not the cube from Test 1. Other settings and geometry are unknown. |

## Calibration cube — controlled infill sweep

All six rows below use the exact same calibration cube and Grid infill. The user confirms that all other slicing settings were identical and only infill percentage changed. Specific fixed setting values, selected profiles, versions, and STL hash remain undocumented. The sweep has not been independently reproduced.

| Infill | Pattern | Bambu Studio (g) | Instant3D Slicer (g) | Absolute difference (g) | Signed error (App−Bambu, g) | Percentage error |
|---:|---|---:|---:|---:|---:|---:|
| 5% | Grid | 3.44 | 3.01 | 0.43 | -0.43 | -12.50% |
| 15% | Grid | 3.96 | 3.71 | 0.25 | -0.25 | -6.31% |
| 25% | Grid | 4.59 | 4.41 | 0.18 | -0.18 | -3.92% |
| 50% | Grid | 6.27 | 6.16 | 0.11 | -0.11 | -1.75% |
| 75% | Grid | 7.90 | 7.91 | 0.01 | +0.01 | +0.13% |
| 100% | Grid | 9.55 | 9.66 | 0.11 | +0.11 | +1.15% |

The unweighted mean absolute percentage error across these six measurements is **4.29%**. It is a provisional summary of this one geometry and these reported runs, not a general accuracy estimate or an error bound. The fixed settings are confirmed to have been held constant within the sweep, which permits infill trend analysis. Their actual values and several comparison metadata items remain unknown.

### Focused mathematical interpretation

For fixed model, material, printer, and all other estimator settings, and with support settings unchanged, the app equation can be written:

- Core volume = model volume − wall volume − top shell volume − bottom shell volume.
- Total volume = wall volume + top shell volume + bottom shell volume + support volume + core volume × infill fraction.
- App grams = total volume ÷ 1,000 × material density.

Thus app mass is linear in infill fraction under this model. Walls, shells, supports, density, and geometry contribute a constant term for the sweep; the core contributes a term proportional to infill percentage.

The six reported app masses lie exactly on the line, to the displayed 0.01 g precision, App grams = 2.66 + 0.07 × infill percentage (where percentage is expressed from 0 to 100, e.g. 5 for 5%). This is a fit to the reported rounded points, not a recovered decomposition of the code's wall, shell, and core volumes. It implies an extrapolated 0% app value of 2.66 g and a measured-rise rate of 0.07 g per percentage point over the sweep. The 2.66 g intercept combines all fixed contributions and cannot separate walls from shells or supports.

Across 5% to 100%, app mass rises 6.65 g; Bambu Studio rises 6.11 g. From 75% to 100%, the increases are 1.75 g app and 1.65 g Bambu. If the comparison's reported grams refer to the same material and same included parts of the print, this is consistent with the app's infill-dependent contribution rising faster than Bambu's over this interval. It does not identify why. Bambu's output also need not be linear with nominal infill percentage because Grid path layout, overlap, gap fill, and slicer decisions affect actual toolpaths.

The app error progresses from -0.43 g at 5% to +0.11 g at 100%, crossing near 75%. A lower fixed app contribution combined with a larger app infill slope than Bambu is one mathematical explanation compatible with this pattern. It is not proven that the app's baseline is low: observed Bambu mass includes its own path choices, and the app/Bambu mass basis, material density, flow, line width, and other fixed settings have not been documented.

### What the controlled sweep establishes — and does not establish

- **Established by the user confirmation:** same calibration cube; Grid infill for every row; other slicing settings fixed within the sweep; infill percentage was the only changed slicing setting.
- **Established by the implementation:** the app's infill contribution is linear in the remaining core volume and infill percentage; the selected pattern does not change the app calculation.
- **Established by the reported values:** app results follow an exactly linear trend at the displayed precision, and signed error shifts from negative to slightly positive across the sweep.
- **Not established:** whether the app's baseline wall/shell estimate is itself too low, whether the app infill slope is physically too high, which Bambu path details cause its changing marginal mass, or whether any material density / reported-mass basis mismatch exists.

A controlled infill sweep supports analyzing the response to infill but does not by itself distinguish a baseline offset from a slope difference in the two estimation systems. The selected material density and the grams included in each result must be confirmed before interpreting grams as directly comparable.

## Separate STL result

The earlier 100% infill result is from a different STL and remains separate from the calibration cube sweep:

- Bambu Studio: 5.62 g
- Instant3D Slicer: 5.81 g
- Signed difference: +0.19 g
- Percentage error: +3.38%

The second model's geometry and nearly all settings are undocumented. It is not included in the cube-sweep MAPE and cannot be used to identify a cause for the cube's trend.

## Review of the current mathematics

### Walls

The current engine estimates wall volume as:

min(model volume, side-facet area × wall count × nozzle diameter)

Horizontal facets are excluded from side-facet area so top and bottom faces are not counted again as walls. Wall volume is capped at model volume.

This remains a surface-area proxy. It does not offset contours or resolve corners, thin walls, holes, variable perimeter counts, overlap, or actual extrusion line width. The engine assumes line width equals nozzle diameter; real profiles can use a different line width. These are approximation limits, not a confirmed unit-conversion error.

### Top, bottom, core, and infill

Each raw shell estimate is:

XY bounding-box area × shell layer count × layer height

If both shells exceed the model volume remaining after walls, their volumes are scaled proportionally so neither shell receives arbitrary priority and the core cannot become negative. Infill is:

remaining core volume × infill percentage / 100

The box footprint can overstate shell coverage for hollow, curved, angled, or irregular layers. The engine also does not know which individual layers become solid or how a slicer handles gaps and skin overlap. At 100% infill with supports off, model-material volume equals mesh volume by construction, subject to floating-point calculations; slicer's path volume can still differ.

Layer height changes the shell thickness estimate. It does not otherwise change modeled wall or infill paths. Nozzle diameter is used as a wall-width proxy; it is not a separate extrusion-width setting. Although the UI records a selected infill pattern, the engine does not use it to change nominal infill volume.

### Supports

For downward-facing facets beyond the 45° threshold, the engine estimates projected facet area × centroid height above the build plate, then scales by support density. It corrects facet normals for globally reversed winding and caps support volume to the density-scaled free portion of the model bounding envelope.

This is not support generation. Overlapping projected facets are not unioned before the cap; tree supports, interface layers, blockers, contact distances, build-plate-only options, and intersections with the model are not calculated. Support error cannot be assessed from the reported comparisons because their support settings and mass inclusion are not recorded.

### Weight, filament length, and cost

The unit conversions are dimensionally consistent:

- Material grams = total mm³ ÷ 1,000 × density in g/cm³.
- Filament length in metres = total mm³ ÷ [π × (filament diameter in mm ÷ 2)²] ÷ 1,000.
- Material cost in PKR = estimated grams ÷ spool grams × spool price in PKR.

They assume the configured density, nominal filament diameter, spool mass, and spool price are accurate. Actual filament diameter, density, flow calibration, and slicer extrusion behavior can differ. Extreme or non-finite results are rejected.

### Issue classification

- **Confirmed mathematical issue:** No arithmetic or unit-conversion defect was established in the reviewed current equations. The engine's pattern choice being ignored is a confirmed implementation limitation, not by itself an arithmetic bug.
- **Plausible source of error:** Surface-area wall proxy, bounding-rectangle shells, linear nominal infill fraction versus actual Grid paths, and projected support approximation can differ from slicer paths. The controlled sweep's changing error is compatible with a baseline offset plus differing infill response, but does not prove that explanation.
- **Unknown pending further experiments:** Whether the measured baseline is inaccurate, whether the app's infill slope is too high in physical material terms, how much of the difference comes from the slicer settings/material/flow/reporting basis, and whether the trend reproduces on another geometry.

No multiplier, hardcoded correction, or result-specific adjustment has been applied.

## Recommended next experiments

1. **Repeat the controlled Grid cube sweep** at 5%, 15%, 25%, 50%, 75%, and 100%, preserving the exact STL bytes and recording their SHA-256 hash. Record all setting values and profiles even though they are held constant: printer/nozzle, line width, layer height, walls, top/bottom layers, support state/settings, material and density, filament diameter, flow ratio, orientation, software versions, and which reported mass field is used. Repeat runs to assess reproducibility.
2. **Measure the zero-infill baseline** on the same cube with the same fixed settings, where supported. This directly constrains the combined wall/shell/support baseline. It still does not separate wall from shell contributions.
3. **Vary wall count alone** on the cube while keeping all other settings fixed. This tests the wall proxy's response. Vary shell layers alone in a separate sweep to test the shell proxy.
4. **Compare patterns at one fixed infill percentage**, such as Grid versus Gyroid at 50%, with all other settings fixed. The app is expected to return the same nominal volume across patterns; the slicer difference will quantify pattern-specific path effects.
5. **Use additional known geometries:** a rectangular block and hollow cylinder for shell footprint and wall-to-volume behavior, and an overhang model for supports. Record dimensions and known mesh volume; preserve model orientation.
6. **Compare support off/on** on the same overhang model, recording support type, threshold, density, interface settings, build-plate-only behavior, and whether the displayed slicer grams include supports and interface material.

Compare model-only material where possible. Record brim, skirt, raft, purge/flush, prime tower, and other extra extrusion separately. Keep the separate unknown STL comparison as its own row.

## Repeatable comparison procedure

1. Use identical STL bytes in Instant3D Slicer and Bambu Studio; retain the file hash and do not repair/re-export between runs.
2. Record app/slicer versions, Instant3D Slicer commit, printer profile, material profile, all relevant settings, and the STL hash.
3. Change only the variable under test; preserve the same orientation and all other settings.
4. Disable or separately record skirt, brim, raft, purge/flush, prime tower, and other non-model extrusion. Use one object and one filament.
5. Compare slicer's model-filament grams with the app estimate. If only whole-job mass is available, identify included purge/support/interface material.
6. Compute signed error and percentage error using the convention at the top of this document. Keep Bambu Studio and OrcaSlicer rows separate.
7. Preserve slicer project files and screenshots/exported reports. Reserve at least one geometry as a holdout for any future model changes.

## Calibration geometries

All dimensions are millimetres.

| ID | Geometry | Construction | Known mesh volume |
|---|---|---|---:|
| G1 | Calibration cube | 20 × 20 × 20 | 8,000 mm³ |
| G2 | Rectangular block | 30 × 20 × 10 | 6,000 mm³ |
| G3 | Hollow vertical cylinder | Outer radius 10, inner radius 8, height 20, 96-sided polygonal rings, base at Z=0 | [96/2 × sin(2π/96) × (10²−8²)] × 20 mm³ |
| G4 | Sloped overhang prism | Extrude 20 mm along Y the X-Z polygon (0,10), (20,0), (20,20), (0,20); Y=0…20 | 6,000 mm³ |

The G3 polygon construction is intentional: it makes the hollow geometry deterministic. Record the app's analyzed mesh volume before comparing filament estimates.

## Test matrix

The following is a proposed controlled test plan, not a record of completed tests. For each sweep, keep all other baseline settings fixed: G1 cube, 0.4 mm nozzle, 0.2 mm layer height, 2 walls, 4/4 shells, 15% Gyroid, supports off, PLA 1.240 g/cm³, and 1.75 mm filament. These are proposed values and do not establish the values used in the reported Grid sweep. In the geometry sweep, use the same settings for each model.

| Sweep | Geometry | Infill / pattern | Walls | Top / bottom | Bambu Studio (g) | Instant3D Slicer (g) | Absolute error (App−Bambu, g) | Percentage error |
|---|---|---|---:|---|---:|---:|---:|---:|
| Infill | G1 cube | 10% Gyroid | 2 | 4 / 4 |  |  |  |  |
| Infill | G1 cube | 15% Gyroid | 2 | 4 / 4 |  |  |  |  |
| Infill | G1 cube | 20% Gyroid | 2 | 4 / 4 |  |  |  |  |
| Infill | G1 cube | 30% Gyroid | 2 | 4 / 4 |  |  |  |  |
| Infill | G1 cube | 100% (record pattern) | 2 | 4 / 4 |  |  |  |  |
| Wall count | G1 cube | 15% Gyroid | 2 | 4 / 4 |  |  |  |  |
| Wall count | G1 cube | 15% Gyroid | 3 | 4 / 4 |  |  |  |  |
| Wall count | G1 cube | 15% Gyroid | 4 | 4 / 4 | 2 | 4 / 4 |  |  |  |  |
| Shell layers | G1 cube | 15% Gyroid | 2 | 3 / 3 |  |  |  |  |
| Shell layers | G1 cube | 15% Gyroid | 2 | 4 / 4 |  |  |  |  |
| Shell layers | G1 cube | 15% Gyroid | 2 | 5 / 5 |  |  |  |  |
| Geometry | G1 cube | 15% Gyroid | 2 | 4 / 4 |  |  |  |  |
| Geometry | G2 block | 15% Gyroid | 2 | 4 / 4 |  |  |  |  |
| Geometry | G3 hollow cylinder | 15% Gyroid | 2 | 4 / 4 |  |  |  |  |
| Geometry | G4 overhang prism | 15% Gyroid | 2 | 4 / 4 |  |  |  |  |

Run an additional G4 support-on comparison as a separate test. Record support settings rather than assuming they match the engine's projected-column approximation.

## Earlier user-supplied results (kept separate)

The separate STL test is not included in the cube-sweep MAPE.

| Test | Bambu Studio (g) | Instant3D Slicer (g) | Error (g) | Percentage error |
|---|---:|---:|---:|---:|
| Test 1 — 15% Grid cube | 3.96 | 3.71 | -0.25 | -6.31% |
| Test 2 — separate STL, 100% infill; other settings/geometry incomplete | 5.62 | 5.81 | +0.19 | +3.38% |

## Preliminary quotation suitability

The estimator is suitable only for a clearly labeled preliminary material-use indication, not a firm or slicer-equivalent quote. The controlled one-geometry sweep improves evidence about how the output changes with infill, but does not establish general accuracy or an error range. For customer-facing fixed quotes, verify with a slicer until the matrix includes several controlled geometries and settings.

Do not fit a global multiplier to these results. Any future calibration should be justified by repeated controlled comparisons, evaluated across the full matrix, and checked against held-out geometries.
