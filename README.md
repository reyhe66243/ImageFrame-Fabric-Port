# ImageFrame (Fabric Port)

A high-performance, server-side-only Fabric port of the popular Spigot plugin **[ImageFrame by LoohpJames](https://www.spigotmc.org/resources/106031/)**. Put images and animated GIFs on maps and walls!

This port has been modernized and optimized for Minecraft 1.21+ (Fabric) while retaining 100% data compatibility with Spigot maps.

---

## ⚠️ Important Disclaimer & Maintenance

*   **Support & Maintenance:** This port was created for a private server with friends. As such, **do not expect regular updates, active maintenance, or guaranteed bugfixes**.
*   **Future Updates:** While support is not guaranteed, critical bugs or compatibility updates might be released if they are needed for our personal server.
*   **Original Creator Invitation:** If the original author (**LoohpJames**) wishes to adapt, use, or merge any part of this Fabric port code into the official project, they are warmly welcomed and encouraged to do so!
*   **Fabric Port Developer:** Developed and optimized for Fabric by **[reyhe66243](https://github.com/reyhe66243)**.
*   **Credits:** Huge credits to [LoohpJames](https://github.com/LOOHP) for the original Spigot plugin design, asset loaders, and architecture.

---

## ✨ Features & Optimizations

This Fabric port is designed to be **incredibly lightweight** and **server-safe**, running entirely on the server-side with no client-side mods required:

1.  **Dynamic Map Color Engine:**
    *   Uses a pixel-perfect Mojang NMS color matching system to map image RGB colors to Minecraft's built-in map colors dynamically.
    *   No hardcoded palette files or inaccurate colors. Parity is absolute.
2.  **Network Visibility Cache:**
    *   Optimized for large multiplayer servers! Maps and animation frames are only sent to players holding a map or standing within 32 blocks of an active map frame.
    *   This eliminates network packet overhead, dramatically reduces bandwidth usage, and prevents clients from lagging due to off-screen animation updates.
3.  **Invisible Item Frames:**
    *   Supports placing maps on invisible frames.
    *   Integrates seamlessly with splash potions of invisibility or area effect clouds containing invisibility. Throwing them on item frames turns them invisible, while breaking or modifying them updates the state correctly.
4.  **No Client-Side Mod Required:**
    *   Works entirely server-side! Vanilla clients can connect and enjoy the custom map paintings.

---

## 🛠️ Build & Installation

### Requirements
*   Java 21 or higher
*   Minecraft 1.21+ (Fabric Server)

### Compiling from Source
To compile the Fabric mod jar:
```bash
cd fabric
./gradlew build -x test
```
The compiled jar will be located under `fabric/build/libs/`.

---

## 📄 License & Original Work

This project is a derivative work of **ImageFrame** by LoohpJames. In compliance with the original project's license, this port is licensed under the **GNU General Public License v3 (GPLv3)**.

*   You are free to use, modify, and redistribute this software.
*   Any derivative works must also be open-source under the GPLv3 license.
*   The original license text is preserved in the `LICENSE` file.
