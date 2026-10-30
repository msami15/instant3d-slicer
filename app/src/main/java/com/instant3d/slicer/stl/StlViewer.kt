package com.instant3d.slicer.stl

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun StlViewer(model: StlModel, modifier: Modifier = Modifier) {
    var mesh by remember(model) { mutableStateOf<StlRenderMesh?>(null) }
    var preparationError by remember(model) { mutableStateOf<String?>(null) }
    var renderError by remember(model) { mutableStateOf<String?>(null) }
    var wireframe by remember(model) { mutableStateOf(false) }

    LaunchedEffect(model) {
        mesh = null
        preparationError = null
        renderError = null
        try {
            mesh = withContext(Dispatchers.Default) { StlRenderMesh.from(model) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            preparationError = error.message ?: "This model could not be prepared for rendering."
        }
    }

    val renderMesh = mesh
    val context = LocalContext.current
    val surface = remember(renderMesh) {
        renderMesh?.let { data ->
            StlSurfaceView(context, data) { message -> renderError = message }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0B100F))
                .then(
                    if (surface == null || renderError != null) Modifier
                    else Modifier.pointerInput(surface) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var previousDistance = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                val active = event.changes.filter { it.pressed }
                                if (active.isEmpty()) break
                                when (active.size) {
                                    1 -> {
                                        val change = active[0]
                                        val delta = change.position - change.previousPosition
                                        surface.renderer.rotate(delta.x, delta.y)
                                        previousDistance = 0f
                                    }
                                    else -> {
                                        val centroidX = active.map { it.position.x }.average().toFloat()
                                        val centroidY = active.map { it.position.y }.average().toFloat()
                                        val previousCentroidX = active.map { it.previousPosition.x }.average().toFloat()
                                        val previousCentroidY = active.map { it.previousPosition.y }.average().toFloat()
                                        surface.renderer.pan(
                                            centroidX - previousCentroidX,
                                            centroidY - previousCentroidY,
                                            size.width.toFloat(),
                                            size.height.toFloat(),
                                        )
                                        val distance = active.map { change ->
                                            val dx = change.position.x - centroidX
                                            val dy = change.position.y - centroidY
                                            sqrt(dx * dx + dy * dy)
                                        }.average().toFloat() * 2f
                                        if (previousDistance > 0f && distance > 0f) {
                                            surface.renderer.zoomBy(distance / previousDistance)
                                        }
                                        previousDistance = distance
                                    }
                                }
                                event.changes.forEach { change -> change.consume() }
                            }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (renderMesh != null && surface != null && renderError == null) {
                AndroidView(factory = { surface }, modifier = Modifier.fillMaxSize())
                DisposableEffect(surface) {
                    surface.onResume()
                    onDispose { surface.onPause() }
                }
            } else {
                val message = preparationError ?: renderError
                if (message == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text("Preparing 3D model…", color = Color.White)
                    }
                } else {
                    Text(
                        "Unable to render this model. $message",
                        modifier = Modifier.padding(24.dp),
                        color = Color(0xFFFFB4AB),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { surface?.renderer?.resetCamera() },
                enabled = surface != null && renderError == null,
                modifier = Modifier.weight(1f).height(44.dp),
            ) { Text("Reset camera") }
            Button(
                onClick = { wireframe = !wireframe; surface?.renderer?.setWireframe(wireframe) },
                enabled = surface != null && renderError == null,
                modifier = Modifier.weight(1f).height(44.dp),
            ) { Text(if (wireframe) "Show solid" else "Wireframe") }
        }
    }

    LaunchedEffect(surface, wireframe) {
        surface?.renderer?.setWireframe(wireframe)
    }
}

private class StlSurfaceView(
    context: Context,
    mesh: StlRenderMesh,
    onRenderError: (String) -> Unit,
) : GLSurfaceView(context) {
    val renderer = StlGLRenderer(mesh, onRenderError)

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

}

private class StlGLRenderer(
    private val mesh: StlRenderMesh,
    private val onRenderError: (String) -> Unit,
) : GLSurfaceView.Renderer {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val errorReported = AtomicBoolean(false)
    @Volatile private var wireframe = false
    @Volatile private var zoom = 1f
    @Volatile private var rotationX = -25f
    @Volatile private var rotationZ = 35f
    @Volatile private var panX = 0f
    @Volatile private var panY = 0f
    private var program = 0
    private var positionAttribute = -1
    private var normalAttribute = -1
    private var mvpUniform = -1
    private var modelUniform = -1
    private var colorUniform = -1
    private var solidBuffer: FloatBuffer? = null
    private var wireBuffer: FloatBuffer? = null
    private var width = 1
    private var height = 1
    private var ready = false

    fun setWireframe(enabled: Boolean) { wireframe = enabled }
    fun rotate(dx: Float, dy: Float) {
        rotationZ += dx * 0.45f
        rotationX = (rotationX + dy * 0.45f).coerceIn(-89f, 89f)
    }
    fun zoomBy(factor: Float) {
        if (factor.isFinite() && factor > 0f) zoom = (zoom * factor).coerceIn(0.3f, 8f)
    }
    fun pan(dx: Float, dy: Float, viewWidth: Float, viewHeight: Float) {
        val distance = mesh.fitRadius / sin(Math.toRadians(22.5)).toFloat() * 1.12f / zoom
        val unitsPerPixel = (2f * distance * tanHalfFov) / viewHeight.coerceAtLeast(1f)
        panX += dx * unitsPerPixel
        panY -= dy * unitsPerPixel
    }
    fun resetCamera() {
        zoom = 1f
        rotationX = -25f
        rotationZ = 35f
        panX = 0f
        panY = 0f
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            GLES20.glClearColor(0.043f, 0.063f, 0.059f, 1f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glDepthFunc(GLES20.GL_LEQUAL)
            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            positionAttribute = GLES20.glGetAttribLocation(program, "aPosition")
            normalAttribute = GLES20.glGetAttribLocation(program, "aNormal")
            mvpUniform = GLES20.glGetUniformLocation(program, "uMvp")
            modelUniform = GLES20.glGetUniformLocation(program, "uModel")
            colorUniform = GLES20.glGetUniformLocation(program, "uColor")
            require(positionAttribute >= 0 && normalAttribute >= 0 && mvpUniform >= 0 && modelUniform >= 0 && colorUniform >= 0) {
                "The graphics driver could not initialize the 3D renderer."
            }
            solidBuffer = directBuffer(mesh.solidVertices)
            wireBuffer = directBuffer(mesh.wireframeVertices)
            ready = true
        } catch (error: Exception) {
            reportError(error)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, surfaceWidth: Int, surfaceHeight: Int) {
        width = surfaceWidth.coerceAtLeast(1)
        height = surfaceHeight.coerceAtLeast(1)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!ready) return
        try {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            GLES20.glUseProgram(program)

            val modelMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
            Matrix.rotateM(modelMatrix, 0, rotationZ, 0f, 0f, 1f)
            Matrix.rotateM(modelMatrix, 0, rotationX, 1f, 0f, 0f)

            val cameraDistance = mesh.fitRadius / sin(Math.toRadians(22.5)).toFloat() * 1.12f / zoom
            val directionLength = sqrt(4f * 4f + 6f * 6f + 4f * 4f)
            val eyeX = cameraDistance * 4f / directionLength
            val eyeY = -cameraDistance * 6f / directionLength
            val eyeZ = cameraDistance * 4f / directionLength
            val viewMatrix = FloatArray(16)
            Matrix.setLookAtM(viewMatrix, 0, eyeX, eyeY, eyeZ, 0f, 0f, 0f, 0f, 0f, 1f)
            Matrix.translateM(viewMatrix, 0, panX, panY, 0f)
            val projectionMatrix = FloatArray(16)
            Matrix.perspectiveM(projectionMatrix, 0, 45f, width.toFloat() / height, 0.01f, 100f)
            val modelView = FloatArray(16)
            val mvp = FloatArray(16)
            Matrix.multiplyMM(modelView, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(mvp, 0, projectionMatrix, 0, modelView, 0)
            GLES20.glUniformMatrix4fv(modelUniform, 1, false, modelMatrix, 0)
            GLES20.glUniformMatrix4fv(mvpUniform, 1, false, mvp, 0)
            GLES20.glUniform4f(colorUniform, 0.2f, 0.82f, 0.68f, 1f)

            val buffer = if (wireframe) wireBuffer else solidBuffer
            val data = buffer ?: return
            val positions = data.duplicate().apply { position(0) }
            val normals = data.duplicate().apply { position(3) }
            GLES20.glEnableVertexAttribArray(positionAttribute)
            GLES20.glEnableVertexAttribArray(normalAttribute)
            GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false, 6 * 4, positions)
            GLES20.glVertexAttribPointer(normalAttribute, 3, GLES20.GL_FLOAT, false, 6 * 4, normals)
            if (wireframe) {
                GLES20.glLineWidth(1.4f)
                GLES20.glDrawArrays(GLES20.GL_LINES, 0, mesh.vertexCount * 2)
            } else {
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount)
            }
            GLES20.glDisableVertexAttribArray(positionAttribute)
            GLES20.glDisableVertexAttribArray(normalAttribute)
        } catch (error: Exception) {
            ready = false
            reportError(error)
        }
    }

    private fun reportError(error: Exception) {
        if (errorReported.compareAndSet(false, true)) {
            mainHandler.post { onRenderError(error.message ?: "The device could not render this model.") }
        }
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val result = GLES20.glCreateProgram()
        GLES20.glAttachShader(result, vertex)
        GLES20.glAttachShader(result, fragment)
        GLES20.glLinkProgram(result)
        val status = IntArray(1)
        GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        require(status[0] == GLES20.GL_TRUE) { "The device could not link the 3D renderer." }
        return result
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        require(status[0] == GLES20.GL_TRUE) {
            GLES20.glGetShaderInfoLog(shader).ifBlank { "The device could not compile a 3D shader." }
        }
        return shader
    }

    private fun directBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(values); position(0) }

    companion object {
        private const val tanHalfFov = 0.41421357f
        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            void main() {
                gl_Position = uMvp * vec4(aPosition, 1.0);
                vNormal = mat3(uModel) * aNormal;
            }
        """
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            varying vec3 vNormal;
            void main() {
                float diffuse = abs(dot(normalize(vNormal), normalize(vec3(0.35, 0.55, 0.75))));
                float light = 0.24 + 0.76 * diffuse;
                gl_FragColor = vec4(uColor.rgb * light, uColor.a);
            }
        """
    }
}
