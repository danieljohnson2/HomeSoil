package homesoil;

import com.google.common.collect.*;
import java.io.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.plugin.java.*;
import org.bukkit.scheduler.*;

/**
 * This is the plugin class itself, which acts as the main entry point for a
 * Bukkit plugin. This also doubles as the listener, and handles events for us.
 *
 * @author DanJ
 */
public class HomeSoilPlugin extends JavaPlugin implements Listener {

    private static final File playersFile = new File("HomeSoil.txt");
    private static final File regenFile = new File("HomeSoilDoom.txt");
    private final PlayerInfoMap playerInfos = new PlayerInfoMap();
    private final DoomSchedule doomSchedule = new DoomSchedule(this, regenFile);

    /**
     * This method provides access to the player info so we can move some logic
     * out to other classes.
     *
     * @return The PlayerInfoMap, from which PlayerInfos may be obtained.
     */
    public PlayerInfoMap getPlayerInfos() {
        return playerInfos;
    }

    /**
     * This method loads player data from the HomeSoil file.
     */
    private void load() {
        getLogger().info("Loading HomeSoil State");

        if (playersFile.exists()) {
            playerInfos.load(playersFile);
        }
    }

    /**
     * This method saves any changes to the HomeSoil file; however this checks
     * for changes and only saves if there might be some.
     */
    private void saveIfNeeded() {
        if (playerInfos.shouldSave()) {
            getLogger().info("Saving HomeSoil State");
            playerInfos.save(playersFile);
        }
    }

    ////////////////////////////////
    // Event Handlers
    @Override
    public void onEnable() {
        super.onEnable();

        load();
        getServer().getPluginManager().registerEvents(this, this);
        doomSchedule.start();
    }

    @Override
    public void onDisable() {
        saveIfNeeded();
        doomSchedule.stop();

        super.onDisable();
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        Projectile projectile = e.getEntity();
        LivingEntity shooter = projectile.getShooter();
        if (shooter instanceof Player) {
            ItemStack held = shooter.getEquipment().getItemInHand();

            if (held != null && held.getType() == Material.SNOW_BALL) {
                ItemMeta itemMeta = held.getItemMeta();
                if (itemMeta.hasDisplayName()) {
                    String displayName = held.getItemMeta().getDisplayName();

                    if (displayName.equals(PlayerInfoMap.COMMON_PLAYER_NAME)) {
                        if (playerInfos.isKnown((Player) shooter)) {
                            tryToContributeCommonHomeChunk((Player) shooter);
                        }
                    } else {
                        OfflinePlayer victimPlayer = getServer().getOfflinePlayer(displayName);

                        if (playerInfos.isKnown(victimPlayer)) {
                            tryToStealHomeChunk((Player) shooter, victimPlayer);
                            directFlamingSnowball(projectile, victimPlayer);
                        }
                    }

                    saveIfNeeded();
                } else {
                    // anonymous snowballs can't steal chunks, but they can
                    // still fly towards one!

                    directFlamingSnowballToAnybody(projectile);
                }
            }
        }
    }

    /**
     * This method will try to steal the home chunk that 'shooter' is standing
     * in from 'victim'; it does nothing if the victim does not own the chunk,
     * which can happen because the victim is identified by the name of the
     * snowball.
     *
     * @param shooter The snowball-throwing miscreant.
     * @param victim The poor fellow named by the snowball; must be a known
     * player.
     */
    private void tryToStealHomeChunk(final Player shooter, OfflinePlayer victim) {
        PlayerInfo victimInfo = playerInfos.get(victim);
        ChunkPosition victimChunk = ChunkPosition.of(shooter.getLocation());

        if (victimInfo.getHomeChunks().contains(victimChunk)) {
            playerInfos.removeHomeChunk(victim, victimChunk);

            if (victim.getPlayer() != shooter) {
                // this branch is for the case where we're stealing another players home

                PlayerInfo shooterInfo = playerInfos.get(shooter);
                shooterInfo.addHomeChunk(victimChunk);

                String shooterName = shooter.getName();
                String victimName = victim.getName();
                List<ChunkPosition> homes = shooterInfo.getHomeChunks();
                String msg = String.format(
                        "§6%s took over %s's chunk and now controls %d!§r",
                        shooterName,
                        victimName,
                        homes.size());

                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage(msg);
                }

                System.out.println(msg);
                //also log the message to console

                int numberOfFireworks = homes.size();
                numberOfFireworks = Math.min(500, numberOfFireworks * numberOfFireworks);

                if (numberOfFireworks > 0) {
                    launchFireworksLater(shooter.getLocation(), numberOfFireworks);
                }
            } else {
                // This branch is for throwing a snowball in your own chunk.

                shooter.setHealth(shooter.getMaxHealth());
                //bump up shooter health to full, because
                //they are sacrificing their chunk for a health buff
            }
        }
    }

    private void tryToContributeCommonHomeChunk(final Player shooter) {
        PlayerInfo shooterInfo = playerInfos.get(shooter);
        ChunkPosition victimChunk = ChunkPosition.of(shooter.getLocation());

        if (shooterInfo.getHomeChunks().contains(victimChunk)) {
            playerInfos.removeHomeChunk(shooter, victimChunk);

            // this branch is for the case where we're stealing another players home

            OfflinePlayer commons = getServer().getOfflinePlayer(PlayerInfoMap.COMMON_PLAYER_NAME);
            playerInfos.addHomeChunk(commons, victimChunk);

            String shooterName = shooter.getName();

            String msg = String.format(
                    "§6%s gave up one of their chunks!§r",
                    shooterName);

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(msg);
            }

            System.out.println(msg);
            //also log the message to console
        }
    }

    /**
     * This method schedules a firework barrage to be launched; the task
     * launches one every ten ticks until it has fired off enough.
     *
     * We also drop XP orbs when the fireworks go off; this method handles all
     * that too.
     *
     * @param spawnLocation The point from which the firework will spawn.
     * @param numberOfFireworks The number of fireworks.
     */
    private void launchFireworksLater(final Location spawnLocation, final int numberOfFireworks) {
        new BukkitRunnable() {
            // lets be safe and not let the location change while we are doing this!
            private final Location launchPoint = spawnLocation.clone();
            // we'll drop XP from a higher point so it 'showers' down
            private final Location xpDropPoint = spawnLocation.clone().add(0, 8, 0);
            // this field will count down the firewsorks so we know when to stop
            private int fireworksRemaining = numberOfFireworks;

            @Override
            public void run() {
                if (fireworksRemaining > 0) {
                    launchFirework(launchPoint);
                    dropXpFrom(xpDropPoint, 1.0, fireworksRemaining);
                    --fireworksRemaining;
                } else {
                    // once there are no more fireworks, we can finally stop
                    // the madness.
                    cancel();
                }
            }
        }.runTaskTimer(this, 0, 10);
    }

    /**
     * This method spawns a firework to celebrate stealing a chunk. It also
     * drops some XP orbs in the bargain.
     *
     * @param spawnLocation The point from which the firework will spawn.
     */
    private void launchFirework(Location spawnLocation) {
        // but let's launch a firework too!
        // Language note: (Firework) here is a cast- spawnEntity does not return the correct type,
        // but we can ask Java to override. This is checked: an error occurs if it's not
        // a firework.

        World world = spawnLocation.getWorld();
        Firework firework = (Firework) world.spawnEntity(spawnLocation, EntityType.FIREWORK);
        FireworkMeta meta = firework.getFireworkMeta().clone();

        // Make it fancy! This is a 'fluent' style class, where we chain method
        // calls with '.'.
        FireworkEffect effect = FireworkEffect.builder().
                withColor(Color.LIME).
                withFlicker().
                withTrail().
                with(FireworkEffect.Type.CREEPER).
                build();
        meta.addEffect(effect);
        meta.setPower(2);
        firework.setFireworkMeta(meta);
    }

    /**
     * This method spawns some experience orbs at the indicated location. They
     * add up to enough experience to gain the specified number of levels
     * (assuming 17 points per level).
     *
     * The orb size is given; we use hte largest orbs on the first firework,
     * then go down from there. The last firework winds up having orb size 1.
     *
     * @param spawnLocation The place to spawn the XP orbs.
     * @param levels The number of levels to grant.
     * @param orbSize The size of the orbs to drop (the last one may be smaller)
     */
    private void dropXpFrom(Location spawnLocation, double levels, int orbSize) {
        World world = spawnLocation.getWorld();

        int xp = (int) Math.round(levels * 17);

        while (xp > 0) {
            ExperienceOrb orb = (ExperienceOrb) world.spawnEntity(spawnLocation, EntityType.EXPERIENCE_ORB);
            orb.setExperience(Math.min(xp, orbSize));
            xp -= orbSize;
        }
    }

    /**
     * This method creates a scheduled task that manipulates the projectile
     * given so that it flies towards the player start of the indicated victim.
     *
     * @param projectile The snowball.
     * @param victim The guy whose name is on the snowball.
     */
    private void directFlamingSnowball(Projectile projectile, OfflinePlayer victim) {
        List<Location> victimSpawns = Lists.newArrayList(playerInfos.getPlayerStarts(victim));
        directFlamingSnowballCore(projectile, true, victimSpawns);
    }

    /**
     * This method creates a scheduled task that manipulates the projectile
     * given so that it flies towards the nearest player start of any player.
     *
     * @param projectile The snowball.
     */
    private void directFlamingSnowballToAnybody(Projectile projectile) {
        List<Location> victimSpawns = Lists.newArrayList();

        for (OfflinePlayer victim : playerInfos.getKnownPlayers()) {
            victimSpawns.addAll(playerInfos.getPlayerStarts(victim));
        }

        directFlamingSnowballCore(projectile, false, victimSpawns);
    }

    /**
     * This method provides the implementation for the directFlamingSnowball
     * methods. It is given the list of candidate targets, but it will modify
     * the list and the locations in it; the list should not be used again after
     * this call.
     *
     * @param projectile The snowball to send toward the nearest victimeSpawn.
     * @param isOnFire If true, the snowball will also be on fire!
     * @param victimSpawns THe candidate locations to send the projectile to;
     * will be modified.
     */
    private void directFlamingSnowballCore(Projectile projectile, boolean isOnFire, List<Location> victimSpawns) {
        if (!victimSpawns.isEmpty()) {
            Location start = projectile.getLocation().clone().add(0, 1, 0);
            projectile.teleport(start);

            sequenceSnowballTargets(start, victimSpawns);

            Location destination = victimSpawns.get(0);

            // the snowball will be moved by the server updating its position
            // periodically; this is done in a scheduled task.
            ProjectileDirector.begin(projectile, destination, isOnFire, this);
        }
    }

    /**
     * This method places a list of locations in the ascending order of distance
     * from a start point. The points are not only sorted, but individually
     * translated into the same world as 'start'.
     *
     * @param start The starting point we order the targets by nearness to this.
     * @param targets The list to update in place; must be mutable.
     */
    private void sequenceSnowballTargets(final Location start, List<Location> targets) {
        World world = start.getWorld();

        // ugly, but we can only work with spawns in the same world, so
        // we translate them all. This relies on getPlayerStarts() returning
        // clones, which it does, but ew.

        for (Location spawn : targets) {
            ChunkPosition.translateToWorld(spawn, world);
        }

        class DistanceComparator implements Comparator<Location> {

            @Override
            public int compare(Location left, Location right) {
                // this compares by distance from 'start', ascending, so the
                // nearest location is first.
                return (int) Math.signum(start.distanceSquared(left) - start.distanceSquared(right));
            }
        }

        // we target the closest spawn the victim has.
        Collections.sort(targets, new DistanceComparator());
    }

    /**
     * This method gives a player a snowball in a designated snowball slot.
     *
     * @param player The player to be gifted with snow!
     */
    @SuppressWarnings("deprecation")
    private void bestowSnowball(Player player) {
        PlayerInventory inventory = player.getInventory();

        ItemStack itemStack = new ItemStack(Material.SNOW_BALL, 8);
        ItemMeta meta = itemStack.getItemMeta().clone();
        meta.setDisplayName(player.getName());
        meta.setLore(Arrays.asList(
                String.format("Seeks %s's", player.getName()),
                "home soil"));
        itemStack.setItemMeta(meta);
        inventory.setItem(35, itemStack);

        player.updateInventory();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        final HumanEntity clicked = e.getWhoClicked();

        if (clicked instanceof Player) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    bestowSnowball((Player) clicked);
                }
            }.runTaskLater(this, 1);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();

        if (!playerInfos.isKnown(player)) {
            String name = player.getName();

            for (ChunkPosition homeChunk : playerInfos.get(player).getHomeChunks()) {
                System.out.println(String.format("'%s' joined the game, and has been given home chunk %s.",
                        name,
                        homeChunk));
            }

            saveIfNeeded();
        }

        bestowSnowball(player);
        playerInfos.sendScoresTo(player);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        bestowSnowball(player);
        e.setRespawnLocation(playerInfos.getPlayerStart(player));
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        // We decided to keep this, but try to optimize by only checking
        // when a player moves from chunk to chunk.

        if (e.getTo().getChunk() != e.getFrom().getChunk()) {
            ChunkPosition fromChunk = ChunkPosition.of(e.getFrom());
            ChunkPosition toChunk = ChunkPosition.of(e.getTo());

            String fromPlayerName = playerInfos.identifyChunkOwner(fromChunk);
            String toPlayerName = playerInfos.identifyChunkOwner(toChunk);

            if (!fromPlayerName.equals(toPlayerName)) {
                Player player = e.getPlayer();
                PlayerInfo playerInfo = playerInfos.get(player);

                boolean isEntering = !toPlayerName.isEmpty();
                boolean isEnteringFormerHome = isEntering && playerInfo.getHistoricalHomeChunks().contains(toChunk);
                boolean isEnteringCommonsHome = isEntering && toPlayerName.equals(PlayerInfoMap.COMMON_PLAYER_NAME);

                if (fromPlayerName.equals(player.getName())) {
                    player.getWorld().playEffect(player.getLocation(), Effect.CLICK2, 0);
                }

                if (isEntering) {
                    OfflinePlayer toPlayer = getServer().getOfflinePlayer(toPlayerName);

                    if (playerInfos.isKnown(toPlayer)) {
                        PlayerInfo toInfo = playerInfos.get(toPlayer);
                        List<ChunkPosition> homes = toInfo.getHomeChunks();
                        int chunkNo = homes.indexOf(toChunk);

                        String msg = null;

                        if (toPlayer.getPlayer() == player) {
                            // silly rabbit, clicks are for kids! (sorry)
                            player.getWorld().playEffect(player.getLocation(), Effect.CLICK1, 0);
                            msg = String.format(
                                    "§6This is §lyour§r§6 home chunk (#%d of %d)§r",
                                    chunkNo + 1,
                                    homes.size());
                        } else if (isEnteringCommonsHome) {
                            player.getWorld().playEffect(player.getLocation(), Effect.CLICK1, 0);
                            msg = String.format(
                                    "§6This is §leverybody's§r§6 home chunk§r",
                                    toPlayerName);
                        } else if (isEnteringFormerHome) {
                            player.getWorld().playEffect(player.getLocation(), Effect.CLICK1, 0);
                            msg = String.format(
                                    "§6This is §l%s's§r§6 home chunk (#%d of %d)§r",
                                    toPlayerName,
                                    chunkNo + 1,
                                    homes.size());
                        }

                        if (msg != null) {
                            player.sendMessage(msg);
                        }
                    }
                }
            }
        }
    }
}