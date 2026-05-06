package org.kvxd.kiwi.test

import org.kvxd.kiwi.agent.RecipeLookup
import org.kvxd.kiwi.harvest.HarvestDatabase
import org.kvxd.kiwi.recipe.RecipeDatabase

internal object GameTestSupport {
    private val initialized: Boolean by lazy {
        if (!RecipeDatabase.isLoaded) {
            RecipeDatabase.load()
        }
        if (!HarvestDatabase.isLoaded) {
            HarvestDatabase.load()
        }
        RecipeLookup.reloadRecipes()
        true
    }

    fun ensureInitialized() {
        initialized
    }
}

internal fun checkClientTest(condition: Boolean, message: () -> String) {
    if (!condition) {
        throw AssertionError(message())
    }
}

internal fun <T : Any> requireNotNullClientTest(value: T?, message: () -> String): T {
    return value ?: throw AssertionError(message())
}
