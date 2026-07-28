package org.kvxd.kiwi.render.util

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.ShapeRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import org.kvxd.kiwi.client
import org.kvxd.kiwi.level
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector4f
import java.awt.Color
import kotlin.math.sqrt

class RenderScope(
    val stack: PoseStack,
    val source: MultiBufferSource,
    val cameraPos: Vec3,
    private val cameraOrientation: Quaternionfc
) {

    companion object {
        private const val FULL_BRIGHT = 15728880
    }

    private val posVec = Vector4f()
    private val normalVec = Vector3f()

    private var isDepthTestEnabled: Boolean = true

    fun depthTest(enabled: Boolean) {
        this.isDepthTestEnabled = enabled
    }

    private fun VertexConsumer.pos(matrix: Matrix4f, x: Float, y: Float, z: Float): VertexConsumer {
        posVec.set(x, y, z, 1.0f)
        matrix.transform(posVec)
        return this.addVertex(posVec.x, posVec.y, posVec.z)
    }

    private fun VertexConsumer.normal(matrix: Matrix3f, x: Float, y: Float, z: Float): VertexConsumer {
        normalVec.set(x, y, z)
        matrix.transform(normalVec)

        if (normalVec.lengthSquared() > 0) normalVec.normalize()
        return this.setNormal(normalVec.x, normalVec.y, normalVec.z)
    }

    private fun relative(pos: Vec3): Vec3 = pos.subtract(cameraPos)
    private fun relative(x: Double, y: Double, z: Double): Vec3 =
        Vec3(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z)

    fun drawLine(
        start: Vec3,
        end: Vec3,
        color: Color,
        lineWidth: Float = 1.0f
    ) {
        val layer = if (isDepthTestEnabled) RenderTypes.lines() else ModRenderLayers.LINES_NO_DEPTH
        val buffer = source.getBuffer(layer)

        val relStart = relative(start)
        val relEnd = relative(end)

        val dx = (relEnd.x - relStart.x).toFloat()
        val dy = (relEnd.y - relStart.y).toFloat()
        val dz = (relEnd.z - relStart.z).toFloat()
        val length = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
        if (length <= 0.0001f) return

        val invLen = 1.0f / length
        val nx = dx * invLen
        val ny = dy * invLen
        val nz = dz * invLen

        val matrices = stack.last()
        val pMat = matrices.pose()
        val nMat = matrices.normal()

        val r = color.red
        val g = color.green
        val b = color.blue
        val a = color.alpha

        buffer.pos(pMat, relStart.x.toFloat(), relStart.y.toFloat(), relStart.z.toFloat())
            .setColor(r, g, b, a)
            .normal(nMat, nx, ny, nz)
            .setLineWidth(lineWidth)

        buffer.pos(pMat, relEnd.x.toFloat(), relEnd.y.toFloat(), relEnd.z.toFloat())
            .setColor(r, g, b, a)
            .normal(nMat, nx, ny, nz)
            .setLineWidth(lineWidth)
    }

    fun drawAABB(
        aabb: AABB,
        color: Color,
        filled: Boolean = true
    ) {
        val layer = if (isDepthTestEnabled) ModRenderLayers.QUADS_DEPTH else ModRenderLayers.QUADS_NO_DEPTH

        if (!filled) {
            drawOutlinedAABB(aabb, color)
            return
        }

        val buffer = source.getBuffer(layer)
        val matrix = stack.last().pose()

        val min = relative(aabb.minX, aabb.minY, aabb.minZ)
        val max = relative(aabb.maxX, aabb.maxY, aabb.maxZ)

        val x1 = min.x.toFloat();
        val y1 = min.y.toFloat();
        val z1 = min.z.toFloat()
        val x2 = max.x.toFloat();
        val y2 = max.y.toFloat();
        val z2 = max.z.toFloat()

        val r = color.red
        val g = color.green
        val b = color.blue
        val a = color.alpha

        fun v(x: Float, y: Float, z: Float) {
            buffer.pos(matrix, x, y, z).setColor(r, g, b, a)
        }

        v(x1, y1, z1); v(x2, y1, z1); v(x2, y1, z2); v(x1, y1, z2)
        v(x1, y2, z2); v(x2, y2, z2); v(x2, y2, z1); v(x1, y2, z1)
        v(x1, y1, z1); v(x1, y1, z2); v(x1, y2, z2); v(x1, y2, z1)
        v(x2, y1, z2); v(x2, y1, z1); v(x2, y2, z1); v(x2, y2, z2)
        v(x1, y1, z2); v(x2, y1, z2); v(x2, y2, z2); v(x1, y2, z2)
        v(x2, y1, z1); v(x1, y1, z1); v(x1, y2, z1); v(x2, y2, z1)
    }

    fun drawBlockShape(
        pos: BlockPos,
        color: Color,
        filled: Boolean = true,
        lineWidth: Float = 1.0f
    ) {
        val state = level.getBlockState(pos)
        val entity = client.gameRenderer.mainCamera.entity()
        val context = if (entity != null) CollisionContext.of(entity) else CollisionContext.empty()
        val shape = state.getShape(level, pos, context)

        if (filled) {
            val boxes = shape.toAabbs()
            if (boxes.isEmpty()) {
                drawAABB(AABB(pos), color, filled = true)
            } else {
                boxes.forEach { drawAABB(it.move(pos), color, filled = true) }
            }
            return
        }

        val layer = if (isDepthTestEnabled) RenderTypes.lines() else ModRenderLayers.LINES_NO_DEPTH
        val buffer = source.getBuffer(layer)
        ShapeRenderer.renderShape(
            stack,
            buffer,
            shape,
            pos.x - cameraPos.x,
            pos.y - cameraPos.y,
            pos.z - cameraPos.z,
            color.rgb,
            lineWidth
        )
    }

    fun drawText(
        text: String,
        pos: Vec3,
        color: Color = Color.WHITE,
        scale: Float = 0.16f,
        centered: Boolean = true,
        seeThrough: Boolean = true
    ) {
        val rel = relative(pos)
        val font = client.font

        stack.pushPose()
        stack.translate(rel.x, rel.y, rel.z)
        stack.mulPose(cameraOrientation)
        stack.scale(scale / 16.0f, -scale / 16.0f, scale / 16.0f)

        val x = if (centered) -font.width(text) / 2.0f else 0.0f
        val mode = if (seeThrough) Font.DisplayMode.SEE_THROUGH else Font.DisplayMode.NORMAL
        font.drawInBatch(
            text,
            x,
            0.0f,
            color.rgb,
            false,
            stack.last().pose(),
            source,
            mode,
            0x66000000,
            FULL_BRIGHT
        )
        stack.popPose()
    }

    private fun drawOutlinedAABB(aabb: AABB, color: Color) {
        val min = Vec3(aabb.minX, aabb.minY, aabb.minZ)
        val max = Vec3(aabb.maxX, aabb.maxY, aabb.maxZ)

        val c000 = min
        val c100 = Vec3(max.x, min.y, min.z)
        val c010 = Vec3(min.x, max.y, min.z)
        val c001 = Vec3(min.x, min.y, max.z)
        val c110 = Vec3(max.x, max.y, min.z)
        val c011 = Vec3(min.x, max.y, max.z)
        val c101 = Vec3(max.x, min.y, max.z)
        val c111 = max

        drawLine(c000, c100, color)
        drawLine(c100, c101, color)
        drawLine(c101, c001, color)
        drawLine(c001, c000, color)

        drawLine(c010, c110, color)
        drawLine(c110, c111, color)
        drawLine(c111, c011, color)
        drawLine(c011, c010, color)

        drawLine(c000, c010, color)
        drawLine(c100, c110, color)
        drawLine(c101, c111, color)
        drawLine(c001, c011, color)
    }
}
