package org.kvxd.kiwi.render

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.util.math.Box
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.control.PathExecutor
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.render.util.Renderer3D
import java.awt.Color

object PathRenderer {

    private val COLOR_WALK = Color.GREEN
    private val COLOR_JUMP = Color(0, 100, 255)
    private val COLOR_DROP = Color.RED
    private val COLOR_DIAGONAL = Color.CYAN
    private val COLOR_PILLAR = Color.ORANGE

    private val COLOR_DEST = Color.MAGENTA

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
                    val node = path[i] ?: continue
                    val currentPos = node.pos.toCenterPos()

                    val segmentColor = when (node.type) {
                        MovementType.WALK -> COLOR_WALK
                        MovementType.JUMP -> COLOR_JUMP
                        MovementType.DROP -> COLOR_DROP
                        MovementType.DIAGONAL -> COLOR_DIAGONAL
                        MovementType.PILLAR -> COLOR_PILLAR
                    }

                    drawLine(
                        start = prevPos,
                        end = currentPos,
                        color = segmentColor,
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
                    color = COLOR_DEST,
                    filled = false
                )
            }
        }
    }

}