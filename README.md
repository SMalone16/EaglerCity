# EaglerCity

EaglerCity is a Paper 1.21.11 classroom plugin for the
[Eaglercraft Classroom Server](https://github.com/SMalone16/Eaglercraft-1.21.11-Server).

It creates one compact town near world spawn and turns its villagers into a lightweight
resource economy with visible town security.

## EaglerCity 1.1

Version 1.1 adds two major quality-of-life systems:

- **Reachable cottages.** Elevated houses receive three-block-wide terraced staircases
  from the front entrance down toward surrounding terrain. Existing 1.0 towns are
  automatically retrofitted on first startup; the houses are not regenerated.
- **Resident schedules.** Villagers now actively path between work, the town plaza,
  casual wandering destinations, and home. Low-stock residents go to their profession
  workstation and receive a target-capped work/restock cycle when they arrive.

## Resident schedule

During a normal Minecraft day, EaglerCity residents cycle through simple states:

1. **Work** — during the morning/day, villagers below about 65% of a profession target
   walk to their workstation. Once they arrive they can consume inputs, produce outputs,
   or collect a small amount of missing profession base stock.
2. **Wander** — residents whose inventories are healthy circulate around the town instead
   of standing in place.
3. **Social / trade** — during the village gathering period residents deliberately walk
   toward the central plaza and then toward nearby residents. The existing inventory
   comparison system performs exchanges once they are close enough.
4. **Home** — in the evening and night residents return to their profession cottage.
5. **Panic** — an attacked resident temporarily overrides the schedule and runs toward
   the nearest EaglerCity guard.

Movement decisions run every few seconds rather than every tick, keeping the activity
visible without making pathfinding a major classroom-server CPU cost.

## Town and economy

- Seven professions, two residents each by default:
  - Farmer
  - Fletcher
  - Toolsmith
  - Armorer
  - Librarian
  - Butcher
  - Mason
- Real villager inventories with profession-specific target stock.
- Daily production, consumption, and capped base stock.
- Local, cooldown-limited resource exchanges between residents.
- Inventory contents drop when a city villager dies.
- Farmers can still use Minecraft's normal crop behavior.
- One city guard per five residents by default, capped at four.
- EaglerCity guards deal 5x normal damage by default.
- Only EaglerCity-tagged villagers and golems are modified.

## Admin commands

```text
/eaglercity status
/eaglercity generate
/eaglercity repopulate
/eaglercity security
/eaglercity repair
```

`/city` is an alias.

`repair` re-runs the cottage entrance retrofit if terrain or blocks around the town
were later changed.

## Build

Requires Java 21.

```bash
mvn clean package
```

The distributable JAR is:

```text
dist/EaglerCity-1.1.0.jar
```

EaglerCity is designed for the Paper 1.21.11 backend and requires no client-side changes,
ProtocolLib, or Eaglercraft browser-client modifications.

## Optional Undercity integration
When EaglerZombiesFall26 is enabled, EaglerCity incrementally excavates a **40×30×20** cavern whose ceiling is ten blocks below the existing city floor. A protected **16×16×16** stepped temple contains four branches and an iron-doored loot chamber. Two outside shrine markers and four inside room markers are populated by the Zombie plugin, and the optional elevator reads the saved entrance coordinates. The world persists `eaglercity:undercity_x`, `eaglercity:undercity_y`, `eaglercity:undercity_z`, and `eaglercity:undercity_ready` integer keys. The published Y is **cavern floor level**. Loot plugin absence leaves a basic fallback chest; the city alone remains functional. The excavation is one-time per world and cannot be mined or modified by players, pistons, explosions or fluid placement. The iron loot door opens briefly for player interactions.
