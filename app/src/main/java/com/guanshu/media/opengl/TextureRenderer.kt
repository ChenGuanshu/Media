package com.guanshu.media.opengl

import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Size
import com.guanshu.media.opengl.filters.FilterConstants
import com.guanshu.media.opengl.filters.RenderGraph
import com.guanshu.media.opengl.filters.SingleImageTextureFilter
import com.guanshu.media.opengl.filters.SingleTextureFilter
import com.guanshu.media.opengl.filters.TwoOesTextureFilter2
import com.guanshu.media.utils.Logger

private const val TAG = "TextureRender"


class TextureData(
    val textureId: Int,
    var matrix: FloatArray,
    var resolution: Size,
    var textureType: Int = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
)

class TextureRender {

    var init = false
        private set

    private var renderGraph: RenderGraph = RenderGraph()
        .apply { addFilter(FilterConstants.SINGLE_TEXTURE) }

    private val fbo by lazy { newFbo() }
    private var fboTextureData: TextureData? = null

    private var testBitmap: Bitmap? = null

    // TODO call before init
    fun addFilter(filterId: Int, index: Int = 0) =
        run { renderGraph = RenderGraph().apply { addFilter(filterId, index) } }

    fun addRenderGraph(renderGraph: RenderGraph) = run { this.renderGraph = renderGraph }

    /**
     * Initializes GL state.  Call this after the EGL surface has been created and made current.
     */
    fun init() {
        if (init) return
        init = true
        Logger.i(TAG, "init $this")

        renderGraph.filtersMap.values.forEach { it.forEach { it.init() } }
        renderGraph.outputFilter?.init()

        Logger.i(TAG, "init filters=${renderGraph.filtersMap}")
        Logger.i(TAG, "init mergingFilter=${renderGraph.outputFilter}")
    }

    fun drawFrame(
        textureDatas: List<TextureData>,
        viewResolution: Size,
    ) {
        checkGlError("onDrawFrame start")

        val nextTextureData = arrayListOf<TextureData>()
        if (renderGraph.filtersMap.values.isNotEmpty()) {
            textureDatas.forEachIndexed { index, textureData ->

                if (fboTextureData == null) {
                    val fboTexture = newTexture(
                        GLES20.GL_TEXTURE_2D,
                        textureData.resolution.width,
                        textureData.resolution.height
                    )
                    fboTextureData = TextureData(
                        fboTexture,
                        textureData.matrix,
                        textureData.resolution,
                        GLES20.GL_TEXTURE_2D,
                    )
                }
                bindFbo(fbo, fboTextureData!!.textureId)

                val filters = renderGraph.filtersMap[index]

                GLES20.glViewport(0, 0, textureData.resolution.width, textureData.resolution.height)
                filters?.forEach { it.render(listOf(textureData), textureData.resolution) }

//                if (testBitmap == null) {
//                    testBitmap =
//                        readToBitmap(textureData.resolution.width, textureData.resolution.height)
//                    Logger.d(TAG, "read to bitmap")
//                }

                unbindFbo()
                Matrix.setIdentityM(fboTextureData!!.matrix, 0)
                nextTextureData.add(fboTextureData!!)

            }

        } else {
            nextTextureData.addAll(textureDatas)
        }

        GLES20.glViewport(0, 0, viewResolution.width, viewResolution.height)
        if (renderGraph.outputFilter != null) {
            renderGraph.outputFilter!!.render(nextTextureData, viewResolution)
        } else if (nextTextureData.size == 1) {
            if (nextTextureData[0].textureType == GLES20.GL_TEXTURE_2D) {
                renderGraph.outputFilter = SingleImageTextureFilter()
            } else {
                renderGraph.outputFilter = SingleTextureFilter()
            }
            renderGraph.outputFilter!!.init()
            renderGraph.outputFilter!!.render(nextTextureData, viewResolution)
            Logger.d(TAG, "drawFrame: lazy init ${renderGraph.outputFilter}")
        } else {
            // TODO it should be image texture
            // TODO support multiple input
            renderGraph.outputFilter = TwoOesTextureFilter2()
            renderGraph.outputFilter!!.init()
            renderGraph.outputFilter!!.render(nextTextureData, viewResolution)
            Logger.d(TAG, "drawFrame: lazy init ${renderGraph.outputFilter}")
        }

        checkGlError("onDrawFrame end")
    }
}