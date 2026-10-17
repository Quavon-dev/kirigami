scoreboard objectives add mt_count dummy
scoreboard players set #piglins mt_count 0
scoreboard players set #portals mt_count 0
gamerule random_tick_speed 30
execute if block -65 110 -50 minecraft:nether_portal run scoreboard players add #portals mt_count 1
execute if block 0 110 -50 minecraft:nether_portal run scoreboard players add #portals mt_count 1
execute if block 65 110 -50 minecraft:nether_portal run scoreboard players add #portals mt_count 1
execute if block -65 110 -25 minecraft:nether_portal run scoreboard players add #portals mt_count 1
execute if block 0 110 -25 minecraft:nether_portal run scoreboard players add #portals mt_count 1
execute if block 65 110 -25 minecraft:nether_portal run scoreboard players add #portals mt_count 1
execute if block -65 110 25 minecraft:nether_portal run scoreboard players add #portals mt_count 1
execute if block 0 110 25 minecraft:nether_portal run scoreboard players add #portals mt_count 1
execute if block 65 110 25 minecraft:nether_portal run scoreboard players add #portals mt_count 1
execute if block -65 110 50 minecraft:nether_portal run scoreboard players add #portals mt_count 1
execute if block 0 110 50 minecraft:nether_portal run scoreboard players add #portals mt_count 1
execute if block 65 110 50 minecraft:nether_portal run scoreboard players add #portals mt_count 1
