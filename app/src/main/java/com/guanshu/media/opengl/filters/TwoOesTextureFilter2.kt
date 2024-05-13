package com.guanshu.media.opengl.filters

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES20.GL_UNSIGNED_INT
import android.opengl.GLES20.glDrawElements
import android.util.Size
import com.guanshu.media.opengl.FLOAT_SIZE_BYTES
import com.guanshu.media.opengl.INT_SIZE_BYTES
import com.guanshu.media.opengl.TextureData
import com.guanshu.media.opengl.TwoOesTexture2Program
import com.guanshu.media.opengl.checkGlError
import com.guanshu.media.utils.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer

// 顶点坐标和纹理坐标
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

private val vertexIndex = intArrayOf(
    0, 1, 2, 1, 2, 3,
    4, 5, 6, 5, 6, 7,
)

private const val TAG = "TwoOesTextureFilter2"

/**
 * 不同于 TwoOesTextureFilter，这里合并的两次渲染操作成一个
 */
class TwoOesTextureFilter2 : BaseFilter(
    TwoOesTexture2Program.VERTEX_SHADER,
    TwoOesTexture2Program.FRAGMENT_SHADER,
) {

    private val vertexVbos = IntArray(1)
    private var aPositionHandle = 0
    private var aTextureHandle = 0
    private var stMatrixHandle1 = 0
    private var stMatrixHandle2 = 0

    private lateinit var indexBuffer: IntBuffer

    override fun init() {
        super.init()
        Logger.i(TAG, "call init")
        aPositionHandle = program.getAttribLocation("aPosition")
        aTextureHandle = program.getAttribLocation("aTextureCoord")
        stMatrixHandle1 = program.getUniformLocation("uSTMatrix1")
        stMatrixHandle2 = program.getUniformLocation("uSTMatrix2")

        indexBuffer = ByteBuffer.allocateDirect(vertexIndex.size * INT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
        indexBuffer.put(vertexIndex).position(0)

        val vertices = ByteBuffer.allocateDirect(vertexData.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        vertices.put(vertexData).position(0)

        GLES20.glGenBuffers(1, vertexVbos, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexVbos[0])
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            vertexData.size * FLOAT_SIZE_BYTES,
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
            val handle = program.getUniformLocation("sTexture${index + 1}")
            GLES20.glUniform1i(handle, index)

            // TODO scale the texture
//            updateTransformMatrix(textureData.matrix, textureData.resolution, Size(viewResolution.width / 2, viewResolution.height))
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

        glDrawElements(GLES20.GL_TRIANGLES, vertexIndex.size, GL_UNSIGNED_INT, indexBuffer)

        checkGlError("glDrawArrays")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }
}