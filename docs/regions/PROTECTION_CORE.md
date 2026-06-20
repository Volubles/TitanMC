# Titan Protection Core

The protection core is a pure Java decision layer. It has no Bukkit or Paper
imports and does not listen to Minecraft events.

## Resolution

1. Actor bypass is evaluated first.
2. Regions at the target position are ordered by descending priority, then key,
   then UUID.
3. Policies return `ALLOW`, `DENY`, or `ABSTAIN`.
4. The highest priority level containing an explicit decision wins.
5. `DENY` wins when policies at the same priority disagree.
6. If every applicable region abstains, the immutable world default decides.
7. Lookup, policy, bypass, null-result, and default failures deny closed.

Every resolution contains the evaluated regions, policy IDs, decisions, errors,
deciding priority, and a machine-readable reason. Later Bukkit adapters must use
this trace for diagnostics rather than reproducing decision logic.

## Policy ownership

Policies are registered by region namespace. The region engine owns geometry;
domain systems own authorization data:

- `cell:*` will resolve renters, owners, and trusted players through CellPolicy.
- `mine:*` will resolve mining actions through MinePolicy.
- Future administrative namespaces may supply their own policies.

Unknown namespaces abstain. Protected worlds should normally default to deny,
while worlds not managed by Titan may default to allow.

## Consistent evaluations

Multi-block operations must open one `ProtectionEvaluation`. It pins the region
snapshot and asks every policy, the defaults provider, and the bypass provider
for an immutable evaluator at one actor and timestamp. Ownership or rental data
may change after the evaluation opens without changing decisions halfway through
an explosion, piston move, or tool batch.

Transition checks expose `SOURCE`, `TARGET`, `BOTH`, and `EITHER`. Their result
retains the independent source and target resolutions so diagnostics can explain
which side of a boundary denied an operation.

## Version policy

The region database has no legacy migration path during development. Only an
empty database or the exact current schema version is accepted. Mutation APIs
likewise require explicit region revisions; there are no last-write-wins legacy
overloads.
