package org.kvxd.kiwi.render.util

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext

object Renderer3D {

    inline fun render(
        context: LevelRenderContext,
        block: RenderScope.() -> Unit
    ) {
        val camera = context.levelState().cameraRenderState
        val scope = RenderScope(context.poseStack(), context.bufferSource(), camera.pos, camera.orientation)
        try {
            scope.block()
        } finally {
            context.bufferSource().endBatch()
        }
    }

}