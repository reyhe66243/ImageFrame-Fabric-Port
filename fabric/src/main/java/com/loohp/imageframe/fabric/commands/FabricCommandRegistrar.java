package com.loohp.imageframe.fabric.commands;

import com.loohp.imageframe.fabric.FabricImageMapManager;
import com.loohp.imageframe.fabric.FabricImageMapManager.FabricImageMap;
import com.loohp.imageframe.fabric.FabricImageMapManager.PlayerSelection;
import com.loohp.imageframe.fabric.language.FabricLanguageManager;
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

    private static String msg(String key, Object... args) {
        return FabricLanguageManager.getInstance().get(key, args);
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("imageframe")
            .executes(context -> {
                context.getSource().sendSuccess(() -> Component.literal("§3ImageFrame §eported to Fabric!\n§6Use §f/imageframe help §6for a list of commands."), false);
                return 1;
            })
            .then(Commands.literal("help")
                .executes(context -> {
                    sendHelp(context.getSource());
                    return 1;
                })
            )
            .then(Commands.literal("reload")
                .executes(context -> {
                    FabricLanguageManager.getInstance().reloadLanguages();
                    context.getSource().sendSuccess(() -> Component.literal("§a" + msg("imageframe.messages.reloaded")), false);
                    return 1;
                })
            )
            .then(Commands.literal("language")
                .then(Commands.argument("lang", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        builder.suggest("en_us");
                        builder.suggest("es_es");
                        builder.suggest("de_de");
                        builder.suggest("fr_fr");
                        builder.suggest("zh_cn");
                        return builder.buildFuture();
                    })
                    .executes(context -> {
                        String lang = StringArgumentType.getString(context, "lang");
                        FabricLanguageManager.getInstance().setServerLanguage(lang);
                        FabricLanguageManager.getInstance().reloadLanguages();
                        context.getSource().sendSuccess(() -> Component.literal("§a[ImageFrame] Language set to: " + lang), false);
                        return 1;
                    })
                )
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
            "§3§l=== ImageFrame Commands ===\n" +
            "§b/imageframe select §7- Select item frames for placement\n" +
            "§b/imageframe create <name> <url> <width> <height> [combined|separated] §7- Create image map\n" +
            "§b/imageframe create <name> <url> selection §7- Create on selected frames\n" +
            "§b/imageframe overlay <name> <url> [selection] §7- Add overlay to maps\n" +
            "§b/imageframe clone <name> <new_name> [selection|combined] §7- Clone an image map\n" +
            "§b/imageframe playback <name> <pause|jumpto> [seconds] §7- Control animation playback\n" +
            "§b/imageframe refresh [name] [new_url] §7- Refresh map from source\n" +
            "§b/imageframe info §7- View details of the map in your hand\n" +
            "§b/imageframe get <name> [selection|combined] §7- Get existing image maps\n" +
            "§b/imageframe delete <name> §7- Delete an image map\n" +
            "§b/imageframe rename <name> <new_name> §7- Rename an image map\n" +
            "§b/imageframe list §7- List all image maps\n" +
            "§b/imageframe language <lang> §7- Change language (en_us, es_es, etc.)\n" +
            "§b/imageframe reload §7- Reload configuration and languages"
        ), false);
    }

    private static CompletableFuture<Suggestions> suggestCreateArgs(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        String[] parts = remaining.isEmpty() ? new String[0] : remaining.split("\\s+");
        boolean endsWithSpace = remaining.endsWith(" ");
        int argIndex = parts.length;
        if (!endsWithSpace && remaining.length() > 0) {
            argIndex--;
        }

        if (argIndex == 0) {
            builder.suggest("<name>");
        } else if (argIndex == 1) {
            builder.suggest(parts[0] + " <url>");
        } else if (argIndex == 2) {
            builder.suggest(parts[0] + " " + parts[1] + " <width>");
            builder.suggest(parts[0] + " " + parts[1] + " selection");
        } else if (argIndex == 3) {
            if (!parts[2].equalsIgnoreCase("selection")) {
                builder.suggest(parts[0] + " " + parts[1] + " " + parts[2] + " <height>");
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
            builder.suggest("<name>");
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
            builder.suggest(parts[0] + " <new_name>");
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
            builder.suggest(parts[0] + " <new_name>");
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
            builder.suggest(parts[0] + " <new_url>");
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
                builder.suggest(parts[0] + " " + parts[1] + " <seconds>");
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
                context.getSource().sendSuccess(() -> Component.literal("§a[ImageFrame] " + msg("imageframe.messages.selection.begin")), false);
            } else {
                context.getSource().sendSuccess(() -> Component.literal("§c[ImageFrame] " + msg("imageframe.messages.selection.clear")), false);
            }
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.no_console")));
            return 0;
        }
    }

    private static int executeCreate(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String fullArgs = StringArgumentType.getString(context, "args").trim();
            String[] args = fullArgs.split("\\s+");

            if (args.length < 3) {
                context.getSource().sendFailure(Component.literal("§cUsage: /imageframe create <name> <url> <width> <height> [combined] or /imageframe create <name> <url> selection"));
                return 0;
            }

            String name = args[0];
            String url = args[1];
            String arg2 = args[2];

            if (arg2.equalsIgnoreCase("selection")) {
                FabricImageMapManager.getInstance().createMap(name, url, 0, 0, player.getUUID(), "floyd-steinberg", false, true, player);
            } else {
                if (args.length < 4) {
                    context.getSource().sendFailure(Component.literal("§cUsage: /imageframe create <name> <url> <width> <height> [combined]"));
                    return 0;
                }
                int width = Integer.parseInt(args[2]);
                int height = Integer.parseInt(args[3]);
                boolean combined = args.length >= 5 && args[4].equalsIgnoreCase("combined");

                FabricImageMapManager.getInstance().createMap(name, url, width, height, player.getUUID(), "floyd-steinberg", combined, false, player);
            }
            return 1;
        } catch (NumberFormatException e) {
            context.getSource().sendFailure(Component.literal("§cWidth and height must be valid numbers."));
            return 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.no_console")));
            return 0;
        }
    }

    private static int executeOverlay(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String fullArgs = StringArgumentType.getString(context, "args").trim();
            String[] args = fullArgs.split("\\s+");

            if (args.length < 2) {
                context.getSource().sendFailure(Component.literal("§cUsage: /imageframe overlay <name> <url> [selection]"));
                return 0;
            }

            String name = args[0];
            String url = args[1];
            boolean selection = args.length >= 3 && args[2].equalsIgnoreCase("selection");

            FabricImageMapManager.getInstance().createMap(name, url, 1, 1, player.getUUID(), "floyd-steinberg", false, selection, player);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.no_console")));
            return 0;
        }
    }

    private static int executeClone(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String fullArgs = StringArgumentType.getString(context, "args").trim();
            String[] args = fullArgs.split("\\s+");

            if (args.length < 2) {
                context.getSource().sendFailure(Component.literal("§cUsage: /imageframe clone <name> <new_name> [selection|combined]"));
                return 0;
            }

            String name = args[0];
            String newName = args[1];
            boolean combined = args.length >= 3 && args[2].equalsIgnoreCase("combined");
            boolean selection = args.length >= 3 && args[2].equalsIgnoreCase("selection");

            FabricImageMap original = FabricImageMapManager.getInstance().getMap(name);
            if (original == null) {
                context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.invalid_image_map")));
                return 0;
            }

            FabricImageMapManager.getInstance().createMap(newName, original.url, original.width, original.height, player.getUUID(), original.dithering, combined || original.isCombined, selection, player);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.no_console")));
            return 0;
        }
    }

    private static int executePlayback(CommandContext<CommandSourceStack> context) {
        String fullArgs = StringArgumentType.getString(context, "args").trim();
        String[] args = fullArgs.split("\\s+");

        if (args.length < 2) {
            context.getSource().sendFailure(Component.literal("§cUsage: /imageframe playback <name> <pause|jumpto> [seconds]"));
            return 0;
        }

        String name = args[0];
        String action = args[1];

        FabricImageMap map = FabricImageMapManager.getInstance().getMap(name);
        if (map == null) {
            context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.invalid_image_map")));
            return 0;
        }

        if (!map.isAnimated) {
            context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.not_an_image_map")));
            return 0;
        }

        if (action.equalsIgnoreCase("pause")) {
            map.isPaused = !map.isPaused;
            context.getSource().sendSuccess(() -> Component.literal("§a[ImageFrame] " + msg("imageframe.messages.image_map_toggle_paused")), false);
        } else if (action.equalsIgnoreCase("jumpto") && args.length >= 3) {
            try {
                double seconds = Double.parseDouble(args[2]);
                int frame = (int) (seconds * 10.0) % map.framesColors.size();
                map.currentFrameIndex = Math.max(0, Math.min(frame, map.framesColors.size() - 1));
                context.getSource().sendSuccess(() -> Component.literal("§a[ImageFrame] " + msg("imageframe.messages.image_map_playback_jump_to", seconds)), false);
            } catch (NumberFormatException e) {
                context.getSource().sendFailure(Component.literal("§cInvalid seconds format."));
            }
        } else {
            context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.invalid_usage")));
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
                ItemStack hand = player.getMainHandItem();
                if (!hand.is(Items.FILLED_MAP)) {
                    context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.not_an_image_map")));
                    return 0;
                }
                MapId mapId = hand.get(DataComponents.MAP_ID);
                if (mapId == null) {
                    context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.invalid_image_map")));
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
                    context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.not_an_image_map")));
                    return 0;
                }
            } else {
                name = args[0];
                FabricImageMap map = FabricImageMapManager.getInstance().getMap(name);
                if (map == null) {
                    context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.invalid_image_map")));
                    return 0;
                }
                newUrl = args.length >= 2 ? args[1] : map.url;
            }

            FabricImageMap map = FabricImageMapManager.getInstance().getMap(name);
            if (map == null) {
                context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.invalid_image_map")));
                return 0;
            }

            if (newUrl != null && !newUrl.trim().isEmpty()) {
                map.url = newUrl;
                FabricImageMapManager.getInstance().saveMapData(map);
            }

            context.getSource().sendSuccess(() -> Component.literal("§e[ImageFrame] " + msg("imageframe.messages.image_map_refreshed")), false);
            FabricImageMapManager.getInstance().createMap(map.name, map.url, map.width, map.height, map.owner, map.dithering, map.isCombined, false, player);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.no_console")));
            return 0;
        }
    }

    private static int executeInfo(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ItemStack hand = player.getMainHandItem();

            if (!hand.is(Items.FILLED_MAP)) {
                context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.not_an_image_map")));
                return 0;
            }

            MapId mapId = hand.get(DataComponents.MAP_ID);
            if (mapId == null) {
                context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.invalid_image_map")));
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
                context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.not_an_image_map")));
                return 0;
            }

            final FabricImageMap finalMap = map;
            context.getSource().sendSuccess(() -> Component.literal(
                "§3§l=== ImageMap Info ===\n" +
                "§6Name: §f" + finalMap.name + "\n" +
                "§6Size: §f" + finalMap.width + "x" + finalMap.height + " §7(" + finalMap.mapIds.size() + " maps)\n" +
                "§6URL: §f" + finalMap.url + "\n" +
                "§6Creator: §f" + finalMap.owner.toString() + "\n" +
                "§6Animated: §f" + (finalMap.isAnimated ? "§aYes" : "§cNo") + "\n" +
                "§6Created: §f" + new Date(finalMap.creationDate).toString()
            ), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.no_console")));
            return 0;
        }
    }

    private static int executeGet(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String fullArgs = StringArgumentType.getString(context, "args").trim();
            String[] args = fullArgs.split("\\s+");

            if (args.length < 1) {
                context.getSource().sendFailure(Component.literal("§cUsage: /imageframe get <name> [selection|combined]"));
                return 0;
            }

            String name = args[0];
            boolean selection = args.length >= 2 && args[1].equalsIgnoreCase("selection");
            boolean combined = args.length >= 2 && args[1].equalsIgnoreCase("combined");

            FabricImageMap map = FabricImageMapManager.getInstance().getMap(name);
            if (map == null) {
                context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.invalid_image_map")));
                return 0;
            }

            if (selection) {
                PlayerSelection sel = FabricImageMapManager.getInstance().getSelection(player.getUUID());
                if (sel == null || sel.corner1 == null || sel.corner2 == null) {
                    context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.selection.no_selection")));
                    return 0;
                }
                List<ItemFrame> frames = FabricImageMapManager.getInstance().getSelectedFrames(player, sel.corner1, sel.corner2);
                for (int i = 0; i < Math.min(frames.size(), map.mapIds.size()); i++) {
                    ItemFrame frame = frames.get(i);
                    ItemStack mapItem = new ItemStack(Items.FILLED_MAP);
                    mapItem.set(DataComponents.MAP_ID, new MapId(map.mapIds.get(i)));
                    frame.setItem(mapItem);
                }
                context.getSource().sendSuccess(() -> Component.literal("§a[ImageFrame] " + msg("imageframe.messages.selection.success", map.width, map.height)), false);
            } else {
                if (combined || map.isCombined) {
                    ItemStack combinedItem = new ItemStack(Items.FILLED_MAP);
                    combinedItem.set(DataComponents.MAP_ID, new MapId(map.mapIds.get(0)));
                    combinedItem.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6ImageMap: " + map.name));
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.literal("§7" + msg("imageframe.settings.combined_map_item.lore.1", map.width, map.height)));
                    lore.add(Component.literal("§8ImageID: " + map.index));
                    lore.add(Component.literal("§8Size: " + map.width + "x" + map.height));
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
                context.getSource().sendSuccess(() -> Component.literal("§a[ImageFrame] ImageMap items added to your inventory."), false);
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.no_console")));
            return 0;
        }
    }

    private static int executeDelete(CommandContext<CommandSourceStack> context) {
        String fullArgs = StringArgumentType.getString(context, "args").trim();
        String[] args = fullArgs.split("\\s+");

        if (args.length < 1) {
            context.getSource().sendFailure(Component.literal("§cUsage: /imageframe delete <name>"));
            return 0;
        }

        String name = args[0];
        FabricImageMap map = FabricImageMapManager.getInstance().getMap(name);
        if (map == null) {
            context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.invalid_image_map")));
            return 0;
        }

        FabricImageMapManager.getInstance().getMaps().remove(name.toLowerCase());
        FabricImageMapManager.getInstance().deleteMapFolder(map);

        context.getSource().sendSuccess(() -> Component.literal("§a[ImageFrame] " + msg("imageframe.messages.image_map_deleted")), false);
        return 1;
    }

    private static int executeRename(CommandContext<CommandSourceStack> context) {
        String fullArgs = StringArgumentType.getString(context, "args").trim();
        String[] args = fullArgs.split("\\s+");

        if (args.length < 2) {
            context.getSource().sendFailure(Component.literal("§cUsage: /imageframe rename <name> <new_name>"));
            return 0;
        }

        String name = args[0];
        String newName = args[1];

        FabricImageMap map = FabricImageMapManager.getInstance().getMap(name);
        if (map == null) {
            context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.invalid_image_map")));
            return 0;
        }

        if (FabricImageMapManager.getInstance().getMap(newName) != null) {
            context.getSource().sendFailure(Component.literal("§c" + msg("imageframe.messages.duplicate_map_name")));
            return 0;
        }

        FabricImageMapManager.getInstance().getMaps().remove(name.toLowerCase());
        map.name = newName;
        FabricImageMapManager.getInstance().getMaps().put(newName.toLowerCase(), map);
        FabricImageMapManager.getInstance().saveMapData(map);

        context.getSource().sendSuccess(() -> Component.literal("§a[ImageFrame] " + msg("imageframe.messages.image_map_renamed")), false);
        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> context) {
        if (FabricImageMapManager.getInstance().getMaps().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("§c[ImageFrame] No image maps found on the server."), false);
            return 1;
        }
        StringBuilder builder = new StringBuilder("§3§l=== " + msg("imageframe.messages.map_lookup") + " ===\n");
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
