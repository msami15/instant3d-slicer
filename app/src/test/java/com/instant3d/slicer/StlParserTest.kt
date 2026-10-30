package com.instant3d.slicer

import com.instant3d.slicer.stl.StlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StlParserTest {
    @Test
    fun parsesAsciiTetrahedronAndCalculatesVolume() {
        val stl = """
            solid tetra
            facet normal 0 0 1
              outer loop
                vertex 0 0 0
                vertex 1 0 0
                vertex 0 1 0
              endloop
            endfacet
            facet normal 0 0 1
              outer loop
                vertex 0 0 0
                vertex 0 1 0
                vertex 0 0 1
              endloop
            endfacet
            facet normal 0 0 1
              outer loop
                vertex 0 0 0
                vertex 0 0 1
                vertex 1 0 0
              endloop
            endfacet
            facet normal 0 0 1
              outer loop
                vertex 1 0 0
                vertex 0 0 1
                vertex 0 1 0
              endloop
            endfacet
            endsolid tetra
        """.trimIndent().toByteArray()

        val model = StlParser.parse(stl)
        assertEquals(4, model.triangles.size)
        assertEquals(1f, model.sizeX, 0.0001f)
        assertEquals(1f, model.sizeY, 0.0001f)
        assertEquals(1f, model.sizeZ, 0.0001f)
        assertTrue(model.isClosed)
        assertEquals(1.0 / 6.0, model.volumeMm3, 0.0001)
    }

    @Test
    fun parsesBinaryStl() {
        val bytes = ByteBuffer.allocate(134).order(ByteOrder.LITTLE_ENDIAN)
        bytes.position(80)
        bytes.putInt(1)
        bytes.putFloat(0f); bytes.putFloat(0f); bytes.putFloat(1f)
        bytes.putFloat(0f); bytes.putFloat(0f); bytes.putFloat(0f)
        bytes.putFloat(2f); bytes.putFloat(0f); bytes.putFloat(0f)
        bytes.putFloat(0f); bytes.putFloat(3f); bytes.putFloat(0f)
        bytes.putShort(0)

        val model = StlParser.parse(bytes.array())
        assertEquals(1, model.triangles.size)
        assertEquals(2f, model.sizeX, 0.0001f)
        assertEquals(3f, model.sizeY, 0.0001f)
        assertFalse(model.isClosed)
    }


    @Test(expected = IllegalArgumentException::class)
    fun rejectsAsciiWithUnstructuredVertices() {
        val stl = """
            solid malformed
            vertex 0 0 0
            vertex 1 0 0
            vertex 0 1 0
            endsolid malformed
        """.trimIndent().toByteArray()

        StlParser.parse(stl)
    }

    @Test
    fun duplicateFacetsWithSameWindingAreNotReportedAsClosed() {
        val facet = """
            facet normal 0 0 1
              outer loop
                vertex 0 0 0
                vertex 1 0 0
                vertex 0 1 0
              endloop
            endfacet
        """.trimIndent()
        val stl = "solid duplicate\\n$facet\\n$facet\\nendsolid duplicate".toByteArray()

        val model = StlParser.parse(stl)

        assertFalse(model.isClosed)
        assertEquals(0.0, model.volumeMm3, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyBinaryStl() {
        val bytes = ByteBuffer.allocate(84).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put(0, 's'.code.toByte())
        bytes.putInt(80, 0)

        StlParser.parse(bytes.array())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidFile() {
        StlParser.parse("not an stl".toByteArray())
    }
}
