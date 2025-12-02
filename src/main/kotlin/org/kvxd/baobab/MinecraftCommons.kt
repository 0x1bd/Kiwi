package org.kvxd.baobab

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.world.World

val client: MinecraftClient
    get() = MinecraftClient.getInstance()!!

val player: ClientPlayerEntity
    get() = client.player!!

val world: World
    get() = client.world!!