package org.kvxd.kiwi.control

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

object BreakSpeed {

    const val UNBREAKABLE = Double.POSITIVE_INFINITY

    fun toolSpeed(stack: ItemStack, state: BlockState): Float {
        if (stack.isEmpty) return 1.0f
        var speed = stack.getDestroySpeed(state)
        if (speed > 1.0f) {
            val efficiency = efficiencyLevel(stack)
            if (efficiency > 0) speed += (efficiency * efficiency + 1).toFloat()
        }
        return speed
    }

    fun toolScore(stack: ItemStack, state: BlockState): Float {
        val speed = toolSpeed(stack, state)
        if (state.requiresCorrectToolForDrops() && !stack.isCorrectToolForDrops(state)) return speed * 0.01f
        return speed
    }

    fun ticksToBreak(state: BlockState, hardness: Float, stack: ItemStack, applyPlayerModifiers: Boolean): Double {
        if (hardness < 0f) return UNBREAKABLE
        if (hardness == 0f) return 0.0

        var speed = toolSpeed(stack, state)
        if (applyPlayerModifiers) speed = applyPlayerModifiers(speed)

        val canHarvest = !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state)
        val damagePerTick = speed / hardness / (if (canHarvest) 30.0 else 100.0)
        if (damagePerTick <= 0.0) return UNBREAKABLE
        return ceil(1.0 / damagePerTick)
    }

    fun bestTicks(state: BlockState, hardness: Float, candidates: List<ItemStack>): Double {
        var best = ticksToBreak(state, hardness, ItemStack.EMPTY, false)
        for (stack in candidates) {
            if (stack.isEmpty) continue
            val ticks = ticksToBreak(state, hardness, stack, false)
            if (ticks < best) best = ticks
        }
        return best
    }

    private fun efficiencyLevel(stack: ItemStack): Int = runCatching {
        EnchantmentHelper.getItemEnchantmentLevel(
            level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY),
            stack
        )
    }.getOrDefault(0)

    private fun applyPlayerModifiers(base: Float): Float {
        var speed = base

        player.getEffect(MobEffects.HASTE)?.let { speed *= 1.0f + (it.amplifier + 1) * 0.2f }
        player.getEffect(MobEffects.MINING_FATIGUE)?.let {
            speed *= when (it.amplifier) {
                0 -> 0.3f
                1 -> 0.09f
                2 -> 0.0027f
                else -> 8.1E-4f
            }
        }

        val helmet = player.getItemBySlot(EquipmentSlot.HEAD)
        val aquaAffinity = runCatching {
            EnchantmentHelper.getItemEnchantmentLevel(
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.AQUA_AFFINITY),
                helmet
            )
        }.getOrDefault(0)

        if (player.isEyeInFluid(FluidTags.WATER) && aquaAffinity <= 0) speed /= 5.0f
        if (!player.onGround()) speed /= 5.0f

        return speed
    }
}
