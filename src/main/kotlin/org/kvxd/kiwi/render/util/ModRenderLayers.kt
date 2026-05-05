package org.kvxd.kiwi.render.util

import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import org.kvxd.kiwi.Kiwi

//TODO: fix depth test
object ModRenderLayers {

    private val PIPELINE_QUADS_NO_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Kiwi.MOD_ID, "quads_no_depth"))
            .withCull(true)
            .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, true))
            //.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
           // .withDepthWrite(true)
            .build()
    )

    private val PIPELINE_QUADS_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Kiwi.MOD_ID, "quads_depth"))
            .withCull(true)
            .withDepthStencilState(DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
            //.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
           // .withDepthWrite(true)
            .build()
    )

    val QUADS_NO_DEPTH = RenderType.create(
        "${Kiwi.MOD_ID}_quads_nd", RenderSetup.builder(PIPELINE_QUADS_NO_DEPTH)
            .sortOnUpload()
            .createRenderSetup()
    )

    val QUADS_DEPTH: RenderType = RenderType.create(
        "${Kiwi.MOD_ID}_quads_d", RenderSetup.builder(PIPELINE_QUADS_DEPTH)
            .sortOnUpload()
            .createRenderSetup()
    )
}