package com.guanshu.media.opengl.filters

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Size
import com.guanshu.media.opengl.FLOAT_SIZE_BYTES
import com.guanshu.media.opengl.TextureData
import com.guanshu.media.opengl.TwoOesTextureMixProgram
import com.guanshu.media.opengl.checkGlError
import com.guanshu.media.utils.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder

// 顶点坐标和纹理坐标
private val verticesData = floatArrayOf(
    // X, Y, Z, U, V
    -1.0f, -1.0f, 0f, 0f, 0f,
    1.0f, -1.0f, 0f, 1f, 0f,
    -1.0f, 1.0f, 0f, 0f, 1f,
    1.0f, 1.0f, 0f, 1f, 1f,
)

private const val TAG = "TwoOesTextureMixFilter"

class TwoOesTextureMixFilter : BaseFilter(
    TwoOesTextureMixProgram.VERTEX_SHADER,
    TwoOesTextureMixProgram.FRAGMENT_SHADER,
) {

    private val vertexVbos = IntArray(1)
    private var aPositionHandle = 0
    private var aTextureHandle = 0
    private var stMatrixHandle1 = 0
    private var stMatrixHandle2 = 0

    override fun init() {
        super.init()
        Logger.i(TAG, "call init")
        aPositionHandle = program.getAttribLocation("aPosition")
        aTextureHandle = program.getAttribLocation("aTextureCoord")
        stMatrixHandle1 = program.getUniformLocation("uSTMatrix1")
        stMatrixHandle2 = program.getUniformLocation("uSTMatrix2")

        val vertices = ByteBuffer.allocateDirect(verticesData.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        vertices.put(verticesData).position(0)

        GLES20.glGenBuffers(1, vertexVbos, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexVbos[0])
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            verticesData.size * FLOAT_SIZE_BYTES,
            vertices,
            GLES20.GL_STATIC_DRAW
        )

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
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
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + index)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureData.textureId)
            val handle = program.getUniformLocation("sTexture${index+1}")
            GLES20.glUniform1i(handle, index)
        }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexVbos[0])
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

        GLES20.glUniformMatrix4fv(stMatrixHandle1, 1, false, textureDatas[0].matrix, 0)
        GLES20.glUniformMatrix4fv(stMatrixHandle2, 1, false, textureDatas[1].matrix, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }
}