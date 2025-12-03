package org.kvxd.kiwi.render.util

import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.awt.Color
import kotlin.math.sqrt

class RenderScope(
    val stack: MatrixStack,
    val consumers: VertexConsumerProvider,
    val cameraPos: Vec3d
) {

    private val posVec = Vector4f()
    private val normalVec = Vector3f()

    private var isDepthTestEnabled: Boolean = true

    fun depthTest(enabled: Boolean) {
        this.isDepthTestEnabled = enabled
    }

    private fun VertexConsumer.pos(matrix: Matrix4f, x: Float, y: Float, z: Float): VertexConsumer {
        posVec.set(x, y, z, 1.0f)
        matrix.transform(posVec)
        return this.vertex(posVec.x, posVec.y, posVec.z)
    }

    private fun VertexConsumer.normal(matrix: Matrix3f, x: Float, y: Float, z: Float): VertexConsumer {
        normalVec.set(x, y, z)
        matrix.transform(normalVec)

        if (normalVec.lengthSquared() > 0) normalVec.normalize()
        return this.normal(normalVec.x, normalVec.y, normalVec.z)
    }

    private fun relative(pos: Vec3d): Vec3d = pos.subtract(cameraPos)
    private fun relative(x: Double, y: Double, z: Double): Vec3d =
        Vec3d(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z)

    fun drawLine(
        start: Vec3d,
        end: Vec3d,
        color: Color,
        lineWidth: Float = 1.0f
    ) {
        val layer = if (isDepthTestEnabled) ModRenderLayers.LINES(lineWidth) else ModRenderLayers.LINES_NO_DEPTH(lineWidth)
        val buffer = consumers.getBuffer(layer)

        val relStart = relative(start)
        val relEnd = relative(end)

        val dx = (relEnd.x - relStart.x).toFloat()
        val dy = (relEnd.y - relStart.y).toFloat()
        val dz = (relEnd.z - relStart.z).toFloat()
        val invLen = 1.0f / sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
        val nx = dx * invLen
        val ny = dy * invLen
        val nz = dz * invLen

        val matrices = stack.peek()
        val pMat = matrices.positionMatrix
        val nMat = matrices.normalMatrix

        val r = color.red
        val g = color.green
        val b = color.blue
        val a = color.alpha

        buffer.pos(pMat, relStart.x.toFloat(), relStart.y.toFloat(), relStart.z.toFloat())
            .color(r, g, b, a)
            .normal(nMat, nx, ny, nz)

        buffer.pos(pMat, relEnd.x.toFloat(), relEnd.y.toFloat(), relEnd.z.toFloat())
            .color(r, g, b, a)
            .normal(nMat, nx, ny, nz)
    }

    fun drawBox(
        box: Box,
        color: Color,
        filled: Boolean = true
    ) {
        val layer = if (isDepthTestEnabled) ModRenderLayers.QUADS_DEPTH else ModRenderLayers.QUADS_NO_DEPTH

        if (!filled) {
            drawOutlinedBox(box, color)
            return
        }

        val buffer = consumers.getBuffer(layer)
        val matrix = stack.peek().positionMatrix

        val min = relative(box.minX, box.minY, box.minZ)
        val max = relative(box.maxX, box.maxY, box.maxZ)

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
            buffer.pos(matrix, x, y, z).color(r, g, b, a)
        }

        v(x1, y1, z1); v(x2, y1, z1); v(x2, y1, z2); v(x1, y1, z2)
        v(x1, y2, z2); v(x2, y2, z2); v(x2, y2, z1); v(x1, y2, z1)
        v(x1, y1, z1); v(x1, y1, z2); v(x1, y2, z2); v(x1, y2, z1)
        v(x2, y1, z2); v(x2, y1, z1); v(x2, y2, z1); v(x2, y2, z2)
        v(x1, y1, z2); v(x2, y1, z2); v(x2, y2, z2); v(x1, y2, z2)
        v(x2, y1, z1); v(x1, y1, z1); v(x1, y2, z1); v(x2, y2, z1)
    }

    private fun drawOutlinedBox(box: Box, color: Color) {
        val min = Vec3d(box.minX, box.minY, box.minZ)
        val max = Vec3d(box.maxX, box.maxY, box.maxZ)

        val c000 = min
        val c100 = Vec3d(max.x, min.y, min.z)
        val c010 = Vec3d(min.x, max.y, min.z)
        val c001 = Vec3d(min.x, min.y, max.z)
        val c110 = Vec3d(max.x, max.y, min.z)
        val c011 = Vec3d(min.x, max.y, max.z)
        val c101 = Vec3d(max.x, min.y, max.z)
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