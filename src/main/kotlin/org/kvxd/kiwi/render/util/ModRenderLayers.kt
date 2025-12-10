package org.kvxd.kiwi.render.util

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import org.kvxd.kiwi.Kiwi
import java.util.*

object ModRenderLayers {

    private val PIPELINE_QUADS_NO_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath(Kiwi.MOD_ID, "quads_no_depth"))
            .withCull(true)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(true)
            .build()
    )

    private val PIPELINE_QUADS_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath(Kiwi.MOD_ID, "quads_depth"))
            .withCull(true)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(true)
            .build()
    )

    private val PIPELINE_LINES_NO_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath(Kiwi.MOD_ID, "lines_no_depth"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(true)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES)
            .build()
    )

    private val PIPELINE_LINES_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath(Kiwi.MOD_ID, "lines_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES)
            .build()
    )

    val QUADS_NO_DEPTH = RenderType.create(
        "${Kiwi.MOD_ID}_quads_nd", 1024, false, true, PIPELINE_QUADS_NO_DEPTH,
        RenderType.CompositeState.builder().createCompositeState(false)
    )

    val QUADS_DEPTH: RenderType = RenderType.create(
        "${Kiwi.MOD_ID}_quads_d", 1024, false, true, PIPELINE_QUADS_DEPTH,
        RenderType.CompositeState.builder().createCompositeState(false)
    )

    private val linesNoDepthCache = mutableMapOf<Float, RenderType>()
    private val linesDepthCache = mutableMapOf<Float, RenderType>()

    fun LINES_NO_DEPTH(width: Float) = linesNoDepthCache.computeIfAbsent(width) { w ->
        RenderType.create(
            "${Kiwi.MOD_ID}_lines_nd_$w", 1024, false, true, PIPELINE_LINES_NO_DEPTH,
            RenderType.CompositeState.builder()
                .setLineState(RenderStateShard.LineStateShard(OptionalDouble.of(w.toDouble())))
                .createCompositeState(false)
        )
    }

    fun LINES(width: Float): RenderType = linesDepthCache.computeIfAbsent(width) { w ->
        RenderType.create(
            "${Kiwi.MOD_ID}_lines_d_$w", 1024, false, true, PIPELINE_LINES_DEPTH,
            RenderType.CompositeState.builder()
                .setLineState(RenderStateShard.LineStateShard(OptionalDouble.of(w.toDouble())))
                .createCompositeState(false)
        )
    }
}