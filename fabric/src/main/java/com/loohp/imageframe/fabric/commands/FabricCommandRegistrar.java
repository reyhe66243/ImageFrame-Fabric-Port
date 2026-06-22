package com.loohp.imageframe.fabric.commands;

import com.loohp.imageframe.fabric.FabricImageMapManager;
import com.loohp.imageframe.fabric.FabricImageMapManager.FabricImageMap;
import com.loohp.imageframe.fabric.FabricImageMapManager.PlayerSelection;
import com.loohp.imageframe.fabric.nms.FabricMapHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FabricCommandRegistrar {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("imageframe")
            .executes(context -> {
                context.getSource().sendSuccess(() -> Component.literal("§3ImageFrame §eported to Fabric by Antigravity!\n§6Use §f/imageframe help §6for list of commands."), false);
                return 1;
            })
            .then(Commands.literal("help")
                .executes(context -> {
                    sendHelp(context.getSource());
                    return 1;
                })
            )
            .then(Commands.literal("select")
                .executes(FabricCommandRegistrar::executeSelect)
            )
            .then(Commands.literal("info")
                .executes(FabricCommandRegistrar::executeInfo)
            )
            .then(Commands.literal("list")
                .executes(FabricCommandRegistrar::executeList)
            )
            .then(Commands.literal("create")
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .suggests(FabricCommandRegistrar::suggestCreateArgs)
                    .executes(FabricCommandRegistrar::executeCreate)
                )
            )
            .then(Commands.literal("overlay")
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .suggests(FabricCommandRegistrar::suggestOverlayArgs)
                    .executes(FabricCommandRegistrar::executeOverlay)
                )
            )
            .then(Commands.literal("clone")
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .suggests(FabricCommandRegistrar::suggestCloneArgs)
                    .executes(FabricCommandRegistrar::executeClone)
                )
            )
            .then(Commands.literal("playback")
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .suggests(FabricCommandRegistrar::suggestPlaybackArgs)
                    .executes(FabricCommandRegistrar::executePlayback)
                )
            )
            .then(Commands.literal("refresh")
                .executes(context -> executeRefresh(context, ""))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .suggests(FabricCommandRegistrar::suggestRefreshArgs)
                    .executes(context -> executeRefresh(context, StringArgumentType.getString(context, "args")))
                )
            )
            .then(Commands.literal("get")
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .suggests(FabricCommandRegistrar::suggestGetArgs)
                    .executes(FabricCommandRegistrar::executeGet)
                )
            )
            .then(Commands.literal("delete")
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .suggests(FabricCommandRegistrar::suggestDeleteArgs)
                    .executes(FabricCommandRegistrar::executeDelete)
                )
            )
            .then(Commands.literal("rename")
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .suggests(FabricCommandRegistrar::suggestRenameArgs)
                    .executes(FabricCommandRegistrar::executeRename)
                )
            )
        );
    }

    private static void sendHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
            "§3§l=== Comandos de ImageFrame ===\n" +
            "§b/imageframe select §7- Seleccionar cuadros de ítems\n" +
            "§b/imageframe create <nombre> <url> <ancho> <alto> [combined] §7- Crear mapa\n" +
            "§b/imageframe create <nombre> <url> selection §7- Crear en selección\n" +
            "§b/imageframe overlay <nombre> <url> [selection] §7- Añadir overlay\n" +
            "§b/imageframe clone <nombre> <nuevo_nombre> [selection|combined] §7- Clonar mapa\n" +
            "§b/imageframe playback <nombre> <pause|jumpto> [segundos] §7- Controlar animaciones\n" +
            "§b/imageframe refresh [nombre] [nueva_url] §7- Refrescar mapa desde origen\n" +
            "§b/imageframe info §7- Ver detalles del mapa en tu mano\n" +
            "§b/imageframe get <nombre> [selection|combined] §7- Obtener mapas existentes\n" +
            "§b/imageframe delete <nombre> §7- Eliminar un mapa\n" +
            "§b/imageframe rename <nombre> <nuevo_nombre> §7- Renombrar mapa\n" +
            "§b/imageframe list §7- Listar todos los mapas de imágenes"
        ), false);
    }

    // Auto-completado Inteligente e Interactivo para URLs sin comillas
    private static CompletableFuture<Suggestions> suggestCreateArgs(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        String[] parts = remaining.isEmpty() ? new String[0] : remaining.split("\\s+");
        boolean endsWithSpace = remaining.endsWith(" ");
        int argIndex = parts.length;
        if (!endsWithSpace && remaining.length() > 0) {
            argIndex--;
        }

        if (argIndex == 0) {
            builder.suggest("<nombre>");
        } else if (argIndex == 1) {
            builder.suggest(parts[0] + " <url>");
        } else if (argIndex == 2) {
            builder.suggest(parts[0] + " " + parts[1] + " <ancho>");
            builder.suggest(parts[0] + " " + parts[1] + " selection");
        } else if (argIndex == 3) {
            if (!parts[2].equalsIgnoreCase("selection")) {
                builder.suggest(parts[0] + " " + parts[1] + " " + parts[2] + " <alto>");
            }
        } else if (argIndex == 4) {
            if (!parts[2].equalsIgnoreCase("selection")) {
                builder.suggest(parts[0] + " " + parts[1] + " " + parts[2] + " " + parts[3] + " combined");
                builder.suggest(parts[0] + " " + parts[1] + " " + parts[2] + " " + parts[3] + " separated");
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestOverlayArgs(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        String[] parts = remaining.isEmpty() ? new String[0] : remaining.split("\\s+");
        boolean endsWithSpace = remaining.endsWith(" ");
        int argIndex = parts.length;
        if (!endsWithSpace && remaining.length() > 0) {
            argIndex--;
        }

        if (argIndex == 0) {
            builder.suggest("<nombre>");
        } else if (argIndex == 1) {
            builder.suggest(parts[0] + " <url>");
        } else if (argIndex == 2) {
            builder.suggest(parts[0] + " " + parts[1] + " selection");
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestCloneArgs(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        String[] parts = remaining.isEmpty() ? new String[0] : remaining.split("\\s+");
        boolean endsWithSpace = remaining.endsWith(" ");
        int argIndex = parts.length;
        if (!endsWithSpace && remaining.length() > 0) {
            argIndex--;
        }

        if (argIndex == 0) {
            String prefix = !endsWithSpace && remaining.length() > 0 ? parts[0].toLowerCase() : "";
            for (String name : FabricImageMapManager.getInstance().getMaps().keySet()) {
                if (name.startsWith(prefix)) {
                    builder.suggest(name);
                }
            }
        } else if (argIndex == 1) {
            builder.suggest(parts[0] + " <nuevo_nombre>");
        } else if (argIndex == 2) {
            builder.suggest(parts[0] + " " + parts[1] + " selection");
            builder.suggest(parts[0] + " " + parts[1] + " combined");
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestGetArgs(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        String[] parts = remaining.isEmpty() ? new String[0] : remaining.split("\\s+");
        boolean endsWithSpace = remaining.endsWith(" ");
        int argIndex = parts.length;
        if (!endsWithSpace && remaining.length() > 0) {
            argIndex--;
        }

        if (argIndex == 0) {
            String prefix = !endsWithSpace && remaining.length() > 0 ? parts[0].toLowerCase() : "";
            for (String name : FabricImageMapManager.getInstance().getMaps().keySet()) {
                if (name.startsWith(prefix)) {
                    builder.suggest(name);
                }
            }
        } else if (argIndex == 1) {
            builder.suggest(parts[0] + " selection");
            builder.suggest(parts[0] + " combined");
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestDeleteArgs(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (String name : FabricImageMapManager.getInstance().getMaps().keySet()) {
            if (name.startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestRenameArgs(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        String[] parts = remaining.isEmpty() ? new String[0] : remaining.split("\\s+");
        boolean endsWithSpace = remaining.endsWith(" ");
        int argIndex = parts.length;
        if (!endsWithSpace && remaining.length() > 0) {
            argIndex--;
        }

        if (argIndex == 0) {
            String prefix = !endsWithSpace && remaining.length() > 0 ? parts[0].toLowerCase() : "";
            for (String name : FabricImageMapManager.getInstance().getMaps().keySet()) {
                if (name.startsWith(prefix)) {
                    builder.suggest(name);
                }
            }
        } else if (argIndex == 1) {
            builder.suggest(parts[0] + " <nuevo_nombre>");
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestRefreshArgs(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        String[] parts = remaining.isEmpty() ? new String[0] : remaining.split("\\s+");
        boolean endsWithSpace = remaining.endsWith(" ");
        int argIndex = parts.length;
        if (!endsWithSpace && remaining.length() > 0) {
            argIndex--;
        }

        if (argIndex == 0) {
            String prefix = !endsWithSpace && remaining.length() > 0 ? parts[0].toLowerCase() : "";
            for (String name : FabricImageMapManager.getInstance().getMaps().keySet()) {
                if (name.startsWith(prefix)) {
                    builder.suggest(name);
                }
            }
        } else if (argIndex == 1) {
            builder.suggest(parts[0] + " <nueva_url>");
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPlaybackArgs(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        String[] parts = remaining.isEmpty() ? new String[0] : remaining.split("\\s+");
        boolean endsWithSpace = remaining.endsWith(" ");
        int argIndex = parts.length;
        if (!endsWithSpace && remaining.length() > 0) {
            argIndex--;
        }

        if (argIndex == 0) {
            String prefix = !endsWithSpace && remaining.length() > 0 ? parts[0].toLowerCase() : "";
            for (String name : FabricImageMapManager.getInstance().getMaps().keySet()) {
                if (name.startsWith(prefix)) {
                    builder.suggest(name);
                }
            }
        } else if (argIndex == 1) {
            builder.suggest(parts[0] + " pause");
            builder.suggest(parts[0] + " jumpto");
        } else if (argIndex == 2) {
            if (parts[1].equalsIgnoreCase("jumpto")) {
                builder.suggest(parts[0] + " " + parts[1] + " <segundos>");
            }
        }
        return builder.buildFuture();
    }

    private static int executeSelect(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            boolean active = FabricImageMapManager.getInstance().isSelectionActive(player.getUUID());
            FabricImageMapManager.getInstance().setSelectionActive(player.getUUID(), !active);
            if (!active) {
                context.getSource().sendSuccess(() -> Component.literal("§a[ImageFrame] Modo selección ACTIVADO. Haz clic derecho en el cuadro superior-izquierdo y luego en el inferior-derecho."), false);
            } else {
                context.getSource().sendSuccess(() -> Component.literal("§c[ImageFrame] Modo selección DESACTIVADO."), false);
            }
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cSolo jugadores pueden ejecutar este comando."));
            return 0;
        }
    }

    private static int executeCreate(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String fullArgs = StringArgumentType.getString(context, "args").trim();
            String[] args = fullArgs.split("\\s+");

            if (args.length < 3) {
                context.getSource().sendFailure(Component.literal("§cUso: /imageframe create <nombre> <url> <ancho> <alto> [combined] o /imageframe create <nombre> <url> selection"));
                return 0;
            }

            String name = args[0];
            String url = args[1];
            String arg2 = args[2];

            if (arg2.equalsIgnoreCase("selection")) {
                FabricImageMapManager.getInstance().createMap(name, url, 0, 0, player.getUUID(), "floyd-steinberg", false, true, player);
            } else {
                if (args.length < 4) {
                    context.getSource().sendFailure(Component.literal("§cUso: /imageframe create <nombre> <url> <ancho> <alto> [combined]"));
                    return 0;
                }
                int width = Integer.parseInt(args[2]);
                int height = Integer.parseInt(args[3]);
                boolean combined = args.length >= 5 && args[4].equalsIgnoreCase("combined");

                FabricImageMapManager.getInstance().createMap(name, url, width, height, player.getUUID(), "floyd-steinberg", combined, false, player);
            }
            return 1;
        } catch (NumberFormatException e) {
            context.getSource().sendFailure(Component.literal("§cEl ancho y alto deben ser números válidos."));
            return 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cSolo jugadores pueden ejecutar este comando."));
            return 0;
        }
    }

    private static int executeOverlay(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String fullArgs = StringArgumentType.getString(context, "args").trim();
            String[] args = fullArgs.split("\\s+");

            if (args.length < 2) {
                context.getSource().sendFailure(Component.literal("§cUso: /imageframe overlay <nombre> <url> [selection]"));
                return 0;
            }

            String name = args[0];
            String url = args[1];
            boolean selection = args.length >= 3 && args[2].equalsIgnoreCase("selection");

            FabricImageMapManager.getInstance().createMap(name, url, 1, 1, player.getUUID(), "floyd-steinberg", false, selection, player);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cSolo jugadores pueden ejecutar este comando."));
            return 0;
        }
    }

    private static int executeClone(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String fullArgs = StringArgumentType.getString(context, "args").trim();
            String[] args = fullArgs.split("\\s+");

            if (args.length < 2) {
                context.getSource().sendFailure(Component.literal("§cUso: /imageframe clone <nombre> <nuevo_nombre> [selection|combined]"));
                return 0;
            }

            String name = args[0];
            String newName = args[1];
            boolean combined = args.length >= 3 && args[2].equalsIgnoreCase("combined");
            boolean selection = args.length >= 3 && args[2].equalsIgnoreCase("selection");

            FabricImageMap original = FabricImageMapManager.getInstance().getMap(name);
            if (original == null) {
                context.getSource().sendFailure(Component.literal("§cNo existe un mapa de imágenes con ese nombre."));
                return 0;
            }

            FabricImageMapManager.getInstance().createMap(newName, original.url, original.width, original.height, player.getUUID(), original.dithering, combined || original.isCombined, selection, player);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cSolo jugadores pueden ejecutar este comando."));
            return 0;
        }
    }

    private static int executePlayback(CommandContext<CommandSourceStack> context) {
        String fullArgs = StringArgumentType.getString(context, "args").trim();
        String[] args = fullArgs.split("\\s+");

        if (args.length < 2) {
            context.getSource().sendFailure(Component.literal("§cUso: /imageframe playback <nombre> <pause|jumpto> [segundos]"));
            return 0;
        }

        String name = args[0];
        String action = args[1];

        FabricImageMap map = FabricImageMapManager.getInstance().getMap(name);
        if (map == null) {
            context.getSource().sendFailure(Component.literal("§cNo existe un mapa de imágenes con ese nombre."));
            return 0;
        }

        if (!map.isAnimated) {
            context.getSource().sendFailure(Component.literal("§cEste mapa de imágenes no está animado."));
            return 0;
        }

        if (action.equalsIgnoreCase("pause")) {
            map.isPaused = !map.isPaused;
            context.getSource().sendSuccess(() -> Component.literal("§a[ImageFrame] Animación " + (map.isPaused ? "PAUSADA" : "REANUDADA") + " para " + map.name), false);
        } else if (action.equalsIgnoreCase("jumpto") && args.length >= 3) {
            try {
                double seconds = Double.parseDouble(args[2]);
                int frame = (int) (seconds * 10.0) % map.framesColors.size(); // Asumiendo aprox. 100ms por frame
                map.currentFrameIndex = Math.max(0, Math.min(frame, map.framesColors.size() - 1));
                context.getSource().sendSuccess(() -> Component.literal("§a[ImageFrame] Animación saltó al frame: " + map.currentFrameIndex), false);
            } catch (NumberFormatException e) {
                context.getSource().sendFailure(Component.literal("§cSegundos inválidos."));
            }
        } else {
            context.getSource().sendFailure(Component.literal("§cAcción inválida."));
        }
        return 1;
    }

    private static int executeRefresh(CommandContext<CommandSourceStack> context, String fullArgs) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String[] args = fullArgs.trim().isEmpty() ? new String[0] : fullArgs.trim().split("\\s+");

            String name = "";
            String newUrl = "";

            if (args.length == 0) {
                // Obtener mapa de la mano
                ItemStack hand = player.getMainHandItem();
                if (!hand.is(Items.FILLED_MAP)) {
                    context.getSource().sendFailure(Component.literal("§cDebes tener un mapa de imágenes en tu mano para refrescarlo."));
                    return 0;
                }
                MapId mapId = hand.get(DataComponents.MAP_ID);
                if (mapId == null) {
                    context.getSource().sendFailure(Component.literal("§cMapa de Minecraft inválido."));
                    return 0;
                }
                for (FabricImageMap m : FabricImageMapManager.getInstance().getMaps().values()) {
                    if (m.mapIds.contains(mapId.id())) {
                        name = m.name;
                        newUrl = m.url;
                        break;
                    }
                }
                if (name.isEmpty()) {
                    context.getSource().sendFailure(Component.literal("§cNo se encontró un mapa de imágenes asociado en tu mano."));
                    return 0;
                }
            } else {
                name = args[0];
                FabricImageMap map = FabricImageMapManager.getInstance().getMap(name);
                if (map == null) {
                    context.getSource().sendFailure(Component.literal("§cNo existe un mapa de imágenes con ese nombre."));
                    return 0;
                }
                newUrl = args.length >= 2 ? args[1] : map.url;
            }

            FabricImageMap map = FabricImageMapManager.getInstance().getMap(name);
            map.url = newUrl;
            FabricImageMapManager.getInstance().saveMapData(map);

            context.getSource().sendSuccess(() -> Component.literal("§e[ImageFrame] Refrescando mapa de imágenes \"" + map.name + "\"..."), false);
            FabricImageMapManager.getInstance().createMap(map.name, map.url, map.width, map.height, map.owner, map.dithering, map.isCombined, false, player);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cSolo jugadores pueden ejecutar este comando."));
            return 0;
        }
    }

    private static int executeInfo(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ItemStack hand = player.getMainHandItem();

            if (!hand.is(Items.FILLED_MAP)) {
                context.getSource().sendFailure(Component.literal("§cDebes sostener un mapa de imágenes en tu mano."));
                return 0;
            }

            MapId mapId = hand.get(DataComponents.MAP_ID);
            if (mapId == null) {
                context.getSource().sendFailure(Component.literal("§cMapa inválido."));
                return 0;
            }

            FabricImageMap map = null;
            for (FabricImageMap m : FabricImageMapManager.getInstance().getMaps().values()) {
                if (m.mapIds.contains(mapId.id())) {
                    map = m;
                    break;
                }
            }

            if (map == null) {
                context.getSource().sendFailure(Component.literal("§cEste mapa de Minecraft no está asociado a ningún mapa de imágenes."));
                return 0;
            }

            final FabricImageMap finalMap = map;
            context.getSource().sendSuccess(() -> Component.literal(
                "§3§l=== Detalles de ImageMap ===\n" +
                "§6Nombre: §f" + finalMap.name + "\n" +
                "§6Dimensiones: §f" + finalMap.width + "x" + finalMap.height + " §7(" + finalMap.mapIds.size() + " submapas)\n" +
                "§6Origen: §f" + finalMap.url + "\n" +
                "§6Dueño (Creador): §f" + finalMap.owner.toString() + "\n" +
                "§6Animado: §f" + (finalMap.isAnimated ? "§aSí" : "§cNo") + "\n" +
                "§6Creado: §f" + new Date(finalMap.creationDate).toString()
            ), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cSolo jugadores pueden ejecutar este comando."));
            return 0;
        }
    }

    private static int executeGet(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String fullArgs = StringArgumentType.getString(context, "args").trim();
            String[] args = fullArgs.split("\\s+");

            if (args.length < 1) {
                context.getSource().sendFailure(Component.literal("§cUso: /imageframe get <nombre> [selection|combined]"));
                return 0;
            }

            String name = args[0];
            boolean selection = args.length >= 2 && args[1].equalsIgnoreCase("selection");
            boolean combined = args.length >= 2 && args[1].equalsIgnoreCase("combined");

            FabricImageMap map = FabricImageMapManager.getInstance().getMap(name);
            if (map == null) {
                context.getSource().sendFailure(Component.literal("§cNo existe un mapa de imágenes con ese nombre."));
                return 0;
            }

            if (selection) {
                // Llenar selección
                PlayerSelection sel = FabricImageMapManager.getInstance().getSelection(player.getUUID());
                if (sel == null || sel.corner1 == null || sel.corner2 == null) {
                    context.getSource().sendFailure(Component.literal("§cNo tienes una selección de cuadros activa."));
                    return 0;
                }
                List<ItemFrame> frames = FabricImageMapManager.getInstance().getSelectedFrames(player, sel.corner1, sel.corner2);
                for (int i = 0; i < Math.min(frames.size(), map.mapIds.size()); i++) {
                    ItemFrame frame = frames.get(i);
                    ItemStack mapItem = new ItemStack(Items.FILLED_MAP);
                    mapItem.set(DataComponents.MAP_ID, new MapId(map.mapIds.get(i)));
                    frame.setItem(mapItem);
                }
                context.getSource().sendSuccess(() -> Component.literal("§a[ImageFrame] ¡Cuadros de la selección llenados con " + map.name + "!"), false);
            } else {
                // Entregar ítems
                if (combined || map.isCombined) {
                    ItemStack combinedItem = new ItemStack(Items.FILLED_MAP);
                    combinedItem.set(DataComponents.MAP_ID, new MapId(map.mapIds.get(0)));
                    combinedItem.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6Combined: " + map.name));
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.literal("§7ImageMap Combined: " + map.name));
                    lore.add(Component.literal("§7Dimensiones: " + map.width + "x" + map.height));
                    combinedItem.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
                    
                    com.loohp.imageframe.objectholders.CombinedMapItemInfo info = new com.loohp.imageframe.objectholders.CombinedMapItemInfo(map.mapIds.get(0));
                    FabricMapHelper.withCombinedMapItemInfo(combinedItem, info);

                    player.getInventory().add(combinedItem);
                } else {
                    for (int id : map.mapIds) {
                        ItemStack mapItem = new ItemStack(Items.FILLED_MAP);
                        mapItem.set(DataComponents.MAP_ID, new MapId(id));
                        player.getInventory().add(mapItem);
                    }
                }
                context.getSource().sendSuccess(() -> Component.literal("§a[ImageFrame] Se han agregado los mapas de \"" + map.name + "\" a tu inventario."), false);
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cSolo jugadores pueden ejecutar este comando."));
            return 0;
        }
    }

    private static int executeDelete(CommandContext<CommandSourceStack> context) {
        String fullArgs = StringArgumentType.getString(context, "args").trim();
        String[] args = fullArgs.split("\\s+");

        if (args.length < 1) {
            context.getSource().sendFailure(Component.literal("§cUso: /imageframe delete <nombre>"));
            return 0;
        }

        String name = args[0];
        FabricImageMap map = FabricImageMapManager.getInstance().getMap(name);
        if (map == null) {
            context.getSource().sendFailure(Component.literal("§cNo existe un mapa de imágenes con ese nombre."));
            return 0;
        }

        FabricImageMapManager.getInstance().getMaps().remove(name.toLowerCase());
        FabricImageMapManager.getInstance().deleteMapFolder(map);

        context.getSource().sendSuccess(() -> Component.literal("§a[ImageFrame] Mapa de imágenes \"" + map.name + "\" eliminado."), false);
        return 1;
    }

    private static int executeRename(CommandContext<CommandSourceStack> context) {
        String fullArgs = StringArgumentType.getString(context, "args").trim();
        String[] args = fullArgs.split("\\s+");

        if (args.length < 2) {
            context.getSource().sendFailure(Component.literal("§cUso: /imageframe rename <nombre> <nuevo_nombre>"));
            return 0;
        }

        String name = args[0];
        String newName = args[1];

        FabricImageMap map = FabricImageMapManager.getInstance().getMap(name);
        if (map == null) {
            context.getSource().sendFailure(Component.literal("§cNo existe un mapa de imágenes con ese nombre."));
            return 0;
        }

        if (FabricImageMapManager.getInstance().getMap(newName) != null) {
            context.getSource().sendFailure(Component.literal("§cYa existe un mapa de imágenes con el nuevo nombre."));
            return 0;
        }

        FabricImageMapManager.getInstance().getMaps().remove(name.toLowerCase());
        map.name = newName;
        FabricImageMapManager.getInstance().getMaps().put(newName.toLowerCase(), map);
        FabricImageMapManager.getInstance().saveMapData(map);

        context.getSource().sendSuccess(() -> Component.literal("§a[ImageFrame] Mapa de imágenes \"" + name + "\" renombrado a \"" + newName + "\"."), false);
        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> context) {
        if (FabricImageMapManager.getInstance().getMaps().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("§c[ImageFrame] No hay ningún mapa de imágenes registrado en el servidor."), false);
            return 1;
        }
        StringBuilder builder = new StringBuilder("§3§l=== Mapas Registrados ===\n");
        for (FabricImageMap map : FabricImageMapManager.getInstance().getMaps().values()) {
            builder.append("§e- ").append(map.name)
                   .append(" §7(").append(map.width).append("x").append(map.height).append(")")
                   .append(map.isAnimated ? " §d[GIF]" : "")
                   .append("\n");
        }
        context.getSource().sendSuccess(() -> Component.literal(builder.toString()), false);
        return 1;
    }
}
