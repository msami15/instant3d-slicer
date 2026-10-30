package com.instant3d.slicer.estimator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.instant3d.slicer.profile.*
import com.instant3d.slicer.stl.StlModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EstimatorScreen(model: StlModel?, modelName: String, onImport: () -> Unit, repository: ProfileRepository) {
    val materials by repository.materials.collectAsState(emptyList())
    val printers by repository.printers.collectAsState(emptyList())
    val preferences by repository.preferences.collectAsState(null)
    val scope = rememberCoroutineScope()
    var materialId by remember { mutableStateOf<String?>(null) }
    var printerId by remember { mutableStateOf<String?>(null) }
    var layer by remember { mutableStateOf("0.20") }
    var nozzle by remember { mutableStateOf("0.40") }
    var wallCount by remember { mutableStateOf("2") }
    var infill by remember { mutableStateOf("15") }
    var top by remember { mutableStateOf("4") }
    var bottom by remember { mutableStateOf("4") }
    var supportDensity by remember { mutableStateOf("15") }
    var supports by remember { mutableStateOf(false) }
    var pattern by remember { mutableStateOf(InfillPattern.GYROID) }
    var result by remember { mutableStateOf<PrintEstimate?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(materials, printers, preferences) {
        if (preferences != null && materialId == null && materials.isNotEmpty()) {
            materialId = materials.firstOrNull { it.id == preferences.defaultMaterialId }?.id ?: materials.first().id
        }
        if (preferences != null && printerId == null && printers.isNotEmpty()) {
            printerId = printers.firstOrNull { it.id == preferences.defaultPrinterId }?.id ?: printers.first().id
            printers.firstOrNull { it.id == printerId }?.let { nozzle = "%.2f".format(it.nozzleDiameterMm) }
        }
    }
    LaunchedEffect(model) { result = null; error = null }
    val material = materials.firstOrNull { it.id == materialId }
    val printer = printers.firstOrNull { it.id == printerId }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(top = 16.dp)) {
                Text("Print estimator", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Offline geometric approximation, not slicer output.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (model == null) {
            item {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose an STL model to estimate.")
                    Button(onClick = onImport) { Text("Import STL") }
                } }
            }
        } else {
            item {
                EstimatorCard("MODEL") {
                    Text(modelName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Bounds %.1f × %.1f × %.1f mm".format(model.sizeX, model.sizeY, model.sizeZ))
                    Text("Mesh volume %.3f cm³".format(model.volumeCm3))
                    Button(onClick = onImport) { Text("Choose another STL") }
                }
            }
            item {
                EstimatorCard("PROFILES") {
                    ProfilePicker("Printer", printer?.name ?: "Select printer", printers.map { it.id to it.name }) {
                        printerId = it
                        printers.firstOrNull { profile -> profile.id == it }?.let { profile -> nozzle = "%.2f".format(profile.nozzleDiameterMm) }
                        result = null
                    }
                    ProfilePicker("Material", material?.name ?: "Select material", materials.map { it.id to it.name }) { materialId = it; result = null }
                    Text(printer?.let { "Build volume: %.0f × %.0f × %.0f mm".format(it.buildXmm, it.buildYmm, it.buildZmm) } ?: "Build volume: —")
                    Text(material?.let { "Density: %.3f g/cm³".format(it.densityGPerCm3) } ?: "Density: —")
                }
            }
            item {
                EstimatorCard("PRINT SETTINGS") {
                    NumberInput("Layer height (mm)", layer, { layer = it; result = null }, true)
                    NumberInput("Nozzle diameter (mm)", nozzle, { nozzle = it; result = null }, true)
                    NumberInput("Wall / perimeter count", wallCount, { wallCount = it; result = null })
                    NumberInput("Infill (%)", infill, { infill = it; result = null }, true)
                    ProfilePicker("Infill pattern", pattern.displayName, InfillPattern.values().map { it.name to it.displayName }) {
                        pattern = InfillPattern.valueOf(it); result = null
                    }
                    NumberInput("Top shell layers", top, { top = it; result = null })
                    NumberInput("Bottom shell layers", bottom, { bottom = it; result = null })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Generate supports", fontWeight = FontWeight.Medium)
                            Text("Estimate downward-facing overhangs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(supports, { supports = it; result = null })
                    }
                    if (supports) NumberInput("Support density (%)", supportDensity, { supportDensity = it; result = null }, true)
                }
            }
            item {
                Button(onClick = {
                    if (material == null || printer == null) {
                        error = "Material and printer profiles are loading."
                    } else {
                        busy = true; error = null
                        scope.launch {
                            try {
                                val settings = EstimateSettings(
                                    layerHeightMm = layer.toDoubleOrNull() ?: Double.NaN,
                                    nozzleDiameterMm = nozzle.toDoubleOrNull() ?: Double.NaN,
                                    wallCount = wallCount.toIntOrNull() ?: -1,
                                    infillPercent = infill.toDoubleOrNull() ?: Double.NaN,
                                    infillPattern = pattern,
                                    topShellLayers = top.toIntOrNull() ?: -1,
                                    bottomShellLayers = bottom.toIntOrNull() ?: -1,
                                    supportsEnabled = supports,
                                    supportDensityPercent = supportDensity.toDoubleOrNull() ?: Double.NaN,
                                )
                                result = withContext(Dispatchers.Default) {
                                    FilamentEstimator.estimate(model, material, printer, settings)
                                }
                            } catch (e: IllegalArgumentException) {
                                error = e.message ?: "Unable to estimate this model."
                            } finally { busy = false }
                        }
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    if (busy) CircularProgressIndicator() else Text("Estimate material and cost")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            result?.let { estimate ->
                item { EstimateResults(estimate, material, printer) }
            }
            item {
                Text(
                    "Approximation only: this does not slice layers, offset perimeter paths, plan machine-specific flow, or generate collision-aware supports. Use a slicer for print-ready estimates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun EstimatorCard(title: String, content: @Composable Column.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ProfilePicker(label: String, selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(onClick = { expanded = true }, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(selected)
                Text("⌄", color = MaterialTheme.colorScheme.primary)
                DropdownMenu(expanded, { expanded = false }) {
                    options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false }) }
                }
            }
        }
    }
}

@Composable
private fun NumberInput(label: String, value: String, onChange: (String) -> Unit, decimal: Boolean = false) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}

@Composable
private fun EstimateResults(result: PrintEstimate, material: MaterialProfile?, printer: PrinterProfile?) {
    EstimatorCard("ESTIMATED RESULTS") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryValue("FILAMENT", "%.2f g".format(result.weightGrams))
            SummaryValue("LENGTH", "%.2f m".format(result.filamentLengthMeters))
            SummaryValue("COST", if ((material?.spoolPricePkr ?: 0.0) > 0.0) "PKR %.2f".format(result.costPkr) else "Set spool price")
        }
        Text("Model volume: %.3f cm³".format(result.modelVolumeMm3 / 1000.0), fontWeight = FontWeight.Medium)
        Text((printer?.name ?: "Printer") + " • " + (material?.name ?: "Material"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Settings: %.2f mm layer, %.2f mm nozzle, %d walls, %.0f%% %s infill, %d top / %d bottom layers%s".format(
                result.settings.layerHeightMm, result.settings.nozzleDiameterMm, result.settings.wallCount,
                result.settings.infillPercent, result.settings.infillPattern.displayName,
                result.settings.topShellLayers, result.settings.bottomShellLayers,
                if (result.settings.supportsEnabled) ", supports %.0f%%".format(result.settings.supportDensityPercent) else "",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Material use breakdown", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        BreakdownLine("Walls", result.breakdown.wallsMm3)
        BreakdownLine("Infill", result.breakdown.infillMm3)
        BreakdownLine("Top shell", result.breakdown.topShellMm3)
        BreakdownLine("Bottom shell", result.breakdown.bottomShellMm3)
        BreakdownLine("Supports", result.breakdown.supportsMm3)
        BreakdownLine("Total extruded", result.totalVolumeMm3, true)
        Text("Pattern is recorded but does not alter nominal volume in this approximation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BreakdownLine(label: String, volume: Double, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
        Text("%.1f mm³ (%.2f cm³)".format(volume, volume / 1000.0), fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
    }
}
