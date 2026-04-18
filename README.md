# Zed's Better ModList (ZBetterModList)

Adds filters, sorting, and in-game Steam Workshop actions to Project Zomboid's vanilla **Mod Selector** screen.

Nothing is replaced - the mod only hooks the existing panel and adds quality-of-life features on top.

## Features

- **Filter** the mod list by:
  - `All`
  - `Enabled`
  - `Disabled`
  - `Java` - mods that ship a Java JAR (require [ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy))
  - `New` - mods you haven't seen before
- **Sort** by `Name` or `Date`
- **Unsubscribe** Workshop mods directly from the in-game list (no need to alt-tab to Steam)
- Workshop mods you unsubscribed this session are **hidden without restarting the game**
- For Java mods, the JAR path row shows an extra, color-coded status taken from ZombieBuddy:
  - `loaded` / `blocked` - whether the JAR was actually loaded this run
  - `allow` / `deny` - the decision you gave ZombieBuddy
  - `persisted` / `session` - whether that decision was saved to disk or is session-only

  Example: `media/java/client/ZBetterModList.jar  [loaded, allow, persisted]`

## Requirements

- **Project Zomboid** B42.12 or newer
- [**ZombieBuddy**](https://github.com/zed-0xff/ZombieBuddy) - Java agent framework (required, includes its own one-time install step)
- **zdk** - Zomboid Development Kit (required)

## Installation

1. Subscribe to [**ZombieBuddy**](https://steamcommunity.com/sharedfiles/filedetails/?id=3619862853) on the Steam Workshop and follow its one-time setup instructions (copy the JAR/DLL, set Steam launch options). This step is only needed once - all mods that depend on ZombieBuddy work automatically afterwards.
2. Subscribe to **zdk** on the Steam Workshop.
3. Subscribe to **ZBetterModList**.
4. Enable all three mods in the in-game mod list and launch the game.

Because ZBetterModList ships a Java JAR, the **first** time you launch the game after installing it, ZombieBuddy will show a native approval dialog with the mod id, JAR path, last-modified date, and SHA-256 fingerprint. Click `Yes` and optionally choose to persist the decision; subsequent launches load the JAR silently until the JAR changes on disk.

## FAQ

**Does it work in multiplayer?**
Yes. It only affects the client-side mod selector UI.

**Save compatibility?**
Safe to add or remove on existing saves - the mod does not touch world data.

**Does it modify game files?**
No. All UI changes are applied at runtime via ZombieBuddy patches and zdk hooks.

**I don't see the new filters / the Java status column.**
Make sure all three mods (ZombieBuddy, zdk, ZBetterModList) are enabled and that ZombieBuddy's agent is actually loaded. A loaded agent appends `[ZB]` to the game version string on the main menu.

## Links

- **GitHub**: https://github.com/zed-0xff/ZBetterModList
- **Steam Workshop**: see [steam.txt](steam.txt)
- **ZombieBuddy**: https://github.com/zed-0xff/ZombieBuddy

## Support the Project

If you find this mod useful, consider buying me a coffee:

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/zed_0xff)

## License

Copyright (c) 2025 Andrey "Zed" Zaikin. Released under a permissive open-source license; see `LICENSE.txt` if present in the repository.
