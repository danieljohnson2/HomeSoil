package homesoil;

import com.google.common.base.*;
import com.google.common.collect.*;
import java.io.*;
import java.util.*;
import java.util.logging.*;
import org.bukkit.*;
import org.bukkit.entity.*;

/**
 * This class holds the PlayerInfo objects, keyed by name. It will generate them
 * as needed.
 *
 * @author DanJ
 */
public class PlayerInfoMap {

    private final File playersFile;
    private final HomeSoilPlugin plugin;
    private final Map<String, PlayerInfo> infos = Maps.newHashMap();
    private final Set<ChunkPosition> homeChunks = Sets.newHashSet();
    private int homeChunksGenCount = 0;
    private static final int spawnRadiusInChunks = 32;
    private final Random random = new Random();

    public PlayerInfoMap(File playersFile, HomeSoilPlugin plugin) {
        this.playersFile = Preconditions.checkNotNull(playersFile);
        this.plugin = Preconditions.checkNotNull(plugin);
    }

    /**
     * This method returns the player info object for a player. If there is
     * none, we create it, and immediately save it.
     *
     * @param player
     * @return
     */
    public PlayerInfo get(Player player) {
        String name = player.getName();

        PlayerInfo info = infos.get(name);

        if (info == null) {
            ChunkPosition home = getInitialChunkPosition(player.getWorld());
            info = new PlayerInfo(home);
            infos.put(name, info);
            save();
        }

        return info;
    }

    /**
     * This method determines whether a given player already has a PlayerInfo
     * assigned, but does not actually create one.
     *
     * @param player The player to check.
     * @return True if the player has an PlayerInfo assigned.
     */
    public boolean isKnown(Player player) {
        return infos.containsKey(player.getName());
    }

    /**
     * This method returns a set containing each chunk that is the home for any
     * player. The set is immutable and lazy allocated.
     *
     * @param position The position to check.
     * @return True if the chunk is anyone's home chunk.
     */
    public Set<ChunkPosition> getHomeChunks() {
        int currentGenCount = PlayerInfo.getGenerationCount();
        
        if (homeChunksGenCount != currentGenCount) {
            homeChunksGenCount = currentGenCount;

            homeChunks.clear();

            for (PlayerInfo info : infos.values()) {
                homeChunks.add(info.getHomeChunk());
            }
        }

        return homeChunks;
    }

    /**
     * This method picks a position for a player that is not assigned to any
     * player yet.
     *
     * @param world The world the player will spawn in.
     */
    private ChunkPosition getInitialChunkPosition(World world) {
        Set<ChunkPosition> oldHomes = getHomeChunks();

        for (;;) {
            int x = random.nextInt(spawnRadiusInChunks * 2) - spawnRadiusInChunks;
            int z = random.nextInt(spawnRadiusInChunks * 2) - spawnRadiusInChunks;

            ChunkPosition homeChunk = new ChunkPosition(x, z, world);

            if (!oldHomes.contains(homeChunk)) {
                return homeChunk;
            }
        }
    }

    public void load() {
        Logger logger = getLogger();

        logger.info("Loading HomeSoil State");

        infos.clear();

        if (playersFile.exists()) {
            MapFileMap.read(playersFile).copyInto(infos, PlayerInfo.class);
        }
    }

    public void save() {
        getLogger().info("Saving HomeSoil State");
        MapFileMap.write(playersFile, infos);
    }

    private Logger getLogger() {
        return plugin.getLogger();
    }
}
