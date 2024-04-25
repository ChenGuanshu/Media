package com.guanshu.media.opengl.filters

import android.opengl.GLES11Ext
import android.opengl.GLES20.GL_ARRAY_BUFFER
import android.opengl.GLES20.GL_COLOR_BUFFER_BIT
import android.opengl.GLES20.GL_DEPTH_BUFFER_BIT
import android.opengl.GLES20.GL_ELEMENT_ARRAY_BUFFER
import android.opengl.GLES20.GL_FLOAT
import android.opengl.GLES20.GL_STATIC_DRAW
import android.opengl.GLES20.GL_TEXTURE0
import android.opengl.GLES20.GL_TRIANGLES
import android.opengl.GLES20.GL_TRIANGLE_STRIP
import android.opengl.GLES20.GL_UNSIGNED_INT
import android.opengl.GLES20.glActiveTexture
import android.opengl.GLES20.glBindBuffer
import android.opengl.GLES20.glBindTexture
import android.opengl.GLES20.glBufferData
import android.opengl.GLES20.glClear
import android.opengl.GLES20.glClearColor
import android.opengl.GLES20.glDrawArrays
import android.opengl.GLES20.glDrawElements
import android.opengl.GLES20.glEnableVertexAttribArray
import android.opengl.GLES20.glGenBuffers
import android.opengl.GLES20.glUniformMatrix4fv
import android.opengl.GLES20.glUseProgram
import android.opengl.GLES20.glVertexAttribPointer
import android.opengl.Matrix
import android.util.Size
import com.guanshu.media.opengl.FLOAT_SIZE_BYTES
import com.guanshu.media.opengl.INT_SIZE_BYTES
import com.guanshu.media.opengl.checkGlError
import com.guanshu.media.opengl.getAtrribLocation
import com.guanshu.media.opengl.getUniformLocation
import com.guanshu.media.utils.DefaultSize
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer

// 顶点坐标
val vertexCoord = floatArrayOf(
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

    0.0f, 0.0f, 0f, 0f, 0f,
    1.0f, 0.0f, 0f, 1f, 0f,
    0.0f, 1.0f, 0f, 0f, 1f,
    1.0f, 1.0f, 0f, 1f, 1f,
)

val vertexIndex = intArrayOf(
    0, 1, 2, 1, 2, 3,
    4, 5, 6, 5, 6, 7,
    8, 9, 10, 9, 10, 11,
    12, 13, 14, 13, 14, 15,
)

/**
 * 将画面平铺成 2*2， 使用绘制4个矩形的方式完成
 */
class FlattenFilter : BaseFilter(
    VERTEX_SHADER,
    FRAGMENT_SHADER,
) {

    private val mvpMatrix = FloatArray(16)

    private var mvpMatrixHandle = 0
    private var stMatrixHandle = 0
    private var aPositionHandle = 0
    private var aTextureHandle = 0

    private val vertexVbos = IntArray(1)
    private val vertextEbos = IntArray(1)

    private lateinit var indexBuffer: IntBuffer

    override fun init() {
        super.init()
        // 纹理坐标

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

    override fun render(
        textureId: Int,
        textMatrix: FloatArray,
        mediaResolution: Size,
        screenResolution: Size
    ) {
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        glClear(GL_DEPTH_BUFFER_BIT or GL_COLOR_BUFFER_BIT)
        glUseProgram(program)
        checkGlError("glUseProgram")
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        Matrix.setIdentityM(mvpMatrix, 0)
        adjustTransformMatrix(mvpMatrix, mediaResolution, screenResolution)

        glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        glUniformMatrix4fv(stMatrixHandle, 1, false, textMatrix, 0)

        glBindBuffer(GL_ARRAY_BUFFER, vertexVbos[0])
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vertextEbos[0])

        // 1： 4个矩形分开画
//        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)  // 第一个矩形
//        glDrawArrays(GL_TRIANGLE_STRIP, 4, 4)  // 第二个矩形
//        glDrawArrays(GL_TRIANGLE_STRIP, 8, 4)  // 第三个矩形
//        glDrawArrays(GL_TRIANGLE_STRIP, 12, 4) // 第四个矩形

        // 2: 用 index buffer 直接画，有一次 cpu -> gpu 的index传递
//        glDrawElements(GL_TRIANGLES, vertexIndex.size, GL_UNSIGNED_INT, indexBuffer)

        // 3. 用ebo 传输顶点 index
        glDrawElements(GL_TRIANGLES, vertexIndex.size, GL_UNSIGNED_INT, 0)

        checkGlError("glDrawArrays")

        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    /**
     * TODO 这个可以优化成只做一次
     */
    private fun adjustTransformMatrix(
        matrix: FloatArray,
        mediaResolution: Size,
        screenResolution: Size,
    ) {
        if (mediaResolution == DefaultSize || screenResolution == DefaultSize) {
            return
        }
        val mediaAspectRatio = mediaResolution.width.toFloat() / mediaResolution.height
        val viewAspectRatio = screenResolution.width.toFloat() / screenResolution.height

        var scaleX = 1f
        var scaleY = 1f
        if (mediaAspectRatio > viewAspectRatio) {
            // 视频比view更宽,x填满整个屏幕,y需要缩放，
            val expectedHeight =
                screenResolution.width.toFloat() / mediaResolution.width * mediaResolution.height
            // 视频高度被默认拉伸填充了view，需要缩放
            scaleY = expectedHeight / screenResolution.height
        } else {
            val expectedWidth =
                screenResolution.height.toFloat() / mediaResolution.height * mediaResolution.width
            scaleX = expectedWidth / screenResolution.width
        }
        Matrix.scaleM(matrix, 0, scaleX, scaleY, 1f)
    }
}