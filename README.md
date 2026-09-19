# Shoreline V2

Experimental Minecraft 1.21.11 Fabric client mod built on lolwut's Shoreline v2 codebase.

## Building

```bash
./gradlew build
```

The built jar will be in `build/libs/`:
- `shoreline-2.4.jar` - Client mod jar (remap & obfuscated)

## Requirements

- Java 21
- Gradle (wrapper included)

## Features & Modules

-  **Combat**: KillAura, AutoCrystal, AutoPot, AnchorAura, Criticals, BowAim, ClickCrystal, SelfBow
-  **Movement**: Flight, Speed, Scaffold, Phase (GrimCC supported), Elytra, Jesus, Velocity (Grim & PhaseOnly), FastSwim, IceSpeed, AntiLevitation, TickShift
-  **Render**: ESP, Chams, Tracers, Nametags, NoRender, Shaders, Skybox, KillEffect, NewChunks, NoMineAnimation
-  **HUD**: ArrayList, Coords, Speed, Ping, FPS, TPS, Potions, Notifications
-  **World**: Nuker, Timer, FastPlace, XRay, AutoTool, SpeedMine, AutoMine (Head, Ceiling, & Feet)
-  **Exploit**: NoFall, AntiHunger, Backtrack, Phase, Reach, PortalGodMode, ClientSpoof, Disabler, PacketCanceller
-  **Misc**: FakePlayer, AutoReconnect, AutoRespawn, ChestStealer, Spammer, AntiAFK, AutoGG, PingStabler, NoLag (Sound lag protection)

