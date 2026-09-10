package com.loohp.imageframe.fabric.permissions;

import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.PermissionLevel;

public class FabricPermissionManager {

    public static final String PREFIX = "imageframe.";

    // Command permissions
    public static final String CREATE = "imageframe.create";
    public static final String CREATE_OTHERS = "imageframe.create.others";
    public static final String OVERLAY = "imageframe.overlay";
    public static final String CLONE = "imageframe.clone";
    public static final String PLAYBACK = "imageframe.playback";
    public static final String SELECT = "imageframe.select";
    public static final String GET = "imageframe.get";
    public static final String GET_OTHERS = "imageframe.get.others";
    public static final String LIST = "imageframe.list";
    public static final String INFO = "imageframe.info";
    public static final String REFRESH = "imageframe.refresh";
    public static final String REFRESH_OTHERS = "imageframe.refresh.others";
    public static final String RENAME = "imageframe.rename";
    public static final String RENAME_OTHERS = "imageframe.rename.others";
    public static final String DELETE = "imageframe.delete";
    public static final String DELETE_OTHERS = "imageframe.delete.others";
    public static final String RELOAD = "imageframe.reload";
    public static final String ADMIN = "imageframe.admin";

    public static Identifier toIdentifier(String permission) {
        if (permission.startsWith(PREFIX)) {
            return Identifier.fromNamespaceAndPath("imageframe", permission.substring(PREFIX.length()));
        }
        if (permission.contains(":")) {
            return Identifier.parse(permission);
        }
        return Identifier.fromNamespaceAndPath("imageframe", permission);
    }

    private static PermissionCheck getVanillaCheck(int defaultOpLevel) {
        return switch (defaultOpLevel) {
            case 0 -> Commands.LEVEL_ALL;
            case 1 -> Commands.LEVEL_MODERATORS;
            case 2 -> Commands.LEVEL_GAMEMASTERS;
            case 3 -> Commands.LEVEL_ADMINS;
            default -> Commands.LEVEL_OWNERS;
        };
    }

    /**
     * Check permission on CommandSourceStack with a fallback vanilla OP level.
     * Compatible with LuckPerms and fabric-permission-api-v1.
     */
    public static boolean hasPermission(CommandSourceStack source, String permission, int defaultOpLevel) {
        Identifier id = toIdentifier(permission);
        PermissionLevel level = PermissionLevel.byId(Math.max(0, Math.min(defaultOpLevel, 4)));
        if (source instanceof PermissionContextOwner owner) {
            return owner.checkPermission(id, level);
        }
        return getVanillaCheck(defaultOpLevel).check(source.permissions());
    }

    /**
     * Check permission on ServerPlayer directly with fallback OP level.
     */
    public static boolean hasPermission(ServerPlayer player, String permission, int defaultOpLevel) {
        Identifier id = toIdentifier(permission);
        PermissionLevel level = PermissionLevel.byId(Math.max(0, Math.min(defaultOpLevel, 4)));
        if (player instanceof PermissionContextOwner owner) {
            return owner.checkPermission(id, level);
        }
        return getVanillaCheck(defaultOpLevel).check(player.permissions());
    }

    /**
     * Check if player has admin bypass or imageframe.admin
     */
    public static boolean isAdmin(CommandSourceStack source) {
        return hasPermission(source, ADMIN, 2);
    }

    public static boolean isAdmin(ServerPlayer player) {
        return hasPermission(player, ADMIN, 2);
    }
}
