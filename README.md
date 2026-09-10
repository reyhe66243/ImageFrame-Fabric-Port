### ImageFrame (Fabric Port)

A high-performance, universal Fabric port of the popular Spigot plugin **[ImageFrame by LoohpJames](https://www.spigotmc.org/resources/106031/)**. Put images and animated GIFs on maps and walls with native high-definition (HD) support!

This project is a hybrid (Universal) mod: it functions completely server-side for vanilla clients, while offering native HD map rendering, instant HD GIF animation synchronization, and inventory item preview tooltips when installed on the client.

![GIF placement](https://cdn.eyhe.org/assets/d0c9782c20b378043ec48e9941c6c6f31369eb84.gif)
![IMAGE placement](https://cdn.eyhe.org/assets/d0edd0121bab7e180a5ba1db7d0d2497529150ef.gif)
### Important Disclaimer & Maintenance

*   **Support & Maintenance:** This port was created for a private server with friends. As such, **do not expect regular updates, active maintenance, or guaranteed bugfixes**.
*   **Future Updates:** While support is not guaranteed, critical bugs or compatibility updates might be released if they are needed for our personal server.
*   **Original Creator Invitation:** If the original author (**LoohpJames**) wishes to adapt, use, or merge any part of this Fabric port code into the official project, they are warmly welcomed and encouraged to do so!
*   **Fabric Port Developer:** Developed and optimized for Fabric by **[reyhe66243](https://github.com/reyhe66243)**.
*   **Credits:** Huge credits to [LoohpJames](https://github.com/LOOHP) for the original Spigot plugin design, asset loaders, and architecture.

---



### Features & Optimizations

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

### Universal Architecture & Client Compatibility

Use **this exact same mod JAR** on both the server and client. Do **not** use the original official client mod, as this port uses an updated, zero-lag networking protocol designed for Minecraft 1.21+.

*   **Vanilla Clients:** Can connect without installing any client-side mod. Maps will display smoothly in standard Minecraft resolution (128x128).
*   **Modded Clients (with this mod):** Automatically perform a background handshake with the server to request native High-Definition (HD) image textures, instant HD GIF animation frame synchronization, and inventory hover tooltips.


---

### Mod Compatibility & Optimization Notes

If you use **ImmediatelyFast**, you must disable its map atlas optimization feature:
*   Set `"map_atlas_generation": false` inside your `config/immediatelyfast.json` file.
*   **Reason:** ImmediatelyFast's `map_atlas_generation` forces all map textures into a low-resolution 128x128 atlas, which overrides and crops HD textures. Disabling `map_atlas_generation` allows ImageFrame to render in full HD while ImmediatelyFast continues optimizing 99% of your other graphics performance (fonts, HUD, signs, GUI, buffers).
*   This mod includes automatic detection logic to attempt disabling this setting at launch.
---

### Configuration & Permissions

ImageFrame on Fabric features a streamlined, fully functional `config/ImageFrame/config.yml` and 1:1 permission parity with the original Spigot plugin.

#### Key Configuration Options
* **`RequireEmptyMaps`:** Require survival players to hold empty maps (`width * height`) to create or retrieve image maps.
* **`MaxSize`:** Limit map dimensions (in blocks) to prevent server lag.
* **`PlayerCreationLimit`:** Limit the maximum number of maps each player or group can create.
* **`RestrictImageUrl`:** Domain whitelist (e.g. Imgur, Discord CDN) to restrict where players can download images from.
* **`CombinedByDefault`:** Automatically give combined map items instead of separate map tiles when running `/imageframe get <name>`.
* **`InvisibleFrame`:** Configure survival potion splash conversions for invisible item frames.

#### Permissions & LuckPerms
Compatible with **LuckPerms** via `fabric-permissions-api-v1` and native Minecraft OP levels (user commands default to Level 0 / all players):

| Command / Feature | Permission Node | Default Access |
| :--- | :--- | :--- |
| `/imageframe create` | `imageframe.create` | All players |
| `/imageframe get` | `imageframe.get` | All players (own maps) |
| Manage others' maps | `imageframe.get.others`, `imageframe.delete.others` | OP Level 2 |
| `/imageframe delete` | `imageframe.delete` | All players (own maps) |
| `/imageframe rename` | `imageframe.rename` | All players (own maps) |
| `/imageframe refresh` | `imageframe.refresh` | All players (own maps) |
| `/imageframe playback` | `imageframe.playback` | All players |
| `/imageframe select` | `imageframe.select` | All players |
| `/imageframe list` | `imageframe.list` | All players (shows own maps) |
| View all server maps | `imageframe.list.others` | OP Level 2 |
| Map creation limit tier | `imageframe.createlimit.<group>` | Custom tiers in config |
| Unlimited map creation | `imageframe.createlimit.unlimited` | OP Level 2 |
| Full admin bypass | `imageframe.admin` | OP Level 2 |
| `/imageframe reload` | `imageframe.reload` | OP Level 2 |
| `/imageframe language` | `imageframe.admin` | OP Level 2 |
