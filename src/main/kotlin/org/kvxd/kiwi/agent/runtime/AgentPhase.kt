package org.kvxd.kiwi.agent.runtime

enum class AgentPhase(val label: String) {
    IDLE("IDLE"),
    PLANNING("PLANNING"),
    MOVING("MOVING"),
    COLLECTING("COLLECT"),
    MINING("MINE"),
    CRAFTING("CRAFT"),
    SMELTING("SMELT"),
    RECOVERING("RECOVER"),
    DONE("DONE")
}
