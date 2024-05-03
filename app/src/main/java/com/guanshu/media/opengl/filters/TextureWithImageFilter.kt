package com.guanshu.media.opengl.filters

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import com.guanshu.media.opengl.FLOAT_SIZE_BYTES
import com.guanshu.media.opengl.ImageTextureProgram
import com.guanshu.media.opengl.OesTextureProgram
import com.guanshu.media.opengl.TextureData
import com.guanshu.media.opengl.checkGlError
import com.guanshu.media.opengl.createProgram
import com.guanshu.media.opengl.getAtrribLocation
import com.guanshu.media.opengl.getUniformLocation
import com.guanshu.media.opengl.newTexture
import com.guanshu.media.opengl.updateTransformMatrix
import com.guanshu.media.utils.DefaultSize
import com.guanshu.media.utils.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer


// 顶点坐标和纹理坐标
private val verticesData = floatArrayOf(
    // X, Y, Z, U, V
    -1.0f, -1.0f, 0f, 0f, 0f,
    1.0f, -1.0f, 0f, 1f, 0f,
    -1.0f, 1.0f, 0f, 0f, 1f,
    1.0f, 1.0f, 0f, 1f, 1f,
)

// image
private val imageVerticesData = floatArrayOf(
    // X, Y, Z, U, V
    -1.0f, 0.0f, 0f, 0f, 0f,
    0.0f, 0.0f, 0f, 1f, 0f,
    -1.0f, 1.0f, 0f, 0f, 1f,
    0.0f, 1.0f, 0f, 1f, 1f,
)

private const val TAG = "TextureWithImageFilter"

class TextureWithImageFilter : BaseFilter(
    OesTextureProgram.VERTEX_SHADER,
    OesTextureProgram.FRAGMENT_SHADER,
) {

    private lateinit var vertexBuffer: FloatBuffer
    private val mvpMatrix = FloatArray(16)
    private var mvpMatrixHandle = 0
    private var stMatrixHandle = 0
    private var aPositionHandle = 0
    private var aTextureHandle = 0
    private var sTextureHandle = 0

    private lateinit var bitmap: Bitmap
    private lateinit var imageVertexBuffer: FloatBuffer
    private var bitmapTexture: Int = -12345
    private var bitmapProgram: Int = 0
    private val imageMvpMatrix = FloatArray(16)
    private val imageTextureMatrix = FloatArray(16)

    override fun init() {
        super.init()
        Logger.i(TAG, "call init")
        initOesTexture()
        initBitmapTexture()
    }

    private fun initOesTexture() {
        aPositionHandle = program.getAtrribLocation("aPosition")
        mvpMatrixHandle = program.getUniformLocation("uMVPMatrix")
        aTextureHandle = program.getAtrribLocation("aTextureCoord")
        stMatrixHandle = program.getUniformLocation("uSTMatrix")
        sTextureHandle = program.getUniformLocation("sTexture")

        vertexBuffer = ByteBuffer.allocateDirect(verticesData.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        vertexBuffer.put(verticesData).position(0)
    }

    private fun initBitmapTexture() {
        val stream = this.javaClass.getResourceAsStream("/res/drawable/pikachu.png")
        bitmap = BitmapFactory
            .decodeStream(stream)

        val textures = IntArray(1)
        newTexture(textures, GLES20.GL_TEXTURE_2D)
        bitmapTexture = textures[0]
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        checkGlError("texImage2D")

        Logger.i(TAG, "initBitmapTexture $bitmapTexture, ${bitmap.width},${bitmap.height}")

        bitmapProgram = createProgram(
            ImageTextureProgram.VERTEX_SHADER,
            ImageTextureProgram.FRAGMENT_SHADER,
        )
        if (bitmapProgram == 0) {
            throw RuntimeException("failed creating program")
        }
        Logger.i(TAG, "initBitmapTexture, bitmapProgram=$bitmapProgram")

        imageVertexBuffer = ByteBuffer.allocateDirect(imageVerticesData.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        imageVertexBuffer.put(imageVerticesData).position(0)
    }

    override fun render(
        textureDatas: List<TextureData>,
        viewResolution: Size,
    ) {
        val textureData = textureDatas.first()

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

        renderOesTexture(textureData, viewResolution)
        renderBitmapTexture()
    }

    private fun renderOesTexture(
        textureData: TextureData,
        viewResolution: Size,
    ) {
        GLES20.glUseProgram(program)
        checkGlError("glUseProgram")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureData.textureId)
        GLES20.glUniform1i(sTextureHandle, 0)
        checkGlError("glUniform1i, $sTextureHandle")

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(
            aPositionHandle, 3, GLES20.GL_FLOAT, false,
            5 * FLOAT_SIZE_BYTES, vertexBuffer,
        )
        checkGlError("glVertexAttribPointer aPosition")
        GLES20.glEnableVertexAttribArray(aPositionHandle)

        checkGlError("glEnableVertexAttribArray maPositionHandle")
        vertexBuffer.position(3)
        GLES20.glVertexAttribPointer(
            aTextureHandle, 2, GLES20.GL_FLOAT, false,
            5 * FLOAT_SIZE_BYTES, vertexBuffer,
        )
        checkGlError("glVertexAttribPointer maTextureHandle")
        GLES20.glEnableVertexAttribArray(aTextureHandle)

        Matrix.setIdentityM(mvpMatrix, 0)
        updateTransformMatrix(mvpMatrix, textureData.resolution, viewResolution)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(stMatrixHandle, 1, false, textureData.matrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")
    }

    private fun renderBitmapTexture() {
        GLES20.glUseProgram(bitmapProgram)
        checkGlError("glUseProgram")

        val aPositionHandle = bitmapProgram.getAtrribLocation("aPosition")
        val mvpMatrixHandle = bitmapProgram.getUniformLocation("uMVPMatrix")
        val aTextureHandle = bitmapProgram.getAtrribLocation("aTextureCoord")
        val stMatrixHandle = bitmapProgram.getUniformLocation("uSTMatrix")
        val sTextureHandle = bitmapProgram.getUniformLocation("sTexture")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bitmapTexture)
        GLES20.glUniform1i(sTextureHandle, 1)
        checkGlError("glUniform1i")

        imageVertexBuffer.position(0)
        GLES20.glVertexAttribPointer(
            aPositionHandle, 3, GLES20.GL_FLOAT, false,
            5 * FLOAT_SIZE_BYTES, imageVertexBuffer
        )
        checkGlError("glVertexAttribPointer aPosition")
        GLES20.glEnableVertexAttribArray(aPositionHandle)

        checkGlError("glEnableVertexAttribArray maPositionHandle")
        imageVertexBuffer.position(3)
        GLES20.glVertexAttribPointer(
            aTextureHandle, 2, GLES20.GL_FLOAT, false,
            5 * FLOAT_SIZE_BYTES, imageVertexBuffer
        )
        checkGlError("glVertexAttribPointer maTextureHandle")
        GLES20.glEnableVertexAttribArray(aTextureHandle)

        Matrix.setIdentityM(imageMvpMatrix, 0)
        Matrix.setIdentityM(imageTextureMatrix, 0)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, imageMvpMatrix, 0)
        GLES20.glUniformMatrix4fv(stMatrixHandle, 1, false, imageTextureMatrix, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")
    }
}