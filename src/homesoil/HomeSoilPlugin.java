package homesoil;

import com.google.common.base.*;
import com.google.common.collect.*;
import java.io.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.*;

// TODO: snowball for each player
/**
 * This is the plugin class itself, which acts as the main entry point for a
 * Bukkit plugin. This also doubles as the listener, and handles events for us.
 *
 * @author DanJ
 */
public class HomeSoilPlugin extends JavaPlugin implements Listener {

    private static final File playersFile = new File("HomeSoil.txt");
    private final Set<ChunkPosition> alreadyLoadedOnce = Sets.newHashSet();
    private final PlayerInfoMap playerInfos = new PlayerInfoMap();

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

    /**
     * This method regenerates a chunk if it needs to be; But only on loading
     * old ones, and it checks to ensure that it won't regenerate a home chunk
     * nor any chunk already regenerated once. We also don't regenerate the
     * nether or the end.
     *
     * As an optimization we don't even call this for new chunks but only for
     * chunks being reloaded.
     *
     * @param chunk The chunk to regenerate.
     */
    private void regenerateIfNeeded(Chunk chunk) {
        if (chunk.getWorld().getEnvironment() == World.Environment.NORMAL) {
            ChunkPosition pos = ChunkPosition.of(chunk);

            if (alreadyLoadedOnce.add(pos)) {
                if (playerInfos.getHomeChunks().contains(pos)) {
                    getLogger().info(String.format("Unable to regenerate [%s] as it is a home chunk.", pos));
                } else {
                    chunk.getWorld().regenerateChunk(pos.x, pos.z);
                }
            }
        }
    }

    ////////////////////////////////
    // Event Handlers
    @Override
    public void onEnable() {
        super.onEnable();

        load();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        saveIfNeeded();

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
    private void tryToStealHomeChunk(Player shooter, OfflinePlayer victim) {
        if (playerInfos.isKnown(victim)) {
            PlayerInfo victimInfo = playerInfos.get(victim);
            ChunkPosition victimChunk = ChunkPosition.of(shooter.getLocation());

            if (victimInfo.getHomeChunks().contains(victimChunk)) {
                playerInfos.removeHomeChunk(victim, victimChunk, getServer());

                if (victim.getPlayer() != shooter) {
                    PlayerInfo shooterInfo = playerInfos.get(shooter);
                    shooterInfo.addHomeChunk(victimChunk);
                }
            }
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

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();

        if (!playerInfos.isKnown(player)) {
            player.teleport(playerInfos.getPlayerStart(player));
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
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!e.isNewChunk()) {
            regenerateIfNeeded(e.getChunk());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        //we are going to want to remove this in final build entirely:
        //I'd prefer not overriding something so fundamental to play.
        //However, it's got a job to do now - chris

        List<ChunkPosition> homes = playerInfos.get(e.getPlayer()).getHomeChunks();

        boolean wasHome = homes.contains(ChunkPosition.of(e.getFrom()));
        boolean isHome = homes.contains(ChunkPosition.of(e.getTo()));

        if (wasHome != isHome) {
            if (isHome) {
                e.getPlayer().chat("You have entered your home chunk");
            } else {
                e.getPlayer().chat("You have exited your home chunk");
            }
        }
    }
}
