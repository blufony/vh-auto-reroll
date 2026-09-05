# Auto Reroll Mod for The Vault

Automatically rerolls the Black Market until you find your desired item using Create mod filters, and auto-rerolls Bounty Table bounties until you get the rarity and bounty type you want.

## Features
- Click-based auto-rerolling with GUI controls

### Black Market
- Use Create attribute/list filters to specify target items
- Optional: Search all 3 trade slots or just center
- Optional: Automatically buy target items
- Configurable max rerolls and delay between attempts
- Visual feedback with sounds and tooltips
- In-game config access via shift-click

### Bounty Table
- "Rare Only" toggle: stop on rare (yellow) or better bounty pools; off accepts common and above
- Bounty type toggles: pick which task types to keep (Kill, Completion, Submit Items, Discover Items, Mining); none enabled accepts any type
- Both filters combine: the loop stops only when a rolled bounty satisfies rarity and type

## Requirements

- Minecraft 1.18.2
- Forge 40.x
- The Vault mod
- **Create mod** (required for Black Market attribute filters)
- **Vault-Filters mod** (required for Black Market NBT matching)

## Installation

Like any other mod: copy `auto_reroll-{version}.jar` to your mods folder

## Usage

### Black Market
Open the Black Market, hold a Create filter or simple item, then click the filter slot (top-left). Click the auto-reroll button in the top right (requires Whispers of the Market prestige) to start; shift-click for config.

| Filter Type | Matches | Example |
|--------|---------|---------|
| Simple item | Item ID only | Pyretic Focus, any Inscription, any Booster Pack |
| Create Attribute Filter (+ Vault Filters) | Specific NBT | Any Omega Inscription, Specific Inscription, size 5 Catalyst, multiple items |

Stops when: match found, max rerolls reached, or click again to cancel.

### Bounty Table
Select a bounty, then use the switches at the bottom of the table to set your targets: the "Rare Only" switch (right of the auto-reroll button) stops on rare or better pools, and the type switches (bottom-left row) keep only the selected bounty types. Click the auto-reroll button (right of the vanilla reroll button) to start; click again to stop at any time.

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
