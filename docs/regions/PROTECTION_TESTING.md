# Protection Testing Strategy

Titan protection uses three test levels. Passing one level does not replace the
levels below it when the behavior crosses their boundary.

## Pure JUnit

Use ordinary JUnit tests for the region engine, protection resolution, cell
policies, persistence, revisions, batches, and domain validation. These tests
must not import Bukkit or MockBukkit.

## MockBukkit

Use MockBukkit only for the Paper adapter boundary:

- translating Bukkit objects into `ProtectionRequest` values;
- registering listeners;
- cancellation of block break, block place, and interaction events;
- commands, permissions, players, and basic world/block state;
- plugin enable/disable behavior when dependencies can be represented safely.

All MockBukkit tests extend `MockBukkitProtectionTestSupport`, which creates a
fresh `ServerMock` before each test and always calls `MockBukkit.unmock()` after
it. An `UnimplementedOperationException` aborts a MockBukkit test. A skipped
test is therefore not accepted as evidence that the behavior works.

## Real Paper Server

A real development server test is required for behavior that depends on the
Minecraft simulation, Paper timing, or integrations MockBukkit does not fully
implement:

- piston movement and multi-block event ordering;
- fluid and fire propagation across scheduled ticks;
- explosion source, affected-block lists, and entity/block explosion variants;
- entity, vehicle, hanging, and container edge cases;
- chunk load/unload and world lifecycle behavior;
- scheduler and thread-affinity behavior;
- WorldEdit, Vault, PlaceholderAPI, and MichelleLib integration;
- any MockBukkit test that is skipped or throws `UnimplementedOperationException`.

For these cases, the implementation is not considered complete until the exact
scenario has been exercised on the configured Paper development server.
