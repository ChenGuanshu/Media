package com.guanshu.media.opengl.filters

import com.guanshu.media.opengl.filters.FilterConstants.toFilter

val DefaultRenderGraph = RenderGraph().apply { addFilter(FilterConstants.SINGLE_TEXTURE) }

class RenderGraph() {

    constructor(filterId: Int) : this() {
        addFilter(filterId)
    }

    val filtersMap = hashMapOf<Int, ArrayList<RenderPass>>()
    var outputFilter: RenderPass? = null

    fun addFilter(filterId: Int, index: Int = 0) {
        val filters = filtersMap.getOrPut(index) { arrayListOf() }
        filters.add(filterId.toFilter())
    }

    fun addOutputFilter(filterId: Int) {
        outputFilter = filterId.toFilter()
    }
}