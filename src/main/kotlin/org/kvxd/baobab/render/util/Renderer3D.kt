package org.kvxd.baobab.render.util

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import org.kvxd.baobab.client

object Renderer3D {

    inline fun render(
        stack: MatrixStack,
        consumers: VertexConsumerProvider,
        block: RenderScope.() -> Unit
    ) {
        val camera = client.gameRenderer.camera
        val scope = RenderScope(stack, consumers, camera.pos)
        scope.block()

        if (consumers is VertexConsumerProvider.Immediate) {
            consumers.draw()
        }
    }

    inline fun render(
        context: WorldRenderContext,
        block: RenderScope.() -> Unit
    ) {
        val camera = client.gameRenderer.camera
        val scope = RenderScope(context.matrices(), context.consumers(), camera.pos)
        scope.block()
    }

}