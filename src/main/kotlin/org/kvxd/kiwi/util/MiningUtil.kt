package org.kvxd.kiwi.util

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.tags.FluidTags
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.state.BlockState
import org.kvxd.kiwi.level
import org.kvxd.kiwi.player
import kotlin.math.ceil

object MiningUtil {

    fun selectBestTool(state: BlockState) {
        val bestSlot = InventoryUtil.findBestSlot { stack ->
            getToolScore(stack, state)
        }

        if (bestSlot != -1) {
            InventoryUtil.selectSlot(bestSlot)
        }
    }

    private fun getToolScore(stack: ItemStack, state: BlockState): Float {
        if (stack.isEmpty) return 1.0f
        var speed = stack.getDestroySpeed(state)
        if (speed > 1.0f) {
            val efficiency = EnchantmentHelper.getItemEnchantmentLevel(
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY),
                stack
            )

            speed += efficiency * efficiency + 1
        }
        return speed
    }

    fun getBreakTime(pos: BlockPos): Double {
        val state = level.getBlockState(pos)
        val hardness = state.getDestroySpeed(level, pos)

        if (hardness == -1.0f) return Double.POSITIVE_INFINITY
        if (hardness == 0.0f) return 0.0

        val speed = getBreakSpeed(state)
        val canHarvest = player.hasCorrectToolForDrops(state)

        val damage = if (canHarvest) speed / hardness / 30.0 else speed / hardness / 100.0
        val ticks = ceil(1.0 / damage)

        return ticks / 20.0
    }

    private fun getBreakSpeed(state: BlockState): Float {
        var speed = player.getDestroySpeed(state)

        if (speed > 1.0f) {
            val efficiency = EnchantmentHelper.getItemEnchantmentLevel(
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.EFFICIENCY),
                player.mainHandItem
            )

            if (efficiency > 0 && !player.mainHandItem.isEmpty) {
                speed += (efficiency * efficiency + 1).toFloat()
            }
        }

        if (player.hasEffect(MobEffects.HASTE)) {
            val lvl = player.getEffect(MobEffects.HASTE)!!.amplifier + 1
            speed *= (1.0f + lvl * 0.2f)
        }

        if (player.hasEffect(MobEffects.HASTE)) {
            val lvl = player.getEffect(MobEffects.HASTE)!!.amplifier
            speed *= when (lvl) {
                0 -> 0.3f
                1 -> 0.09f
                2 -> 0.0027f
                else -> 8.1E-4f
            }
        }

        val helmet = player.getItemBySlot(EquipmentSlot.HEAD)

        val aquaAffinity = level.registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.AQUA_AFFINITY)

        val hasAquaAffinity = EnchantmentHelper.getItemEnchantmentLevel(aquaAffinity, helmet) > 0

        if (player.isEyeInFluid(FluidTags.WATER) && !hasAquaAffinity) {
            speed /= 5.0f
        }

        if (!player.onGround()) {
            speed /= 5.0f
        }

        return speed
    }

}