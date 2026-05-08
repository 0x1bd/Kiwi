package org.kvxd.kiwi.render

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.agent.control.PathNavigator
import org.kvxd.kiwi.agent.pathing.calc.NodePath
import org.kvxd.kiwi.agent.pathing.calc.MovementType
import org.kvxd.kiwi.agent.ui.DebugState
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.render.util.RenderScope
import org.kvxd.kiwi.render.util.Renderer3D
import org.kvxd.kiwi.util.math.toBottomCenterVec
import java.awt.Color
import kotlin.math.min

object PathRenderer {

    private val COLOR_WALK = Color(54, 222, 98, 190)
    private val COLOR_JUMP = Color(73, 156, 255, 210)
    private val COLOR_DROP = Color(255, 120, 58, 210)
    private val COLOR_PILLAR = Color(72, 220, 224, 210)
    private val COLOR_BREAK = Color(255, 92, 126, 120)
    private val COLOR_WATER_WALK = Color(239, 212, 118, 210)
    private val COLOR_DEST = Color(41, 244, 175, 210)
    private val COLOR_CURRENT = Color(255, 245, 112, 220)
    private val COLOR_NEXT = Color(255, 255, 255, 190)
    private val COLOR_PARTIAL = Color(255, 188, 74, 230)
    private val COLOR_MINE_TARGET = Color(255, 80, 255, 135)

    private const val LINE_WIDTH = 3.5f
    private const val PATH_Y_OFFSET = 0.08
    private const val BREADCRUMB_INTERVAL = 4
    private const val MINING_LOOKAHEAD = 10
    private const val LABEL_LOOKAHEAD = 5
    private const val PATH_LABEL_SCALE = 0.22f
    private const val SECONDARY_LABEL_SCALE = 0.19f

    fun init() {
        LevelRenderEvents.END_MAIN.register { context ->
            Renderer3D.render(context) {
                if (!ConfigData.renderPath) return@render

                depthTest(false)
                renderMiningTarget()

                val path = PathNavigator.path
                if (path.isEmpty || path.isFinished) return@render

                renderPlannedMining(path)
                renderPath(path)
                renderPathLabels(path)
            }
        }
    }

    private fun RenderScope.renderMiningTarget() {
        val target = DebugState.agentMineTarget ?: return

        drawBlockShape(target, COLOR_MINE_TARGET, filled = true)
        drawBlockShape(target, Color.MAGENTA, filled = false, lineWidth = LINE_WIDTH)

        val label = DebugState.agentMineBlockId.ifBlank { "block" }
        drawText(
            "MINING $label",
            Vec3(target.x + 0.5, target.y + 1.35, target.z + 0.5),
            Color(255, 210, 255, 255),
            scale = PATH_LABEL_SCALE
        )
    }

    private fun RenderScope.renderPath(path: NodePath) {
        val currentIndex = path.index.coerceIn(0, path.size - 1)
        val firstIndex = (currentIndex - 1).coerceAtLeast(0)

        var prevPos = path[firstIndex]?.pos?.pathPoint() ?: return

        for (i in firstIndex + 1 until path.size) {
            val node = path[i] ?: continue
            val currentPos = node.pos.pathPoint()
            drawLine(prevPos, currentPos, getTypeColor(node.type), LINE_WIDTH)
            prevPos = currentPos
        }

        for (i in currentIndex until path.size) {
            val node = path[i] ?: continue
            val isImportant = i == currentIndex || i == currentIndex + 1 || i == path.size - 1
            val isBreadcrumb = (i - currentIndex) % BREADCRUMB_INTERVAL == 0
            if (!isImportant && !isBreadcrumb) continue

            val color = when (i) {
                currentIndex -> COLOR_CURRENT
                currentIndex + 1 -> COLOR_NEXT
                path.size - 1 -> if (path.isPartial) COLOR_PARTIAL else COLOR_DEST
                else -> getTypeColor(node.type)
            }
            val size = if (isImportant) 0.78 else 0.36
            drawFootprint(node.pos, color, size)
        }

        path.last()?.pos?.let { lastPos ->
            drawAABB(AABB(lastPos), if (path.isPartial) COLOR_PARTIAL else COLOR_DEST, filled = false)
        }
    }

    private fun RenderScope.renderPlannedMining(path: NodePath) {
        val currentIndex = path.index.coerceIn(0, path.size - 1)
        val blocks = linkedSetOf<BlockPos>()

        for (i in currentIndex until min(path.size, currentIndex + MINING_LOOKAHEAD)) {
            path[i]?.miningBlocks?.forEach { blocks.add(it) }
        }

        blocks.forEachIndexed { index, block ->
            drawBlockShape(block, COLOR_BREAK, filled = true)
            drawBlockShape(block, Color(255, 92, 126, 230), filled = false, lineWidth = 2.0f)
            if (index < 3) {
                drawText(
                    "BREAK",
                    Vec3(block.x + 0.5, block.y + 1.2, block.z + 0.5),
                    Color(255, 180, 195, 255),
                    scale = SECONDARY_LABEL_SCALE
                )
            }
        }
    }

    private fun RenderScope.renderPathLabels(path: NodePath) {
        val currentIndex = path.index.coerceIn(0, path.size - 1)
        val labelEnd = min(path.size, currentIndex + LABEL_LOOKAHEAD)

        for (i in currentIndex until labelEnd) {
            val node = path[i] ?: continue
            val progress = "${i + 1}/${path.size}"
            val prefix = when (i) {
                currentIndex -> "NOW"
                currentIndex + 1 -> "NEXT"
                else -> "+${i - currentIndex}"
            }
            val partial = if (path.isPartial && i == path.size - 1) " PARTIAL" else ""
            val mining = node.miningBlocks.size.takeIf { it > 0 }?.let { " break:$it" } ?: ""
            val color = when (i) {
                currentIndex -> Color(255, 245, 170, 255)
                currentIndex + 1 -> Color(245, 245, 245, 245)
                else -> Color(210, 230, 255, 235)
            }

            drawText(
                "$prefix $progress ${node.type.name}$partial$mining",
                node.pos.pathPoint().add(0.0, 0.9 + (i - currentIndex) * 0.08, 0.0),
                color,
                scale = if (i == currentIndex) PATH_LABEL_SCALE else SECONDARY_LABEL_SCALE
            )
        }

        val last = path.last() ?: return
        val goalLabel = if (path.isPartial) "FRONTIER" else "GOAL"
        drawText(
            goalLabel,
            last.pos.pathPoint().add(0.0, 0.85, 0.0),
            if (path.isPartial) COLOR_PARTIAL else COLOR_DEST,
            scale = PATH_LABEL_SCALE
        )
    }

    private fun RenderScope.drawFootprint(pos: BlockPos, color: Color, size: Double) {
        val inset = (1.0 - size).coerceAtLeast(0.0) / 2.0
        val y = pos.y.toDouble()
        val aabb = AABB(
            pos.x + inset,
            y + 0.015,
            pos.z + inset,
            pos.x + 1.0 - inset,
            y + 0.07,
            pos.z + 1.0 - inset
        )
        drawAABB(aabb, color, filled = true)
        drawAABB(aabb, Color(color.red, color.green, color.blue, 240), filled = false)
    }

    private fun BlockPos.pathPoint(): Vec3 {
        return toBottomCenterVec().add(0.0, PATH_Y_OFFSET, 0.0)
    }

    private fun getTypeColor(type: MovementType): Color {
        return when (type) {
            MovementType.TRAVEL -> COLOR_WALK
            MovementType.JUMP -> COLOR_JUMP
            MovementType.DROP -> COLOR_DROP
            MovementType.PILLAR -> COLOR_PILLAR

            MovementType.WATER_WALK -> COLOR_WATER_WALK
        }
    }
}