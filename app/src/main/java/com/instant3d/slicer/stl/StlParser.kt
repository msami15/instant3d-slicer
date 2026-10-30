package com.instant3d.slicer.stl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class Point3(val x: Float, val y: Float, val z: Float)

data class StlTriangle(val a: Point3, val b: Point3, val c: Point3)

data class StlModel(
    val triangles: List<StlTriangle>,
    val min: Point3,
    val max: Point3,
    val volumeMm3: Double,
    val isClosed: Boolean,
) {
    val sizeX: Float get() = max.x - min.x
    val sizeY: Float get() = max.y - min.y
    val sizeZ: Float get() = max.z - min.z
    val volumeCm3: Double get() = volumeMm3 / 1000.0
}

object StlParser {
    private const val MAX_TRIANGLES = 100_000

    fun parse(bytes: ByteArray): StlModel {
        require(bytes.isNotEmpty()) { "The selected file is empty." }
        val binaryCount = if (bytes.size >= 84) {
            ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
        } else 0L
        val binaryLength = 84L + binaryCount * 50L
        val triangles = when {
            binaryCount == 0L && bytes.size == 84 -> emptyList()
            binaryCount in 1..MAX_TRIANGLES.toLong() && binaryLength == bytes.size.toLong() ->
                parseBinary(bytes, binaryCount.toInt())
            else -> parseAscii(bytes)
        }
        require(triangles.isNotEmpty()) { "No triangles were found. Please select a valid STL file." }

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        var signedVolume = 0.0
        val reference = triangles.first().a
        val originX = reference.x.toDouble()
        val originY = reference.y.toDouble()
        val originZ = reference.z.toDouble()
        val edgeCounts = HashMap<String, Int>(triangles.size * 3)
        val edgeDirections = HashMap<String, Int>(triangles.size * 3)

        fun include(p: Point3) {
            minX = min(minX, p.x); minY = min(minY, p.y); minZ = min(minZ, p.z)
            maxX = max(maxX, p.x); maxY = max(maxY, p.y); maxZ = max(maxZ, p.z)
        }
        fun vertexKey(p: Point3): String {
            fun bits(value: Float) = (if (value == 0f) 0f else value).toBits()
            return "${bits(p.x)},${bits(p.y)},${bits(p.z)}"
        }
        fun addEdge(p1: Point3, p2: Point3) {
            val k1 = vertexKey(p1)
            val k2 = vertexKey(p2)
            val edge = if (k1 <= k2) "$k1|$k2" else "$k2|$k1"
            edgeCounts[edge] = (edgeCounts[edge] ?: 0) + 1
            edgeDirections[edge] = (edgeDirections[edge] ?: 0) + if (k1 <= k2) 1 else -1
        }

        triangles.forEach { t ->
            include(t.a); include(t.b); include(t.c)
            // Use a nearby reference point to reduce cancellation for translated meshes.
            val ax = t.a.x.toDouble() - originX
            val ay = t.a.y.toDouble() - originY
            val az = t.a.z.toDouble() - originZ
            val bx = t.b.x.toDouble() - originX
            val by = t.b.y.toDouble() - originY
            val bz = t.b.z.toDouble() - originZ
            val cx = t.c.x.toDouble() - originX
            val cy = t.c.y.toDouble() - originY
            val cz = t.c.z.toDouble() - originZ
            signedVolume += (ax * (by * cz - bz * cy) + ay * (bz * cx - bx * cz) +
                az * (bx * cy - by * cx)) / 6.0
            addEdge(t.a, t.b); addEdge(t.b, t.c); addEdge(t.c, t.a)
        }
        val closed = edgeCounts.isNotEmpty() &&
            edgeCounts.values.all { it == 2 } &&
            edgeDirections.values.all { it == 0 }
        val extentsFinite = (maxX - minX).isFinite() && (maxY - minY).isFinite() && (maxZ - minZ).isFinite()
        require(extentsFinite) { "The STL coordinates are outside the supported range." }
        val volume = if (closed && signedVolume.isFinite()) abs(signedVolume) else 0.0
        return StlModel(
            triangles,
            Point3(minX, minY, minZ),
            Point3(maxX, maxY, maxZ),
            volume,
            closed && signedVolume.isFinite(),
        )
    }

    private fun parseBinary(bytes: ByteArray, count: Int): List<StlTriangle> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(84)
        val result = ArrayList<StlTriangle>(count)
        repeat(count) {
            buffer.position(buffer.position() + 12)
            fun point() = Point3(buffer.float, buffer.float, buffer.float)
            val a = point(); val b = point(); val c = point()
            buffer.position(buffer.position() + 2)
            listOf(a, b, c).forEach { p ->
                require(p.x.isFinite() && p.y.isFinite() && p.z.isFinite()) {
                    "The STL contains invalid coordinates."
                }
            }
            result += StlTriangle(a, b, c)
        }
        return result
    }

    private fun parseAscii(bytes: ByteArray): List<StlTriangle> {
        val lines = bytes.toString(Charsets.US_ASCII).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        require(lines.isNotEmpty() && lines.first().startsWith("solid", ignoreCase = true)) {
            "This file is not a supported binary or ASCII STL."
        }
        require(lines.last().startsWith("endsolid", ignoreCase = true)) {
            "The ASCII STL is missing its endsolid marker."
        }

        val result = ArrayList<StlTriangle>()
        var stage = 0
        var vertices = ArrayList<Point3>(3)
        var sawSolid = false
        var sawEndSolid = false
        for (line in lines) {
            val parts = line.split(Regex("\\s+"))
            when {
                stage == 0 && !sawSolid && parts[0].equals("solid", true) -> sawSolid = true
                stage == 0 && sawSolid && !sawEndSolid && parts[0].equals("facet", true) &&
                    parts.size == 5 && parts[1].equals("normal", true) -> {
                    parts.drop(2).forEach { require(it.toFloatOrNull()?.isFinite() == true) { "The ASCII STL has an invalid facet normal." } }
                    stage = 1
                }
                stage == 1 && parts.size == 2 && parts[0].equals("outer", true) && parts[1].equals("loop", true) -> stage = 2
                stage == 2 && parts[0].equals("vertex", true) && parts.size == 4 -> {
                    val p = Point3(parts[1].toFloatOrNull() ?: Float.NaN, parts[2].toFloatOrNull() ?: Float.NaN, parts[3].toFloatOrNull() ?: Float.NaN)
                    require(p.x.isFinite() && p.y.isFinite() && p.z.isFinite()) { "The STL contains invalid coordinates." }
                    vertices += p
                    require(vertices.size <= 3) { "The ASCII STL facet has more than three vertices." }
                    if (vertices.size == 3) stage = 3
                }
                stage == 3 && parts.size == 1 && parts[0].equals("endloop", true) -> stage = 4
                stage == 4 && parts.size == 1 && parts[0].equals("endfacet", true) -> {
                    result += StlTriangle(vertices[0], vertices[1], vertices[2])
                    require(result.size <= MAX_TRIANGLES) { "This STL contains too many triangles for this version." }
                    vertices = ArrayList(3)
                    stage = 0
                }
                stage == 0 && sawSolid && parts[0].equals("endsolid", true) -> sawEndSolid = true
                else -> throw IllegalArgumentException("The ASCII STL structure is invalid.")
            }
        }
        require(stage == 0 && sawEndSolid) { "The ASCII STL contains an incomplete facet." }
        return result
    }
}
