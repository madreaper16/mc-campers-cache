# Testing

## Automated tests

Run `gradle build` or `gradle runGameTest` with Java 25 and Node.js installed.

The tests cover chest theft, emerald apologies, returning items, multiplayer ownership, saved debts, sleeping, combat range, loot, settings, guard counts, structure spacing, and `/locate`. They also check the two terrain locations that previously exposed the storehouse entrance.

Test classes are separate from the playable mod and are not included in the JAR.

## In-game checks

Use a new Creative world with cheats enabled and Normal difficulty. Remove the old prototype datapack if it is still installed.

For a quick look at all three builds, find a clear area and run:

```mcfunction
/function campers_cache:test_models
```

This preview places the builds near each other and changes terrain. It does not show their natural spacing. Defaults should produce one Camper, four supply-house guards, eight storehouse guards, and 52 ore blocks.

For natural placement, run:

```mcfunction
/camperscache layout
/locate structure campers_cache:camp
/locate structure campers_cache:supply_shack
/locate structure campers_cache:hidden_storehouse
```

Check the ladder, doorway, hatch, roof cover, chest access, and guard positions on both flat and hilly terrain.

Switch to Survival to check camp behavior. Open the chest, apologize with an emerald, then try taking and returning items. Confirm that only the offending player is targeted in multiplayer. Save and reload while items are still owed.

In Mod Menu, check the Items and Gear lists, item search, biome names, presets, profile import/export, and Save/Cancel. Unsaved values should be yellow; saved customizations should be green. Config changes should not refill opened chests or replace existing structures.
