execute in minecraft:overworld as @e[type=minecraft:zombified_piglin,x=-90,y=90,z=-60,dx=180,dy=40,dz=120] run scoreboard players add #piglins mt_count 1
execute in minecraft:overworld run kill @e[type=!minecraft:player,x=-90,y=60,z=-60,dx=180,dy=80,dz=120]
