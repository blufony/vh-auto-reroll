# Auto Reroll Mod for The Vault

Automatically rerolls the Black Market until you find your desired item using Create mod filters.

## Features

- Click-based auto-rerolling with GUI controls
- Use Create attribute/list filters to specify target items
- Configurable max rerolls and delay between attempts
- Optional: Search all 3 trade slots or just center
- Visual feedback with sounds and tooltips
- In-game config access via shift-click

## Requirements

- Minecraft 1.18.2
- Forge 40.x
- The Vault mod
- **Create mod** (required for attribute filters)
- **Vault-Filters mod** (required for NBT matching)

## Installation

Like any other mod: copy `auto_reroll-{version}.jar` to your mods folder

## Usage

Open the Black Market, hold a Create filter or simple item, then click the filter slot (top-left). Click the auto-reroll button in the top right (requires Whispers of the Market prestige) to start; shift-click for config.

| Filter Type | Matches | Example |
|--------|---------|---------|
| Simple item | Item ID only | Pyretic Focus, any Inscription, any Booster Pack |
| Create Attribute Filter (+ Vault Filters) | Specific NBT | Any Omega Inscription, Specific Inscription, size 5 Catalyst, multiple items |

Stops when: match found, max rerolls reached, or click again to cancel.

## Building

```bash
./gradlew clean build
```

Output: `build/libs/auto_reroll-1.0.0.jar`

## Dependencies

- Minecraft 1.18.2
- Forge 40.x
- The Vault mod
- Create mod
- Vault-Filters mod

## License

MIT
