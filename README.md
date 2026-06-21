# Titan

Titan is a custom Paper plugin built for a prison server. It collects the
server's mines, custom tools, menus, and region protection in one codebase.

The project is under active development. The region engine and protection core
are usable, while cells and the remaining Minecraft event coverage are still
being built.

## Current features

- Configurable mines with weighted block palettes and scheduled resets
- Custom donor pickaxes and tools
- A multi-world region engine backed by SQLite
- Overlapping regions with deterministic priorities
- Typed protection rules with fail-closed decisions
- Paper adapters for block breaking, placing, and interaction
- JUnit and MockBukkit test coverage
