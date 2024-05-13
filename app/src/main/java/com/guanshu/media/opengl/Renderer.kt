package com.guanshu.media.opengl

import android.util.Size
import com.guanshu.media.opengl.filters.DefaultRenderGraph
import com.guanshu.media.opengl.filters.RenderGraph
import com.guanshu.media.utils.Logger

private const val TAG = "Renderer"

abstract class Renderer(
    var renderGraph: RenderGraph = DefaultRenderGraph
) {

    // TODO call before init
    fun addFilter(filterId: Int, index: Int = 0) =
        run { renderGraph = RenderGraph().apply { addFilter(filterId, index) } }

    fun addRenderGraph(renderGraph: RenderGraph) = run { this.renderGraph = renderGraph }

    var init = false
        private set

    open fun init() {
        if (init) return
        init = true
        Logger.i(TAG, "init $this")

        renderGraph.filtersMap.values.forEach { it.forEach { it.init() } }
        renderGraph.outputFilter?.init()

        Logger.i(TAG, "init filters=${renderGraph.filtersMap}")
        Logger.i(TAG, "init mergingFilter=${renderGraph.outputFilter}")
    }

    abstract fun drawFrame(
        textureDatas: List<TextureData>,
        viewResolution: Size,
    )

}

object RendererFactory {
    fun createRenderer(renderGraph: RenderGraph): Renderer {
        return TextureRender(renderGraph)
    }
}
