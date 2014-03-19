package homesoil;

import com.google.common.collect.*;
import java.io.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;

/**
 * This class holds the PlayerInfo objects, keyed by name. It will generate them
 * as needed.
 *
 * @author DanJ
 */
public final class PlayerInfoMap {

    private final Map<String, PlayerInfo> infos = Maps.newHashMap();
    private final Set<ChunkPosition> homeChunks = Sets.newHashSet();
    private int homeChunksGenCount = 0;
    private static final int spawnRadiusInChunks = 32;
    private final Random random = new Random();

    /**
     * This method returns the player info object for a player. If there is
     * none, we create it if we can and assign a home chunk. We can create a
     * PlayerInfo only if the player is currently on-line; if he is not and has
     * no player info, this method throws IllegalStateException. If you pass a
     * Player instead of an OfflinePlayer, or if you check isKnown() first, you
     * can avoid this.
     *
     * @param player The player whose info is wanted.
     * @return The info object with the player's data.
     * @throws IllegalStateException The player has no info, but is offline so
     * none can be created.
     */
    public PlayerInfo get(OfflinePlayer player) {
        String name = player.getName();

        PlayerInfo info = infos.get(name);

        if (info == null) {
            Player onlinePlayer = player.getPlayer();

            if (onlinePlayer == null) {
                throw new IllegalStateException(String.format(
                        "The player '%'s has no PlayerInfo, but none can be generated because he is offline.",
                        name));
            }

            info = new PlayerInfo();
            pickNewHomeChunk(onlinePlayer.getWorld(), onlinePlayer.getServer(), info);
            infos.put(name, info);
        }

        return info;
    }

    /**
     * This method determines whether a given player already has a PlayerInfo
     * assigned, but does not actually create one; this therefore will not throw
     * even if the player is actually offline.
     *
     * @param player The player to check.
     * @return True if the player has an PlayerInfo assigned.
     */
    public boolean isKnown(OfflinePlayer player) {
        return infos.containsKey(player.getName());
    }

    /**
     * This method takes a chunk away from a player. If this was his last chunk,
     * we randomly assign a new home chunk.
     *
     * @param player The player whose home chunk is to be removed.
     * @param homeChunk The chunk to remove from the player.
     * @param server The server we are running on.
     */
    public void removeHomeChunk(OfflinePlayer player, ChunkPosition homeChunk, Server server) {
        // if player is not yet known, there is no point to resetting his home
        // chunk. This will happen when he logs in!

        if (isKnown(player)) {
            PlayerInfo info = get(player);
            if (!info.tryRemoveHomeChunk(homeChunk)) {
                World world = homeChunk.getWorld(server);
                pickNewHomeChunk(world, server, info);
            }
        }
    }

    /**
     * This method returns a set containing each chunk that is the home for any
     * player. The set is immutable and lazy allocated; a new one is allocated
     * if the player infos are ever changed.
     *
     * @return An immutable set of chunks that are occupied by a player..
     */
    public Set<ChunkPosition> getHomeChunks() {
        int currentGenCount = PlayerInfo.getGenerationCount();

        if (homeChunksGenCount != currentGenCount) {
            homeChunksGenCount = currentGenCount;

            homeChunks.clear();

            for (PlayerInfo info : infos.values()) {
                homeChunks.addAll(info.getHomeChunks());
            }
        }

        return homeChunks;
    }

    ////////////////////////////////
    // Player Starts
    //
    /**
     * This method returns the location to spawn a player. If the player cannot
     * be spawned in his home chunk, this will assign a new home chunk in the
     * same world for that player.
     *
     * @param player The player whose spawn point is needed.
     * @return The location to spawn him.
     */
    public Location getPlayerStart(Player player) {
        return getPlayerStart(player, player.getWorld(), player.getServer());
    }

    /**
     * This method retrieves the location where the player indicated should
     * spawn. If the player is not known to us, we can still assign a home chunk
     * an use that - as long as the player is online.
     *
     * However, if the player is not known yet, and is also not on-line, we
     * cannot assign a home chunk, and an IllegalStateException will be thrown.
     *
     * You can use the version of this method that takes just a Player object to
     * avoid this, or just check isKnown().
     *
     * @param player The player whose start position is needed.
     * @param world The world the player's chunks should be in, in case we need
     * to regenerate them.
     * @param server The server where the player will be.
     * @return The spawn location, or absent() if the player is unknown.
     */
    public Location getPlayerStart(OfflinePlayer player, World world, Server server) {
        PlayerInfo info = get(player);
        Location spawn = info.findPlayerStartOrNull(server, random, true);

        if (spawn != null) {
            return spawn;
        } else {
            return pickNewHomeChunk(world, server, info);
        }
    }

    /**
     * This method selects a home chunk and assigns it to the 'info' given; it
     * keeps trying to do this until it finds a valid home chunk. If it cannot
     * find one, it will throw an exception.
     *
     * @param world The world in which the home chunk should be found.
     * @param server The server that contains the world.
     * @param info The player info to be updated.
     * @return The spawn point for the player, as a convenience.
     */
    private Location pickNewHomeChunk(World world, Server server, PlayerInfo info) {
        // we'll try many times to find a spawn location
        // with a valid player start.
        for (int limit = 0; limit < 256; ++limit) {
            info.setHomeChunk(getInitialChunkPosition(world));

            // after a while, we'll take what we can get!
            boolean picky = limit < 128;

            Location spawn = info.findPlayerStartOrNull(server, random, picky);
            if (spawn != null) {
                return spawn;
            }
        }

        throw new RuntimeException(String.format("Unable to find any open home chunk in the world '%s'", world.getName()));
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
    ////////////////////////////////
    // Loading and Saving
    //
    private int loadedGenerationCount;

    /**
     * This method populates the map with the contents of the player file.
     */
    public void load(File source) {
        MapFileMap.read(source).copyInto(infos, PlayerInfo.class);
        loadedGenerationCount = PlayerInfo.getGenerationCount();
    }

    /**
     * This method writes the player data out to the players file. We call this
     * whenever anything is changed.
     */
    public void save(File destination) {
        MapFileMap.write(destination, infos);
        loadedGenerationCount = PlayerInfo.getGenerationCount();
    }

    /**
     * This method returns true if there might be changes to save; this checks
     * the global generation count so its not entirely accurate, but it should
     * never return false if there are changes to save.
     *
     * @return True if save() should be called.
     */
    public boolean shouldSave() {
        return loadedGenerationCount != PlayerInfo.getGenerationCount();
    }
}
