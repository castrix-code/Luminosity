# Luminosity

A Paper 1.21.4 plugin. Every player carries a Luminosity score from **-100** to **+100**.
Kills move 20 points from the loser to the winner, and where you land on that scale
decides whether you become an armour-ignoring assassin or an unkillable frontline tank.

## Building

Requires **JDK 21**. The Gradle wrapper handles the rest.

```bash
./gradlew build      # gradlew.bat on Windows
```

The jar lands in `build/libs/Luminosity-1.1.0.jar`. Drop it in `plugins/` and restart.

## The scale

| Luminosity | Stage | Title |
|---|---|---|
| -100 | Void 3 | Void Revenant |
| -80 to -60 | Void 2 | Void Phantom |
| -40 to -20 | Void 1 | Shadowbound |
| 0 | — | Neutral |
| +20 to +40 | Light 1 | Sunbound |
| +60 to +80 | Light 2 | Radiant Guardian |
| +100 | Light 3 | Solar Deity |

Because every transfer is exactly 20 points, only multiples of 20 are ever reachable.

## Void Ban Threshold

Dying while sitting at -100 **bans the player from the server**. The ban goes on the vanilla
ban list (visible in `/banlist`, source `Luminosity`) and they are disconnected a tick after
death, so their drops and death still process normally. There is no spectator phase, so a
dead player cannot scout bases.

They stay banned until a Revival Ritual or `/lum revive` frees them. Because they are offline
when revived, the return is applied on their next login: -40 Luminosity, survival mode.

- A vanilla `/pardon` is treated as a revival too, so admins can use either.
- A revival only ever lifts a ban **this plugin** issued. Someone banned by an admin for
  another reason stays banned even if a Charged Core is spent on them.
- Upgrading from the old spectator-limbo release: anyone still locked is banned when the
  new jar first loads, so nobody slips out during the upgrade.

## The Luminous Core

```
        [ Dragon Head ]
[ Echo ][ Nether Star ][ Echo ]
[ Diamond ][ Netherite Ingot ][ Diamond ]
```

- Crafts with an innate **+20** charge.
- **Crouch + right-click** pours 20 of your own Luminosity into it (one Core at a time;
  you need at least +20 to give).
- At **+100** it becomes a **Charged Core**.
- **Right-click a Beacon** (configurable via `altar-block`) with a Charged Core to open
  the Revival Ritual menu. Pick a void-locked player; the Core is spent and they return
  at **-40 Luminosity**.

## Active abilities

Stage 3 abilities are bound to the **Luminous Sigil** (`/lum sigil`) so they cannot
misfire during ordinary play:

- **Right-click** — Supernova (Light 3)
- **Crouch + right-click** — Shadow Realm (Void 3)
- An Eclipse player holds both on the one sigil.

The Sigil is granted automatically the moment a player reaches Stage 3 on either path,
and is not handed out twice. `/lum sigil` still exists for staff.

**Shadow Dash** (Void 2) is not on the sigil — double-tap crouch, 8s cooldown.

## Commands

`/luminosity` (alias `/lum`)

| Command | Permission | Effect |
|---|---|---|
| `/lum` | — | Your own status |
| `/lum get <player>` | — | Another player's status |
| `/lum set\|add <player> <n>` | `luminosity.admin` | Adjust the score |
| `/lum revive <player>` | `luminosity.admin` | Unban a Void-claimed player |
| `/lum eclipse <player> <bool>` | `luminosity.admin` | Grant or strip Eclipse |
| `/lum core \| sigil [player]` | `luminosity.admin` | Spawn plugin items |

## Implementation notes worth knowing

A few traits could not be done the obvious way, and two rules in the plan contradicted
each other. Here is what was decided and why.

**Void Sight uses private particles, not glowing.** Bukkit's `setGlowing` is global entity
metadata — switching it on would outline the target for *everyone*, which is the opposite
of the intent. Instead each Revenant is sent a per-client particle silhouette of every
player within 40 blocks. Particles render through walls, so the effect matches; it reads
as a shimmer rather than a hard outline. A packet library (ProtocolLib/PacketEvents) is
the only way to get true per-viewer glow, and adding one for a single trait was not worth
the dependency.

**Sun Aura burns on its own timer.** The plan asks for fire that bypasses fire resistance
and cannot be extinguished. Vanilla fire fails both. So `SolarBurn` applies the flame
overlay for looks and deals the damage directly as generic damage on a 1/sec task, which
neither Fire Resistance nor water can stop.

**Phase Strike runs vanilla's armour formula.** There is no API switch for "ignore armour",
so the listener computes the hit the target would take with part of its armour points and
Resistance removed, then solves for the base damage that lands exactly that much against
the real armour. Protection enchantments, absorption hearts and shields are never pierced.

The first version instead undid a share of *all* mitigation between base and final damage.
That silently pierced Protection and absorption too: against Prot IV netherite its "50%"
turned a sword hit from 0.76 into 4.38, nearly six times vanilla.

**Eclipse sheds the contradictory penalties.** The plan grants Eclipse "all Void powers
while retaining all Solar buffs" but is silent on penalties, and inheriting both sets is
self-cancelling — Glow Stigma would negate the True Stealth it just gained, Darkness
Suffocation would punish standing in the dark the Void half is built for, and Solar
Combustion and Sun Stigma would punish the daylight the Solar half is built for. Eclipse
is therefore exempt from Sun Stigma, Solar Combustion, Light Decay, Glow Stigma and
Darkness Suffocation. It **keeps Beacon Mark** — a 5-minute position broadcast is the only
counterplay the server has left against an otherwise complete kit, so removing it would
make the state unanswerable.

**Shadow Dash stops at walls.** It walks the path in half-block steps and lands on the
last passable spot, so the dash can never bury someone inside terrain.

## Balance

The Void path's combat numbers live in `config.yml` under `void-path`, as fractions from 0 to 1,
and take effect on restart. Missing keys are written into an existing config automatically.

| Key | Default | Was |
|---|---|---|
| `executioner-bonus` | 0.08 (+8% vs Light players) | 0.15 |
| `phase-strike-armor-pierce` | 0.15 | 0.50, and pierced far more than armour |
| `vampirism-heal` | 0.25 | 0.25 |

Armour is non-linear, so the pierce value is not "+X% damage". A fully charged netherite sword
against full netherite gains roughly +30% at 0.10, +45% at 0.15, +61% at 0.20, +76% at 0.25,
with or without Protection.

## Server versions

Built against Paper 1.21.4 and verified to run on both 1.21.4 and 1.21.11. Newer Minecraft
keeps adding required options to existing particles (`FLASH` needs a colour on 1.21.11,
`DRAGON_BREATH` a power), which originally made Supernova fail on newer servers: the particle
threw before the knockback and burn ran. All effects now go through `Fx`, which supplies
whatever data the running server requires, and every ability applies its gameplay before its
visuals. To compile against another version: `gradlew build -PpaperVersion=1.21.11-R0.1-SNAPSHOT`.

## Data

Records live in `plugins/Luminosity/players.yml`, flushed every 5 minutes (`autosave-minutes`)
and on shutdown. Attribute modifiers are stripped on disable so a plugin removal does not
leave players with permanent bonus hearts.

## Testing

Verified against real Paper servers — **1.21.11** (build 132) and **1.21.4** (build 232) — driven by
headless mineflayer clients acting as actual players: joining, fighting, dying, getting
banned, reconnecting and right-clicking over the wire. Damage and health are read from the
server itself (`/data`, `/attribute`) wherever a client could misreport them.

Covered: Supernova knockback and its full 5-damage burn on both a mob and a player; the
Dragon Egg Eclipse; Phase Strike landing +45% against full netherite with and without
Protection IV (2.112 → 3.072, 0.76 → 1.106); Executioner at +8% (8 → 8.64); death at -100
banning and disconnecting the player, the ban appearing in `/banlist`, and the player being
refused on reconnect; `/lum revive` and a vanilla `/pardon` both returning them at -40 in
survival; an admin-issued ban surviving a revival; an upgraded install writing the new config
keys and banning a player the old release had spectator-locked; the Sigil grant, Core
pouring, stage health pools, and a clean server log.

Not covered, because they need a human eye: the feel of Shadow Dash’s double-tap window,
Void Sight’s particle silhouettes, Beacon Mark, and the Revival Ritual’s chest UI.

### Bugs this found

`PlayerInteractEvent#isCancelled()` returns **true** for every right-click on
*air* — there is no block, so `useInteractedBlock()` sits at DENY and Bukkit
reports the whole event as cancelled. The interact listener was originally
annotated `@EventHandler(ignoreCancelled = true)`, which silently swallowed every
air right-click and disabled both Core pouring and all sigil abilities. The
listener now takes the event uncancelled and checks `useItemInHand()` instead.

Three more, found by auditing the paths the suite had not reached:

**Stage 3 abilities were unobtainable.** The Luminous Sigil was only ever created by
`/lum sigil`, an op-only command, so Supernova and Shadow Realm were dead content for
ordinary players. `TraitApplier` now grants the Sigil from the per-second tick the
moment a player reaches Stage 3 on either path — which covers every route in (a kill,
an admin set, a login, the Dragon Egg) without each having to remember — and
`LuminousSigil.carries()` stops it handing out a second.

**The void-lock held only by accident.** `onDeath` locked the victim first and then ran
the kill transfer on that same player, which re-applied their traits and would have
handed a spectator their bonus hearts back. It only failed to because the victim was
already clamped at -100 and `set()` early-returns on an unchanged value. The lock now
happens last, and `TraitApplier.apply()` refuses outright for a void-locked player, so
no future caller can re-buff someone in limbo.

**`/lum revive` could freeze the server.** `Bukkit.getOfflinePlayer(String)` blocks on a
Mojang API call for an uncached name. It now resolves through the online player or
`getOfflinePlayerIfCached`, and reports an unknown name instead of stalling the main
thread.

One known behaviour left as-is by choice: the plugin cannot distinguish its own potion
effects from ones a player drank, so dropping out of a stage can strip a matching potion
(e.g. losing Fire Resistance on a fall from +100 to +80).
