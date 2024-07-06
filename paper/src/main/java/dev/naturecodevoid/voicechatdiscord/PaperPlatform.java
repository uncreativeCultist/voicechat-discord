package dev.naturecodevoid.voicechatdiscord;

import com.mojang.brigadier.context.CommandContext;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.ServerPlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

import static dev.naturecodevoid.voicechatdiscord.BukkitHelper.getCraftWorld;
import static dev.naturecodevoid.voicechatdiscord.Core.api;
import static dev.naturecodevoid.voicechatdiscord.PaperPlugin.*;

public class PaperPlatform implements Platform {
    public boolean isValidPlayer(Object sender) {
        if (sender instanceof CommandContext<?> context)
            return commandHelper.bukkitEntity(context) instanceof Player;
        return sender instanceof Player;
    }

    public ServerPlayer commandContextToPlayer(CommandContext<?> context) {
        return api.fromServerPlayer(commandHelper.bukkitEntity(context));
    }

    public @Nullable Position getEntityPosition(ServerLevel level, UUID uuid) {
        if (level.getServerLevel() instanceof World world) {
            // Stupid Bukkit API prevents us from using world.getEntity(uuid) since we aren't on the main thread
            // Using Bukkit.getScheduler().callSyncMethod takes too much time
            // so we are forced to use reflection to get the inner ServerLevel
            // from there we can get Paper's EntityLookup, which allows us to get the entity
            // but wait - we aren't done yet!
            // the NMS Entity getX/Y/Z methods will be obfuscated, which obviously doesn't work well across versions (this is when I wish Paper had support for Fabric's Intermediary mappings, which solves this kind of issue on Fabric)
            // so instead we need to get the Bukkit entity
            // but for some reason getBukkitEntity doesn't exist so instead we cast the CommandSender to an Entity
            // the cast is safe because getBukkitEntity and getBukkitSender return the same thing
            try {
                net.minecraft.server.level.ServerLevel nmsLevel = (net.minecraft.server.level.ServerLevel) getCraftWorld().getMethod("getHandle").invoke(world);
                net.minecraft.world.entity.Entity nmsEntity;
                try {
                    nmsEntity = nmsLevel.moonrise$getEntityLookup().get(uuid);
                } catch (NoSuchMethodError ignored) {
                    nmsEntity = nmsLevel.getEntity(uuid);
                }
                if (nmsEntity == null) return null;
                @SuppressWarnings("DataFlowIssue") Entity entity = (Entity) nmsEntity.getBukkitSender(null);
                return api.createPosition(
                        entity.getLocation().getX(),
                        entity.getLocation().getY(),
                        entity.getLocation().getZ()
                );
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException |
                     ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        if (level.getServerLevel() instanceof net.minecraft.server.level.ServerLevel nmsLevel) {
            net.minecraft.world.entity.Entity nmsEntity = nmsLevel.getEntity(uuid);
            if (nmsEntity == null) return null;
            @SuppressWarnings("DataFlowIssue") Entity entity = (Entity) nmsEntity.getBukkitSender(null);
            return api.createPosition(
                    entity.getLocation().getX(),
                    entity.getLocation().getY(),
                    entity.getLocation().getZ()
            );
        }
        error("level is not World or ServerLevel, it is " + level.getClass().getSimpleName() + ". Please report this on GitHub Issues!");
        return null;
    }

    public boolean isOperator(Object sender) {
        if (sender instanceof CommandContext<?> context)
            return commandHelper.bukkitSender(context).isOp();
        if (sender instanceof Permissible permissible)
            return permissible.isOp();

        return false;
    }

    public boolean hasPermission(Object sender, String permission) {
        if (!(sender instanceof Permissible))
            return false;
        return ((Permissible) sender).hasPermission(permission);
    }

    public void sendMessage(Object sender, String message) {
        if (sender instanceof CommandSender player)
            adventure.sender(player).sendMessage(mm(message));
        else if (sender instanceof CommandContext<?> context) {
            if (commandHelper.bukkitEntity(context) instanceof Player player)
                adventure.player(player).sendMessage(mm(message));
            else
                adventure.sender(commandHelper.bukkitSender(context)).sendMessage(mm(message));
        } else
            warn("Seems like we are trying to send a message to a sender which was not recognized (it is a " + sender.getClass().getSimpleName() + "). Please report this on GitHub issues!");

    }

    public void sendMessage(de.maxhenkel.voicechat.api.Player player, String message) {
        adventure.player((Player) player.getPlayer()).sendMessage(mm(message));
    }

    public String getName(de.maxhenkel.voicechat.api.Player player) {
        return ((Player) player.getPlayer()).getName();
    }

    public String getConfigPath() {
        return "plugins/voicechat-discord/config.yml";
    }

    public Loader getLoader() {
        return Loader.PAPER;
    }

    public void info(String message) {
        LOGGER.info(ansi(mm(message)));
    }

    public void infoRaw(String message) {
        LOGGER.info(message);
    }

    // warn and error will already be colored yellow and red respectfully

    public void warn(String message) {
        LOGGER.warn(message);
    }

    public void error(String message) {
        LOGGER.error(message);
    }
}
