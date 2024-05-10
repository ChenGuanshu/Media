package com.guanshu.media.opengl.filters

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Size
import com.guanshu.media.opengl.FLOAT_SIZE_BYTES
import com.guanshu.media.opengl.OesTextureProgram
import com.guanshu.media.opengl.TextureData
import com.guanshu.media.opengl.abstraction.VertexBuffer
import com.guanshu.media.opengl.checkGlError
import com.guanshu.media.opengl.program.ExternalTextureProgram
import com.guanshu.media.opengl.updateTransformMatrix
import com.guanshu.media.utils.Logger

// 顶点坐标和纹理坐标
private val verticesData = floatArrayOf(
    // X, Y, Z, U, V
    -1.0f, -1.0f, 0f, 0f, 0f,
    1.0f, -1.0f, 0f, 1f, 0f,
    -1.0f, 1.0f, 0f, 0f, 1f,
    1.0f, 1.0f, 0f, 1f, 1f,
)

private const val TAG = "SingleTextureFilter"

/**
 * 渲染texture
 */
class SingleTextureFilter : BaseFilter(ExternalTextureProgram()) {

    private lateinit var vertexBuffer: VertexBuffer
    private val mvpMatrix = FloatArray(16)
    private var mvpMatrixHandle = 0
    private var stMatrixHandle = 0
    private var aPositionHandle = 0
    private var aTextureHandle = 0

    override fun init() {
        super.init()
        Logger.i(TAG, "call init")
        aPositionHandle = program.getAtrribLocation("aPosition")
        mvpMatrixHandle = program.getUniformLocation("uMVPMatrix")
        aTextureHandle = program.getAtrribLocation("aTextureCoord")
        stMatrixHandle = program.getUniformLocation("uSTMatrix")

        vertexBuffer = VertexBuffer()
        vertexBuffer.addBuffer(verticesData)
        vertexBuffer.unbind()
        checkGlError("vertexBuffer")
    }

    override fun render(
        textureDatas: List<TextureData>,
        viewResolution: Size,
    ) {
        val textureData = textureDatas.first()

        checkGlError("before glClearColor")
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

        checkGlError("before glUseProgram")
        program.use()
        checkGlError("after glUseProgram")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureData.textureId)

        vertexBuffer.bind()

        GLES20.glVertexAttribPointer(
            aPositionHandle,
            3,
            GLES20.GL_FLOAT,
            false,
            5 * FLOAT_SIZE_BYTES,
            0,
        )
        GLES20.glVertexAttribPointer(
            aTextureHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            5 * FLOAT_SIZE_BYTES,
            3 * FLOAT_SIZE_BYTES
        )
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glEnableVertexAttribArray(aTextureHandle)

        Matrix.setIdentityM(mvpMatrix, 0)
        updateTransformMatrix(mvpMatrix, textureData.resolution, viewResolution)

//        Matrix.scaleM(mvpMatrix, 0, 0.8f, 0.8f, 1f)

        // 缩放纹理，会导致纹理坐标 >1 的使用 clamp_to_edge mode，出现像素重复
        // 建议缩放顶点坐标
//        Matrix.scaleM(stMatrix, 0, 2f, 2f, 1f)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(stMatrixHandle, 1, false, textureData.matrix, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")

        vertexBuffer.unbind()
    }
}