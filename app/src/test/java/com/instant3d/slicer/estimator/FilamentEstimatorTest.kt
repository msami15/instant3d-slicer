package com.instant3d.slicer.estimator

import com.instant3d.slicer.profile.MaterialProfile
import com.instant3d.slicer.profile.PrinterProfile
import com.instant3d.slicer.stl.MeshIntegrity
import com.instant3d.slicer.stl.Point3
import com.instant3d.slicer.stl.StlModel
import com.instant3d.slicer.stl.StlTriangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.PI

class FilamentEstimatorTest {
    private val material = MaterialProfile("pla", "PLA", 1.24, 1.75, 1000.0, 2000.0, true)
    private val printer = PrinterProfile("test", "Test printer", 256.0, 256.0, 256.0, 0.4)
    private val cube = cubeModel(20f)
    private val settings = EstimateSettings()

    @Test
    fun knownCubeProducesExpectedComponentVolumesAndMass() {
        assertEquals(8000.0, cube.volumeMm3, 0.0)
        assertEquals(2400.0, cube.surfaceAreaMm2, 0.0)
        val estimate = FilamentEstimator.estimate(cube, material, printer, settings)
        assertEquals(1920.0, estimate.breakdown.wallsMm3, 1e-6)
        assertEquals(320.0, estimate.breakdown.topShellMm3, 1e-6)
        assertEquals(320.0, estimate.breakdown.bottomShellMm3, 1e-6)
        assertEquals(816.0, estimate.breakdown.infillMm3, 1e-6)
        assertEquals(0.0, estimate.breakdown.supportsMm3, 0.0)
        assertEquals(3376.0, estimate.totalVolumeMm3, 1e-6)
        assertEquals(3376.0 / 1000.0 * 1.24, estimate.weightGrams, 1e-9)
    }

    @Test
    fun filamentLengthUsesSelectedDiameterAndCostUsesSpoolRatio() {
        val estimate = FilamentEstimator.estimate(cube, material, printer, settings)
        val crossSection = PI * (1.75 / 2.0) * (1.75 / 2.0)
        assertEquals(3376.0 / crossSection / 1000.0, estimate.filamentLengthMeters, 1e-9)
        assertEquals(estimate.weightGrams / 1000.0 * 2000.0, estimate.costPkr, 1e-9)
        val wider = FilamentEstimator.estimate(cube, material.copy(filamentDiameterMm = 2.85), printer, settings)
        assertTrue(wider.filamentLengthMeters < estimate.filamentLengthMeters)
    }

    @Test
    fun higherInfillIncreasesMaterialButPatternIsNominallyVolumeNeutral() {
        val low = FilamentEstimator.estimate(cube, material, printer, settings.copy(infillPercent = 0.0))
        val high = FilamentEstimator.estimate(cube, material, printer, settings.copy(infillPercent = 50.0))
        assertEquals(5440.0, high.breakdown.infillMm3 - low.breakdown.infillMm3, 1e-6)
        val otherPattern = FilamentEstimator.estimate(cube, material, printer, settings.copy(infillPattern = InfillPattern.GRID))
        assertEquals(high.totalVolumeMm3, FilamentEstimator.estimate(cube, material, printer, settings.copy(infillPercent = 50.0)).totalVolumeMm3, 0.0)
        assertEquals(low.totalVolumeMm3, otherPattern.totalVolumeMm3, 0.0)
    }

    @Test
    fun additionalWallsAndShellLayersIncreaseExtrusion() {
        val oneWall = FilamentEstimator.estimate(cube, material, printer, settings.copy(wallCount = 1))
        val threeWalls = FilamentEstimator.estimate(cube, material, printer, settings.copy(wallCount = 3))
        assertTrue(threeWalls.breakdown.wallsMm3 > oneWall.breakdown.wallsMm3)
        val fewerShells = FilamentEstimator.estimate(cube, material, printer, settings.copy(topShellLayers = 0, bottomShellLayers = 0))
        assertTrue(estimate().breakdown.topShellMm3 + estimate().breakdown.bottomShellMm3 > 0)
        assertTrue(estimate().totalVolumeMm3 > fewerShells.totalVolumeMm3)
    }

    @Test
    fun layerHeightChangesTopAndBottomShellVolume() {
        val thin = FilamentEstimator.estimate(cube, material, printer, settings.copy(layerHeightMm = 0.1))
        val thick = FilamentEstimator.estimate(cube, material, printer, settings.copy(layerHeightMm = 0.3))
        assertTrue(thick.breakdown.topShellMm3 > thin.breakdown.topShellMm3)
        assertTrue(thick.breakdown.bottomShellMm3 > thin.breakdown.bottomShellMm3)
    }

    @Test
    fun supportEstimateIgnoresBedContactFacesOfCube() {
        val estimate = FilamentEstimator.estimate(cube, material, printer, settings.copy(supportsEnabled = true))
        assertEquals(0.0, estimate.breakdown.supportsMm3, 0.0)
    }

    @Test
    fun invalidSettingsAndOpenMeshesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            FilamentEstimator.estimate(cube, material, printer, settings.copy(infillPercent = 101.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FilamentEstimator.estimate(cube, material, printer, settings.copy(layerHeightMm = 0.5))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FilamentEstimator.estimate(cube, material, printer, settings.copy(wallCount = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FilamentEstimator.estimate(cube.copy(isClosed = false), material, printer, settings)
        }
    }

    @Test
    fun zeroInfillAndZeroShellLayersRemainFinite() {
        val estimate = FilamentEstimator.estimate(
            cube, material, printer,
            settings.copy(infillPercent = 0.0, topShellLayers = 0, bottomShellLayers = 0, supportsEnabled = true, supportDensityPercent = 0.0),
        )
        assertTrue(estimate.totalVolumeMm3 > 0.0)
        assertTrue(estimate.weightGrams.isFinite())
        assertEquals(0.0, estimate.breakdown.infillMm3, 0.0)
        assertEquals(0.0, estimate.breakdown.supportsMm3, 0.0)
    }

    private fun estimate() = FilamentEstimator.estimate(cube, material, printer, settings)

    private fun cubeModel(edge: Float): StlModel {
        fun p(x: Float, y: Float, z: Float) = Point3(x * edge, y * edge, z * edge)
        val v000=p(0f,0f,0f); val v100=p(1f,0f,0f); val v110=p(1f,1f,0f); val v010=p(0f,1f,0f)
        val v001=p(0f,0f,1f); val v101=p(1f,0f,1f); val v111=p(1f,1f,1f); val v011=p(0f,1f,1f)
        val triangles = listOf(
            StlTriangle(v000,v010,v110), StlTriangle(v000,v110,v100),
            StlTriangle(v001,v101,v111), StlTriangle(v001,v111,v011),
            StlTriangle(v000,v100,v101), StlTriangle(v000,v101,v001),
            StlTriangle(v010,v011,v111), StlTriangle(v010,v111,v110),
            StlTriangle(v000,v001,v011), StlTriangle(v000,v011,v010),
            StlTriangle(v100,v110,v111), StlTriangle(v100,v111,v101),
        )
        return StlModel(
            triangles = triangles,
            min = p(0f,0f,0f),
            max = p(1f,1f,1f),
            volumeMm3 = edge.toDouble().let { it * it * it },
            isClosed = true,
            surfaceAreaMm2 = 6.0 * edge * edge,
            integrity = MeshIntegrity(),
        )
    }
}
