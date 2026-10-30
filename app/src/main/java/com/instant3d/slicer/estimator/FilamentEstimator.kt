package com.instant3d.slicer.estimator

import com.instant3d.slicer.profile.MaterialProfile
import com.instant3d.slicer.profile.PrinterProfile
import com.instant3d.slicer.stl.Point3
import com.instant3d.slicer.stl.StlModel
import com.instant3d.slicer.stl.StlTriangle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class InfillPattern(val displayName: String) {
    GRID("Grid"),
    GYROID("Gyroid"),
    LINES("Lines"),
    HONEYCOMB("Honeycomb"),
}

data class EstimateSettings(
    val layerHeightMm: Double = 0.2,
    val nozzleDiameterMm: Double = 0.4,
    val wallCount: Int = 2,
    val infillPercent: Double = 15.0,
    val infillPattern: InfillPattern = InfillPattern.GYROID,
    val topShellLayers: Int = 4,
    val bottomShellLayers: Int = 4,
    val supportsEnabled: Boolean = false,
    val supportDensityPercent: Double = 15.0,
)

data class VolumeBreakdown(
    val wallsMm3: Double,
    val infillMm3: Double,
    val topShellMm3: Double,
    val bottomShellMm3: Double,
    val supportsMm3: Double,
) {
    val totalMm3: Double
        get() = wallsMm3 + infillMm3 + topShellMm3 + bottomShellMm3 + supportsMm3
}

data class PrintEstimate(
    val modelVolumeMm3: Double,
    val breakdown: VolumeBreakdown,
    val totalVolumeMm3: Double,
    val weightGrams: Double,
    val filamentLengthMeters: Double,
    val costPkr: Double,
    val settings: EstimateSettings,
)

object FilamentEstimator {
    private const val MAX_WALLS = 20
    private const val MAX_SHELL_LAYERS = 50
    private val OVERHANG_NORMAL_THRESHOLD = sqrt(0.5)

    fun estimate(
        model: StlModel,
        material: MaterialProfile,
        printer: PrinterProfile,
        settings: EstimateSettings,
    ): PrintEstimate {
        validate(model, material, printer, settings)

        val modelVolume = model.volumeMm3
        val wallThickness = settings.wallCount * settings.nozzleDiameterMm
        val rawWallVolume = model.surfaceAreaMm2 * wallThickness
        val wallVolume = min(modelVolume, rawWallVolume)

        val remainingAfterWalls = max(0.0, modelVolume - wallVolume)
        val footprintMm2 = model.sizeX.toDouble() * model.sizeY.toDouble()
        val topRaw = footprintMm2 * settings.topShellLayers * settings.layerHeightMm
        val topVolume = min(remainingAfterWalls, topRaw)
        val remainingAfterTop = remainingAfterWalls - topVolume
        val bottomRaw = footprintMm2 * settings.bottomShellLayers * settings.layerHeightMm
        val bottomVolume = min(remainingAfterTop, bottomRaw)

        val coreVolume = max(0.0, remainingAfterTop - bottomVolume)
        val infillVolume = coreVolume * (settings.infillPercent / 100.0)
        val supportsVolume = if (settings.supportsEnabled) {
            estimateSupportVolume(model, settings.supportDensityPercent)
        } else 0.0

        val breakdown = VolumeBreakdown(
            wallsMm3 = wallVolume,
            infillMm3 = infillVolume,
            topShellMm3 = topVolume,
            bottomShellMm3 = bottomVolume,
            supportsMm3 = supportsVolume,
        )
        val totalVolume = breakdown.totalMm3
        val weightGrams = (totalVolume / 1000.0) * material.densityGPerCm3
        val filamentCrossSectionMm2 = Math.PI * (material.filamentDiameterMm / 2.0).let { it * it }
        val filamentLengthMeters = totalVolume / filamentCrossSectionMm2 / 1000.0
        val cost = (weightGrams / material.spoolWeightGrams) * material.spoolPricePkr

        return PrintEstimate(
            modelVolumeMm3 = modelVolume,
            breakdown = breakdown,
            totalVolumeMm3 = totalVolume,
            weightGrams = weightGrams,
            filamentLengthMeters = filamentLengthMeters,
            costPkr = cost,
            settings = settings,
        )
    }

    fun validate(
        model: StlModel,
        material: MaterialProfile,
        printer: PrinterProfile,
        settings: EstimateSettings,
    ) {
        require(model.triangles.isNotEmpty()) { "Import a non-empty STL model first." }
        require(model.integrity.isValid && model.isClosed && model.volumeMm3 > 0.0) {
            "A closed, consistently oriented mesh is required for this estimate."
        }
        require(model.surfaceAreaMm2.isFinite() && model.surfaceAreaMm2 > 0.0) {
            "The model has no measurable surface area."
        }
        require(model.sizeX > 0f && model.sizeY > 0f && model.sizeZ > 0f) {
            "The model must have positive dimensions on all axes."
        }
        require(model.sizeX <= printer.buildXmm && model.sizeY <= printer.buildYmm && model.sizeZ <= printer.buildZmm) {
            "The model's bounding box exceeds the selected printer's build volume."
        }
        require(material.densityGPerCm3.isFinite() && material.densityGPerCm3 > 0.0) { "Material density must be greater than zero." }
        require(material.filamentDiameterMm.isFinite() && material.filamentDiameterMm > 0.0) { "Filament diameter must be greater than zero." }
        require(material.spoolWeightGrams.isFinite() && material.spoolWeightGrams > 0.0) { "Spool weight must be greater than zero." }
        require(material.spoolPricePkr.isFinite() && material.spoolPricePkr >= 0.0) { "Spool price cannot be negative." }
        require(printer.buildXmm.isFinite() && printer.buildXmm > 0.0 &&
            printer.buildYmm.isFinite() && printer.buildYmm > 0.0 &&
            printer.buildZmm.isFinite() && printer.buildZmm > 0.0) { "Printer build dimensions must be positive." }
        require(settings.nozzleDiameterMm.isFinite() && settings.nozzleDiameterMm > 0.0) { "Nozzle diameter must be greater than zero." }
        require(settings.layerHeightMm.isFinite() && settings.layerHeightMm >= 0.04 &&
            settings.layerHeightMm <= settings.nozzleDiameterMm) {
            "Layer height must be between 0.04 mm and the nozzle diameter."
        }
        require(settings.wallCount in 1..MAX_WALLS) { "Wall count must be between 1 and $MAX_WALLS." }
        require(settings.infillPercent.isFinite() && settings.infillPercent in 0.0..100.0) { "Infill must be between 0% and 100%." }
        require(settings.topShellLayers in 0..MAX_SHELL_LAYERS && settings.bottomShellLayers in 0..MAX_SHELL_LAYERS) {
            "Top and bottom shell layers must be between 0 and $MAX_SHELL_LAYERS."
        }
        require(settings.supportDensityPercent.isFinite() && settings.supportDensityPercent in 0.0..100.0) {
            "Support density must be between 0% and 100%."
        }
    }

    private fun estimateSupportVolume(model: StlModel, densityPercent: Double): Double {
        if (densityPercent == 0.0) return 0.0
        var projectedAreaHeight = 0.0
        for (triangle in model.triangles) {
            val geometry = triangleGeometry(triangle) ?: continue
            // A downward-facing facet steeper than 45° is treated as an unsupported overhang.
            if (geometry.normalZ > -OVERHANG_NORMAL_THRESHOLD) continue
            val centroidZ = (triangle.a.z + triangle.b.z + triangle.c.z) / 3.0 - model.min.z
            if (centroidZ <= 0.0) continue
            val horizontalProjectedArea = geometry.area * abs(geometry.normalZ)
            projectedAreaHeight += horizontalProjectedArea * centroidZ
        }
        return projectedAreaHeight * (densityPercent / 100.0)
    }

    private data class TriangleGeometry(val area: Double, val normalZ: Double)

    private fun triangleGeometry(t: StlTriangle): TriangleGeometry? {
        val abx = (t.b.x - t.a.x).toDouble()
        val aby = (t.b.y - t.a.y).toDouble()
        val abz = (t.b.z - t.a.z).toDouble()
        val acx = (t.c.x - t.a.x).toDouble()
        val acy = (t.c.y - t.a.y).toDouble()
        val acz = (t.c.z - t.a.z).toDouble()
        val nx = aby * acz - abz * acy
        val ny = abz * acx - abx * acz
        val nz = abx * acy - aby * acx
        val magnitude = sqrt(nx * nx + ny * ny + nz * nz)
        if (!magnitude.isFinite() || magnitude <= 0.0) return null
        return TriangleGeometry(area = magnitude * 0.5, normalZ = nz / magnitude)
    }
}
