# Sable Player Ragdoll

Sable Player Ragdoll is a NeoForge mod that adds player ragdolls powered by
Sable's physics system. It can ragdoll players, seat the real player onto the
simulated body, spawn playerless dummy ragdolls, and expose a small public API
for addon mods.

This project is still early and experimental. The goal is to keep the core mod
small while giving addon developers enough hooks to trigger ragdolls from their
own gameplay.

## Features

- Manual player ragdoll trigger through a client keybind.
- Physics-backed ragdoll body parts using Sable sublevels.
- Automatic player seating so the camera follows the ragdoll.
- Playerless dummy ragdolls with position, heading, skin profile, velocity, and
  despawn options.
- Per-limb spawn pose and joint stiffness/damping control through the public API.
- Playerless ragdoll equipment snapshots for vanilla equipment, Curios, and
  Accessories visual state.
- Simple API for addon mods to launch ragdolls or query active sessions.
- Ragdoll part interaction events for addon mods.
- Datapack item tag support for weapons that ragdoll players on hit.
- Test commands for spawning dummies and giving a ragdoll test stick.

## Requirements

- Minecraft `1.21.1`
- NeoForge `21.1.219` or newer
- Sable `1.1.0` or newer
- Java `21`

## Commands

The command root is:

```mcfunction
/sable_player_ragdoll
```

Useful test commands:

```mcfunction
/sable_player_ragdoll dummy
/sable_player_ragdoll dummy <pos> <heading>
/sable_player_ragdoll dummy profile <profile>
/sable_player_ragdoll dummy profile <profile> <pos> <heading>
/sable_player_ragdoll wailing start
/sable_player_ragdoll wailing start <duration_ticks> <stiffness> <interval_ticks> [targets]
/sable_player_ragdoll wailing stop [targets]
/sable_player_ragdoll test_stick
```

Dummy despawn options can be appended where supported:

```mcfunction
despawn default
despawn never
despawn after_ticks <ticks>
despawn below_speed <meters_per_second>
```

`heading` is in degrees, using Minecraft's yaw-style direction.

## Datapack Item Tag

Items in this tag ragdoll players when used to hit them:

```text
sable_player_ragdoll:ragdoll_on_hit
```

Example datapack file:

```json
{
  "replace": false,
  "values": [
    "minecraft:stick"
  ]
}
```

Path:

```text
data/sable_player_ragdoll/tags/item/ragdoll_on_hit.json
```

## Public API

Addon mods can depend on this mod jar and call the public methods in
`dev.leo.sableplayerragdoll.api.RagdollAPI`.

Main API entry points:

```java
RagdollAPI.launch(player, velocity);
RagdollAPI.launch(player, velocity, despawnConditions);
RagdollAPI.launch(player, velocity, launchOptions);
RagdollAPI.spawnPlayerless(level, position, headingDegrees);
RagdollAPI.spawnPlayerless(level, position, headingDegrees, velocity);
RagdollAPI.spawnPlayerless(level, position, headingDegrees, profile, velocity);
RagdollAPI.detachActive(player, playerlessDespawnRule);
RagdollAPI.activeSession(player);
RagdollAPI.isRagdolled(player);
RagdollAPI.isRagdollSubLevel(subLevelId);
RagdollAPI.isRagdollSubLevel(subLevel);
RagdollAPI.torsoSubLevelId(headSubLevelId);
RagdollAPI.setGrabDisabled(level, partSubLevelId, disabled);
RagdollAPI.captureEquipment(player, equipmentScope);
RagdollAPI.applyEquipmentSnapshot(level, headSubLevelId, snapshot);
```

Despawning helpers are available through:

```java
DespawnCondition.afterTicks(ticks);
DespawnCondition.belowSpeed(metersPerSecond);
DespawnCondition.belowSpeedAfterTicks(metersPerSecond, minTicks);
DespawnCondition.never();
DespawnCondition.all(...);
DespawnCondition.any(...);
```

Player launches can be customized per call with `RagdollLaunchOptions`:

```java
RagdollLaunchOptions options = RagdollLaunchOptions.builder()
   .autoSeat(false)
   .despawnConditions(List.of(DespawnCondition.afterTicks(80)))
   .lockDismount(false)
   .build();

RagdollAPI.launch(player, velocityMetersPerSecond, options);
```

`lockDismount(true)` prevents the player from manually exiting the ragdoll via
the keybind or interaction. The ragdoll will still end if the despawn conditions
trigger or if `session.release()` is called. To keep a player in ragdoll
indefinitely until released by code:

```java
RagdollLaunchOptions options = RagdollLaunchOptions.builder()
   .lockDismount(true)
   .despawnConditions(List.of(DespawnCondition.never()))
   .build();

RagdollSession session = RagdollAPI.launch(player, velocity, options);
// later, when you want to release:
session.release();
```

### Per-limb pose and joint control

`RagdollLimbOptions` lets you set the spawn pose and joint stiffness/damping for
individual limbs. All fields are optional, unset limbs use the built-in defaults,
so you only need to specify what you want to override.

```java
RagdollLimbOptions limbs = RagdollLimbOptions.builder()
   .limb(BodyPart.LEFT_ARM,  RagdollLimbConfig.builder().rotation(0, 0, 90))
   .limb(BodyPart.RIGHT_ARM, RagdollLimbConfig.builder().rotation(0, 0, -90))
   .limb(BodyPart.HEAD,      RagdollLimbConfig.builder().stiffness(120).damping(10))
   .build();

RagdollAPI.launch(player, velocity,
   RagdollLaunchOptions.builder().limbs(limbs).build());
```

`rotation(pitchDegrees, yawDegrees, rollDegrees)` sets the limb's rest angle. Individual axes can be set separately with
`.pitch(d)`, `.yaw(d)`, and `.roll(d)`.

The same API is available for dummies:

```java
RagdollAPI.spawnPlayerless(level, position, headingDegrees, profile, velocity,
   despawnRule, limbs);
```

### Playerless ragdoll equipment

Addon mods can capture a player's render-relevant equipment into a detached
snapshot and apply it to a playerless ragdoll. The snapshot is visual state only:
vanilla hand/armor slots plus supported optional equipment slots. It is not the
player's full inventory.

```java
RagdollEquipmentSnapshot snapshot =
   RagdollAPI.captureEquipment(player, RagdollEquipmentScope.ALL);

RagdollAPI.applyEquipmentSnapshot(level, headSubLevelId, snapshot);
```

`RagdollEquipmentScope` controls what gets captured:

```java
RagdollEquipmentScope.VANILLA;
RagdollEquipmentScope.OPTIONAL_MODS;
RagdollEquipmentScope.ALL;
```

Snapshots can be merged when capture timing differs between vanilla and optional
equipment systems:

```java
RagdollEquipmentSnapshot vanilla =
   RagdollAPI.captureEquipment(player, RagdollEquipmentScope.VANILLA);

RagdollEquipmentSnapshot optional =
   RagdollAPI.captureEquipment(player, RagdollEquipmentScope.OPTIONAL_MODS);

RagdollEquipmentSnapshot combined = vanilla.merge(optional);
RagdollAPI.applyEquipmentSnapshot(level, headSubLevelId, combined);
```

Snapshots can also be filtered against an item pool before re-applying them. This
is useful for addon mods with lootable mannequins, corpses, or other detached
ragdolls whose visual gear should disappear as matching items are removed.

```java
RagdollEquipmentSnapshot visible = snapshot.filteredByAvailableItems(items);
RagdollAPI.applyEquipmentSnapshot(level, headSubLevelId, visible);
```

For playerless ragdolls created from an active player ragdoll, use:

```java
PlayerlessRagdollSession corpse =
   RagdollAPI.detachActive(player, PlayerlessDespawnRule.never());
```

`torsoSubLevelId(headSubLevelId)` can be used to find the torso part, and
`setGrabDisabled(level, partSubLevelId, true)` can disable the grab-drag mechanic
for a specific part.

### Wailing motor effects

Active sessions can temporarily retarget their joint motors for a twitching or
wailing motion. This works for both player ragdolls and playerless ragdolls.

```java
RagdollSession session = RagdollAPI.launch(player, velocity);
if (session != null) {
   session.applyWailing(RagdollWailingOptions.builder()
      .durationTicks(100)
      .stiffness(15.0)
      .intervalTicks(10)
      .startDelayTicks(2)
      .build());
}
```

Use `session.stopWailing()` to restore the ragdoll joints to their base motor
targets before the duration ends. By default, wailing waits 2 ticks before the
first retarget so the ragdoll can finish spawning cleanly. Convenience overloads
are also available:

```java
session.applyWailing(100);
session.applyWailing(15.0, 100, 10);
```

`RagdollKeybindExample` in the source is a worked end-to-end example showing an
on-foot pose and an elytra pose built with this API.

Playerless ragdolls use `PlayerlessDespawnRule`:

```java
PlayerlessDespawnRule.defaultRule();
PlayerlessDespawnRule.never();
PlayerlessDespawnRule.afterTicks(ticks);
PlayerlessDespawnRule.belowSpeed(metersPerSecond);
```

Addon hooks are posted on the NeoForge game event bus:

```java
RagdollStartEvent
RagdollEndEvent
RagdollInteractEvent
```

`RagdollStartEvent` fires before a player ragdoll is assembled. It is
cancellable, and listeners can replace the launch velocity.

`RagdollEndEvent` fires after a player exits a ragdoll. It exposes the player,
the exit velocity inherited from the ragdoll, and a reason.

`RagdollInteractEvent` fires when a player interacts with a ragdoll part. It
exposes the player, head sublevel UUID, interacted part sublevel UUID, world
position, and server level. It is cancellable, allowing addon mods to replace
the default interaction behavior.

`isRagdollSubLevel` lets other mods check whether a given sub-level (or its UUID)
belongs to a ragdoll.

The API covers spawning, despawning, playerless detaching, per-limb pose/joint
control, temporary wailing effects, session locking, visual equipment snapshots,
part interaction hooks, basic session queries, and sub-level identification. It
does not currently expose direct force application to an already active ragdoll
or full inventory storage.

## License

All rights reserved. Do not redistribute.

See [LICENSE](LICENSE) for the full license text.
