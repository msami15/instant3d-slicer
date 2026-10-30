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
    private const val MAX_TRIANGLES = 1_000_000

    fun parse(bytes: ByteArray): StlModel {
        require(bytes.isNotEmpty()) { "The selected file is empty." }
        val binaryCount = if (bytes.size >= 84) {
            ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
        } else 0L
        val binaryLength = 84L + binaryCount * 50L
        val triangles = if (binaryCount in 1..MAX_TRIANGLES.toLong() && binaryLength == bytes.size.toLong()) {
            parseBinary(bytes, binaryCount.toInt())
        } else {
            parseAscii(bytes)
        }
        require(triangles.isNotEmpty()) { "No triangles were found. Please select a valid STL file." }
        require(triangles.size <= MAX_TRIANGLES) { "This STL contains too many triangles for this version." }

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        var signedVolume = 0.0
        val edgeCounts = HashMap<String, Int>(triangles.size * 3)

        fun include(p: Point3) {
            minX = min(minX, p.x); minY = min(minY, p.y); minZ = min(minZ, p.z)
            maxX = max(maxX, p.x); maxY = max(maxY, p.y); maxZ = max(maxZ, p.z)
        }
        fun key(p: Point3): String = "${(p.x * 10000).toLong()},${(p.y * 10000).toLong()},${(p.z * 10000).toLong()}"
        fun addEdge(p1: Point3, p2: Point3) {
            val k1 = key(p1); val k2 = key(p2)
            val edge = if (k1 <= k2) "$k1|$k2" else "$k2|$k1"
            edgeCounts[edge] = (edgeCounts[edge] ?: 0) + 1
        }

        triangles.forEach { t ->
            include(t.a); include(t.b); include(t.c)
            val ax = t.a.x.toDouble(); val ay = t.a.y.toDouble(); val az = t.a.z.toDouble()
            val bx = t.b.x.toDouble(); val by = t.b.y.toDouble(); val bz = t.b.z.toDouble()
            val cx = t.c.x.toDouble(); val cy = t.c.y.toDouble(); val cz = t.c.z.toDouble()
            signedVolume += (ax * (by * cz - bz * cy) + ay * (bz * cx - bx * cz) + az * (bx * cy - by * cx)) / 6.0
            addEdge(t.a, t.b); addEdge(t.b, t.c); addEdge(t.c, t.a)
        }
        val closed = edgeCounts.isNotEmpty() && edgeCounts.values.all { it == 2 }
        val volume = if (closed) abs(signedVolume) else 0.0
        return StlModel(
            triangles,
            Point3(minX, minY, minZ),
            Point3(maxX, maxY, maxZ),
            volume,
            closed,
        )
    }

    private fun parseBinary(bytes: ByteArray, count: Int): List<StlTriangle> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(84)
        val result = ArrayList<StlTriangle>(count)
        repeat(count) {
            buffer.position(buffer.position() + 12) // stored normal; vertices are recalculated by the viewer
            fun point() = Point3(buffer.float, buffer.float, buffer.float)
            val a = point(); val b = point(); val c = point()
            buffer.position(buffer.position() + 2) // attribute byte count
            listOf(a, b, c).forEach { p ->
                require(p.x.isFinite() && p.y.isFinite() && p.z.isFinite()) { "The STL contains invalid coordinates." }
            }
            result += StlTriangle(a, b, c)
        }
        return result
    }

    private fun parseAscii(bytes: ByteArray): List<StlTriangle> {
        val text = bytes.toString(Charsets.US_ASCII)
        require(text.trimStart().startsWith("solid", ignoreCase = true)) {
            "This file is not a supported binary or ASCII STL."
        }
        val vertices = ArrayList<Point3>()
        text.lineSequence().forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 4 && parts[0].equals("vertex", ignoreCase = true)) {
                val point = Point3(parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat())
                require(point.x.isFinite() && point.y.isFinite() && point.z.isFinite()) {
                    "The STL contains invalid coordinates."
                }
                vertices += point
                require(vertices.size / 3 <= MAX_TRIANGLES) { "This STL contains too many triangles for this version." }
            }
        }
        require(vertices.size % 3 == 0) { "The ASCII STL has an incomplete triangle." }
        return vertices.chunked(3).map { StlTriangle(it[0], it[1], it[2]) }
    }
}
