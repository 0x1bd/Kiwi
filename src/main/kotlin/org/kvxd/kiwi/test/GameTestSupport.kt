package org.kvxd.kiwi.test

import net.minecraft.gametest.framework.GameTestHelper
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

internal fun GameTestHelper.runKiwiTest(block: () -> Unit) {
    GameTestSupport.ensureInitialized()
    block()
    succeed()
}

internal fun GameTestHelper.assertThat(condition: Boolean, message: () -> String) {
    if (!condition) {
        failTest(message())
    }
}

internal fun <T : Any> GameTestHelper.assertNotNull(value: T?, message: () -> String): T {
    if (value == null) {
        failTest(message())
    }
    return value
}

private fun GameTestHelper.failTest(message: String): Nothing {
    fail(message)
    throw AssertionError(message)
}
