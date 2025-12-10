package org.kvxd.kiwi.config

import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import net.minecraft.util.Formatting
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

    fun toDisplayText(): Text {
        val nameText = Text.literal(key)
            .formatted(Formatting.AQUA)

        val valueText = Text.literal(" = $value")
            .formatted(Formatting.GREEN)

        val descText = Text.literal("  (${description})")
            .formatted(Formatting.GRAY, Formatting.ITALIC)

        val resetText = Text.literal("[reset]")
            .formatted(Formatting.RED)
            .styled {
                it.withClickEvent(ClickEvent.SuggestCommand("/kiwi config set $key $default"))
                    .withHoverEvent(HoverEvent.ShowText(Text.literal("Reset to default: $default")))
            }

        return Text.empty()
            .append(nameText)
            .append(valueText)
            .append(descText)
            .append(Text.literal(" "))
            .append(resetText)
    }
}
