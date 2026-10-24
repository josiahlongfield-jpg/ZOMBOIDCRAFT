# ZomboidCraft

A Fabric mod for Minecraft Java 26.3 that makes survival play like a first-person Project Zomboid.

## Stage 1 (this build)

**The dead**
- Zombies, husks and zombie villagers shamble slowly. About one in twenty runs.
- They hunt by sound. Walking (5 blocks), sprinting (14), fighting (10) and breaking blocks (12) draw every idle zombie in earshot. Sneaking is silent.
- They don't burn in daylight, there are no baby zombies, and they break down doors on Hard.
- Small groups keep spawning out of sight in the overworld, day and night, up to 24 around each player.

**Survival**
- **Thirst** drains over about 24 minutes, faster when sprinting. Water bottles, milk, stews and fruit restore it. At zero you take damage.
- **Tiredness** builds over about 30 minutes awake. A full night's sleep resets it.
- **Bleeding**: zombie hits can scratch or bite, opening wounds that drain health until they clot or you use a **bandage** (1 wool = 3 bandages, or paper + string).
- **Infection**: a bite always infects; a scratch sometimes does. There's no cure. Symptoms appear partway in, and after about 40 minutes you die and rise as a zombie with your name.
- Status labels show in the top-right corner, Zomboid style.

**Testing**: ops can use `/zc stats`, `/zc infect`, `/zc cure`, `/zc reset`, `/zc thirst <0-100>`, `/zc fatigue <0-100>` and `/zc wounds <n>`.

## Install

Put the jar in your Modrinth profile's `mods` folder next to Fabric API. For multiplayer through e4mc, every player needs the mod installed.

## Build

GitHub Actions builds every push and uploads the jar as an artifact. Locally: `./gradlew build` with Java 25.
