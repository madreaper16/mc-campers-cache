# Configuration

Open **Mods → Camper's Cache → Configure** in Mod Menu. Client settings apply across worlds in that instance. Dedicated servers use `config/campers_cache.json` and load it on startup. Clients cannot change a remote server's settings.

## Saving and presets

The screen keeps edits in a draft until Save. Changing pages does not apply them.

- Yellow: unsaved changes.
- Green: saved values that differ from Generous defaults.
- Undo Edits: return to the last save.
- Defaults: load Generous defaults into the draft.
- Cancel: leave without saving.

Presets are **Scarce, Average, Generous, Abundant**. They change camp frequency, loot amounts, enchantments, rare drops, and ore weights. Editing a preset switches to **Custom**. Selecting another preset replaces the whole draft, including custom loot; a confirmation appears first. Save is still required.

Profiles are stored in `config/campers_cache_profiles/NAME.json`. Names can contain letters, digits, hyphens, and underscores. Export saves the draft to a new file. Import replaces the draft after confirmation. Existing profile files are not overwritten.

## Available settings

- **Features And Camper:** enable the mod, Camper, guards, and ores. The wander/chase radius is limited to 20–60 blocks.
- **Generation:** camp frequency, region spacing, destination distance, guard counts, structure toggles, allowed biomes, and per-biome frequency. Terrain and entrance checks stay enabled.
- **Loot:** Camp, Supply House, and Storehouse each have Items and Gear lists. Add or remove entries and edit their quantity, chance, and enchantments. Any registered item can be added to either list. The two storehouse chest halves appear together.
- **Enchantments:** vanilla levels and compatible combinations only. Each enchantment has its own chance and level range. Books become enchanted books when an enchantment succeeds.
- **God Armor:** enable the drop and set its chance. Its diamond chestplate, name, Unbreaking III, Mending I, and Protection V are fixed. It is the only above-vanilla exception.
- **Ores:** set the five ore weights. The screen shows the resulting percentages. Zero disables that ore; all five weights cannot be zero. Disabling ores replaces the markers with ordinary deepslate.

The original loot draw rules are kept behind the simpler Items and Gear lists. Item chance is checked after selection; setting it to 100% does not guarantee that item will be selected on every draw. New Items entries get their own draw. New Gear entries join the gear selection.

Chest capacity is checked before saving. If a setup could produce more than 27 stacks in one chest half, reduce the quantities or remove entries. Enchanted books count as unstackable.

## What changes immediately

Radius changes affect existing active Campers. Loot settings affect unopened destination chests that still have a loot-table reference. Opened chests never reroll, and existing camp supplies are kept.

Generation and ore changes affect new terrain. They do not move buildings or remove existing guards. Use a fresh test world when checking spacing, distance, or biome changes.

Disabling the Camper stops new Camper spawns and pauses existing Camper AI and chest disputes. Existing NPCs and debts are kept.

Map prices, single-purchase rules, Camper damage and health, emerald apologies, exact-item restitution, sleeping, and transport behavior are fixed. Map trading is still in development.

Old ore-only settings are read without losing their weights. Save writes the current format. Invalid files are left untouched, and the last valid settings stay active.

## Default ore weights

| Ore | Weight | Chance |
| --- | ---: | ---: |
| Deepslate Iron Ore | 14 | 14% |
| Deepslate Coal Ore | 13 | 13% |
| Deepslate Copper Ore | 13 | 13% |
| Deepslate Diamond Ore | 30 | 30% |
| Deepslate Emerald Ore | 30 | 30% |

Each of the storehouse's 52 markers gets a separate roll. The percentages are chances, not guaranteed totals.
