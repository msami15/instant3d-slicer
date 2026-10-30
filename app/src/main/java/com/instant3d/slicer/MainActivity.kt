package com.instant3d.slicer

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.instant3d.slicer.stl.StlModel
import com.instant3d.slicer.stl.StlParser
import com.instant3d.slicer.stl.StlViewer
import com.instant3d.slicer.profile.ProfileDatabase
import com.instant3d.slicer.profile.ProfileRepository
import com.instant3d.slicer.profile.MaterialManagementScreen
import com.instant3d.slicer.profile.PrinterProfilesScreen
import com.instant3d.slicer.estimator.EstimatorScreen
import com.instant3d.slicer.ui.theme.Instant3DSlicerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_STL_BYTES = 20 * 1024 * 1024

private fun readBytesAtMost(input: java.io.InputStream, maxBytes: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(32 * 1024)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        require(count <= maxBytes - total) { "This file is larger than the 20 MB import limit." }
        output.write(buffer, 0, count)
        total += count
    }
    return output.toByteArray()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Instant3DSlicerTheme { SlicerApp() } }
    }
}

private data class Destination(val route: String, val label: String, val symbol: String)

private val destinations = listOf(
    Destination("home", "Home", "⌂"),
    Destination("projects", "Projects", "▦"),
    Destination("calculator", "Calculator", "⌗"),
    Destination("materials", "Materials", "◈"),
    Destination("settings", "Settings", "⚙"),
)

@Composable
private fun SlicerApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileRepository = remember { ProfileRepository(ProfileDatabase.get(context).profileDao()) }
    LaunchedEffect(profileRepository) { withContext(Dispatchers.IO) { profileRepository.ensureDefaults() } }
    var selectedModel by remember { mutableStateOf<StlModel?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            importing = true
            importError = null
            scope.launch {
                try {
                    val (name, bytes) = withContext(Dispatchers.IO) {
                        val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Imported model.stl"
                        val bytes = context.contentResolver.openInputStream(uri)?.use {
                            readBytesAtMost(it, MAX_STL_BYTES)
                        } ?: error("Android could not open this file.")
                        name to bytes
                    }
                    val model = withContext(Dispatchers.Default) { StlParser.parse(bytes) }
                    selectedFileName = name
                    selectedModel = model
                    navController.navigate("model") { launchSingleTop = true }
                } catch (error: Exception) {
                    importError = error.message ?: "Unable to import this STL file."
                } finally {
                    importing = false
                }
            }
        }
    }
    val importStl: () -> Unit = { filePicker.launch(arrayOf("*/*")) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute != "model") {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(destination.symbol, fontSize = 20.sp) },
                            label = { Text(destination.label) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(onImport = importStl, importing = importing, error = importError) }
            composable("projects") { ProjectsScreen(onImport = importStl, importing = importing, error = importError) }
            composable("model") {
                selectedModel?.let { model ->
                    ModelScreen(
                        fileName = selectedFileName,
                        model = model,
                        onBack = { navController.popBackStack() },
                        onImportAnother = importStl,
                        importing = importing,
                        error = importError,
                    )
                }
            }
            composable("calculator") { EstimatorScreen(selectedModel, selectedFileName, importStl, profileRepository) }
            composable("materials") { MaterialManagementScreen(profileRepository) }
            composable("printer_profiles") { PrinterProfilesScreen(profileRepository) }
            composable("settings") {
                Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text("Choose and configure your local printer profile.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { navController.navigate("printer_profiles") }) { Text("Manage printer profiles") }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onImport: () -> Unit, importing: Boolean, error: String?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column {
            Text("INSTANT3D", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(5.dp))
            Text("Material estimator", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("A clearer view of every print.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("●", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    Text("OFFLINE WORKSPACE", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Text("Inspect a 3D model", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Import an STL file to view the model, check its dimensions and inspect its mesh.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onImport, enabled = !importing, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                    Text(if (importing) "Importing…" else "＋   Import STL")
                }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("PROJECTS", "0", Modifier.weight(1f))
            MetricCard("MATERIALS", "0", Modifier.weight(1f))
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ESTIMATION STATUS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("Model measurements are available in this phase. Filament estimates will be added separately.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ProjectsScreen(onImport: () -> Unit, importing: Boolean, error: String?) {
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Projects", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("Your model workspace", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("▦", color = MaterialTheme.colorScheme.primary, fontSize = 28.sp)
                Text("No projects yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Import an STL file to start inspecting a model.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onImport, enabled = !importing) { Text(if (importing) "Importing…" else "Import STL model") }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ModelScreen(
    fileName: String,
    model: StlModel,
    onBack: () -> Unit,
    onImportAnother: () -> Unit,
    importing: Boolean,
    error: String?,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("MODEL INSPECTOR", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(fileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Button(onClick = onBack) { Text("Back") }
        }
        StlViewer(model, Modifier.fillMaxWidth().weight(1f))
        Text("One finger: rotate  •  Two fingers: pan and pinch to zoom", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("BOUNDING BOX · mm", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(Modifier.fillMaxWidth()) {
                    Text("AXIS", modifier = Modifier.weight(0.65f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("MIN", modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("MAX", modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("SIZE", modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BoundingBoxRow("X", model.min.x, model.max.x, model.sizeX)
                BoundingBoxRow("Y", model.min.y, model.max.y, model.sizeY)
                BoundingBoxRow("Z", model.min.z, model.max.z, model.sizeZ)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    InfoValue("VOLUME", if (model.isClosed) String.format("%.3f cm³", model.volumeCm3) else "Unavailable")
                    InfoValue("SURFACE AREA", String.format("%.2f cm²", model.surfaceAreaCm2))
                    InfoValue("TRIANGLES", model.triangles.size.toString())
                }
                Text("MESH INTEGRITY", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                if (model.integrity.isValid) {
                    Text("Watertight, consistently oriented mesh with no degenerate facets.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (model.integrity.openEdgeCount > 0) IntegrityWarning("Open edges", model.integrity.openEdgeCount)
                        if (model.integrity.nonManifoldEdgeCount > 0) IntegrityWarning("Non-manifold edges", model.integrity.nonManifoldEdgeCount)
                        if (model.integrity.inconsistentEdgeCount > 0) IntegrityWarning("Inconsistently oriented edges", model.integrity.inconsistentEdgeCount)
                        if (model.integrity.degenerateTriangleCount > 0) IntegrityWarning("Degenerate triangles", model.integrity.degenerateTriangleCount)
                    }
                }
            }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = onImportAnother,
            enabled = !importing,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (importing) "Importing…" else "＋  Import another STL") }
    }
}

@Composable
private fun BoundingBoxRow(axis: String, min: Float, max: Float, size: Float) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(axis, modifier = Modifier.weight(0.65f), fontWeight = FontWeight.SemiBold)
        Text(String.format("%.2f", min), modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text(String.format("%.2f", max), modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text(String.format("%.2f", size), modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun IntegrityWarning(label: String, count: Int) {
    Text(
        "$label: $count",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun InfoValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PlaceholderScreen(title: String, description: String) {
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
