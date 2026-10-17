from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "farms" / "mtdet" / "data" / "mtdet" / "function"
MEASURE_TICK = 6020
STREAM = ["cobblestone", "dirt", "sand", "gravel"]
FILTERED = ["cobblestone", "dirt", "sand"]
FILLERS = ["stick", "bone", "string", "feather"]


def chains(build: list[str], fill: list[str], tick: list[str]) -> None:
    build += [
        "scoreboard objectives add mt_det dummy",
        "fill -22 99 28 22 99 42 minecraft:glass",
        "setblock -19 101 30 minecraft:chest",
        "fill -19 100 30 19 100 30 minecraft:hopper[facing=east]",
        "setblock 20 100 30 minecraft:chest",
        "setblock 19 101 34 minecraft:chest",
        "fill -19 100 34 19 100 34 minecraft:hopper[facing=west]",
        "setblock -20 100 34 minecraft:chest",
        "fill -1 100 40 9 100 40 minecraft:redstone_wire",
        "setblock 10 100 40 minecraft:dropper[facing=east]",
        "setblock 11 100 40 minecraft:chest",
    ]
    for slot in range(27):
        fill.append(f"item replace block -19 101 30 container.{slot} with minecraft:cobblestone 64")
        fill.append(f"item replace block 19 101 34 container.{slot} with minecraft:cobblestone 64")
    for slot in range(9):
        fill.append(f"item replace block 10 100 40 container.{slot} with minecraft:cobblestone 64")
    fill += ["setblock -3 100 40 minecraft:observer[facing=east]", "setblock -2 100 40 minecraft:observer[facing=west]"]
    tick += [
        f"execute if score #t mt_det matches 2020 store result score #obs mt_det if items block 11 100 40 container.* minecraft:cobblestone",
        f"execute if score #t mt_det matches {MEASURE_TICK} store result score #east mt_det if items block 20 100 30 container.* minecraft:cobblestone",
        f"execute if score #t mt_det matches {MEASURE_TICK} store result score #west mt_det if items block -20 100 34 container.* minecraft:cobblestone",
        f"execute if score #t mt_det matches {MEASURE_TICK} store result score #eastsrc mt_det if items block -19 101 30 container.* minecraft:cobblestone",
        f"execute if score #t mt_det matches {MEASURE_TICK} store result score #westsrc mt_det if items block 19 101 34 container.* minecraft:cobblestone",
    ]


def race(build: list[str], fill: list[str], tick: list[str]) -> None:
    build += [
        "fill -2 99 48 2 99 52 minecraft:glass",
        "setblock -1 100 50 minecraft:hopper[facing=east]",
        "setblock -1 101 50 minecraft:chest",
        "setblock 0 100 50 minecraft:chest",
        "setblock 1 100 50 minecraft:hopper[facing=west]",
        "setblock 1 101 50 minecraft:chest",
    ]
    fill += [f"item replace block 0 100 50 container.{slot} with minecraft:stone 64" for slot in range(26)]
    fill += ["item replace block -1 101 50 container.0 with minecraft:cobblestone 64", "item replace block 1 101 50 container.0 with minecraft:dirt 64"]
    tick += [
        f"execute if score #t mt_det matches {MEASURE_TICK} store result score #racecobble mt_det if items block 0 100 50 container.* minecraft:cobblestone",
        f"execute if score #t mt_det matches {MEASURE_TICK} store result score #racedirt mt_det if items block 0 100 50 container.* minecraft:dirt",
    ]


def sorter(tag: str, x0: int, z: int, build: list[str], fill: list[str], tick: list[str]) -> list[str]:
    keys = []
    build += [
        f"fill {x0 - 1} 98 {z - 1} {x0 + 18} 98 {z + 4} minecraft:glass",
        f"fill {x0} 102 {z} {x0 + 16} 102 {z} minecraft:hopper[facing=east]",
        f"setblock {x0} 103 {z} minecraft:chest",
        f"setblock {x0 + 17} 102 {z} minecraft:chest",
    ]
    for index, item in enumerate(FILTERED):
        fx = x0 + 4 + index * 4
        build += [
            f"setblock {fx} 101 {z} minecraft:hopper[facing=south]",
            f"setblock {fx} 100 {z} minecraft:hopper[facing=down]",
            f"setblock {fx} 99 {z} minecraft:chest",
            f"fill {fx} 100 {z + 1} {fx} 100 {z + 3} minecraft:stone",
            f"fill {fx + 1} 100 {z + 3} {fx + 2} 100 {z + 3} minecraft:stone",
            f"fill {fx + 2} 100 {z} {fx + 2} 100 {z + 2} minecraft:stone",
            f"setblock {fx} 101 {z + 1} minecraft:comparator[facing=north]",
            f"setblock {fx} 101 {z + 2} minecraft:repeater[facing=north]",
            f"fill {fx} 101 {z + 3} {fx + 2} 101 {z + 3} minecraft:redstone_wire",
            f"fill {fx + 2} 101 {z} {fx + 2} 101 {z + 2} minecraft:redstone_wire",
            f"setblock {fx + 1} 100 {z} minecraft:redstone_wall_torch[facing=west]",
        ]
        fill.append(f"item replace block {fx} 101 {z} container.0 with minecraft:{item} 18")
        fill += [f"item replace block {fx} 101 {z} container.{slot + 1} with minecraft:{filler} 1" for slot, filler in enumerate(FILLERS)]
        key = f"{tag}{item[:4]}"
        keys.append(key)
        tick.append(f"execute if score #t mt_det matches {MEASURE_TICK} store result score #{key} mt_det if items block {fx} 99 {z} container.* minecraft:{item}")
    fill += [f"item replace block {x0} 103 {z} container.{slot} with minecraft:{STREAM[slot % 4]} 64" for slot in range(27)]
    for item in STREAM:
        key = f"{tag}end{item[:4]}"
        keys.append(key)
        tick.append(f"execute if score #t mt_det matches {MEASURE_TICK} store result score #{key} mt_det if items block {x0 + 17} 102 {z} container.* minecraft:{item}")
    return keys


def pickup(build: list[str], tick: list[str]) -> list[str]:
    z = 22
    build.append(f"fill -4 98 {z - 1} 3 98 {z + 1} minecraft:glass")
    keys = []
    for index, x in enumerate(range(-3, 3)):
        build += [f"setblock {x} 100 {z} minecraft:hopper[facing=down]", f"setblock {x} 99 {z} minecraft:barrel"]
        key = f"pk{'abcdef'[index]}"
        keys.append(key)
        tick.append(f"execute if score #t mt_det matches {MEASURE_TICK} store result score #{key} mt_det if items block {x} 99 {z} container.* *")
    for offset, item in ((-0.02, "cobblestone"), (0.02, "dirt"), (-1.5, "sand"), (1.5, "gravel"), (-0.98, "stone")):
        tick.append(f'execute if score #t mt_det matches 20..820 run summon minecraft:item {offset} 101.2 {z + 0.5} {{Item:{{id:"minecraft:{item}",count:1}},PickupDelay:0s,Motion:[0.0d,0.0d,0.0d]}}')
    return keys


def main() -> None:
    build: list[str] = []
    fill: list[str] = []
    tick = [
        "execute if score #go mt_det matches 1 run scoreboard players add #t mt_det 1",
        "execute if score #t mt_det matches 20 run function mtdet:fill",
    ]
    chains(build, fill, tick)
    race(build, fill, tick)
    keys = ["t", "obs", "east", "west", "eastsrc", "westsrc", "racecobble", "racedirt"]
    keys += sorter("sa", -6, 60, build, fill, tick)
    keys += sorter("sb", 40, 60, build, fill, tick)
    keys += pickup(build, tick)
    for name, lines in (("build", build), ("fill", fill), ("tick", tick)):
        (ROOT / f"{name}.mcfunction").write_text("\n".join(lines) + "\n")
    (ROOT.parent.parent.parent / "keys.txt").write_text(" ".join(keys) + "\n")
    print(" ".join(keys))


if __name__ == "__main__":
    main()
