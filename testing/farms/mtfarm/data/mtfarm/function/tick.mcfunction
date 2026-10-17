execute in minecraft:overworld as @e[type=minecraft:zombified_piglin,tag=!mt_seen,x=-64,y=95,z=-50,dx=82,dy=35,dz=20] run scoreboard players add #piglins mt_count 1
execute in minecraft:overworld run tag @e[type=minecraft:zombified_piglin,tag=!mt_seen,x=-64,y=95,z=-50,dx=82,dy=35,dz=20] add mt_seen
execute in minecraft:overworld as @e[type=minecraft:iron_golem,x=26,y=100,z=-14,dx=30,dy=25,dz=28] run scoreboard players add #golems mt_count 1
execute in minecraft:overworld run kill @e[type=minecraft:iron_golem,x=26,y=100,z=-14,dx=30,dy=25,dz=28]
execute store result score #daytime mt_count run time query minecraft:day
execute if score #daytime mt_count matches 1000..11000 in minecraft:overworld unless entity @e[type=minecraft:zombie,tag=mt_scare] run summon minecraft:zombie 40.5 112 6.5 {PersistenceRequired:1b,Tags:["mt_scare"]}
execute unless score #daytime mt_count matches 1000..11000 in minecraft:overworld run tp @e[type=minecraft:zombie,tag=mt_scare] 40.5 -200 6.5
execute in minecraft:overworld as @e[type=!minecraft:item,type=!minecraft:player,tag=!mt_platform,x=-9,y=136,z=-9,dx=18,dy=2,dz=18] run scoreboard players add #platform mt_count 1
execute in minecraft:overworld as @e[type=minecraft:creeper,tag=!mt_platform,x=-9,y=136,z=-9,dx=18,dy=2,dz=18] run scoreboard players add #pcreeper mt_count 1
execute in minecraft:overworld as @e[type=minecraft:skeleton,tag=!mt_platform,x=-9,y=136,z=-9,dx=18,dy=2,dz=18] run scoreboard players add #pskeleton mt_count 1
execute in minecraft:overworld as @e[type=minecraft:zombie,tag=!mt_platform,x=-9,y=136,z=-9,dx=18,dy=2,dz=18] run scoreboard players add #pzombie mt_count 1
execute in minecraft:overworld as @e[type=minecraft:spider,tag=!mt_platform,x=-9,y=136,z=-9,dx=18,dy=2,dz=18] run scoreboard players add #pspider mt_count 1
execute in minecraft:overworld run tag @e[type=!minecraft:item,type=!minecraft:player,tag=!mt_platform,x=-9,y=136,z=-9,dx=18,dy=2,dz=18] add mt_platform
scoreboard players add #ft mt_count 1
execute if score #ft mt_count matches 30000 run function mtfarm:cropcount
