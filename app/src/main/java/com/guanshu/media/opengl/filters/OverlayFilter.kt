package com.guanshu.media.opengl.filters

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.util.Size
import com.guanshu.media.opengl.PerfLogger
import com.guanshu.media.opengl.TextureData
import com.guanshu.media.opengl.abstraction.Sampler2DTexture
import com.guanshu.media.opengl.bindFbo
import com.guanshu.media.opengl.buildIndexArray
import com.guanshu.media.opengl.buildVertexArray
import com.guanshu.media.opengl.checkGlError
import com.guanshu.media.opengl.newFbo
import com.guanshu.media.opengl.newMatrix
import com.guanshu.media.opengl.program.ExternalTextureProgram
import com.guanshu.media.opengl.program.Texture2dProgram
import com.guanshu.media.opengl.toFloatBuffer
import com.guanshu.media.opengl.toIntBuffer
import com.guanshu.media.opengl.unbindFbo
import com.guanshu.media.utils.Logger
import java.nio.FloatBuffer
import java.nio.IntBuffer

private val oesVertex = buildVertexArray(0f, 0f, 2f, 2f)

private val imageVertex = buildVertexArray(
    -0.75f, -0.75f, 0.5f, 0.5f,
    -0.25f, -0.75f, 0.5f, 0.5f,
    0.25f, -0.75f, 0.5f, 0.5f,
    0.75f, -0.75f, 0.5f, 0.5f,

    -0.75f, -0.25f, 0.5f, 0.5f,
    -0.25f, -0.25f, 0.5f, 0.5f,
    0.25f, -0.25f, 0.5f, 0.5f,
    0.75f, -0.25f, 0.5f, 0.5f,

    -0.75f, 0.25f, 0.5f, 0.5f,
    -0.25f, 0.25f, 0.5f, 0.5f,
    0.25f, 0.25f, 0.5f, 0.5f,
    0.75f, 0.25f, 0.5f, 0.5f,

    -0.75f, 0.75f, 0.5f, 0.5f,
    -0.25f, 0.75f, 0.5f, 0.5f,
    0.25f, 0.75f, 0.5f, 0.5f,
    0.75f, 0.75f, 0.5f, 0.5f,
)

private val imageIndex = buildIndexArray(imageVertex)

private const val TAG = "OverlayFilter"
private const val ALPHA = 0.5f
private const val DEBUG = false
private fun maybeGlFinish() {
    if (DEBUG) GLES20.glFinish()
}

/**
 * 测试数据
 * A: 16张图片做离屏合成为一张texture:
 * initCacheTexture, run 182 times, avg cost:39282.25274725275 ns, 0.03928225274725275 ms
 * renderOesTexture, run 182 times, avg cost:2019756.0824175824 ns, 2.019756082417582 ms
 * renderImageTexture, run 182 times, avg cost:1719746.1098901099 ns, 1.71974610989011 ms
 *
 * B: 16张图片每次用一个draw call重新渲染
 * renderOesTexture, run 182 times, avg cost:2052554.5934065934 ns, 2.0525545934065934 ms
 * renderImageTexture, run 182 times, avg cost:3033459.230769231 ns, 3.033459230769231 ms
 *
 * C: 16张图片每次用独立的draw call重新渲染
 * renderOesTexture, run 182 times, avg cost:2145864.4285714286 ns, 2.1458644285714286 ms
 * renderImageTexture, run 182 times, avg cost:3810358.401098901 ns, 3.810358401098901 ms
 *
 * 从渲染效率上看，每帧率的渲染时长从5ms优化到4ms，提升20%
 * 但对于帧率为30fps的视频来说，几乎没有影响。渲染速度的优化，最好针对于临界值的情况，比如30fps，每帧渲染渲染超过33ms。
 */
class OverlayFilter : BaseFilter(ExternalTextureProgram()) {

    private lateinit var oesVertexBuffer: FloatBuffer
    private val eProgram = program as ExternalTextureProgram
    private val mvpMatrix = newMatrix()

    private lateinit var bitmap: Bitmap
    private lateinit var imageVertexBuffer: FloatBuffer
    private lateinit var imageIndexBuffer: IntBuffer
    private lateinit var bitmapTexture: Sampler2DTexture
    private val imageProgram = Texture2dProgram()
    private val imageMvpMatrix = newMatrix()
    private var cachedTexture: Sampler2DTexture? = null

    private val perfLogger = PerfLogger()

    var onBeforeDraw: (() -> Unit)? = null

    override fun init() {
        super.init()
        oesVertexBuffer = oesVertex.toFloatBuffer()

        imageProgram.init()
        imageVertexBuffer = imageVertex.toFloatBuffer()
        imageIndexBuffer = imageIndex.toIntBuffer()
        bitmap = BitmapFactory
            .decodeStream(javaClass.getResourceAsStream("/res/drawable/pikachu.png"))
        bitmapTexture = Sampler2DTexture.fromBitmap(bitmap)
        bitmap.recycle()
        checkGlError("texImage2D")
        Logger.d(TAG, "initBitmapTexture $bitmapTexture, ${bitmap.width},${bitmap.height}")
    }

    override fun render(textureDatas: List<TextureData>, viewResolution: Size) {
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        perfLogger.logPerf("initCacheTexture") {
            maybeInitCacheTexture(viewResolution)
            maybeGlFinish()
        }
        onBeforeDraw?.invoke()
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
        perfLogger.logPerf("renderOesTexture") {
            renderOesTexture(textureDatas.first(), viewResolution)
            maybeGlFinish()
        }
        perfLogger.logPerf("renderImageTexture") {
            renderImageTexture()
            maybeGlFinish()
        }
        if (DEBUG) perfLogger.print()

        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun maybeInitCacheTexture(viewResolution: Size) {
        if (cachedTexture != null) return

        // TODO 测试性能
        cachedTexture = Sampler2DTexture.create(viewResolution)
        val fbo = newFbo()
        bindFbo(fbo, cachedTexture!!.textureId)

        Logger.d(TAG, "initCacheTexture $viewResolution")
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

        imageProgram.use()
        bitmapTexture.bind(1)
        imageProgram.sTextureHandle.bindUniform(1)
        imageVertexBuffer.position(0)
        imageProgram.aPositionHandle.bindAttribPointer(3, 5 * Float.SIZE_BYTES, imageVertexBuffer)
        imageVertexBuffer.position(3)
        imageProgram.aTextureHandle.bindAttribPointer(2, 5 * Float.SIZE_BYTES, imageVertexBuffer)
        imageProgram.mvpMatrixHandle.bindUniform(1, imageMvpMatrix, 0)
        imageProgram.stMatrixHandle.bindUniform(1, bitmapTexture.matrix, 0)
        checkGlError("before bindUniform")
        imageProgram.alphaHandle.bindUniform(ALPHA)
        checkGlError("after bindUniform")
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            imageIndex.size,
            GLES20.GL_UNSIGNED_INT,
            imageIndexBuffer,
        )

        unbindFbo()
        checkGlError("after maybeInitCacheTexture")

        Logger.d(TAG, "initCacheTexture DONE")
    }

    private fun renderImageTexture() {
        val imageTexture = cachedTexture ?: return
        checkGlError("renderImageTexture start")
        imageProgram.use()

        imageTexture.bind(1)
        imageProgram.sTextureHandle.bindUniform(1)

        oesVertexBuffer.position(0)
        imageProgram.aPositionHandle.bindAttribPointer(3, 5 * Float.SIZE_BYTES, oesVertexBuffer)
        oesVertexBuffer.position(3)
        imageProgram.aTextureHandle.bindAttribPointer(2, 5 * Float.SIZE_BYTES, oesVertexBuffer)

        imageProgram.mvpMatrixHandle.bindUniform(0, mvpMatrix, 0)
        imageProgram.stMatrixHandle.bindUniform(0, imageTexture.matrix, 0)
        imageProgram.alphaHandle.bindUniform(ALPHA)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        checkGlError("renderImageTexture end")
    }

    private fun renderOesTexture(
        textureData: TextureData,
        viewResolution: Size,
    ) {
        checkGlError("renderOesTexture start")
        program.use()

        textureData.bind(0)
        eProgram.sTextureHandle.bindUniform(0)

        oesVertexBuffer.position(0)
        eProgram.aPositionHandle.bindAttribPointer(3, 5 * Float.SIZE_BYTES, oesVertexBuffer)
        oesVertexBuffer.position(3)
        eProgram.aTextureHandle.bindAttribPointer(2, 5 * Float.SIZE_BYTES, oesVertexBuffer)

        eProgram.mvpMatrixHandle.bindUniform(0, mvpMatrix, 0)
        eProgram.stMatrixHandle.bindUniform(0, textureData.matrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("renderOesTexture end")
    }
}