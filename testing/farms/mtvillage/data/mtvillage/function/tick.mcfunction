execute if score #ft mt_count matches 0.. run scoreboard players add #ft mt_count 1
execute if score #ft mt_count matches 6000 store result score #va mt_count if entity @e[type=minecraft:villager,x=-48,y=95,z=-24,dx=96,dy=15,dz=48]
execute if score #ft mt_count matches 6000 store result score #ea mt_count if entity @e[type=minecraft:villager,x=-48,y=95,z=-24,dx=96,dy=15,dz=48,nbt=!{VillagerData:{profession:"minecraft:none"}}]
execute if score #ft mt_count matches 12000 store result score #vb mt_count if entity @e[type=minecraft:villager,x=-48,y=95,z=-24,dx=96,dy=15,dz=48]
execute if score #ft mt_count matches 12000 store result score #eb mt_count if entity @e[type=minecraft:villager,x=-48,y=95,z=-24,dx=96,dy=15,dz=48,nbt=!{VillagerData:{profession:"minecraft:none"}}]
execute if score #ft mt_count matches 24000 store result score #vc mt_count if entity @e[type=minecraft:villager,x=-48,y=95,z=-24,dx=96,dy=15,dz=48]
execute if score #ft mt_count matches 24000 store result score #ec mt_count if entity @e[type=minecraft:villager,x=-48,y=95,z=-24,dx=96,dy=15,dz=48,nbt=!{VillagerData:{profession:"minecraft:none"}}]
execute if score #ft mt_count matches 48000 store result score #vd mt_count if entity @e[type=minecraft:villager,x=-48,y=95,z=-24,dx=96,dy=15,dz=48]
execute if score #ft mt_count matches 48000 store result score #ed mt_count if entity @e[type=minecraft:villager,x=-48,y=95,z=-24,dx=96,dy=15,dz=48,nbt=!{VillagerData:{profession:"minecraft:none"}}]
