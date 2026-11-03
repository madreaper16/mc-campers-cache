# Camper's Cache

A Minecraft exploration mod by **MadReaper16**.

Find camps in the wilderness, search guarded supply houses, and uncover hidden storehouses. Campers keep an eye on their supplies: open their chest and they'll get angry; take something and they'll want it back.

## Requirements

- Minecraft Java 26.3
- Java 25
- Fabric Loader 0.20.1 or newer
- Fabric API
- Mod Menu 21.0.0 or newer on clients

Install the mod and its dependencies in your instance's `mods` folder. Multiplayer servers also need the mod and Fabric API. Mod Menu is only needed on clients.

This is a beta. Map trading and the Camper's custom outfit are still in progress.

## Camps and caches

Each camp has two destinations on opposite sides: a supply house and an underground storehouse. With the default settings, each destination is about 944 blocks from camp. Structures use terrain checks to keep the storehouse and its entrance underground.

Supply houses contain useful materials, food, enchanted books, and 1–2 pieces of diamond gear. Storehouses have more supplies, stronger gear, a chance of netherite scrap, and a 1.5% chance of a God Armor chestplate. See [default loot](LOOT.md).

Opening a camp chest angers the Camper at that player only. An emerald stops the attacks. If items were taken, return the same item types and quantities to settle the debt. The sign lists what is missing. Recovered items are consumed and cannot be taken back by killing the Camper.

## Settings

Open **Mods → Camper's Cache → Configure** in Mod Menu.

Settings apply across worlds in the same instance. Presets range from Scarce to Abundant, and each location has Items and Gear menus. You can change loot, enchantments, biomes, structure frequency, guards, ores, and the Camper's 20–60 block radius.

Yellow values have unsaved changes. Green values are saved customizations. Nothing applies until you press Save. Profiles can be imported and exported.

Generation changes affect new terrain. Loot changes affect unopened destination chests; opened chests do not refill. Dedicated servers read `config/campers_cache.json` on startup. See the [config guide](config/README.md).

## Building

Use Java 25, Node.js, and Gradle 9.5:

```powershell
gradle build
```

The build runs the game tests and creates the mod JAR in `build/libs`. The structure templates are included; the original design world is not needed to build. See [testing](TESTING.md) and [structure notes](design/README.md).

## Links and license

- [Source](https://github.com/madreaper16/mc-campers-cache)
- [Report a bug](https://github.com/madreaper16/mc-campers-cache/issues)

Copyright (c) 2026 MadReaper16. Released under the [MIT license](LICENSE).
