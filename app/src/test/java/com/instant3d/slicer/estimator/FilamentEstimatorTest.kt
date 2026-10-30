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
import kotlin.math.cos
import kotlin.math.sin

class FilamentEstimatorTest {
    private val material = MaterialProfile("pla", "PLA", 1.24, 1.75, 1000.0, 2000.0, true)
    private val printer = PrinterProfile("test", "Test printer", 256.0, 256.0, 256.0, 0.4)
    private val cube = boxModel(20f, 20f, 20f)
    private val settings = EstimateSettings()

    @Test
    fun solidCubeUsesSideWallsAndSeparateShellsWithoutDoubleCounting() {
        assertEquals(8000.0, cube.volumeMm3, 0.0)
        assertEquals(2400.0, cube.surfaceAreaMm2, 0.0)
        val result = estimate(cube)
        assertEquals(1280.0, result.breakdown.wallsMm3, 1e-6)
        assertEquals(320.0, result.breakdown.topShellMm3, 1e-6)
        assertEquals(320.0, result.breakdown.bottomShellMm3, 1e-6)
        assertEquals(912.0, result.breakdown.infillMm3, 1e-6)
        assertEquals(2832.0, result.totalVolumeMm3, 1e-6)
        assertEquals(2832.0 / 1000.0 * 1.24, result.weightGrams, 1e-9)
        assertSane(result, cube)
    }

    @Test
    fun rectangularBlockUsesAnalyticBoundsVolumeAndSideArea() {
        val block = boxModel(30f, 20f, 10f)
        assertEquals(6000.0, block.volumeMm3, 0.0)
        assertEquals(2200.0, block.surfaceAreaMm2, 0.0)
        val result = estimate(block)
        assertEquals(800.0, result.breakdown.wallsMm3, 1e-6)
        assertEquals(480.0, result.breakdown.topShellMm3, 1e-6)
        assertEquals(480.0, result.breakdown.bottomShellMm3, 1e-6)
        assertSane(result, block)
    }

    @Test
    fun hollowCylinderUsesKnownPolygonalTubeGeometry() {
        val tube = hollowCylinder(10.0, 8.0, 20.0, 96)
        assertEquals(tube.volumeMm3, tube.volumeMm3, 0.0)
        assertTrue(tube.volumeMm3 > 0.0)
        assertTrue(tube.surfaceAreaMm2 > 0.0)
        val expectedVolume = polygonArea(10.0, 96) * 20.0 - polygonArea(8.0, 96) * 20.0
        val perimeter = 2.0 * 96 * 10.0 * sin(PI / 96.0) + 2.0 * 96 * 8.0 * sin(PI / 96.0)
        val expectedSurface = perimeter * 20.0 + 2.0 * (polygonArea(10.0, 96) - polygonArea(8.0, 96))
        assertEquals(expectedVolume, tube.volumeMm3, 1e-5)
        assertEquals(expectedSurface, tube.surfaceAreaMm2, 1e-5)
        val result = estimate(tube)
        assertTrue(result.breakdown.wallsMm3 > 0.0)
        assertSane(result, tube)
    }

    @Test
    fun overhangFacetCreatesBoundedSupportEstimateAndBedContactCreatesNone() {
        val overhang = overhangPrism()
        val result = estimate(overhang, settings.copy(supportsEnabled = true, supportDensityPercent = 15.0))
        assertEquals(300.0, result.breakdown.supportsMm3, 1e-5)
        val fullDensity = estimate(overhang, settings.copy(supportsEnabled = true, supportDensityPercent = 100.0))
        val freeEnvelope = 20.0 * 20.0 * 20.0 - overhang.volumeMm3
        assertTrue(fullDensity.breakdown.supportsMm3 <= freeEnvelope)
        assertSane(fullDensity, overhang)
        assertEquals(0.0, estimate(cube, settings.copy(supportsEnabled = true)).breakdown.supportsMm3, 0.0)
    }

    @Test
    fun moreWallsIncreaseShellAndInfillRespondsToRemainingCore() {
        val oneWall = estimate(cube, settings.copy(wallCount = 1))
        val threeWalls = estimate(cube, settings.copy(wallCount = 3))
        assertTrue(threeWalls.breakdown.wallsMm3 > oneWall.breakdown.wallsMm3)
        val zeroInfill = estimate(cube, settings.copy(infillPercent = 0.0))
        val halfInfill = estimate(cube, settings.copy(infillPercent = 50.0))
        assertEquals(3040.0, halfInfill.breakdown.infillMm3 - zeroInfill.breakdown.infillMm3, 1e-6)
        assertTrue(halfInfill.totalVolumeMm3 > zeroInfill.totalVolumeMm3)
    }

    @Test
    fun shellLayerHeightChangesCapVolumeAndThinModelSplitsShellBudget() {
        val thin = estimate(cube, settings.copy(layerHeightMm = 0.1))
        val thick = estimate(cube, settings.copy(layerHeightMm = 0.3))
        assertTrue(thick.breakdown.topShellMm3 > thin.breakdown.topShellMm3)
        assertTrue(thick.breakdown.bottomShellMm3 > thin.breakdown.bottomShellMm3)

        val thinBlock = boxModel(2f, 2f, 0.2f)
        val split = estimate(thinBlock, settings.copy(layerHeightMm = 0.1, topShellLayers = 10, bottomShellLayers = 2))
        assertTrue(split.breakdown.topShellMm3 > split.breakdown.bottomShellMm3)
        assertTrue(split.breakdown.topShellMm3 + split.breakdown.bottomShellMm3 <= thinBlock.volumeMm3)
        assertSane(split, thinBlock)
    }

    @Test
    fun filamentLengthAndCostConversionsUseProfileValues() {
        val result = estimate(cube)
        val crossSection = PI * (1.75 / 2.0) * (1.75 / 2.0)
        assertEquals(2832.0 / crossSection / 1000.0, result.filamentLengthMeters, 1e-9)
        assertEquals(result.weightGrams / 1000.0 * 2000.0, result.costPkr, 1e-9)
        val wider = estimate(cube, material.copy(filamentDiameterMm = 2.85))
        assertTrue(wider.filamentLengthMeters < result.filamentLengthMeters)
        val denser = estimate(cube, material.copy(densityGPerCm3 = 1.5))
        assertEquals(result.weightGrams * (1.5 / 1.24), denser.weightGrams, 1e-9)
    }

    @Test
    fun invalidSettingsProfilesAndMeshesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { estimate(cube, settings.copy(infillPercent = 101.0)) }
        assertThrows(IllegalArgumentException::class.java) { estimate(cube, settings.copy(layerHeightMm = 0.5)) }
        assertThrows(IllegalArgumentException::class.java) { estimate(cube, settings.copy(wallCount = 0)) }
        assertThrows(IllegalArgumentException::class.java) { estimate(cube, settings.copy(supportDensityPercent = Double.NaN)) }
        assertThrows(IllegalArgumentException::class.java) { estimate(cube.copy(isClosed = false)) }
        assertThrows(IllegalArgumentException::class.java) { estimate(cube, material.copy(spoolWeightGrams = 0.0)) }
        assertThrows(IllegalArgumentException::class.java) {
            FilamentEstimator.estimate(boxModel(300f, 20f, 20f), material, printer, settings)
        }
    }

    @Test
    fun zeroInfillAndZeroDensitySupportStayFiniteAndNonNegative() {
        val result = estimate(
            overhangPrism(),
            settings.copy(infillPercent = 0.0, topShellLayers = 0, bottomShellLayers = 0, supportsEnabled = true, supportDensityPercent = 0.0),
        )
        assertEquals(0.0, result.breakdown.infillMm3, 0.0)
        assertEquals(0.0, result.breakdown.supportsMm3, 0.0)
        assertSane(result, overhangPrism())
    }

    private fun estimate(model: StlModel = cube, settings: EstimateSettings = this.settings) =
        FilamentEstimator.estimate(model, material, printer, settings)

    private fun estimate(model: StlModel, settings: EstimateSettings) =
        FilamentEstimator.estimate(model, material, printer, settings)

    private fun estimate(model: StlModel, material: MaterialProfile) =
        FilamentEstimator.estimate(model, material, printer, settings)

    private fun assertSane(result: PrintEstimate, model: StlModel) {
        val parts = listOf(
            result.breakdown.wallsMm3, result.breakdown.infillMm3, result.breakdown.topShellMm3,
            result.breakdown.bottomShellMm3, result.breakdown.supportsMm3, result.totalVolumeMm3,
            result.weightGrams, result.filamentLengthMeters, result.costPkr,
        )
        assertTrue(parts.all { it.isFinite() && it >= 0.0 })
        val envelope = model.sizeX.toDouble() * model.sizeY.toDouble() * model.sizeZ.toDouble()
        assertTrue("Estimated material must not exceed the model bounding envelope.", result.totalVolumeMm3 <= envelope + 1e-5)
    }

    private fun boxModel(x: Float, y: Float, z: Float): StlModel {
        fun p(a: Float, b: Float, c: Float) = Point3(a, b, c)
        val v000=p(0f,0f,0f); val v100=p(x,0f,0f); val v110=p(x,y,0f); val v010=p(0f,y,0f)
        val v001=p(0f,0f,z); val v101=p(x,0f,z); val v111=p(x,y,z); val v011=p(0f,y,z)
        val triangles = listOf(
            StlTriangle(v000,v010,v110), StlTriangle(v000,v110,v100),
            StlTriangle(v001,v101,v111), StlTriangle(v001,v111,v011),
            StlTriangle(v000,v100,v101), StlTriangle(v000,v101,v001),
            StlTriangle(v010,v011,v111), StlTriangle(v010,v111,v110),
            StlTriangle(v000,v001,v011), StlTriangle(v000,v011,v010),
            StlTriangle(v100,v110,v111), StlTriangle(v100,v111,v101),
        )
        return StlModel(
            triangles, p(0f,0f,0f), p(x,y,z), x.toDouble()*y*z, true,
            2.0 * (x*y + x*z + y*z), MeshIntegrity(),
        )
    }

    private fun hollowCylinder(outerRadius: Double, innerRadius: Double, height: Double, segments: Int): StlModel {
        fun point(radius: Double, i: Int, z: Double): Point3 {
            val angle = 2.0 * PI * i / segments
            return Point3((radius*cos(angle)).toFloat(), (radius*sin(angle)).toFloat(), z.toFloat())
        }
        val facets = mutableListOf<StlTriangle>()
        for (i in 0 until segments) {
            val j = (i + 1) % segments
            val ob0=point(outerRadius,i,0.0); val ob1=point(outerRadius,j,0.0)
            val ot0=point(outerRadius,i,height); val ot1=point(outerRadius,j,height)
            val ib0=point(innerRadius,i,0.0); val ib1=point(innerRadius,j,0.0)
            val it0=point(innerRadius,i,height); val it1=point(innerRadius,j,height)
            facets += StlTriangle(ob0,ob1,ot1); facets += StlTriangle(ob0,ot1,ot0)
            facets += StlTriangle(ib0,it0,it1); facets += StlTriangle(ib0,it1,ib1)
            facets += StlTriangle(ot0,ot1,it1); facets += StlTriangle(ot0,it1,it0)
            facets += StlTriangle(ob0,ib1,ob1); facets += StlTriangle(ob0,ib0,ib1)
        }
        val area = polygonArea(outerRadius, segments) - polygonArea(innerRadius, segments)
        val perimeter = 2.0 * segments * (outerRadius + innerRadius) * sin(PI / segments)
        return StlModel(
            facets, Point3(-outerRadius.toFloat(),-outerRadius.toFloat(),0f),
            Point3(outerRadius.toFloat(),outerRadius.toFloat(),height.toFloat()),
            area * height, true,
            perimeter * height + 2.0 * area, MeshIntegrity(),
        )
    }

    private fun polygonArea(radius: Double, segments: Int): Double =
        segments * radius * radius * sin(2.0 * PI / segments) / 2.0

    private fun overhangPrism(): StlModel {
        fun p(x: Float, y: Float, z: Float) = Point3(x,y,z)
        val a=p(0f,0f,10f); val b=p(0f,20f,10f); val c=p(20f,20f,0f); val d=p(20f,0f,0f)
        val e=p(0f,0f,20f); val f=p(20f,0f,20f); val g=p(20f,20f,20f); val h=p(0f,20f,20f)
        val triangles = listOf(
            StlTriangle(a,b,c), StlTriangle(a,c,d), // downward-facing sloped underside
            StlTriangle(e,f,g), StlTriangle(e,g,h), // top
            StlTriangle(a,e,h), StlTriangle(a,h,b), // x-min
            StlTriangle(d,c,g), StlTriangle(d,g,f), // x-max
            StlTriangle(a,d,f), StlTriangle(a,f,e), // y-min
            StlTriangle(b,h,g), StlTriangle(b,g,c), // y-max
        )
        return StlModel(triangles,p(0f,0f,0f),p(20f,20f,20f),6000.0,true,3000.0,MeshIntegrity())
    }
}
