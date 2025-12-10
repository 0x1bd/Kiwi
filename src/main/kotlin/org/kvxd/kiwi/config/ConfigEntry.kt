package org.kvxd.kiwi.config

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class ConfigEntry<T>(
    val key: String,
    val description: String,
    val default: T
) : ReadWriteProperty<Any, T>, CommandConfigEntry<T> {

    protected var value: T = default

    override fun getValue(thisRef: Any, property: KProperty<*>): T = value
    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        this@ConfigEntry.value = value

        ConfigRegistry.markDirty()
    }

    fun toDisplay(): Component {
        val nameText = Component.literal(key)
            .withStyle(ChatFormatting.AQUA)

        val valueText = Component.literal(" = $value")
            .withStyle(ChatFormatting.GREEN)

        val descText = Component.literal("  (${description})")
            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)

        val resetText = Component.literal("[reset]")
            .withStyle(ChatFormatting.RED)
            .withStyle {
                it.withClickEvent(ClickEvent.SuggestCommand("/kiwi config set $key $default"))
                    .withHoverEvent(HoverEvent.ShowText(Component.literal("Reset to default: $default")))
            }

        return Component.empty()
            .append(nameText)
            .append(valueText)
            .append(descText)
            .append(Component.literal(" "))
            .append(resetText)
    }
}
