package com.instant3d.slicer.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDefaultsTest {
    @Test
    fun builtInMaterialsHaveExpectedDefaultsAndValidValues() {
        val materials = ProfileRepository.DEFAULT_MATERIALS.associateBy { it.id }
        assertEquals(setOf("pla", "petg", "abs", "tpu"), materials.keys)
        assertEquals(1.24, materials.getValue("pla").densityGPerCm3, 0.0001)
        assertEquals(1.27, materials.getValue("petg").densityGPerCm3, 0.0001)
        assertEquals(1.04, materials.getValue("abs").densityGPerCm3, 0.0001)
        assertEquals(1.21, materials.getValue("tpu").densityGPerCm3, 0.0001)
        materials.values.forEach {
            assertEquals(1.75, it.filamentDiameterMm, 0.0)
            assertEquals(1000.0, it.spoolWeightGrams, 0.0)
            assertEquals(0.0, it.spoolPricePkr, 0.0)
            assertTrue(it.isBuiltIn)
            validateMaterialProfile(it)
        }
    }

    @Test
    fun printerDefaultsUsePublishedBuildVolumesAndIncludedNozzles() {
        val printers = ProfileRepository.DEFAULT_PRINTERS.associateBy { it.id }
        assertEquals(5, printers.size)
        assertVolume(printers.getValue("bambu-a1"), 256.0)
        assertVolume(printers.getValue("bambu-a1-mini"), 180.0)
        assertVolume(printers.getValue("bambu-p1p"), 256.0)
        assertVolume(printers.getValue("bambu-p1s"), 256.0)
        assertVolume(printers.getValue("bambu-x1-carbon"), 256.0)
        printers.values.forEach {
            assertEquals(0.4, it.nozzleDiameterMm, 0.0)
            assertTrue(it.isBuiltIn)
            validatePrinterProfile(it)
        }
    }

    @Test
    fun materialValidationRejectsInvalidDensityAndNegativePrice() {
        val pla = ProfileRepository.DEFAULT_MATERIALS.first()
        assertThrows(IllegalArgumentException::class.java) {
            validateMaterialProfile(pla.copy(densityGPerCm3 = Double.NaN))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateMaterialProfile(pla.copy(spoolPricePkr = -1.0))
        }
    }

    @Test
    fun printerValidationRejectsMissingBuildDimension() {
        val a1 = ProfileRepository.DEFAULT_PRINTERS.first()
        assertThrows(IllegalArgumentException::class.java) {
            validatePrinterProfile(a1.copy(buildZmm = 0.0))
        }
    }

    @Test
    fun onlyCustomMaterialsAreMarkedDeletable() {
        assertFalse(ProfileRepository.DEFAULT_MATERIALS.first().isBuiltIn)
    }

    private fun assertVolume(profile: PrinterProfile, size: Double) {
        assertEquals(size, profile.buildXmm, 0.0)
        assertEquals(size, profile.buildYmm, 0.0)
        assertEquals(size, profile.buildZmm, 0.0)
    }
}
