package com.instant3d.slicer.estimator

import com.instant3d.slicer.profile.MaterialProfile
import com.instant3d.slicer.profile.PrinterProfile
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
    private const val HORIZONTAL_FACE_NORMAL_THRESHOLD = 0.999
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
        val sideSurfaceArea = estimateWallSurfaceArea(model)
        val wallVolume = min(modelVolume, sideSurfaceArea * wallThickness)

        val remainingAfterWalls = max(0.0, modelVolume - wallVolume)
        val footprintMm2 = model.sizeX.toDouble() * model.sizeY.toDouble()
        val topRaw = footprintMm2 * settings.topShellLayers * settings.layerHeightMm
        val bottomRaw = footprintMm2 * settings.bottomShellLayers * settings.layerHeightMm
        // Share available shell volume proportionally if top/bottom shells overlap in thin models.
        val shellTotalRaw = topRaw + bottomRaw
        val shellScale = if (shellTotalRaw > 0.0) min(1.0, remainingAfterWalls / shellTotalRaw) else 0.0
        val topVolume = topRaw * shellScale
        val bottomVolume = bottomRaw * shellScale
        val coreVolume = max(0.0, remainingAfterWalls - topVolume - bottomVolume)
        val infillVolume = coreVolume * settings.infillPercent / 100.0
        val supportVolume = if (settings.supportsEnabled) {
            estimateSupportVolume(model, settings.supportDensityPercent)
        } else 0.0

        val breakdown = VolumeBreakdown(
            wallsMm3 = wallVolume,
            infillMm3 = infillVolume,
            topShellMm3 = topVolume,
            bottomShellMm3 = bottomVolume,
            supportsMm3 = supportVolume,
        )
        val totalVolume = breakdown.totalMm3
        val weightGrams = totalVolume / 1000.0 * material.densityGPerCm3
        val filamentAreaMm2 = Math.PI * (material.filamentDiameterMm / 2.0) * (material.filamentDiameterMm / 2.0)
        val filamentLengthMeters = totalVolume / filamentAreaMm2 / 1000.0
        val cost = weightGrams / material.spoolWeightGrams * material.spoolPricePkr
        require(totalVolume.isFinite() && weightGrams.isFinite() && filamentLengthMeters.isFinite() && cost.isFinite()) {
            "The estimate exceeds the supported numeric range; check your profile values."
        }

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
        require(model.integrity.isValid && model.isClosed && model.volumeMm3.isFinite() && model.volumeMm3 > 0.0) {
            "A closed, consistently oriented mesh is required for this estimate."
        }
        require(
            model.min.x.isFinite() && model.min.y.isFinite() && model.min.z.isFinite() &&
                model.max.x.isFinite() && model.max.y.isFinite() && model.max.z.isFinite() &&
                model.sizeX.isFinite() && model.sizeY.isFinite() && model.sizeZ.isFinite(),
        ) { "The model has non-finite bounds." }
        require(model.surfaceAreaMm2.isFinite() && model.surfaceAreaMm2 > 0.0) {
            "The model has no measurable surface area."
        }
        require(model.sizeX > 0f && model.sizeY > 0f && model.sizeZ > 0f) {
            "The model must have positive dimensions on all axes."
        }
        val envelopeVolume = model.sizeX.toDouble() * model.sizeY.toDouble() * model.sizeZ.toDouble()
        require(envelopeVolume.isFinite() && model.volumeMm3 <= envelopeVolume + max(1e-6, envelopeVolume * 1e-6)) {
            "Mesh volume exceeds its bounding-box volume; check the STL integrity."
        }
        require(printer.buildXmm.isFinite() && printer.buildXmm > 0.0 &&
            printer.buildYmm.isFinite() && printer.buildYmm > 0.0 &&
            printer.buildZmm.isFinite() && printer.buildZmm > 0.0) {
            "Printer build dimensions must be positive."
        }
        require(model.sizeX <= printer.buildXmm && model.sizeY <= printer.buildYmm && model.sizeZ <= printer.buildZmm) {
            "The model's bounding box exceeds the selected printer's build volume."
        }
        require(material.densityGPerCm3.isFinite() && material.densityGPerCm3 > 0.0) { "Material density must be greater than zero." }
        require(material.filamentDiameterMm.isFinite() && material.filamentDiameterMm > 0.0) { "Filament diameter must be greater than zero." }
        require(material.spoolWeightGrams.isFinite() && material.spoolWeightGrams > 0.0) { "Spool weight must be greater than zero." }
        require(material.spoolPricePkr.isFinite() && material.spoolPricePkr >= 0.0) { "Spool price cannot be negative." }
        val filamentArea = Math.PI * (material.filamentDiameterMm / 2.0) * (material.filamentDiameterMm / 2.0)
        require(filamentArea.isFinite() && filamentArea > 0.0) { "Filament diameter is outside the supported numeric range." }
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

    /**
     * Side-shell proxy: horizontal facets are assigned to top/bottom shells so their area
     * is not counted again as wall material.
     */
    private fun estimateWallSurfaceArea(model: StlModel): Double {
        var area = 0.0
        for (triangle in model.triangles) {
            val geometry = triangleGeometry(triangle) ?: continue
            if (abs(geometry.normalZ) < HORIZONTAL_FACE_NORMAL_THRESHOLD) area += geometry.area
        }
        return area
    }

    private fun estimateSupportVolume(model: StlModel, densityPercent: Double): Double {
        if (densityPercent == 0.0) return 0.0
        var projectedAreaHeight = 0.0
        val outwardNormalSign = meshOrientationSign(model)
        for (triangle in model.triangles) {
            val geometry = triangleGeometry(triangle) ?: continue
            // Approximate supports under downward-facing facets steeper than 45°.
            if (geometry.normalZ * outwardNormalSign > -OVERHANG_NORMAL_THRESHOLD) continue
            val centroidZ = (triangle.a.z + triangle.b.z + triangle.c.z) / 3.0 - model.min.z
            if (centroidZ <= 0.0) continue
            val horizontalProjectedArea = geometry.area * abs(geometry.normalZ)
            // For one triangle this is the projected integral of height above the build plate.
            projectedAreaHeight += horizontalProjectedArea * centroidZ
        }
        val fraction = densityPercent / 100.0
        val envelopeVolume = model.sizeX.toDouble() * model.sizeY.toDouble() * model.sizeZ.toDouble()
        // Triangle projections can overlap; cap supports to the free volume inside the model AABB.
        val freeEnvelope = max(0.0, envelopeVolume - model.volumeMm3)
        return min(projectedAreaHeight * fraction, freeEnvelope * fraction)
    }

    private fun meshOrientationSign(model: StlModel): Double {
        val origin = model.triangles.first().a
        var signedVolume = 0.0
        for (t in model.triangles) {
            val ax = t.a.x.toDouble() - origin.x
            val ay = t.a.y.toDouble() - origin.y
            val az = t.a.z.toDouble() - origin.z
            val bx = t.b.x.toDouble() - origin.x
            val by = t.b.y.toDouble() - origin.y
            val bz = t.b.z.toDouble() - origin.z
            val cx = t.c.x.toDouble() - origin.x
            val cy = t.c.y.toDouble() - origin.y
            val cz = t.c.z.toDouble() - origin.z
            signedVolume += (ax * (by * cz - bz * cy) + ay * (bz * cx - bx * cz) +
                az * (bx * cy - by * cx)) / 6.0
        }
        return if (signedVolume < 0.0) -1.0 else 1.0
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
