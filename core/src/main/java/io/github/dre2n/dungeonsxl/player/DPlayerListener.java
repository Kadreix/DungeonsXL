/*
 * Copyright (C) 2012-2017 Frank Baumann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.github.dre2n.dungeonsxl.player;

import io.github.dre2n.commons.util.messageutil.MessageUtil;
import io.github.dre2n.dungeonsxl.DungeonsXL;
import io.github.dre2n.dungeonsxl.config.DMessages;
import io.github.dre2n.dungeonsxl.config.MainConfig;
import io.github.dre2n.dungeonsxl.game.Game;
import io.github.dre2n.dungeonsxl.global.DPortal;
import io.github.dre2n.dungeonsxl.global.GlobalProtection;
import io.github.dre2n.dungeonsxl.mob.DMob;
import io.github.dre2n.dungeonsxl.trigger.UseItemTrigger;
import io.github.dre2n.dungeonsxl.util.ParsingUtil;
import io.github.dre2n.dungeonsxl.world.DEditWorld;
import io.github.dre2n.dungeonsxl.world.DGameWorld;
import io.github.dre2n.dungeonsxl.world.block.LockedDoor;
import java.util.ArrayList;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/**
 * @author Daniel Saukel, Frank Baumann, Milan Albrecht
 */
public class DPlayerListener implements Listener {

    DungeonsXL plugin;
    DPlayers dPlayers;
    MainConfig config = DungeonsXL.getMainConfig();

    public static final String ALL = "@all ";

    public DPlayerListener(DPlayers dPlayers) {
        this.plugin = DungeonsXL.getInstance();
        this.dPlayers = dPlayers;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        World world = event.getEntity().getWorld();
        DGameWorld gameWorld = DGameWorld.getByWorld(world);

        if (gameWorld == null) {
            return;
        }

        // Deny all Damage in Lobby
        if (!gameWorld.isPlaying()) {
            event.setCancelled(true);
        }

        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        boolean dead = ((LivingEntity) event.getEntity()).getHealth() - event.getFinalDamage() <= 0;
        if (dead && DMob.getByEntity(event.getEntity()) != null) {
            String killer = null;

            if (event instanceof EntityDamageByEntityEvent) {
                Entity damager = ((EntityDamageByEntityEvent) event).getDamager();

                if (damager instanceof Projectile) {
                    if (((Projectile) damager).getShooter() instanceof Player) {
                        damager = (Player) ((Projectile) damager).getShooter();
                    }
                }

                if (damager instanceof Player) {
                    killer = damager.getName();
                }
            }

            gameWorld.getGame().addKill(killer);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        World world = event.getEntity().getWorld();
        DGameWorld gameWorld = DGameWorld.getByWorld(world);

        if (gameWorld == null) {
            return;
        }

        Game game = gameWorld.getGame();

        if (game == null) {
            return;
        }

        if (!game.hasStarted()) {
            return;
        }

        boolean pvp = game.getRules().isPlayerVersusPlayer();
        boolean friendlyFire = game.getRules().isFriendlyFire();

        Entity attackerEntity = event.getDamager();
        Entity attackedEntity = event.getEntity();

        if (attackerEntity instanceof Projectile) {
            attackerEntity = (Entity) ((Projectile) attackerEntity).getShooter();
        }

        Player attackerPlayer = null;
        Player attackedPlayer = null;

        DGroup attackerDGroup = null;
        DGroup attackedDGroup = null;

        if (!(attackerEntity instanceof LivingEntity) || !(attackedEntity instanceof LivingEntity)) {
            return;
        }

        if (attackerEntity instanceof Player && attackedEntity instanceof Player) {
            attackerPlayer = (Player) attackerEntity;
            attackedPlayer = (Player) attackedEntity;

            attackerDGroup = DGroup.getByPlayer(attackerPlayer);
            attackedDGroup = DGroup.getByPlayer(attackedPlayer);

            if (!pvp) {
                event.setCancelled(true);
                return;
            }

            if (attackerDGroup != null && attackedDGroup != null) {
                if (!friendlyFire && attackerDGroup.equals(attackedDGroup)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // Check Dogs
        if (attackerEntity instanceof Player || attackedEntity instanceof Player) {
            for (DGamePlayer dPlayer : DGamePlayer.getByWorld(gameWorld.getWorld())) {
                if (dPlayer.getWolf() != null) {
                    if (attackerEntity == dPlayer.getWolf() || attackedEntity == dPlayer.getWolf()) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        for (DGamePlayer dPlayer : DGamePlayer.getByWorld(gameWorld.getWorld())) {
            if (dPlayer.getWolf() != null) {
                if (attackerEntity instanceof Player || attackedEntity instanceof Player) {
                    if (attackerEntity == dPlayer.getWolf() || attackedEntity == dPlayer.getWolf()) {
                        event.setCancelled(true);
                        return;
                    }

                } else if (attackerEntity == dPlayer.getWolf() || attackedEntity == dPlayer.getWolf()) {
                    event.setCancelled(false);
                    return;
                }
            }
        }
    }

    // Players don't need to eat in lobbies
    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        World world = event.getEntity().getWorld();
        DGameWorld gameWorld = DGameWorld.getByWorld(world);
        if (gameWorld != null) {
            if (!gameWorld.isPlaying()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (isCitizensNPC(player)) {
            return;
        }
        DGlobalPlayer dPlayer = dPlayers.getByPlayer(player);
        if (dPlayer == null) {
            return;
        }
        if (!dPlayer.isInGroupChat()) {
            return;
        }
        DGroup dGroup = DGroup.getByPlayer(player);
        if (dGroup == null) {
            return;
        }

        boolean game = event.getMessage().startsWith(ALL) && dPlayer instanceof DInstancePlayer;
        event.setCancelled(true);
        if (game) {
            ((DInstancePlayer) dPlayer).chat(event.getMessage().substring(ALL.length()));
        } else {
            dGroup.sendMessage(ParsingUtil.replaceChatPlaceholders(config.getChatFormatGroup(), dPlayer) + event.getMessage());
        }
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (isCitizensNPC(player)) {
            return;
        }

        if (DPermissions.hasPermission(player, DPermissions.BYPASS)) {
            return;
        }

        if (!(dPlayers.getByPlayer(player) instanceof DInstancePlayer)) {
            return;
        }
        DInstancePlayer dPlayer = (DInstancePlayer) dPlayers.getByPlayer(player);

        String command = event.getMessage().toLowerCase();
        ArrayList<String> commandWhitelist = new ArrayList<>();

        Game game = Game.getByWorld(dPlayer.getWorld());

        if (dPlayer instanceof DEditPlayer) {
            if (DPermissions.hasPermission(player, DPermissions.CMD_EDIT)) {
                return;

            } else {
                commandWhitelist.addAll(config.getEditCommandWhitelist());
            }

        } else if (game != null) {
            if (game.getRules() != null) {
                commandWhitelist.addAll(game.getRules().getGameCommandWhitelist());
            }
        }

        commandWhitelist.add("dungeonsxl");
        commandWhitelist.add("dungeon");
        commandWhitelist.add("dxl");

        event.setCancelled(true);

        for (String whitelistEntry : commandWhitelist) {
            if (command.equals('/' + whitelistEntry.toLowerCase()) || command.startsWith('/' + whitelistEntry.toLowerCase() + ' ')) {
                event.setCancelled(false);
            }
        }

        if (event.isCancelled()) {
            MessageUtil.sendMessage(player, DMessages.ERROR_CMD.getMessage());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (isCitizensNPC(player)) {
            return;
        }
        DGamePlayer dPlayer = DGamePlayer.getByPlayer(player);
        if (dPlayer == null) {
            return;
        }
        dPlayer.onDeath(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (isCitizensNPC(player)) {
            return;
        }

        DGlobalPlayer dPlayer = dPlayers.getByPlayer(player);
        if (dPlayer == null) {
            return;
        }

        if (dPlayer instanceof DEditPlayer && !config.getDropItems() && !DPermissions.hasPermission(player, DPermissions.INSECURE)) {
            event.setCancelled(true);
        }

        if (!(dPlayer instanceof DGamePlayer)) {
            return;
        }

        DGamePlayer gamePlayer = (DGamePlayer) dPlayer;

        DGroup dGroup = DGroup.getByPlayer(player);
        if (dGroup == null) {
            return;
        }

        if (!dGroup.isPlaying()) {
            event.setCancelled(true);
            return;
        }

        if (!gamePlayer.isReady()) {
            event.setCancelled(true);
            return;
        }

        Game game = Game.getByWorld(gamePlayer.getWorld());

        for (ItemStack item : game.getRules().getSecureObjects()) {
            if (event.getItemDrop().getItemStack().isSimilar(item)) {
                event.setCancelled(true);
                MessageUtil.sendMessage(player, DMessages.ERROR_DROP.getMessage());
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (dPlayers.checkPlayer(player)) {
            return;
        }

        DGlobalPlayer dPlayer = new DGlobalPlayer(player);
        if (player.hasPlayedBefore()) {
            return;
        }

        if (!config.isTutorialActivated()) {
            return;
        }

        if (DGamePlayer.getByPlayer(player) != null) {
            return;
        }

        dPlayer.startTutorial();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        MainConfig config = DungeonsXL.getMainConfig();

        if (!config.isTutorialActivated()) {
            return;
        }

        if (DGamePlayer.getByPlayer(player) != null) {
            return;
        }

        if (plugin.getPermissionProvider() == null || !plugin.getPermissionProvider().hasGroupSupport()) {
            return;
        }

        if ((config.getTutorialDungeon() == null || config.getTutorialStartGroup() == null || config.getTutorialEndGroup() == null)) {
            return;
        }

        for (String group : plugin.getPermissionProvider().getPlayerGroups(player)) {
            if (!config.getTutorialStartGroup().equalsIgnoreCase(group)) {
                continue;
            }

            if (DungeonsXL.getDWorlds().getGameWorlds().size() >= config.getMaxInstances()) {
                event.setResult(PlayerLoginEvent.Result.KICK_FULL);
                event.setKickMessage(DMessages.ERROR_TOO_MANY_TUTORIALS.getMessage());
            }
            return;
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (isCitizensNPC(player)) {
            return;
        }
        DGameWorld gameWorld = DGameWorld.getByWorld(player.getWorld());
        DGamePlayer gamePlayer = DGamePlayer.getByPlayer(player);
        if (gameWorld != null && gamePlayer != null && gamePlayer.isStealing()) {
            DGroup group = gamePlayer.getDGroup();
            Location startLocation = gameWorld.getStartLocation(group);

            if (startLocation.distance(player.getLocation()) < 3) {
                gamePlayer.captureFlag();
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        DGlobalPlayer dPlayer = dPlayers.getByPlayer(player);
        DGroup dGroup = DGroup.getByPlayer(player);
        Game game = Game.getByWorld(player.getWorld());

        if (!(dPlayer instanceof DInstancePlayer)) {
            dPlayers.removePlayer(dPlayer);
            if (dGroup != null) {
                dGroup.removePlayer(player);
            }

        } else if (game != null) {
            int timeUntilKickOfflinePlayer = game.getRules().getTimeUntilKickOfflinePlayer();

            if (timeUntilKickOfflinePlayer == 0) {
                ((DGamePlayer) dPlayer).leave();

            } else if (timeUntilKickOfflinePlayer > 0) {
                dGroup.sendMessage(DMessages.PLAYER_OFFLINE.getMessage(dPlayer.getName(), String.valueOf(timeUntilKickOfflinePlayer)), player);
                ((DGamePlayer) dPlayer).setOfflineTime(System.currentTimeMillis() + timeUntilKickOfflinePlayer * 1000);

            } else {
                dGroup.sendMessage(DMessages.PLAYER_OFFLINE_NEVER.getMessage(dPlayer.getName()), player);
            }

        } else if (dPlayer instanceof DEditPlayer) {
            ((DEditPlayer) dPlayer).leave();
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (isCitizensNPC(player)) {
            return;
        }
        DungeonsXL.getDPlayers().getByPlayer(player).applyRespawnInventory();

        DGlobalPlayer dPlayer = DGamePlayer.getByPlayer(player);
        if (dPlayer == null) {
            return;
        }

        if (dPlayer instanceof DEditPlayer) {
            DEditWorld editWorld = DEditWorld.getByWorld(((DEditPlayer) dPlayer).getWorld());
            if (editWorld == null) {
                return;
            }

            if (editWorld.getLobbyLocation() == null) {
                event.setRespawnLocation(editWorld.getWorld().getSpawnLocation());

            } else {
                event.setRespawnLocation(editWorld.getLobbyLocation());
            }

        } else if (dPlayer instanceof DGamePlayer) {
            DGamePlayer gamePlayer = (DGamePlayer) dPlayer;

            DGameWorld gameWorld = DGameWorld.getByWorld(gamePlayer.getWorld());

            if (gameWorld == null) {
                return;
            }

            DGroup dGroup = DGroup.getByPlayer(dPlayer.getPlayer());

            Location respawn = gamePlayer.getCheckpoint();

            if (respawn == null) {
                respawn = dGroup.getGameWorld().getStartLocation(dGroup);
            }

            // Because some plugins set another respawn point, DXL teleports a few ticks later.
            new RespawnTask(player, respawn).runTaskLater(plugin, 10);

            // Don't forget Doge!
            if (gamePlayer.getWolf() != null) {
                gamePlayer.getWolf().teleport(respawn);
            }
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (isCitizensNPC(player)) {
            return;
        }
        DGamePlayer dPlayer = DGamePlayer.getByPlayer(player);

        if (dPlayer == null) {
            return;
        }

        if (dPlayer.getWorld() == event.getTo().getWorld()) {
            return;
        }

        if (!DPermissions.hasPermission(player, DPermissions.BYPASS)) {
            event.setCancelled(true);
        }
    }

    public static boolean isCitizensNPC(LivingEntity entity) {
        return entity.hasMetadata("NPC");
    }

    /* SUBJECT TO CHANGE */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (isCitizensNPC(player)) {
            return;
        }
        DGlobalPlayer dGlobalPlayer = dPlayers.getByPlayer(player);
        Block clickedBlock = event.getClickedBlock();
        DGameWorld dGameWorld = DGameWorld.getByWorld(player.getWorld());
        if (clickedBlock != null) {
            // Block Enderchests
            if (dGameWorld != null || DEditWorld.getByWorld(player.getWorld()) != null) {
                if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
                    if (clickedBlock.getType() == Material.ENDER_CHEST) {
                        if (!DPermissions.hasPermission(player, DPermissions.BYPASS)) {
                            MessageUtil.sendMessage(player, DMessages.ERROR_ENDERCHEST.getMessage());
                            event.setCancelled(true);
                        }

                    } else if (clickedBlock.getType() == Material.BED_BLOCK) {
                        if (!DPermissions.hasPermission(player, DPermissions.BYPASS)) {
                            MessageUtil.sendMessage(player, DMessages.ERROR_BED.getMessage());
                            event.setCancelled(true);
                        }
                    }
                }
            }

            // Block Dispensers
            if (dGameWorld != null) {
                if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
                    if (clickedBlock.getType() == Material.DISPENSER) {
                        if (!DPermissions.hasPermission(player, DPermissions.BYPASS)) {
                            MessageUtil.sendMessage(player, DMessages.ERROR_DISPENSER.getMessage());
                            event.setCancelled(true);
                        }
                    }
                }

                for (LockedDoor door : dGameWorld.getLockedDoors()) {
                    if (clickedBlock.equals(door.getBlock()) || clickedBlock.equals(door.getAttachedBlock())) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        // Check Portals
        if (event.getItem() != null) {
            ItemStack item = event.getItem();

            if (dGlobalPlayer.isCreatingPortal()) {
                if (item.getType() == Material.WOOD_SWORD) {
                    if (clickedBlock != null) {
                        for (GlobalProtection protection : DungeonsXL.getGlobalProtections().getProtections(DPortal.class
                        )) {
                            DPortal dPortal = (DPortal) protection;
                            if (!dPortal.isActive()) {
                                if (dPortal == dGlobalPlayer.getPortal()) {
                                    if (dPortal.getBlock1() == null) {
                                        dPortal.setBlock1(event.getClickedBlock());
                                        MessageUtil.sendMessage(player, DMessages.PLAYER_PORTAL_PROGRESS.getMessage());

                                    } else if (dPortal.getBlock2() == null) {
                                        dPortal.setBlock2(event.getClickedBlock());
                                        dPortal.setActive(true);
                                        dPortal.create(dGlobalPlayer);
                                        MessageUtil.sendMessage(player, DMessages.PLAYER_PORTAL_CREATED.getMessage());
                                    }
                                    event.setCancelled(true);
                                }
                            }
                        }
                    }
                }
            }

            // Copy/Paste a Sign and Block-info
            if (DEditWorld.getByWorld(player.getWorld()) != null) {
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    if (item.getType() == Material.STICK) {
                        DEditPlayer dPlayer = DEditPlayer.getByPlayer(player);
                        if (dPlayer != null) {
                            dPlayer.poke(clickedBlock);
                            event.setCancelled(true);
                        }
                    }
                }
            }

            // Trigger UseItem Signs
            DGameWorld gameWorld = DGameWorld.getByWorld(player.getWorld());
            if (gameWorld != null) {
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
                    String name = null;
                    if (item.hasItemMeta()) {
                        if (item.getItemMeta().hasDisplayName()) {
                            name = item.getItemMeta().getDisplayName();

                        } else if (item.getType() == Material.WRITTEN_BOOK || item.getType() == Material.BOOK_AND_QUILL) {
                            if (item.getItemMeta() instanceof BookMeta) {
                                BookMeta meta = (BookMeta) item.getItemMeta();
                                if (meta.hasTitle()) {
                                    name = meta.getTitle();
                                }
                            }
                        }
                    }
                    if (name == null) {
                        name = item.getType().toString();
                    }
                    UseItemTrigger trigger = UseItemTrigger.getByName(name, gameWorld);
                    if (trigger != null) {
                        trigger.onTrigger(player);
                    }
                }
            }
        }
    }

}
