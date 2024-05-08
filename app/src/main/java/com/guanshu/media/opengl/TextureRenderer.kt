package com.guanshu.media.opengl

import android.util.Size
import com.guanshu.media.opengl.filters.FilterConstants
import com.guanshu.media.opengl.filters.RenderGraph
import com.guanshu.media.opengl.filters.SingleTextureFilter
import com.guanshu.media.opengl.filters.TwoOesTextureFilter2
import com.guanshu.media.utils.Logger

private const val TAG = "TextureRender"


class TextureData(
    val textureId: Int,
    val matrix: FloatArray,
    var resolution: Size,
)

class TextureRender {

    var init = false
        private set

    private var renderGraph: RenderGraph = RenderGraph()
        .apply { addFilter(FilterConstants.SINGLE_TEXTURE) }

    private val fbo by lazy { newFbo() }

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
//            bindFbo(fbo,)

            textureDatas.forEachIndexed { index, textureData ->
                val filters = renderGraph.filtersMap[index]
                var input = textureData
                filters?.forEach {
                    it.render(listOf(textureData), viewResolution)
                }
            }

//            unbindFbo()
        } else {
            nextTextureData.addAll(textureDatas)
        }

        if (renderGraph.outputFilter != null) {
            renderGraph.outputFilter!!.render(textureDatas, viewResolution)
        } else if (textureDatas.size == 1) {
            Logger.d(TAG, "drawFrame: lazy init single output filter")
            renderGraph.outputFilter = SingleTextureFilter()
            renderGraph.outputFilter!!.init()
            renderGraph.outputFilter!!.render(textureDatas, viewResolution)
        } else {
            // TODO support multiple input
            Logger.d(TAG, "drawFrame: lazy init multiple output filter")
            renderGraph.outputFilter = TwoOesTextureFilter2()
            renderGraph.outputFilter!!.init()
            renderGraph.outputFilter!!.render(textureDatas, viewResolution)
        }

        checkGlError("onDrawFrame end")
    }
}