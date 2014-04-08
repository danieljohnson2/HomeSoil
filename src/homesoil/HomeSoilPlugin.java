package homesoil;

import com.google.common.collect.*;
import java.io.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.*;
import org.bukkit.scheduler.BukkitRunnable;

// TODO: snowball for each player
/**
 * This is the plugin class itself, which acts as the main entry point for a
 * Bukkit plugin. This also doubles as the listener, and handles events for us.
 *
 * @author DanJ
 */
public class HomeSoilPlugin extends JavaPlugin implements Listener {

    private static final File playersFile = new File("HomeSoil.txt");
    private final PlayerInfoMap playerInfos = new PlayerInfoMap();
    private final DoomSchedule doomSchedule = new DoomSchedule(this);

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

    public PlayerInfoMap getPlayerInfos() {
        return playerInfos;
    }

    ////////////////////////////////
    // Event Handlers
    @Override
    public void onEnable() {
        super.onEnable();

        load();
        getServer().getPluginManager().registerEvents(this, this);
        doomSchedule.start(this);
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
                    OfflinePlayer victimPlayer = getServer().getOfflinePlayer(displayName);

                    if (victimPlayer != null) {
                        tryToStealHomeChunk((Player) shooter, victimPlayer);
                        directFlamingSnowball(projectile, victimPlayer);
                        saveIfNeeded();
                    }
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
     * @param victim The poor fellow named by the snowball.
     */
    private void tryToStealHomeChunk(final Player shooter, OfflinePlayer victim) {
        if (playerInfos.isKnown(victim)) {
            PlayerInfo victimInfo = playerInfos.get(victim);
            ChunkPosition victimChunk = ChunkPosition.of(shooter.getLocation());

            if (victimInfo.getHomeChunks().contains(victimChunk)) {
                playerInfos.removeHomeChunk(victim, victimChunk, getServer());

                if (victim.getPlayer() != shooter) {
                    PlayerInfo shooterInfo = playerInfos.get(shooter);
                    shooterInfo.addHomeChunk(victimChunk);

                    int numberOfFireworks = shooterInfo.getHomeChunks().size();
                    numberOfFireworks = Math.min(500, numberOfFireworks * numberOfFireworks);
                    final Location loc = shooter.getLocation().clone();

                    for (int i = 0; i < numberOfFireworks; ++i) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                launchFirework(loc);
                            }
                        }.runTaskLater(this, 10 * i);
                    }
                }
            }
        }
    }

    private void launchFirework(Location loc) {
        // but let's launch a firework too!
        // Language note: (Firework) here is a cast- spawnEntity does not return the correct type,
        // but we can ask Java to override. This is checked: an error occurs if it's not
        // a firework.

        Firework firework = (Firework) loc.getWorld().spawnEntity(loc, EntityType.FIREWORK);
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
     * This method creates a scheduled task that manipulates the projectile
     * given so that it flies towards the player start of the indicated victim.
     *
     * @param projectile The snowball.
     * @param victim The guy whose name is on the snowball.
     */
    private void directFlamingSnowball(Projectile projectile, OfflinePlayer victim) {
        List<Location> victimSpawns = Lists.newArrayList(playerInfos.getPlayerStarts(victim, getServer()));

        if (!victimSpawns.isEmpty()) {
            final Location start = projectile.getLocation().clone();

            class DistanceComparator implements Comparator<Location> {

                @Override
                public int compare(Location left, Location right) {
                    // this compares by distance from 'start', ascending, so the
                    // nearest location is first.
                    return (int) Math.signum(start.distanceSquared(left) - start.distanceSquared(right));
                }
            }

            Collections.sort(victimSpawns, new DistanceComparator());

            Location victimSpawn = victimSpawns.get(0);

            start.add(0, 1, 0);
            projectile.teleport(start);

            Location destination = victimSpawn.clone();

            // if a player throws a snowball named after a player, we
            // change its effect. Since the snowball itself is gone, and the
            // snowball-projectile is a different thing with no special name,
            // we'll stash the player info in it.

            // This is also where we reassign home chunks if needed:
            // the mechanism works on the throw, not the hit (which can
            // operate normally)
            ProjectileDirector.begin(projectile, destination, this);
            //note: beginning the snowball at destination.y + 1 would be good,
            //not sure on the specifics of how that's done
            //ProjectileDirector now handles its own speed as it varies w. distance
        }
    }

    @SuppressWarnings("deprecation")
    private void bestowSnowball(Player player) {
        PlayerInventory inventory = player.getInventory();

        ItemStack itemStack = new ItemStack(Material.SNOW_BALL, 16);
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
                getLogger().warning(String.format("'%s' joined the game, and has been given home chunk %s.",
                        name,
                        homeChunk));
            }

            saveIfNeeded();
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        e.setRespawnLocation(playerInfos.getPlayerStart(e.getPlayer()));
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        //we are going to want to remove this in final build entirely:
        //I'd prefer not overriding something so fundamental to play.
        //However, it's got a job to do now - chris

        if (e.getTo().getChunk() != e.getFrom().getChunk()) {
            ChunkPosition fromChunk = ChunkPosition.of(e.getFrom());
            ChunkPosition toChunk = ChunkPosition.of(e.getTo());

            String fromPlayerName = playerInfos.identifyChunkOwner(fromChunk);
            String toPlayerName = playerInfos.identifyChunkOwner(toChunk);

            if (!fromPlayerName.equals(toPlayerName)) {
                Player player = e.getPlayer();

                List<ChunkPosition> homes = playerInfos.get(player).getHomeChunks();

                boolean isHome = homes.contains(toChunk);
                boolean isLeaving = !fromPlayerName.isEmpty();
                boolean isEntering = !toPlayerName.isEmpty();

                if (isLeaving) {
                    player.getWorld().playEffect(player.getLocation(), Effect.CLICK2, 0);
                }

                if (isEntering) {
                    player.getWorld().playEffect(player.getLocation(), Effect.CLICK1, 0);

                    if (isHome) {
                        int chunkNo = homes.indexOf(toChunk);
                        player.chat(String.format("This is §lyour§r home chunk (#%d of %d)",
                                chunkNo + 1,
                                homes.size()));
                    }
                }
            }
        }
    }
}