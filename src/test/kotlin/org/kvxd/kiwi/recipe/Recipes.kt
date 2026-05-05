package org.kvxd.kiwi.recipe

const val WOODEN_PICKAXE = """
{
  "type": "minecraft:crafting_shaped",
  "category": "equipment",
  "key": {
    "#": "minecraft:stick",
    "X": "#minecraft:wooden_tool_materials"
  },
  "pattern": [
    "XXX",
    " # ",
    " # "
  ],
  "result": {
    "id": "minecraft:wooden_pickaxe"
  }
}
"""

const val FLINT_AND_STEEL = """
{
  "type": "minecraft:crafting_shapeless",
  "category": "equipment",
  "ingredients": [
    "minecraft:iron_ingot",
    "minecraft:flint"
  ],
  "result": {
    "id": "minecraft:flint_and_steel"
  }
}
"""

const val COAL_FROM_SMELTING = """
{
  "type": "minecraft:smelting",
  "category": "misc",
  "cookingtime": 200,
  "experience": 0.1,
  "group": "coal",
  "ingredient": "minecraft:coal_ore",
  "result": {
    "id": "minecraft:coal"
  }
}
"""

const val COAL_FROM_BLASTING = """
{
  "type": "minecraft:blasting",
  "category": "misc",
  "cookingtime": 100,
  "experience": 0.1,
  "group": "coal",
  "ingredient": "minecraft:coal_ore",
  "result": {
    "id": "minecraft:coal"
  }
}
"""

const val BAKED_POTATO_FROM_SMOKING = """
{
  "type": "minecraft:smoking",
  "category": "food",
  "cookingtime": 100,
  "experience": 0.35,
  "ingredient": "minecraft:potato",
  "result": {
    "id": "minecraft:baked_potato"
  }
}
"""

const val TAG_INGREDIENT_TEST = """
{
  "type": "minecraft:crafting_shaped",
  "key": {
    "X": "#minecraft:wooden_tool_materials"
  },
  "pattern": [
    "X"
  ],
  "result": {
    "id": "minecraft:test"
  }
}
"""

const val CRAFTING_TABLE = """
{
  "type": "minecraft:crafting_shaped",
  "category": "misc",
  "key": {
    "#": "#minecraft:planks"
  },
  "pattern": [
    "##",
    "##"
  ],
  "result": {
    "id": "minecraft:crafting_table"
  },
  "show_notification": false
}
"""

const val STONE_SLAB_FROM_STONECUTTING = """
{
  "type": "minecraft:stonecutting",
  "ingredient": "minecraft:stone",
  "result": {
    "count": 2,
    "id": "minecraft:stone_slab"
  }
}
"""

const val CRAFTING_TABLE_2X2 = """
{
  "type": "minecraft:crafting_shaped",
  "key": {
    "#": "minecraft:oak_planks"
  },
  "pattern": [
    "##",
    "##"
  ],
  "result": {
    "id": "minecraft:crafting_table"
  }
}
"""

const val CRAFTING_TABLE_3X3 = """
{
  "type": "minecraft:crafting_shaped",
  "key": {
    "#": "minecraft:stick",
    "X": "#minecraft:wooden_tool_materials"
  },
  "pattern": [
    "XXX",
    " # ",
    " # "
  ],
  "result": {
    "id": "minecraft:wooden_pickaxe"
  }
}
"""

const val STONE_BUTTON = """
{
  "type": "minecraft:crafting_shaped",
  "key": {
    "#": "minecraft:stone"
  },
  "pattern": [
    "#"
  ],
  "result": {
    "id": "minecraft:stone_button"
  }
}
"""

const val IRON_INGOT_FROM_SMELTING = """
{
  "type": "minecraft:smelting",
  "cookingtime": 200,
  "experience": 0.1,
  "ingredient": "minecraft:iron_ore",
  "result": {
    "id": "minecraft:iron_ingot"
  }
}
"""
