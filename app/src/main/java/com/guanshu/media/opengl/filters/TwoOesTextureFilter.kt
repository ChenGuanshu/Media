package com.guanshu.media.opengl.filters

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Size
import com.guanshu.media.opengl.FLOAT_SIZE_BYTES
import com.guanshu.media.opengl.OesTextureProgram
import com.guanshu.media.opengl.TextureData
import com.guanshu.media.opengl.checkGlError
import com.guanshu.media.opengl.getAtrribLocation
import com.guanshu.media.opengl.getUniformLocation
import com.guanshu.media.opengl.updateTransformMatrix
import com.guanshu.media.utils.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

private val vertexData = floatArrayOf(
    // left
    -1.0f, -1.0f, 0f, 0f, 0f,
    0.0f, -1.0f, 0f, 1f, 0f,
    -1.0f, 1.0f, 0f, 0f, 1f,
    0.0f, 1.0f, 0f, 1f, 1f,
    // right
    0.0f, -1.0f, 0f, 0f, 0f,
    1.0f, -1.0f, 0f, 1f, 0f,
    0.0f, 1.0f, 0f, 0f, 1f,
    1.0f, 1.0f, 0f, 1f, 1f,
)

private const val TAG = "TwoOesTextureFilter"

/**
 * 渲染texture
 */
class TwoOesTextureFilter : BaseFilter(
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

    override fun init() {
        super.init()
        Logger.i(TAG, "call init")
        aPositionHandle = program.getAtrribLocation("aPosition")
        mvpMatrixHandle = program.getUniformLocation("uMVPMatrix")
        aTextureHandle = program.getAtrribLocation("aTextureCoord")
        stMatrixHandle = program.getUniformLocation("uSTMatrix")
        sTextureHandle = program.getUniformLocation("sTexture")

        vertexBuffer = ByteBuffer.allocateDirect(vertexData.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        vertexBuffer.put(vertexData).position(0)
    }

    override fun render(
        textureDatas: List<TextureData>,
        viewResolution: Size,
    ) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
        program.use()
        checkGlError("glUseProgram")
        textureDatas.forEachIndexed { index, textureData ->
            render(index, textureData, Size(viewResolution.width / 2, viewResolution.height))
        }
    }

    private fun render(index: Int, textureData: TextureData, viewResolution: Size) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + index)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureData.textureId)
        GLES20.glUniform1i(sTextureHandle, index)
        checkGlError("glUniform1i, $sTextureHandle")

        vertexBuffer.position(index * 20)
        GLES20.glVertexAttribPointer(
            aPositionHandle, 3, GLES20.GL_FLOAT, false,
            5 * FLOAT_SIZE_BYTES, vertexBuffer,
        )
        checkGlError("glVertexAttribPointer aPosition")
        GLES20.glEnableVertexAttribArray(aPositionHandle)

        checkGlError("glEnableVertexAttribArray maPositionHandle")
        vertexBuffer.position(index * 20 + 3)

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
}