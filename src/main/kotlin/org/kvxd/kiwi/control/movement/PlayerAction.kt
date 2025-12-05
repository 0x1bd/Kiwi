package org.kvxd.kiwi.control.movement

import kotlin.reflect.KClass

interface PlayerAction {

    fun execute()

}

typealias ActionResult = MutableList<PlayerAction>

fun actionResult(): ActionResult = mutableListOf()

fun mergeActionResult(actions: ActionResult): ActionResult {
    if (actions.isEmpty()) return actions

    val merged = mutableListOf<PlayerAction>()
    val mergeMap = mutableMapOf<KClass<out MergeableAction>, MergeableAction>()

    actions.forEach { action ->
        when (action) {
            is MergeableAction -> {
                val key = action::class
                val existing = mergeMap[key]

                mergeMap[key] = if (existing != null)
                    existing.merge(action) ?: action
                else
                    action
            }

            else -> merged += action
        }
    }

    merged += mergeMap.values
    return merged
}