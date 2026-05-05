package org.kvxd.kiwi.render.util

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import org.kvxd.kiwi.client

object Renderer3D {

    inline fun render(
        context: LevelRenderContext,
        block: RenderScope.() -> Unit
    ) {
        val camera = client.gameRenderer.mainCamera
        val scope = RenderScope(context.poseStack(), context.bufferSource(), camera.position())
        scope.block()
    }

}