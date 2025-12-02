package org.kvxd.baobab.render

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.util.math.Box
import org.kvxd.baobab.config.ConfigManager
import org.kvxd.baobab.control.PathExecutor
import org.kvxd.baobab.render.util.Renderer3D
import java.awt.Color

object PathRenderer {

    val LINE_COLOR: Color = Color.ORANGE
    val DEST_COLOR: Color = Color.GREEN
    const val LINE_WIDTH = 4.0f

    fun init() {
        WorldRenderEvents.END_MAIN.register { context ->
            Renderer3D.render(context) {
                val path = PathExecutor.path

                if (path.isEmpty || path.isFinished) return@render
                if (!ConfigManager.data.renderPath) return@render

                val startIndex = path.index.coerceIn(0, path.size - 1)

                var prevPos = path[startIndex]?.pos?.toCenterPos() ?: return@render

                depthTest(false)

                for (i in (startIndex + 1) until path.size) {
                    val node = path[i]
                    val currentPos = node?.pos?.toCenterPos() ?: return@render

                    drawLine(
                        start = prevPos,
                        end = currentPos,
                        color = LINE_COLOR,
                        lineWidth = LINE_WIDTH
                    )

                    prevPos = currentPos
                }

                val lastPos = path.last()?.pos ?: return@render
                val destBox = Box(
                    lastPos.x.toDouble(), lastPos.y.toDouble(), lastPos.z.toDouble(),
                    lastPos.x + 1.0, lastPos.y + 1.0, lastPos.z + 1.0
                )

                drawBox(
                    box = destBox,
                    color = DEST_COLOR,
                    filled = false
                )
            }
        }
    }

}