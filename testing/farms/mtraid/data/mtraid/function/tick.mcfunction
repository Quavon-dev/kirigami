execute if score #ft mt_count matches 0.. run scoreboard players add #ft mt_count 1
scoreboard players operation #phase mt_count = #ft mt_count
scoreboard players operation #phase mt_count %= #period mt_count
execute if score #phase mt_count matches 1200 run scoreboard players add #omens mt_count 1
execute if score #phase mt_count matches 1200 run effect give @a minecraft:bad_omen 600 0
execute as @e[type=minecraft:pillager,tag=!mtseen] run scoreboard players add #pillager mt_count 1
execute as @e[type=minecraft:vindicator,tag=!mtseen] run scoreboard players add #vindicator mt_count 1
execute as @e[type=minecraft:evoker,tag=!mtseen] run scoreboard players add #evoker mt_count 1
execute as @e[type=minecraft:witch,tag=!mtseen] run scoreboard players add #witch mt_count 1
execute as @e[type=minecraft:ravager,tag=!mtseen] run scoreboard players add #ravager mt_count 1
tag @e[type=#minecraft:raiders] add mtseen
kill @e[type=#minecraft:raiders]
execute store result score #villagers mt_count if entity @e[type=minecraft:villager]
