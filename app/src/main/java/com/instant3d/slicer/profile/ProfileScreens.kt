package com.instant3d.slicer.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun MaterialManagementScreen(repository: ProfileRepository) {
    val materials by repository.materials.collectAsState(emptyList())
    val prefs by repository.preferences.collectAsState(null)
    val scope = rememberCoroutineScope()
    var editor by remember { mutableStateOf<MaterialProfile?>(null) }
    var adding by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    Scaffold(floatingActionButton = { Button({ adding = true }) { Text("＋ Add material") } }) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.padding(bottom = 4.dp)) {
                    Text("Material management", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("Offline filament profiles for future print calculations.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(materials, key = { it.id }) { m ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (prefs?.defaultMaterialId == m.id) Text("DEFAULT", color = MaterialTheme.colorScheme.primary)
                        }
                        Text("Density %.3f g/cm³  •  Diameter %.2f mm".format(m.densityGPerCm3, m.filamentDiameterMm))
                        Text("Spool %.0f g  •  PKR %.0f".format(m.spoolWeightGrams, m.spoolPricePkr), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            TextButton({ scope.launch { repository.setDefaultMaterial(m.id) } }) { Text("Set default") }
                            TextButton({ editor = m }) { Text("Edit") }
                            if (!m.isBuiltIn) TextButton({ scope.launch { repository.deleteCustomMaterial(m.id) } }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            formError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item { Text("Preset densities are typical values. Adjust them to match your filament manufacturer.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 24.dp)) }
        }
    }
    val target = editor
    if (adding || target != null) {
        MaterialDialog(target, formError, onDismiss = { adding = false; editor = null; formError = null }) { name, density, diameter, weight, price ->
            scope.launch {
                try {
                    repository.saveMaterial(MaterialProfile(
                        id = target?.id ?: "custom-${UUID.randomUUID()}",
                        name = name,
                        densityGPerCm3 = density.toDoubleOrNull() ?: 0.0,
                        filamentDiameterMm = diameter.toDoubleOrNull() ?: 0.0,
                        spoolWeightGrams = weight.toDoubleOrNull() ?: 0.0,
                        spoolPricePkr = price.toDoubleOrNull() ?: -1.0,
                        isBuiltIn = target?.isBuiltIn ?: false,
                    ))
                    adding = false; editor = null; formError = null
                } catch (e: IllegalArgumentException) { formError = e.message }
            }
        }
    }
}

@Composable
private fun MaterialDialog(initial: MaterialProfile?, error: String?, onDismiss: () -> Unit, onSave: (String,String,String,String,String) -> Unit) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var density by remember(initial?.id) { mutableStateOf(initial?.densityGPerCm3?.toString() ?: "") }
    var diameter by remember(initial?.id) { mutableStateOf(initial?.filamentDiameterMm?.toString() ?: "1.75") }
    var weight by remember(initial?.id) { mutableStateOf(initial?.spoolWeightGrams?.toString() ?: "1000") }
    var price by remember(initial?.id) { mutableStateOf(initial?.spoolPricePkr?.toString() ?: "0") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "Add material" else "Edit material") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(name, { name=it }, label={Text("Name")}, singleLine=true)
            OutlinedTextField(density, { density=it }, label={Text("Density (g/cm³)")}, singleLine=true)
            OutlinedTextField(diameter, { diameter=it }, label={Text("Filament diameter (mm)")}, singleLine=true)
            OutlinedTextField(weight, { weight=it }, label={Text("Spool weight (g)")}, singleLine=true)
            OutlinedTextField(price, { price=it }, label={Text("Spool price (PKR)")}, singleLine=true)
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton={ TextButton({onSave(name,density,diameter,weight,price)}) {Text("Save")} }, dismissButton={TextButton(onDismiss){Text("Cancel")}})
}

@Composable
fun PrinterProfilesScreen(repository: ProfileRepository) {
    val printers by repository.printers.collectAsState(emptyList())
    val prefs by repository.preferences.collectAsState(null)
    val scope = rememberCoroutineScope()
    var editor by remember { mutableStateOf<PrinterProfile?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=18.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Column(Modifier.padding(top=18.dp,bottom=4.dp)) {
            Text("Printer profiles", style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.SemiBold)
            Text("Published manufacturer build volumes and included nozzle defaults; editable for your setup.", color=MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        items(printers,key={it.id}) { p ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(p.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                    if(prefs?.defaultPrinterId==p.id) Text("DEFAULT",color=MaterialTheme.colorScheme.primary)
                }
                Text("%.0f × %.0f × %.0f mm".format(p.buildXmm,p.buildYmm,p.buildZmm))
                Text("Nozzle %.2f mm".format(p.nozzleDiameterMm),color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    TextButton({scope.launch{repository.setDefaultPrinter(p.id)}}){Text("Set default")}
                    TextButton({editor=p}){Text("Edit values")}
                }
            } }
        }
        item { Text("Specifications: [A1](https://cdn1.bambulab.com/documentation/quick-start-a75adcb1d5d5e/Quick%20Start%20Guide%20for%20A1.pdf), [A1 Mini](https://cdn1.bambulab.com/documentation/quick-start-f507128172bdf/Quick%20start%20guide%20-%20A1%20mini-EN.pdf), [P1P](https://public-cdn.bambulab.com/store/bambulab-P1P-tech-specs.pdf), [P1S](https://cdn1.bambulab.com/documentation/quick-start-59b0cefdc0fc4/P1S/English%20version-Quick%20Start%20Guide%20for%20P1S.pdf), [X1 Carbon](https://public-cdn.bambulab.com/store/bambulab-X1-carbon-tech-specs.pdf).", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.padding(bottom=24.dp)) }
    }
    editor?.let { p -> PrinterDialog(p, {editor=null}) { x,y,z,n ->
        scope.launch {
            try {
                repository.savePrinter(p.copy(buildXmm=x.toDoubleOrNull()?:0.0,buildYmm=y.toDoubleOrNull()?:0.0,buildZmm=z.toDoubleOrNull()?:0.0,nozzleDiameterMm=n.toDoubleOrNull()?:0.0))
                editor=null
            } catch (_: IllegalArgumentException) { }
        }
    } }
}

@Composable
private fun PrinterDialog(p: PrinterProfile,onDismiss:()->Unit,onSave:(String,String,String,String)->Unit) {
    var x by remember(p.id){mutableStateOf(p.buildXmm.toString())}
    var y by remember(p.id){mutableStateOf(p.buildYmm.toString())}
    var z by remember(p.id){mutableStateOf(p.buildZmm.toString())}
    var n by remember(p.id){mutableStateOf(p.nozzleDiameterMm.toString())}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Configure ${p.name}")},text={
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
            OutlinedTextField(x,{x=it},label={Text("Build X (mm)")},singleLine=true)
            OutlinedTextField(y,{y=it},label={Text("Build Y (mm)")},singleLine=true)
            OutlinedTextField(z,{z=it},label={Text("Build Z (mm)")},singleLine=true)
            OutlinedTextField(n,{n=it},label={Text("Nozzle (mm)")},singleLine=true)
        }
    },confirmButton={TextButton({onSave(x,y,z,n)}){Text("Save")}},dismissButton={TextButton(onDismiss){Text("Cancel")}})
}
