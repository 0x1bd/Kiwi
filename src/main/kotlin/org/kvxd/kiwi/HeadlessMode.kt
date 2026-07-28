package org.kvxd.kiwi

object HeadlessMode {

    private const val GAMETEST_PROPERTY = "fabric.client.gametest"
    private const val SHOW_WINDOW_PROPERTY = "kiwi.gametest.showWindow"

    @JvmStatic
    val isGameTest: Boolean = System.getProperty(GAMETEST_PROPERTY) != null

    @JvmStatic
    val isEnabled: Boolean = isGameTest && System.getProperty(SHOW_WINDOW_PROPERTY) == null
}
