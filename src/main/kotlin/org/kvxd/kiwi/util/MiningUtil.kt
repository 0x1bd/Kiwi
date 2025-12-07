package org.kvxd.kiwi.util

import net.minecraft.block.BlockState
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.FluidTags
import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.player
import org.kvxd.kiwi.world
import kotlin.math.ceil

object MiningUtil {

    fun getBreakTime(pos: BlockPos): Double {
        val state = world.getBlockState(pos)
        val hardness = state.getHardness(world, pos)

        if (hardness == -1.0f) return Double.POSITIVE_INFINITY
        if (hardness == 0.0f) return 0.0

        val speed = getBreakSpeed(state)
        val canHarvest = player.canHarvest(state)

        val damage = if (canHarvest) speed / hardness / 30.0 else speed / hardness / 100.0
        val ticks = ceil(1.0 / damage)

        return ticks / 20.0
    }

    private fun getBreakSpeed(state: BlockState): Float {
        var speed = player.getBlockBreakingSpeed(state)

        if (speed > 1.0f) {
            val efficiency = EnchantmentHelper.getLevel(
                world.registryManager.getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY),
                player.mainHandStack
            )

            if (efficiency > 0 && !player.inventory.selectedStack.isEmpty) {
                speed += (efficiency * efficiency + 1).toFloat()
            }
        }

        if (player.hasStatusEffect(StatusEffects.HASTE)) {
            val lvl = player.getStatusEffect(StatusEffects.HASTE)!!.amplifier + 1
            speed *= (1.0f + lvl * 0.2f)
        }

        if (player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            val lvl = player.getStatusEffect(StatusEffects.MINING_FATIGUE)!!.amplifier
            speed *= when (lvl) {
                0 -> 0.3f
                1 -> 0.09f
                2 -> 0.0027f
                else -> 8.1E-4f
            }
        }

        val helmet = player.getEquippedStack(EquipmentSlot.HEAD)

        val aquaAffinity = world.registryManager
            .getOrThrow(RegistryKeys.ENCHANTMENT)
            .getOrThrow(Enchantments.AQUA_AFFINITY)

        val hasAquaAffinity = EnchantmentHelper.getLevel(aquaAffinity, helmet) > 0

        if (player.isSubmergedIn(FluidTags.WATER) && !hasAquaAffinity) {
            speed /= 5.0f
        }

        if (!player.isOnGround) {
            speed /= 5.0f
        }

        return speed
    }

}