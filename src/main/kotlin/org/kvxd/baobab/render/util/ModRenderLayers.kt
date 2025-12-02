package org.kvxd.baobab.render.util

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderPhase
import net.minecraft.client.render.VertexFormats
import net.minecraft.util.Identifier
import org.kvxd.baobab.Baobab
import java.util.OptionalDouble

object ModRenderLayers {
    
    private val PIPELINE_QUADS_NO_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(Baobab.MOD_ID, "quads_no_depth"))
            .withCull(true)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(true)
            .build()
    )

    private val PIPELINE_QUADS_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(Baobab.MOD_ID, "quads_depth"))
            .withCull(true)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(true)
            .build()
    )

    private val PIPELINE_LINES_NO_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(Identifier.of(Baobab.MOD_ID, "lines_no_depth"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(true)
            .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL, VertexFormat.DrawMode.LINES)
            .build()
    )

    private val PIPELINE_LINES_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(Identifier.of(Baobab.MOD_ID, "lines_depth"))
            .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL, VertexFormat.DrawMode.LINES)
            .build()
    )
    
    val QUADS_NO_DEPTH: RenderLayer = RenderLayer.of(
        "${Baobab.MOD_ID}_quads_nd", 1024, false, true, PIPELINE_QUADS_NO_DEPTH,
        RenderLayer.MultiPhaseParameters.builder().build(false)
    )

    val QUADS_DEPTH: RenderLayer = RenderLayer.of(
        "${Baobab.MOD_ID}_quads_d", 1024, false, true, PIPELINE_QUADS_DEPTH,
        RenderLayer.MultiPhaseParameters.builder().build(false)
    )

    private val linesNoDepthCache = mutableMapOf<Float, RenderLayer>()
    private val linesDepthCache = mutableMapOf<Float, RenderLayer>()

    fun LINES_NO_DEPTH(width: Float): RenderLayer = linesNoDepthCache.computeIfAbsent(width) { w ->
        RenderLayer.of(
            "${Baobab.MOD_ID}_lines_nd_$w", 1024, false, true, PIPELINE_LINES_NO_DEPTH,
            RenderLayer.MultiPhaseParameters.builder()
                .lineWidth(RenderPhase.LineWidth(OptionalDouble.of(w.toDouble())))
                .build(false)
        )
    }

    fun LINES(width: Float): RenderLayer = linesDepthCache.computeIfAbsent(width) { w ->
        RenderLayer.of(
            "${Baobab.MOD_ID}_lines_d_$w", 1024, false, true, PIPELINE_LINES_DEPTH,
            RenderLayer.MultiPhaseParameters.builder()
                .lineWidth(RenderPhase.LineWidth(OptionalDouble.of(w.toDouble())))
                .build(false)
        )
    }
}