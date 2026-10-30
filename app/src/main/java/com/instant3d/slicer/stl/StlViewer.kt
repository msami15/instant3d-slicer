package com.instant3d.slicer.stl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun StlViewer(model: StlModel, modifier: Modifier = Modifier) {
    var rotationX by remember(model) { mutableFloatStateOf(-0.45f) }
    var rotationY by remember(model) { mutableFloatStateOf(0.65f) }
    var zoom by remember(model) { mutableFloatStateOf(1f) }
    var panX by remember(model) { mutableFloatStateOf(0f) }
    var panY by remember(model) { mutableFloatStateOf(0f) }

    Surface(modifier = modifier, color = Color(0xFF0B100F), shape = RoundedCornerShape(18.dp)) {
        Box(Modifier.fillMaxSize()) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(model) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        rotationY += pan.x * 0.009f
                        rotationX += pan.y * 0.009f
                        zoom = (zoom * gestureZoom).coerceIn(0.25f, 8f)
                        panX += pan.x * 0.35f
                        panY += pan.y * 0.35f
                    }
                }
            ) {
                val centerX = (model.min.x + model.max.x) / 2f
                val centerY = (model.min.y + model.max.y) / 2f
                val centerZ = (model.min.z + model.max.z) / 2f
                val largest = max(max(model.sizeX, model.sizeY), model.sizeZ).coerceAtLeast(0.001f)
                val scale = minOf(size.width, size.height) * 0.68f / largest * zoom
                val cosX = cos(rotationX); val sinX = sin(rotationX)
                val cosY = cos(rotationY); val sinY = sin(rotationY)

                data class Rendered(val depth: Float, val path: Path, val shade: Float)
                val stride = max(1, model.triangles.size / 24000)
                val rendered = ArrayList<Rendered>(model.triangles.size / stride + 1)

                fun project(p: Point3): Triple<Float, Float, Float> {
                    val x = p.x - centerX
                    val y = p.y - centerY
                    val z = p.z - centerZ
                    val y1 = y * cosX - z * sinX
                    val z1 = y * sinX + z * cosX
                    val x2 = x * cosY + z1 * sinY
                    val z2 = -x * sinY + z1 * cosY
                    return Triple(size.width / 2f + x2 * scale + panX, size.height / 2f - y1 * scale + panY, z2)
                }

                model.triangles.forEachIndexed { index, tri ->
                    if (index % stride == 0) {
                        val a = project(tri.a); val b = project(tri.b); val c = project(tri.c)
                        val ux = b.first - a.first; val uy = b.second - a.second
                        val vx = c.first - a.first; val vy = c.second - a.second
                        val cross = ux * vy - uy * vx
                        val shade = (0.48f + 0.42f * (cross / (sqrt(cross * cross + 1f)))).coerceIn(0.22f, 0.95f)
                        val path = Path().apply {
                            moveTo(a.first, a.second); lineTo(b.first, b.second); lineTo(c.first, c.second); close()
                        }
                        rendered += Rendered((a.third + b.third + c.third) / 3f, path, shade)
                    }
                }
                rendered.sortBy { it.depth }
                rendered.forEach { triangle ->
                    val base = triangle.shade
                    drawPath(triangle.path, Color(red = 0.24f * base, green = 0.83f * base, blue = 0.69f * base), style = Fill)
                    drawPath(triangle.path, Color(0xFF9AF0D7).copy(alpha = 0.18f), style = Stroke(width = 0.45f))
                }
                // Subtle ground reference.
                drawLine(Color(0xFF26332F), Offset(size.width * 0.12f, size.height * 0.84f), Offset(size.width * 0.88f, size.height * 0.84f), 1.dp.toPx())
            }
        }
    }
}
