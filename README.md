# EaglerCity

EaglerCity is a Paper 1.21.11 classroom plugin for the
[Eaglercraft Classroom Server](https://github.com/SMalone16/Eaglercraft-1.21.11-Server).

It creates one compact town near world spawn and turns its villagers into a lightweight
resource economy with visible town security.

## What the plugin does

- Finds a reasonably flat site roughly 96 blocks from spawn instead of overwriting spawn.
- Builds a central plaza, roads, seven profession cottages, job blocks, beds, lighting,
  and a mature working farm.
- Creates two residents for each profession:
  - Farmer
  - Fletcher
  - Toolsmith
  - Armorer
  - Librarian
  - Butcher
  - Mason
- Gives each resident a real villager inventory with profession-specific target stock.
- Lets farmers keep using Minecraft's normal crop harvesting behavior.
- Runs one production/consumption cycle per Minecraft day for the wider town economy.
- Gives each profession a small daily base stock, capped so inventories do not inflate forever.
- Lets idle villagers compare inventories during the normal village gathering period.
- Moves useful surplus items toward villagers who are below their target stock.
- Prefers two-way trades, but allows a one-way gift when that is the only useful transfer.
- Shows successful exchanges with happy-villager particles and sounds.
- Drops a city villager's carried inventory when that villager dies.
- Maintains an EaglerCity-only iron golem security quota.
- Makes EaglerCity guards deal 5x normal damage by default.
- Makes attacked residents run toward the nearest city guard.
- Makes the nearest city guard target the attacking player.
- Does not modify unrelated villages, villagers, or iron golems.

## Economy model

Each profession has 4–7 target resources. A villager tries to hover around those target
amounts instead of becoming a warehouse.

A daily cycle has three parts:

1. **Consumption** removes a small amount of food and profession inputs.
2. **Work production** converts profession inputs into useful outputs.
3. **Base stock** injects a few difficult-to-source essentials, but only up to a capped level.

During the village gathering window, nearby residents compare inventories. If one villager
has more than their target amount of something that another villager needs, that surplus
can move to the other villager. Two-way exchanges are preferred.

This is intentionally a small systems simulation rather than a full market/pricing engine:
it is readable to students, visible in play, and inexpensive for the classroom server CPU.

## Security

The default security target is one guard per five living EaglerCity residents, capped at
four guards.

Only guards spawned and tagged by this plugin receive the damage multiplier.

When a player attacks an EaglerCity resident:

1. The resident enters a short panic state.
2. The resident pathfinds toward the nearest city guard.
3. The guard targets the attacking player.
4. The resident temporarily stops participating in town trading.

## Admin commands

Operators can use:

```text
/eaglercity status
/eaglercity generate
/eaglercity repopulate
/eaglercity security
```

`/city` is an alias.

`repopulate` restores missing profession residents up to the configured population.
It is intentionally an admin action rather than automatic respawning so killing villagers
cannot become an infinite loot farm.

## Configuration

Key defaults are in `src/main/resources/config.yml`.

Notable settings include:

- town distance from spawn
- resident count per profession
- economy heartbeat and trade radius
- trading time window
- daily stock/production toggles
- villagers per security golem
- maximum city guards
- panic range and speed
- iron golem damage multiplier

## Build

Requires Java 21.

```bash
mvn clean package
```

The distributable JAR is:

```text
target/EaglerCity-1.0.0.jar
```

GitHub Actions also publishes the latest successful build to:

```text
dist/EaglerCity-1.0.0.jar
```

## Server integration

The classroom server's plugin picker expects the compiled JAR at:

```text
dist/EaglerCity-1.0.0.jar
```

EaglerCity is designed for the Paper 1.21.11 backend. It does not require client-side
changes, ProtocolLib, or changes to the Eaglercraft browser client.

## Performance philosophy

EaglerCity avoids per-tick all-to-all villager simulation.

- Economy checks happen every few seconds.
- Trades are local and cooldown-limited.
- Each resident participates in at most one exchange per round.
- Production is once per Minecraft day.
- Security checks are periodic.
- Only tagged city entities are scanned for city-specific behavior.

This keeps the town lively without turning the classroom server into an NPC simulation benchmark.
