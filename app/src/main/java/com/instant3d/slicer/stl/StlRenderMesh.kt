package com.instant3d.slicer.stl

import kotlin.math.max
import kotlin.math.sqrt

/**
 * CPU-side, normalized mesh buffers shared with the GL renderer. Every triangle has flat normals;
 * edges are duplicated intentionally so wireframe mode can render with GLES 2.0's GL_LINES.
 */
internal data class StlRenderMesh(
    val solidVertices: FloatArray,
    val wireframeVertices: FloatArray,
    val vertexCount: Int,
    val fitRadius: Float,
) {
    companion object {
        fun from(model: StlModel): StlRenderMesh {
            require(model.triangles.isNotEmpty()) { "This model has no triangles to render." }
            val largest = max(max(model.sizeX, model.sizeY), model.sizeZ)
            require(largest.isFinite() && largest > 0f) { "This model has no measurable size." }

            val centerX = model.min.x.toDouble() + (model.max.x.toDouble() - model.min.x) / 2.0
            val centerY = model.min.y.toDouble() + (model.max.y.toDouble() - model.min.y) / 2.0
            val centerZ = model.min.z.toDouble() + (model.max.z.toDouble() - model.min.z) / 2.0
            val scale = 2.0 / largest.toDouble()
            val solid = FloatArray(model.triangles.size * 3 * 6)
            val wire = FloatArray(model.triangles.size * 6 * 6)
            var solidOffset = 0
            var wireOffset = 0

            fun normalized(point: Point3): FloatArray = floatArrayOf(
                ((point.x.toDouble() - centerX) * scale).toFloat(),
                ((point.y.toDouble() - centerY) * scale).toFloat(),
                ((point.z.toDouble() - centerZ) * scale).toFloat(),
            )
            fun put(target: FloatArray, offset: Int, p: FloatArray, normal: FloatArray): Int {
                target[offset] = p[0]
                target[offset + 1] = p[1]
                target[offset + 2] = p[2]
                target[offset + 3] = normal[0]
                target[offset + 4] = normal[1]
                target[offset + 5] = normal[2]
                return offset + 6
            }

            model.triangles.forEach { triangle ->
                val a = normalized(triangle.a)
                val b = normalized(triangle.b)
                val c = normalized(triangle.c)
                val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
                val vx = c[0] - a[0]; val vy = c[1] - a[1]; val vz = c[2] - a[2]
                val nx = uy * vz - uz * vy
                val ny = uz * vx - ux * vz
                val nz = ux * vy - uy * vx
                val length = sqrt(nx * nx + ny * ny + nz * nz)
                require(length.isFinite() && length > 1e-8f) { "This model contains a degenerate triangle." }
                val normal = floatArrayOf(nx / length, ny / length, nz / length)
                solidOffset = put(solid, solidOffset, a, normal)
                solidOffset = put(solid, solidOffset, b, normal)
                solidOffset = put(solid, solidOffset, c, normal)
                wireOffset = put(wire, wireOffset, a, normal)
                wireOffset = put(wire, wireOffset, b, normal)
                wireOffset = put(wire, wireOffset, b, normal)
                wireOffset = put(wire, wireOffset, c, normal)
                wireOffset = put(wire, wireOffset, c, normal)
                wireOffset = put(wire, wireOffset, a, normal)
            }

            val radius = sqrt(3f)
            return StlRenderMesh(solid, wire, solid.size / 6, radius)
        }
    }
}
