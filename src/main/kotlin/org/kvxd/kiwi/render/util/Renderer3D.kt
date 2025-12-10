package org.kvxd.kiwi.render.util

import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.renderer.MultiBufferSource
import org.kvxd.kiwi.client

object Renderer3D {

    inline fun render(
        stack: PoseStack,
        bufferSource: MultiBufferSource,
        block: RenderScope.() -> Unit
    ) {
        val camera = client.gameRenderer.mainCamera
        val scope = RenderScope(stack, bufferSource, camera.position)
        scope.block()

        if (bufferSource is MultiBufferSource.BufferSource) {
            bufferSource.endBatch()
        }
    }

    inline fun render(
        context: WorldRenderContext,
        block: RenderScope.() -> Unit
    ) {
        val camera = client.gameRenderer.mainCamera
        val scope = RenderScope(context.matrices(), context.consumers(), camera.position)
        scope.block()
    }

}