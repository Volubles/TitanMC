# Titan Region and Protection Engine

## Invariants

- SQLite region definitions are the source of truth; the spatial index is derived.
- Every region and world has a stable UUID.
- A `(world, namespace, name)` key is unique.
- Boxes use half-open bounds: minimum inclusive, maximum exclusive.
- Queries may return multiple regions ordered by priority, key, then UUID.
- Published index snapshots are immutable and queries never acquire a write lock.
- Mutations are serialized and publish a complete snapshot atomically.
- Index limits reject pathological geometry before it can exhaust server memory.

## Boundaries

The model, index, persistence, and protection decision packages do not depend on
Bukkit or Paper. Paper listeners only translate events and delegate decisions
to `ProtectionService`; they do not contain authorization rules.

Region geometry is separate from domain ownership. Cell, mine, and future admin
policies own their authorization data and expose immutable evaluators for
multi-block operations.

The engine deliberately has no region inheritance, magical aggregate build
flag, passthrough region, or geometric global region. Overlaps are resolved by
explicit decisions and priority.
