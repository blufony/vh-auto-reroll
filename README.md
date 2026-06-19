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

1. Copy `auto_reroll-1.0.0.jar` to your mods folder
2. Launch Minecraft
3. Open Black Market and set your filter item (see Usage)

## Usage

### Setting the Filter

1. Open the Black Market
2. Hold a Create attribute filter or list filter in your hand
3. Click the filter slot in the top-left of the Black Market GUI
4. Tooltip shows current filter item

### Starting Auto-Reroll

1. Ensure you have the "Whispers of the Market" prestige unlocked
2. Click the auto-reroll button (blue button with cycle icon)
3. Mod will reroll until:
   - Matching item found in trade slots
   - Max rerolls reached
   - You click again to stop

### Config Access

- **Shift+Click** auto-reroll button to open config screen

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
