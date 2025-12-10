package org.kvxd.kiwi.render

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.control.PathExecutor
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.render.util.RenderScope
import org.kvxd.kiwi.render.util.Renderer3D
import java.awt.Color

object PathRenderer {

    private val COLOR_WALK = Color(0, 255, 0, 200)
    private val COLOR_JUMP = Color(0, 100, 255, 200)
    private val COLOR_DROP = Color(255, 50, 50, 200)
    private val COLOR_PILLAR = Color(255, 165, 0, 200)
    private val COLOR_MINE = Color(255, 105, 180, 200)
    private val COLOR_DEST = Color(255, 0, 255, 255)

    private const val LINE_WIDTH = 3.0f
    private const val NODE_SIZE = 0.15

    fun init() {
        WorldRenderEvents.END_MAIN.register { context ->
            Renderer3D.render(context) {
                val path = PathExecutor.path

                if (path.isEmpty || path.isFinished) return@render
                if (!ConfigData.renderPath) return@render

                depthTest(false)

                val vecOffset = Vec3(0.0, 0.5, 0.0)
                val currentIndex = path.index.coerceIn(0, path.size - 1)

                var prevPos: Vec3?
                var loopStart: Int

                if (currentIndex > 0) {
                    val prevNode = path[currentIndex - 1] ?: return@render
                    prevPos = prevNode.pos.center.add(vecOffset)

                    drawNodeMarker(prevPos, getTypeColor(prevNode.type))

                    loopStart = currentIndex
                } else {
                    val startNode = path[0] ?: return@render
                    prevPos = startNode.pos.center.add(vecOffset)

                    drawNodeMarker(prevPos, getTypeColor(startNode.type))

                    loopStart = 1
                }

                for (i in loopStart until path.size) {
                    val node = path[i] ?: continue
                    val currentPos = node.pos.center.add(vecOffset)
                    val segmentColor = getTypeColor(node.type)

                    drawLine(
                        start = prevPos!!,
                        end = currentPos,
                        color = segmentColor,
                        lineWidth = LINE_WIDTH
                    )

                    drawNodeMarker(currentPos, segmentColor)

                    prevPos = currentPos
                }

                val lastPos = path.last()?.pos ?: return@render

                val destAABB = AABB(
                    lastPos.x.toDouble(), lastPos.y.toDouble(), lastPos.z.toDouble(),
                    lastPos.x + 1.0, lastPos.y + 1.0, lastPos.z + 1.0
                )

                drawAABB(aabb = destAABB, color = COLOR_DEST, filled = false)
            }
        }
    }

    private fun RenderScope.drawNodeMarker(pos: Vec3, color: Color) {
        val half = NODE_SIZE / 2.0
        val aabb = AABB(
            pos.x - half, pos.y - half, pos.z - half,
            pos.x + half, pos.y + half, pos.z + half
        )

        drawAABB(aabb, color, filled = true)
        drawAABB(aabb, Color.WHITE, filled = false)
    }

    private fun getTypeColor(type: MovementType): Color {
        return when (type) {
            MovementType.TRAVEL -> COLOR_WALK
            MovementType.JUMP -> COLOR_JUMP
            MovementType.DROP -> COLOR_DROP
            MovementType.PILLAR -> COLOR_PILLAR
            MovementType.MINE -> COLOR_MINE
        }
    }
}