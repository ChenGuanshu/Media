package com.guanshu.media.view

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.guanshu.media.opengl.FLOAT_SIZE_BYTES
import com.guanshu.media.opengl.abstraction.VertexBuffer
import com.guanshu.media.opengl.checkGlError
import com.guanshu.media.opengl.matrixReset
import com.guanshu.media.opengl.newMatrix
import com.guanshu.media.opengl.program.DefaultProgram
import com.guanshu.media.utils.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val TAG = "Cube3DGlSurfaceView"

class Cube3DGlSurfaceView : GLSurfaceView {

    constructor(context: Context) : super(context, null)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    init {
        setEGLContextClientVersion(3)
        setRenderer(Cube3dRenderer())
    }

    inner class Cube3dRenderer : Renderer {


        private val vertexBuffer: FloatArray
        private val indexBuffer: IntBuffer
        private var ratio = 1f
        private val modelMatrix = newMatrix()
        private val viewMatrix = newMatrix()
        private val projectionMatrix = newMatrix()
        private val mvpMatrix = newMatrix()
        private var curRotation = 0f

        private lateinit var program: DefaultProgram
        private lateinit var vbo: VertexBuffer

        init {
            val r = 0.5f
            val c = 1.0f
            vertexBuffer = floatArrayOf(
                // vertex
                r, r, r, //0
                -r, r, r, //1
                -r, -r, r, //2
                r, -r, r, //3
                r, r, -r, //4
                -r, r, -r, //5
                -r, -r, -r, //6
                r, -r, -r, //7
                // color
                c, c, c, 1f,
                0f, c, c, 1f,
                0f, 0f, c, 1f,
                c, 0f, c, 1f,
                c, c, 0f, 1f,
                0f, c, 0f, 1f,
                0f, 0f, 0f, 1f,
                c, 0f, 0f, 1f,
            )

//            // TODO
//            vertexBuffer = floatArrayOf(
//                0.8f, -0.8f, 0.0f,
//                -0.8f, -0.8f, 0.0f,
//                0.0f, 0.8f, 0.0f,
//            )

            val index = intArrayOf(
                0, 2, 1, 0, 2, 3, //前面
                0, 5, 1, 0, 5, 4, //上面
                0, 7, 3, 0, 7, 4, //右面
                6, 4, 5, 6, 4, 7, //后面
                6, 3, 2, 6, 3, 7, //下面
                6, 1, 2, 6, 1, 5 //左面
            )

//            vertexBuffer = ByteBuffer.allocateDirect(vertex.size * Float.SIZE_BYTES)
//                .order(ByteOrder.nativeOrder())
//                .asFloatBuffer()
//            vertexBuffer.put(vertex).position(0)

            indexBuffer = ByteBuffer.allocateDirect(index.size * Int.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer()
            indexBuffer.put(index).position(0)
        }


        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            Logger.d(TAG, "onSurfaceCreated")
            this@Cube3DGlSurfaceView.renderMode = RENDERMODE_CONTINUOUSLY

            //设置背景颜色
            GLES30.glClearColor(0.1f, 0.2f, 0.3f, 0.4f)
            //启动深度测试
            GLES20.glEnable(GLES30.GL_DEPTH_TEST)
            //编译着色器
            program = DefaultProgram()
            program.init()

            vbo = VertexBuffer()
            vbo.bind()
            vbo.addBuffer(vertexBuffer, GLES20.GL_STATIC_DRAW)
            vbo.unbind()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            //设置视图窗口
            Logger.d(TAG, "onSurfaceChanged $width*$height")
            GLES30.glViewport(0, 0, width, height)
            ratio = 1.0f * width / height
        }

        override fun onDrawFrame(gl: GL10?) {
//            Logger.v(TAG,"onDrawFrame")

            checkGlError("before drawFrame")
            program.use()

            //将颜色缓冲区设置为预设的颜色
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
            transform()

            vbo.bind()
            program.positionHandle.bindAtrribPointer(3, 0, 0)
            program.colorHandle.bindAtrribPointer(4, 0, 24 * Float.SIZE_BYTES)
            checkGlError("bindAttrib")

            //绘制正方体的表面（6个面，每面2个三角形，每个三角形3个顶点）
            GLES20.glDrawElements(
                GLES30.GL_TRIANGLES,
                6 * 6,
                GLES30.GL_UNSIGNED_BYTE,
                indexBuffer,
            )
            checkGlError("drawElements")

            program.positionHandle.unbind()
            program.colorHandle.unbind()
            vbo.unbind()

            checkGlError("after drawFrame")
        }


        private fun transform() {
            //初始化modelMatrix, viewMatrix, projectionMatrix
//            modelMatrix.matrixReset()
//            viewMatrix.matrixReset()
//            projectionMatrix.matrixReset()
//            curRotation = (curRotation + 2) % 360;
//            Matrix.rotateM(modelMatrix, 0, curRotation, 1f, 1f, 1f); //获取模型旋转变换矩阵
//            Matrix.setLookAtM(viewMatrix, 0, 0f, 5f, 10f, 0f, 0f, 0f, 0f, 1f, 0f) //获取观测变换矩阵
//            Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 20f) //获取投影变换矩阵
//
//            //计算MVP变换矩阵: mvpMatrix = projectionMatrix * viewMatrix * modelMatrix
//            mvpMatrix.matrixReset()
//            Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0);
//            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0);
            //设置MVP变换矩阵

            mvpMatrix.matrixReset()
            program.matrixHandle.bindUniform(1, mvpMatrix, 0)
        }
    }
}