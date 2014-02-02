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
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

// TODO: do not regenerate the nether or the end!
// TODO: snowball for each player
// TODO: players can take over other players chunks
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
     * old ones, and it checks to ensure that it won't regen a home chunk nor
     * any chunk already regenerated once.
     *
     * As an optimization we don't even call this for new chunks but only for
     * chunks being reloaded.
     *
     * @param chunk The chunk to regenerate.
     */
    private void regenerateIfNeeded(Chunk chunk) {
        ChunkPosition pos = ChunkPosition.of(chunk);

        if (alreadyLoadedOnce.add(pos)) {
            if (playerInfos.getHomeChunks().contains(pos)) {
                getLogger().info(String.format("Unable to regenerate [%s] as it is a home chunk.", pos));
            } else {
                chunk.getWorld().regenerateChunk(pos.x, pos.z);
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
        ItemStack held = projectile.getShooter().getEquipment().getItemInHand();

        if (held != null && held.getType() == Material.SNOW_BALL) {
            ItemMeta itemMeta = held.getItemMeta();
            if (itemMeta.hasDisplayName()) {
                String displayName = held.getItemMeta().getDisplayName();
                OfflinePlayer player = getServer().getOfflinePlayer(displayName);

                if (player != null) {
                    Optional<Location> spawn = playerInfos.getPlayerStartIfKnown(player, getServer());

                    if (info.isPresent()) {
                        // if a player throws a snowball named after a player, we
                        // change its effect. Since the snowball itself is gone, and the
                        // snowball-projectile is a different thing with no special name,
                        // we'll stash the player info in it.
                        
                        // This is also where we reassign home chunks if needed:
                        // the mechanism works on the throw, not the hit (which can
                        // operate normally)

                        // if a player throws a snowball named after a player, we
                        // change the movement of the snowball so it slowly heads over
                        // to the named player's home soil.
                        ProjectileDirector.begin(projectile, destination, 0.25, this);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile projectile = e.getEntity();

        if (projectile.getType() == EntityType.SNOWBALL) {
            LivingEntity shooter = projectile.getShooter();

            // there should be only one metadata "HS_PlayerInfo", but
            // whatever; we'll take the first that works.
            List<MetadataValue> meta = projectile.getMetadata("HS_PlayerInfo");

            for (MetadataValue m : meta) {
                PlayerInfo info = (PlayerInfo) m.value();

                Optional<Location> spawn = info.findPlayerStart(getServer());

                if (spawn.isPresent()) {
                    // If we find player info stashed, that means we
                    // override the teleporation and land the player
                    // in the designated player's chunk.

                    //Not sure this event handler is necessary: to my knowledge
                    //there is never a need to do anything with the snowball hit.
                    //It is the throw event that triggers different behaviors, but
                    //when it hits it always just goes 'paf' and is gone.
                    //Therefore we don't need onProjectileHit to be overriden,
                    //unless we are testing by giving it temporary ender pearl behaviors?
                                       
                    shooter.teleport(spawn.get());
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (!playerInfos.isKnown(e.getPlayer())) {
            PlayerInfo info = playerInfos.get(e.getPlayer());
            Optional<Location> spawn = info.findPlayerStart(getServer());

            if (spawn.isPresent()) {
                e.getPlayer().teleport(spawn.get());
            }

            saveIfNeeded();
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        PlayerInfo info = playerInfos.get(e.getPlayer());
        Optional<Location> spawn = info.findPlayerStart(getServer());

        if (spawn.isPresent()) {
            e.setRespawnLocation(spawn.get());
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!e.isNewChunk()) {
            regenerateIfNeeded(e.getChunk());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        PlayerInfo info = playerInfos.get(e.getPlayer());
        ChunkPosition home = info.getHomeChunk();

        boolean wasHome = home.contains(e.getFrom());
        boolean isHome = home.contains(e.getTo());

        if (wasHome != isHome) {
            if (isHome) {
                e.getPlayer().chat("You have entered your home chunk");
            } else {
                e.getPlayer().chat("You have exited your home chunk");
            }
        }
    } //we are going to want to remove this in final build entirely:
    //I'd prefer not overriding something so fundamental to play.
    //However, it's got a job to do now - chris
}
