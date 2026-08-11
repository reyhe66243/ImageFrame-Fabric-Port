# ImageFrame (Fabric Port)

A high-performance, universal Fabric port of the popular Spigot plugin **[ImageFrame by LoohpJames](https://www.spigotmc.org/resources/106031/)**. Put images and animated GIFs on maps and walls with native high-definition (HD) support!

This project is a hybrid (Universal) mod: it functions completely server-side for vanilla clients, while offering native HD map rendering, instant HD GIF animation synchronization, and inventory item preview tooltips when installed on the client.

---

## Important Disclaimer & Maintenance

*   **Support & Maintenance:** This port was created for a private server with friends. As such, **do not expect regular updates, active maintenance, or guaranteed bugfixes**.
*   **Future Updates:** While support is not guaranteed, critical bugs or compatibility updates might be released if they are needed for our personal server.
*   **Original Creator Invitation:** If the original author (**LoohpJames**) wishes to adapt, use, or merge any part of this Fabric port code into the official project, they are warmly welcomed and encouraged to do so!
*   **Fabric Port Developer:** Developed and optimized for Fabric by **[reyhe66243](https://github.com/reyhe66243)**.
*   **Credits:** Huge credits to [LoohpJames](https://github.com/LOOHP) for the original Spigot plugin design, asset loaders, and architecture.

---

## Universal Architecture & Client Compatibility

### Single Mod for Server & Client
Use **this exact same mod JAR** on both the server and client. Do **not** use the original official client mod, as this port uses an updated, zero-lag networking protocol designed for Minecraft 1.21+.

*   **Vanilla Clients:** Can connect without installing any client-side mod. Maps will display smoothly in standard Minecraft resolution (128x128).
*   **Modded Clients (with this mod):** Automatically perform a background handshake with the server to request native High-Definition (HD) image textures, instant HD GIF animation frame synchronization, and inventory hover tooltips.

---

## Features & Optimizations

1.  **Native High-Definition (HD) Map Renderer:**
    *   Renders static images and multi-frame GIFs in high definition when installed on the client.
    *   Uses thread-safe OpenGL VRAM texture allocation on the client render thread for maximum performance.
2.  **Instant HD GIF Animation Synchronization:**
    *   Multi-frame HD textures are cached in client memory and synchronized using lightweight server timestamp/frame signals.
    *   Eliminates network packet overhead, screen flickering, and re-fetching during GIF playback.
3.  **Dynamic Map Color Engine (Vanilla Fallback):**
    *   Uses a pixel-perfect Mojang color matching system to map image RGB colors to Minecraft's built-in map colors dynamically for vanilla players.
    *   No hardcoded palette files or inaccurate colors. Parity is absolute.
4.  **Network Visibility Cache:**
    *   Optimized for large multiplayer servers! Maps and animation frames are only sent to players holding a map or standing within 32 blocks of an active map frame.
    *   This eliminates network packet overhead, dramatically reduces bandwidth usage, and prevents clients from lagging due to off-screen animation updates.
5.  **Invisible Item Frames:**
    *   Supports placing maps on invisible frames.
    *   Integrates seamlessly with splash potions of invisibility or area effect clouds containing invisibility. Throwing them on item frames turns them invisible, while breaking or modifying them updates the state correctly.
6.  **Inventory Preview Tooltips:**
    *   Includes item hover previews for single maps, image map grids, and paintings directly in player inventory tooltips.

---

## Mod Compatibility & Optimization Notes

### ImmediatelyFast Compatibility Warning
If you use **ImmediatelyFast**, you must disable its map atlas optimization feature:
*   Set `"map_atlas_generation": false` inside your `config/immediatelyfast.json` file.
*   **Reason:** ImmediatelyFast's `map_atlas_generation` forces all map textures into a low-resolution 128x128 atlas, which overrides and crops HD textures. Disabling `map_atlas_generation` allows ImageFrame to render in full HD while ImmediatelyFast continues optimizing 99% of your other graphics performance (fonts, HUD, signs, GUI, buffers).
*   This mod includes automatic detection logic to attempt disabling this setting at launch.

---

## Build & Installation

### Requirements
*   Java 21 or higher
*   Minecraft 1.21+ (Fabric Server & Client)

### Compiling from Source
To compile the Fabric mod jar:
```bash
cd fabric
./gradlew build -x test
```
The compiled jar will be located under `fabric/build/libs/`.

---

## License & Original Work

This project is a derivative work of **ImageFrame** by LoohpJames. In compliance with the original project's license, this port is licensed under the **GNU General Public License v3 (GPLv3)**.

*   You are free to use, modify, and redistribute this software.
*   Any derivative works must also be open-source under the GPLv3 license.
*   The original license text is preserved in the `LICENSE` file.
