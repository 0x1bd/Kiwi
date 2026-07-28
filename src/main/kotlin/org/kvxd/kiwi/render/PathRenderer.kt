package org.kvxd.kiwi.render

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.bot.Bot
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.control.BlockBreaker
import org.kvxd.kiwi.path.MoveKind
import org.kvxd.kiwi.path.Path
import org.kvxd.kiwi.path.PathNode
import org.kvxd.kiwi.render.util.RenderScope
import org.kvxd.kiwi.render.util.Renderer3D
import java.awt.Color
import kotlin.math.min

object PathRenderer {

    private val COLOR_WALK = Color(54, 222, 98, 190)
    private val COLOR_JUMP = Color(73, 156, 255, 210)
    private val COLOR_FALL = Color(255, 120, 58, 210)
    private val COLOR_PILLAR = Color(72, 220, 224, 210)
    private val COLOR_DESCEND = Color(212, 128, 255, 210)
    private val COLOR_CLIMB = Color(180, 255, 120, 210)
    private val COLOR_SWIM = Color(239, 212, 118, 210)
    private val COLOR_BREAK = Color(255, 92, 126, 120)
    private val COLOR_DEST = Color(41, 244, 175, 210)
    private val COLOR_CURRENT = Color(255, 245, 112, 220)
    private val COLOR_PARTIAL = Color(255, 188, 74, 230)
    private val COLOR_MINING = Color(255, 80, 255, 135)

    private const val LINE_WIDTH = 3.5f
    private const val BREADCRUMB_INTERVAL = 4
    private const val BREAK_LOOKAHEAD = 12
    private const val LABEL_SCALE = 0.22f

    fun init() {
        LevelRenderEvents.END_MAIN.register { context ->
            Renderer3D.render(context) {
                if (!ConfigData.renderPath) return@render
                depthTest(false)

                renderBreakTarget()

                val path = Bot.navigator.path
                if (path.isEmpty) return@render

                val index = Bot.navigator.currentIndex.coerceIn(0, (path.size - 1).coerceAtLeast(0))
                renderPlannedBreaks(path, index)
                renderPath(path, index)
                renderLabels(path, index)
            }
        }
    }

    private fun RenderScope.renderBreakTarget() {
        val target = BlockBreaker.currentTarget ?: return
        drawBlockShape(target, COLOR_MINING, filled = true)
        drawBlockShape(target, Color.MAGENTA, filled = false, lineWidth = LINE_WIDTH)
        drawText(
            "MINING",
            Vec3(target.x + 0.5, target.y + 1.35, target.z + 0.5),
            Color(255, 210, 255, 255),
            scale = LABEL_SCALE
        )
    }

    private fun RenderScope.renderPath(path: Path, index: Int) {
        val start = (index - 1).coerceAtLeast(0)
        var previous = path.node(start)?.point() ?: return

        for (i in start + 1 until path.size) {
            val node = path.node(i) ?: continue
            val point = node.point()
            drawLine(previous, point, colorOf(node.kind), LINE_WIDTH)
            previous = point
        }

        for (i in index until path.size) {
            val node = path.node(i) ?: continue
            val important = i == index || i == path.size - 1
            if (!important && (i - index) % BREADCRUMB_INTERVAL != 0) continue

            val color = when {
                i == index -> COLOR_CURRENT
                i == path.size - 1 -> if (path.isPartial) COLOR_PARTIAL else COLOR_DEST
                else -> colorOf(node.kind)
            }
            drawFootprint(node, color, if (important) 0.78 else 0.36)
        }

        path.destination()?.let { destination ->
            drawAABB(AABB(destination.pos()), if (path.isPartial) COLOR_PARTIAL else COLOR_DEST, filled = false)
        }
    }

    private fun RenderScope.renderPlannedBreaks(path: Path, index: Int) {
        val cells = LinkedHashSet<BlockPos>()
        for (i in index until min(path.size, index + BREAK_LOOKAHEAD)) {
            val node = path.node(i) ?: continue
            for (cell in node.breaks) cells.add(BlockPos.of(cell))
        }

        for (cell in cells) {
            drawBlockShape(cell, COLOR_BREAK, filled = true)
            drawBlockShape(cell, Color(255, 92, 126, 230), filled = false, lineWidth = 2.0f)
        }
    }

    private fun RenderScope.renderLabels(path: Path, index: Int) {
        val node = path.node(index) ?: return
        val breaks = if (node.breaks.isEmpty()) "" else " break:${node.breaks.size}"
        drawText(
            "${node.kind} ${index + 1}/${path.size}$breaks",
            node.point().add(0.0, 0.9, 0.0),
            Color(255, 245, 170, 255),
            scale = LABEL_SCALE
        )

        val destination = path.destination() ?: return
        drawText(
            if (path.isPartial) "FRONTIER" else "GOAL",
            destination.point().add(0.0, 0.85, 0.0),
            if (path.isPartial) COLOR_PARTIAL else COLOR_DEST,
            scale = LABEL_SCALE
        )
    }

    private fun RenderScope.drawFootprint(node: PathNode, color: Color, size: Double) {
        val inset = (1.0 - size).coerceAtLeast(0.0) / 2.0
        val box = AABB(
            node.x + inset,
            node.feetY + 0.015,
            node.z + inset,
            node.x + 1.0 - inset,
            node.feetY + 0.07,
            node.z + 1.0 - inset
        )
        drawAABB(box, color, filled = true)
        drawAABB(box, Color(color.red, color.green, color.blue, 240), filled = false)
    }

    private fun PathNode.point(): Vec3 = Vec3(x + 0.5, feetY + 0.08, z + 0.5)

    private fun colorOf(kind: MoveKind): Color = when (kind) {
        MoveKind.WALK -> COLOR_WALK
        MoveKind.JUMP -> COLOR_JUMP
        MoveKind.FALL -> COLOR_FALL
        MoveKind.SWIM -> COLOR_SWIM
        MoveKind.CLIMB_UP, MoveKind.CLIMB_DOWN -> COLOR_CLIMB
        MoveKind.PILLAR -> COLOR_PILLAR
        MoveKind.DESCEND -> COLOR_DESCEND
    }
}
