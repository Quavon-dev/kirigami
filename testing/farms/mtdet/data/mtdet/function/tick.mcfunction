execute if score #go mt_det matches 1 run scoreboard players add #t mt_det 1
execute if score #t mt_det matches 20 run function mtdet:fill
execute if score #t mt_det matches 2020 store result score #obs mt_det if items block 11 100 40 container.* minecraft:cobblestone
execute if score #t mt_det matches 6020 store result score #east mt_det if items block 20 100 30 container.* minecraft:cobblestone
execute if score #t mt_det matches 6020 store result score #west mt_det if items block -20 100 34 container.* minecraft:cobblestone
execute if score #t mt_det matches 6020 store result score #eastsrc mt_det if items block -19 101 30 container.* minecraft:cobblestone
execute if score #t mt_det matches 6020 store result score #westsrc mt_det if items block 19 101 34 container.* minecraft:cobblestone
execute if score #t mt_det matches 6020 store result score #racecobble mt_det if items block 0 100 50 container.* minecraft:cobblestone
execute if score #t mt_det matches 6020 store result score #racedirt mt_det if items block 0 100 50 container.* minecraft:dirt
execute if score #t mt_det matches 6020 store result score #sacobb mt_det if items block -2 99 60 container.* minecraft:cobblestone
execute if score #t mt_det matches 6020 store result score #sadirt mt_det if items block 2 99 60 container.* minecraft:dirt
execute if score #t mt_det matches 6020 store result score #sasand mt_det if items block 6 99 60 container.* minecraft:sand
execute if score #t mt_det matches 6020 store result score #saendcobb mt_det if items block 11 102 60 container.* minecraft:cobblestone
execute if score #t mt_det matches 6020 store result score #saenddirt mt_det if items block 11 102 60 container.* minecraft:dirt
execute if score #t mt_det matches 6020 store result score #saendsand mt_det if items block 11 102 60 container.* minecraft:sand
execute if score #t mt_det matches 6020 store result score #saendgrav mt_det if items block 11 102 60 container.* minecraft:gravel
execute if score #t mt_det matches 6020 store result score #sbcobb mt_det if items block 44 99 60 container.* minecraft:cobblestone
execute if score #t mt_det matches 6020 store result score #sbdirt mt_det if items block 48 99 60 container.* minecraft:dirt
execute if score #t mt_det matches 6020 store result score #sbsand mt_det if items block 52 99 60 container.* minecraft:sand
execute if score #t mt_det matches 6020 store result score #sbendcobb mt_det if items block 57 102 60 container.* minecraft:cobblestone
execute if score #t mt_det matches 6020 store result score #sbenddirt mt_det if items block 57 102 60 container.* minecraft:dirt
execute if score #t mt_det matches 6020 store result score #sbendsand mt_det if items block 57 102 60 container.* minecraft:sand
execute if score #t mt_det matches 6020 store result score #sbendgrav mt_det if items block 57 102 60 container.* minecraft:gravel
execute if score #t mt_det matches 6020 store result score #pka mt_det if items block -3 99 22 container.* *
execute if score #t mt_det matches 6020 store result score #pkb mt_det if items block -2 99 22 container.* *
execute if score #t mt_det matches 6020 store result score #pkc mt_det if items block -1 99 22 container.* *
execute if score #t mt_det matches 6020 store result score #pkd mt_det if items block 0 99 22 container.* *
execute if score #t mt_det matches 6020 store result score #pke mt_det if items block 1 99 22 container.* *
execute if score #t mt_det matches 6020 store result score #pkf mt_det if items block 2 99 22 container.* *
execute if score #t mt_det matches 20..820 run summon minecraft:item -0.02 101.2 22.5 {Item:{id:"minecraft:cobblestone",count:1},PickupDelay:0s,Motion:[0.0d,0.0d,0.0d]}
execute if score #t mt_det matches 20..820 run summon minecraft:item 0.02 101.2 22.5 {Item:{id:"minecraft:dirt",count:1},PickupDelay:0s,Motion:[0.0d,0.0d,0.0d]}
execute if score #t mt_det matches 20..820 run summon minecraft:item -1.5 101.2 22.5 {Item:{id:"minecraft:sand",count:1},PickupDelay:0s,Motion:[0.0d,0.0d,0.0d]}
execute if score #t mt_det matches 20..820 run summon minecraft:item 1.5 101.2 22.5 {Item:{id:"minecraft:gravel",count:1},PickupDelay:0s,Motion:[0.0d,0.0d,0.0d]}
execute if score #t mt_det matches 20..820 run summon minecraft:item -0.98 101.2 22.5 {Item:{id:"minecraft:stone",count:1},PickupDelay:0s,Motion:[0.0d,0.0d,0.0d]}
execute if score #t mt_det matches 200 store result score #cxa mt_det run data get entity @e[type=minecraft:minecart,limit=1,x=-8,y=99,z=-8,dx=16,dy=5,dz=16] Pos[0] 100
execute if score #t mt_det matches 200 store result score #cza mt_det run data get entity @e[type=minecraft:minecart,limit=1,x=-8,y=99,z=-8,dx=16,dy=5,dz=16] Pos[2] 100
execute if score #t mt_det matches 1000 store result score #cxb mt_det run data get entity @e[type=minecraft:minecart,limit=1,x=-8,y=99,z=-8,dx=16,dy=5,dz=16] Pos[0] 100
execute if score #t mt_det matches 1000 store result score #czb mt_det run data get entity @e[type=minecraft:minecart,limit=1,x=-8,y=99,z=-8,dx=16,dy=5,dz=16] Pos[2] 100
execute if score #t mt_det matches 3000 store result score #cxc mt_det run data get entity @e[type=minecraft:minecart,limit=1,x=-8,y=99,z=-8,dx=16,dy=5,dz=16] Pos[0] 100
execute if score #t mt_det matches 3000 store result score #czc mt_det run data get entity @e[type=minecraft:minecart,limit=1,x=-8,y=99,z=-8,dx=16,dy=5,dz=16] Pos[2] 100
execute if score #t mt_det matches 6020 store result score #carts mt_det if entity @e[type=minecraft:minecart,x=-8,y=99,z=-8,dx=16,dy=5,dz=16]
execute if score #t mt_det matches 6020 if block 12 100 0 minecraft:stone run scoreboard players set #prn mt_det 1
execute if score #t mt_det matches 6020 if block 12 100 0 minecraft:dirt run scoreboard players set #prn mt_det 2
execute if score #t mt_det matches 6020 if block 12 100 0 minecraft:air run scoreboard players set #prn mt_det 3
execute if score #t mt_det matches 6020 if block 14 100 0 minecraft:stone run scoreboard players set #prs mt_det 1
execute if score #t mt_det matches 6020 if block 14 100 0 minecraft:dirt run scoreboard players set #prs mt_det 2
execute if score #t mt_det matches 6020 if block 14 100 0 minecraft:air run scoreboard players set #prs mt_det 3
execute if score #t mt_det matches 6020 store result score #fiing mt_det if items block 20 99 2 container.* minecraft:iron_ingot
execute if score #t mt_det matches 6020 store result score #fisrc mt_det if items block 20 103 -6 container.* minecraft:raw_iron
execute if score #t mt_det matches 6020 store result score #fiin mt_det if items block 20 101 2 container.0 minecraft:raw_iron
