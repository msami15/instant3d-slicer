package com.instant3d.slicer.stl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StlRenderMeshTest {
    @Test
    fun centersAndNormalizesModelCoordinates() {
        val model = StlModel(
            triangles = listOf(
                StlTriangle(
                    Point3(10f, -2f, 0f),
                    Point3(12f, -2f, 0f),
                    Point3(10f, 2f, 2f),
                ),
            ),
            min = Point3(10f, -2f, 0f),
            max = Point3(12f, 2f, 2f),
            volumeMm3 = 0.0,
            isClosed = false,
        )

        val mesh = StlRenderMesh.from(model)

        assertEquals(3, mesh.vertexCount)
        assertEquals(18, mesh.solidVertices.size)
        assertEquals(36, mesh.wireframeVertices.size)
        assertEquals(-0.5f, mesh.solidVertices[0], 0.0001f)
        assertEquals(-1f, mesh.solidVertices[1], 0.0001f)
        assertEquals(-0.5f, mesh.solidVertices[2], 0.0001f)
        assertEquals(1.732f, mesh.fitRadius, 0.001f)
        assertTrue(mesh.solidVertices.all { it.isFinite() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDegenerateTrianglesForRendering() {
        val point = Point3(0f, 0f, 0f)
        StlRenderMesh.from(
            StlModel(
                triangles = listOf(StlTriangle(point, point, point)),
                min = point,
                max = Point3(1f, 1f, 1f),
                volumeMm3 = 0.0,
                isClosed = false,
            ),
        )
    }
}
