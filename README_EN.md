# ECO AE Extension (GTNH)

**ECO AE Extension** is a GTNH (Minecraft 1.7.10) addon that brings the **E-Storage Array** and
the **E-Calculator (Extensible Computation Subsystem)** to GT5-Unofficial multiblocks with deep
Applied Energistics 2 (Unofficial) integration.

| | |
|---|---|
| Mod ID | `ecoaegtnh` |
| Dependencies | GregTech 5-Unofficial (`gregtech`), Applied Energistics 2 Unofficial (`appliedenergistics2`), StructureLib (`structurelib`) |
| Branch `master` | GTNH **2.9.0-beta-2** (AE2U rv3-beta-1000 / GT5U 5.09.54.20) |
| Branch `290b1` | GTNH **2.9.0-beta-1** (AE2U rv3-beta-977 / GT5U 5.09.52.594) |
| Branch `284` | GTNH **2.8.4** (AE2U rv3-beta-695 / GT5U 5.09.51.482) |
| Language | English / 简体中文（[README.md](README.md)） |

> ⚠️ **Branch selection**: the three branches target three different GTNH pack versions and are
> not interchangeable (different AE2U/GT5U versions; some recipe materials and fluid registry IDs
> differ). Pick the branch matching your pack. The `290b1` branch is a beta-1-specific build:
> that pack's bartworks has no Superdense-plate material and different fluid registry IDs, so the
> recipes are adapted for the beta-1 environment.

---

## 🧮 E-Calculator Array (Extensible Computation Subsystem)

A GT multiblock controller **powered entirely by the ME network (no GT energy hatches), no
maintenance required**. The structure is a head + 1–12 extension segments: each segment has
2 Cell Drives + 1 Transmitter Bus on the front side and 2 Parallel-Core Drives + 1 Thread-Core
Drive on the back side. Hold a stack of N (1–12) controller items to project/build an N-segment
structure.

### Parts & Items

| Category | Items |
|---|---|
| Structure parts | Computation casing, Cell Drive, Parallel-Core Drive, Thread-Core Drive, ME Matrix Channel, Superconducting Transmitter Bus |
| **Flash Cell Arrays** (byte pool) | 256k / 1024k / 4096k / 16M / 64M / 256M / 1024M / 4096M / 16384M / **Singularity** (infinite) — inserted into Cell Drives to add bytes to the pool |
| **Parallel Cores** (parallelism) | 1 / 4 / 16 / 64 / 256 / 1024 / 4096 / 16384 / 65536 — inserted into Parallel-Core Drives |
| **Thread Cores** (thread slots) | Normal 1 / 4 / 16 / 32 / 64; Hyper 4 / 8 / 16 (hyper slots add +10% virtual capacity, free in overclock mode) — inserted into Thread-Core Drives |

### vCPU System (core mechanic)

- **Virtual crafting CPUs**: jobs in the AE grid are executed by virtual CPUs provided by the
  array — no physical crafting blocks are occupied. Shown in the AE terminal as
  `ECO vCPU` / `ECO vCPU #n`.
- **Thread-slot assignment**: new jobs take thread slots in a fixed priority — built-in normal
  thread slots first, then external normal thread slots, then built-in hyper slots, finally
  external hyper slots.
- **Byte-pool accounting**: job bytes are charged against the shared pool in real time
  (available = total − Σ in-flight task bytes); new jobs pause below 10% of the pool (5% in
  overclock mode); the hyper +10% virtual reserve never overdraws the pool.
- **Job merging**: repeated requests for the same output merge into the running vCPU instead of
  occupying another thread slot.
- **Upgrade-tree gates**: cell chain / parallel chain / thread chain — inserting cells and cores
  requires unlocking the corresponding node.
- **Parallelism**: Parallel Cores provide multiple operations per tick (accelerator semantics).

### Failure Safety (materials are never swallowed)

| Scenario | Behaviour |
|---|---|
| ME network down | Jobs **freeze (not cancelled)**, materials stay safe, and resume automatically on reconnect |
| Structure invalid (unformed) | In-flight job data is **kept**; re-forming the machine resumes them automatically |
| Machine removed (block broken) | Jobs are cancelled and **refunded to the grid**; if the refund cannot complete (network unreachable) the materials enter "orphan" protection and refund automatically on reconnect or after rebuilding the machine |
| Server stop / save unload | In-flight jobs are cancelled and refunded automatically |
| Thread-core drive block removed | In-flight jobs are adopted as orphans, continue to completion or refund later |

### GUI

Structure state, ME channel state, segment count, bytes (used / available / total).

---

## 🗄️ E-Storage Array

A GT multiblock controller: **head + 1–12 drive columns** (extendable). Parts: storage array
casing, drive bay, capacitance (A/B/C), vent, ME bus. Powered by the ME network, no maintenance.

### Storage Cells (27 + 2 special)

| Type | Capacities |
|---|---|
| Item cells | 256k / 1024k / 4096k / 16M / 64M / 256M / 1024M / 4096M / 16384M / **UNIVERSE** |
| Fluid cells | same 10 tiers + **Infinite Water** |
| Essentia cells (requires ThaumicEnergistics) | same 10 tiers + **Arcane** |

Matching ME Storage Components (crafting materials) and Storage Housings **Mk.I / Mk.II /
Mk.III** (item / fluid / essentia, the k / M / big-M capacity bands) are craftable as well.

### Upgrade Tree (milestone lines)

Three independent upgrade lines (item / fluid / essentia): inserting a cell requires the
corresponding chain node; drive-column length and capacity tiers progress through the tree.

### GUI

Structure state, drive bays, columns, total cells (item/fluid/essentia breakdown), type and
byte statistics (20-tick cached, smooth with many drives).

---

## Building

Requirements: JDK 8 (compile toolchain) + JDK 21 (Gradle daemon); GTNH Gradle template
(RetroFuturaGradle).

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot"  # daemon needs 21+
.\gradlew.bat build
# Artifact: build/libs/ecoaegtnh.jar
```

For the 2.8.4 version check out the `284` branch (its build.gradle pins the 695 dependency set).

## Installation

1. Put `build/libs/ecoaegtnh.jar` into the `mods/` folder of both the server and the client.
2. Dependencies: GT5U, AE2U, StructureLib (versions per branch).
3. Client and server must use the same jar version (identical SHA256).

## License

[GNU Lesser General Public License v3.0](LICENSE)

---

*This repository publishes the source code only; design documents and the implementation log
stay private.*

## Acknowledgements

This project is based on the design concept of NovaEngineering-ECOAEExtension
(sddsd2332, Kasumi_Nova, WI_8614_ice) and was ported and developed with AI assistance:
dual-version adaptation for GTNH 2.9.0-beta-2 (AE2U rv3-beta-1000) and GTNH 2.8.4
(AE2U rv3-beta-695), including the vCPU crafting system, the storage array, failure-safety
handling and audit fixes. Issues and PRs are welcome.
