package org.kvxd.kiwi

import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.level.Level

val client: Minecraft
    get() = Minecraft.getInstance()!!

val player: LocalPlayer
    get() = client.player!!

val level: Level
    get() = client.level!!