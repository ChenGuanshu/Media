package com.guanshu.media.opengl.filters

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES20.GL_ARRAY_BUFFER
import android.opengl.GLES20.GL_COLOR_BUFFER_BIT
import android.opengl.GLES20.GL_DEPTH_BUFFER_BIT
import android.opengl.GLES20.GL_ELEMENT_ARRAY_BUFFER
import android.opengl.GLES20.GL_FLOAT
import android.opengl.GLES20.GL_STATIC_DRAW
import android.opengl.GLES20.GL_TEXTURE0
import android.opengl.GLES20.GL_TRIANGLES
import android.opengl.GLES20.GL_UNSIGNED_INT
import android.opengl.GLES20.glActiveTexture
import android.opengl.GLES20.glBindBuffer
import android.opengl.GLES20.glBindTexture
import android.opengl.GLES20.glBufferData
import android.opengl.GLES20.glClear
import android.opengl.GLES20.glClearColor
import android.opengl.GLES20.glDisableVertexAttribArray
import android.opengl.GLES20.glDrawElements
import android.opengl.GLES20.glEnableVertexAttribArray
import android.opengl.GLES20.glGenBuffers
import android.opengl.GLES20.glUniformMatrix4fv
import android.opengl.GLES20.glUseProgram
import android.opengl.GLES20.glVertexAttribPointer
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import com.guanshu.media.opengl.FLOAT_SIZE_BYTES
import com.guanshu.media.opengl.INT_SIZE_BYTES
import com.guanshu.media.opengl.ImageTextureProgram
import com.guanshu.media.opengl.OesTextureProgram
import com.guanshu.media.opengl.TextureData
import com.guanshu.media.opengl.checkGlError
import com.guanshu.media.opengl.createProgram
import com.guanshu.media.opengl.getAtrribLocation
import com.guanshu.media.opengl.getUniformLocation
import com.guanshu.media.opengl.newTexture
import com.guanshu.media.opengl.updateTransformMatrix
import com.guanshu.media.utils.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

// 顶点坐标
private val vertexCoord = floatArrayOf(
    -1.0f, -1.0f, 0f, 0f, 0f,
    0.0f, -1.0f, 0f, 1f, 0f,
    -1.0f, 0.0f, 0f, 0f, 1f,
    0.0f, 0.0f, 0f, 1f, 1f,

    0.0f, -1.0f, 0f, 0f, 0f,
    1.0f, -1.0f, 0f, 1f, 0f,
    0.0f, 0.0f, 0f, 0f, 1f,
    1.0f, 0.0f, 0f, 1f, 1f,

    -1.0f, 0.0f, 0f, 0f, 0f,
    0.0f, 0.0f, 0f, 1f, 0f,
    -1.0f, 1.0f, 0f, 0f, 1f,
    0.0f, 1.0f, 0f, 1f, 1f,
)

private val vertexIndex = intArrayOf(
    0, 1, 2, 1, 2, 3,
    4, 5, 6, 5, 6, 7,
    8, 9, 10, 9, 10, 11,
)

private val imageVertexCoord = floatArrayOf(
    0.0f, 0.0f, 0f, 0f, 0f,
    1.0f, 0.0f, 0f, 1f, 0f,
    0.0f, 1.0f, 0f, 0f, 1f,
    1.0f, 1.0f, 0f, 1f, 1f,
)

private const val TAG = "FlattenWithImageFilter"

class FlattenWithImageFilter : BaseFilter(
    OesTextureProgram.VERTEX_SHADER,
    OesTextureProgram.FRAGMENT_SHADER,
) {

    private val mvpMatrix = FloatArray(16)

    private var mvpMatrixHandle = 0
    private var stMatrixHandle = 0
    private var aPositionHandle = 0
    private var aTextureHandle = 0

    private val vertexVbos = IntArray(1)
    private val vertextEbos = IntArray(1)

    private lateinit var indexBuffer: IntBuffer

    private lateinit var bitmap: Bitmap
    private lateinit var imageVertexBuffer: FloatBuffer
    private var bitmapTexture: Int = -12345
    private var bitmapProgram: Int = 0
    private val imageMvpMatrix = FloatArray(16)
    private val imageTextureMatrix = FloatArray(16)

    override fun init() {
        super.init()
        initOesTexture()
        initImageTexture()
    }

    private fun initOesTexture() {
        aPositionHandle = program.getAtrribLocation("aPosition")
        mvpMatrixHandle = program.getUniformLocation("uMVPMatrix")
        aTextureHandle = program.getAtrribLocation("aTextureCoord")
        stMatrixHandle = program.getUniformLocation("uSTMatrix")

        val vertexBuffer = ByteBuffer.allocateDirect(vertexCoord.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        vertexBuffer.put(vertexCoord).position(0)

        indexBuffer = ByteBuffer.allocateDirect(vertexIndex.size * INT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
        indexBuffer.put(vertexIndex).position(0)

        glGenBuffers(1, vertexVbos, 0)
        glBindBuffer(GL_ARRAY_BUFFER, vertexVbos[0])
        glBufferData(
            GL_ARRAY_BUFFER,
            vertexCoord.size * FLOAT_SIZE_BYTES,
            vertexBuffer,
            GL_STATIC_DRAW
        )

        glBindBuffer(GL_ARRAY_BUFFER, 0)

        glGenBuffers(1, vertextEbos, 0)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vertextEbos[0])
        glBufferData(
            GL_ELEMENT_ARRAY_BUFFER,
            vertexIndex.size * INT_SIZE_BYTES,
            indexBuffer,
            GL_STATIC_DRAW
        )
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun initImageTexture() {
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

        imageVertexBuffer = ByteBuffer.allocateDirect(imageVertexCoord.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        imageVertexBuffer.put(imageVertexCoord).position(0)
    }

    override fun render(
        textureDatas: List<TextureData>,
        viewResolution: Size,
    ) {
        val textureData = textureDatas.first()

        glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        glClear(GL_DEPTH_BUFFER_BIT or GL_COLOR_BUFFER_BIT)

        renderOesTexture(textureData, viewResolution)
        renderImageTexture()
    }

    private fun renderOesTexture(
        textureData: TextureData,
        viewResolution: Size,
    ) {
        glUseProgram(program)
        checkGlError("glUseProgram")

        val sTextureHandle = bitmapProgram.getUniformLocation("sTexture")
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureData.textureId)
        GLES20.glUniform1i(sTextureHandle, 0)

        Matrix.setIdentityM(mvpMatrix, 0)
        updateTransformMatrix(mvpMatrix, textureData.resolution, viewResolution)

        glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        glUniformMatrix4fv(stMatrixHandle, 1, false, textureData.matrix, 0)

        glBindBuffer(GL_ARRAY_BUFFER, vertexVbos[0])
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vertextEbos[0])

        glVertexAttribPointer(
            aPositionHandle,
            3,
            GL_FLOAT,
            false,
            5 * FLOAT_SIZE_BYTES,
            0,
        )
        glVertexAttribPointer(
            aTextureHandle,
            2,
            GL_FLOAT,
            false,
            5 * FLOAT_SIZE_BYTES,
            3 * FLOAT_SIZE_BYTES,
        )
        glEnableVertexAttribArray(aPositionHandle)
        glEnableVertexAttribArray(aTextureHandle)

        glDrawElements(GL_TRIANGLES, vertexIndex.size, GL_UNSIGNED_INT, 0)

        checkGlError("glDrawArrays")

        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun renderImageTexture() {
        glUseProgram(bitmapProgram)
        checkGlError("glUseProgram")

        val aPositionHandle = bitmapProgram.getAtrribLocation("aPosition")
        val mvpMatrixHandle = bitmapProgram.getUniformLocation("uMVPMatrix")
        val aTextureHandle = bitmapProgram.getAtrribLocation("aTextureCoord")
        val stMatrixHandle = bitmapProgram.getUniformLocation("uSTMatrix")
        val sTextureHandle = bitmapProgram.getUniformLocation("sTexture")

        glActiveTexture(GLES20.GL_TEXTURE1)
        glBindTexture(GLES20.GL_TEXTURE_2D, bitmapTexture)
        GLES20.glUniform1i(sTextureHandle, 1)
        checkGlError("glUniform1i")

        imageVertexBuffer.position(0)
        glVertexAttribPointer(
            aPositionHandle, 3, GL_FLOAT, false,
            5 * FLOAT_SIZE_BYTES, imageVertexBuffer
        )
        checkGlError("glVertexAttribPointer aPosition")
        glEnableVertexAttribArray(aPositionHandle)

        checkGlError("glEnableVertexAttribArray maPositionHandle")
        imageVertexBuffer.position(3)
        glVertexAttribPointer(
            aTextureHandle, 2, GL_FLOAT, false,
            5 * FLOAT_SIZE_BYTES, imageVertexBuffer
        )
        checkGlError("glVertexAttribPointer maTextureHandle")

        glEnableVertexAttribArray(aTextureHandle)

        Matrix.setIdentityM(imageMvpMatrix, 0)
        Matrix.setIdentityM(imageTextureMatrix, 0)

        glUniformMatrix4fv(mvpMatrixHandle, 1, false, imageMvpMatrix, 0)
        glUniformMatrix4fv(stMatrixHandle, 1, false, imageTextureMatrix, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")

        glDisableVertexAttribArray(aPositionHandle)
        glDisableVertexAttribArray(aTextureHandle)
    }
}