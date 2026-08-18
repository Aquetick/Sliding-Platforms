# Sliding Platforms

**Working sliding platforms built from any blocks — doors, elevator cabins, even transit lines — for Minecraft 1.20.1, Fabric.**

A standalone mod inspired by Moving Elevators, written from scratch: real platforms that actually
move, real cabins you can ride, and real block-by-block collision. No placeholders — a wall of
blocks turns into a living entity, slides at your speed along any axis, and lands back as normal blocks.

Requires: Fabric Loader + Fabric API, Minecraft 1.20.1, Java 17+

---

## Features

### Sliding platform controller
- Place the controller against any flat wall of blocks, right-click — the wall becomes a
  moving entity and slides away (X, Y or Z axis, your choice).
- Doors up to **12×12×3 by default**, longer travel, speed and size caps configurable live.
- The door is **real while moving**: you can stand on it, it pushes mobs and players,
  block-by-block collision is simulated the same way on server and client.
- Smooth trapezoid speed profile: acceleration, cruise, pre-computed braking and
  a final "dock" onto the target position.
- Settings menu (Shift+Right-click): axis buttons, travel distance slider (auto = full door
  size), speed slider, live sound & glow toggles.
- **Manual pick mode**: auto-scan guessed the wrong shape? Click the blocks yourself with
  right-click; the yellow outline shows the current set.
- **Cascade / curtain mode**: the door can split into rows that travel one after another,
  first-to-move reversing at close. Fine delay control per row, optional root-first inversion —
  great for theatrical curtain effects.

### Elevator rides
- **Elevator screens** plan real trips between floors: right-click a screen for a passenger
  menu — click a floor, the cabin flies to it immediately.
- Routes support multiple floors in a straight line, with per-floor names and numbers.
- **Screen chains**: link several screens to call the cabin from any floor — peer-to-peer,
  no "master" block.
- A cabin in flight waits its turn politely (FIFO), parks flush against the destination's face
  and keeps the ride even across server restarts.
- Route helpers: landing-zone checks before departure, total-path cap, rollback on obstacles.

### Platform glow
- Redstone lamps within **6 blocks** of a door light up automatically while the panel is near:
  the tunnel lights ahead of a riding cabin, the doorway shines at the doorstep.
- No redstone required — the mod only flips the lamps' `lit` state; your real redstone circuits
  are never touched. Per-controller on/off toggle; server-wide default configurable.

### Shared sounds
- Custom per-door start / move-loop / stop / arrive chime sounds — and they play for
  **everyone on the server**.
- The server builds a standard server resource pack from `slidingplatforms_sounds/` and serves it
  the vanilla way. Players with the mod upload new `.ogg` files straight from the sound menu —
  admins can also just drop files into the folder (rescanned every 30 s).
- Delivery uses a built-in mini-HTTP server (default port **24466**); if the download is
  blocked, the same pack is delivered chunk-by-chunk over the live game connection.
  Sound pack, port, host header and chunked fallback are all configurable.

### Locks, sensors & remote control
- **Remote switch** block: binds to a controller (Shift+Right-click to link) and toggles it
  from anywhere — redstone input works too.
- **Sensor tab**: trigger doors by players / mobs nearby, with a name whitelist.
- **Lock tab**: owner-only access to a controller's menu (player-name based).
- **Redstone pulse** on a controller or switch acts as a right-click.
- Auto-close timer, cabin presence indicator, controller names in the player's own language.

### Server config menu — `/slidingplatformscfg`
- One command opens a full GUI (ops of level 2+ or the singleplayer host only).
- **Limits** tab: curated presets for door size, travel, speed, lift ride length, new-door
  defaults, sound pack toggles, debug logs.
- **No restrictions** tab: free numeric fields — type any values (even 900 blocks/sec if
  you dare), applied instantly, saved to `config/slidingplatforms.json`, broadcast to everyone
  online. A red warning in the menu reminds you about lag risks.
- Every edit applies live — no restarts; clients who never open the menu see zero change.

---

## Blocks & Recipes

| Block | What it does | Crafting |
|---|---|---|
| **Platform Controller** | The brain: scans the wall, moves the door, holds all settings | Iron ×6, piston, redstone ×2 |
| **Elevator Screen** | Route planner for elevator cabins: floors, names, rides | Glass, redstone, iron |
| **Remote Switch** | Wireless toggle for a bound controller; redstone-aware | Stone button, redstone, iron |

Controls cheat-sheet:

| Gesture | Action |
|---|---|
| Right-click controller | Open / close the door |
| Shift+Right-click (empty hand) on controller | Settings menu |
| Shift+Right-click (empty hand) on switch | Switch menu (controller list) |
| Shift+Right-click a controller with the switch item | Bind switch → controller |
| Redstone pulse into controller / switch | Same as right-click |
| Right-click in pick mode | Add/remove blocks; yellow outline = current set |

## Commands

| Command | Who | What |
|---|---|---|
| `/slidingplatformscfg` | Server ops (level 2+), singleplayer host | Opens the server config menu (limits, defaults, sound pack, logs). Edits save to `config/slidingplatforms.json` and apply live. |

## Install

1. Fabric Loader **0.15+** for Minecraft **1.20.1**, Fabric API, Java **17+**.
2. Drop `sliding-platforms-1.0.0.jar` into `mods/` on **both** server and clients
   (the mod is server-authoritative; a vanilla client still gets everything via the server
   resource pack, but menus of this mod need the client mod).

## Build from source

```bash
cd slide-doors
gradle build
# jar lands in build/libs/sliding-platforms-<version>.jar
```

## Compatibility & notes

- Client and server versions may differ by a patch — unknown bits of the network protocol are
  ignored gracefully both ways.
- All moving blocks are restored even if the server restarts mid-flight.
- English and Russian localizations included; new blocks are named in the placing player's
  language.

## License

MIT — see [LICENSE](../LICENSE).
