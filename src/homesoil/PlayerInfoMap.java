package homesoil;

import com.google.common.base.Optional;
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
public class PlayerInfoMap {

    private final Map<String, PlayerInfo> infos = Maps.newHashMap();
    private final Set<ChunkPosition> homeChunks = Sets.newHashSet();
    private int homeChunksGenCount = 0;
    private static final int spawnRadiusInChunks = 32;
    private final Random random = new Random();

    /**
     * This method returns the player info object for a player. If there is
     * none, we create it and assign a home chunk.
     *
     * @param player The player whose info is wanted.
     * @return The info object with the player's data.
     */
    public PlayerInfo get(Player player) {
        String name = player.getName();

        PlayerInfo info = infos.get(name);

        if (info == null) {
            info = new PlayerInfo();
            pickNewHomeChunk(player.getWorld(), player.getServer(), info);
            infos.put(name, info);
        }

        return info;
    }

    public void resetHomeChunk(OfflinePlayer player, World world, Server server) {
        String name = player.getName();

        PlayerInfo info = infos.get(name);

        if (info != null) {
            pickNewHomeChunk(world, server, info);
        }

    }

    /**
     * This method retrieves the player info for a player who may be offline; we
     * do not generate the player info if the player is offline. This method
     * will return it if we have it, and return absent if not.
     *
     * @param player The player whose info is needed.
     * @return The info, or absent if the player is not known to this map.
     */
    public Optional<PlayerInfo> getIfKnown(OfflinePlayer player) {
        String name = player.getName();
        return Optional.fromNullable(infos.get(name));
    }

    /**
     * This method determines whether a given player already has a PlayerInfo
     * assigned, but does not actually create one.
     *
     * @param player The player to check.
     * @return True if the player has an PlayerInfo assigned.
     */
    public boolean isKnown(OfflinePlayer player) {
        return infos.containsKey(player.getName());
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
                homeChunks.add(info.getHomeChunk());
            }
        }

        return homeChunks;
    }

    ////////////////////////////////
    // Player Starts
    //
    /**
     * This method returns the position of the player's home chunk, allocating
     * it if necessary.
     *
     * @param player The player whose home is needed.
     * @return The position of the player's home chunk.
     */
    public ChunkPosition getHomeChunk(Player player) {
        return get(player).getHomeChunk();
    }

    public Optional<ChunkPosition> getHomeChunkIfKnown(OfflinePlayer player) {
        Optional<PlayerInfo> info = getIfKnown(player);

        if (info.isPresent()) {
            return Optional.of(info.get().getHomeChunk());
        } else {
            return Optional.absent();
        }
    }

    /**
     * This method returns the location to spawn a player. If the player cannot
     * be spawned in his home chunk, this will assign a new home chunk in the
     * same world for that player.
     *
     * @param player The player whose spawn point is needed.
     * @return The location to spawn him.
     */
    public Location getPlayerStart(Player player) {
        return getPlayerStartCore(get(player), player.getServer());
    }

    /**
     * This method retrieves the location where the player indicated should
     * spawn, if that player is known to us. If not, this returns absent.
     *
     * This will reassign the player's home chunk if required to find a valid
     * spawn point, and will fail only if the player is not known to us.
     *
     * @param player The player whose start position is needed.
     * @param server The server where the player will be.
     * @return The spawn location, or absent() if the player is unknown.
     */
    public Optional<Location> getPlayerStartIfKnown(OfflinePlayer player, Server server) {
        Optional<PlayerInfo> info = getIfKnown(player);

        if (info.isPresent()) {
            return Optional.of(getPlayerStartCore(info.get(), server));
        } else {
            return Optional.absent();
        }
    }

    /**
     * This method is the core implementation for the getPlayerStart methods; it
     * returns the player's spawn point but allocates a new home chunk if the
     * current home is not valid; if this fails it merely throws an exception.
     *
     * @param player The player whose spawn point is needed.
     * @param info The player's info object.
     * @param server The server on which to find a new home chunk (if needed)
     * @return The location to spawn.
     */
    private Location getPlayerStartCore(PlayerInfo info, Server server) {
        Optional<Location> spawn = info.findPlayerStart(server, true);

        if (spawn.isPresent()) {
            return spawn.get();
        } else {
            World world = info.getHomeChunk().getWorld(server);
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

            Optional<Location> spawn = info.findPlayerStart(server, picky);
            if (spawn.isPresent()) {
                return spawn.get();
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
