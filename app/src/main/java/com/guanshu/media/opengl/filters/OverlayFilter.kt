package com.guanshu.media.opengl.filters

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.util.Log
import android.util.Size
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
import com.guanshu.media.opengl.readToBitmap
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

        maybeInitCacheTexture(viewResolution)
        onBeforeDraw?.invoke()
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
        renderOesTexture(textureDatas.first(), viewResolution)
        renderImageTexture()

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