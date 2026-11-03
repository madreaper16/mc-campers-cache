# Structure templates

These builds were made by MadReaper16 and exported from the design world.

| Template | Size (X × Y × Z) | Markers |
| --- | --- | --- |
| `templates/camp.nbt` | 9 × 6 × 15 | Camper, chest, bed and sign |
| `templates/supply_shack.nbt` | 10 × 8 × 14 | Four guard positions |
| `templates/hidden_storehouse.nbt` | 14 × 9 × 14 | Eight guards and 52 ore positions |

Sizes include the ground layer and a small border. `structures.json` records the original bounds and local marker coordinates.

Yellow wool marks guards. Pink wool marks ores. The exported source templates have air at guard markers and deepslate at ore markers. During the build, the exporter adds guards, the Camper, chest loot tables, and the ore processor.

The original design world remains the editable version. To export changes, run these commands from the project folder:

```powershell
node tools/inspect-world.cjs "PATH_TO_DESIGN_WORLD"
node tools/export-designs.cjs "PATH_TO_DESIGN_WORLD"
```

The exporter uses fixed bounds for the three builds. Update those bounds in `tools/export-designs.cjs` if a build gets larger. Rebuild the mod afterward.
