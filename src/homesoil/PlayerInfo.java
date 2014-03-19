package homesoil;

import com.google.common.base.*;
import com.google.common.collect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.bukkit.*;
import org.bukkit.block.*;

/**
 * This class holds the HomeSoil data for a particular player and is saved to
 * the HomeSoil file.
 *
 * @author DanJ
 */
public final class PlayerInfo implements MapFileMap.Storable {

    public PlayerInfo() {
        incrementGenerationCount();
    }
    ////////////////////////////////
    // Home Chunks
    private List<ChunkPosition> homeChunks = Collections.emptyList();

    /**
     * This method returns an immutable list that contains each home chunk
     * belonging to this player; this is empty only if the player info is not
     * yet initialized, and the chunks are in the order they were acquired.
     *
     * @return The list of home chunks of the list.
     */
    public List<ChunkPosition> getHomeChunks() {
        return ImmutableList.copyOf(homeChunks);
    }

    /**
     * This method assigns a single home chunk to the player; if he had any
     * chunks, they will be removed in favor of this new one.
     *
     * @param pos The new home chunk.
     */
    public void setHomeChunk(ChunkPosition homeChunk) {
        homeChunks = ImmutableList.of(homeChunk);
        incrementGenerationCount();
    }

    /**
     * This method adds a new home chunk for this player; if the chunk is
     * already a home chunk for this player, this method does nothing. Existing
     * home chunks remain assigned.
     *
     * @param homeChunk The new home chunk of this player.
     */
    public void addHomeChunk(ChunkPosition homeChunk) {
        if (!homeChunks.contains(homeChunk)) {
            homeChunks = ImmutableList.copyOf(Iterables.concat(
                    homeChunks,
                    ImmutableList.of(homeChunk)));

            incrementGenerationCount();
        }
    }

    /**
     * This will remove a home chunk from the list of chunks; but it cannot
     * remove the last chunk. If 'homeChunk' is not actually a home chunk of
     * this player, this method does nothing but returns true since the chunk is
     * trivially removed.
     *
     * @param homeChunk The chunk to remove.
     * @return True if the chunk is no longer present; false if it was the last
     * chunk.
     */
    public boolean tryRemoveHomeChunk(ChunkPosition homeChunk) {
        if (!homeChunks.contains(homeChunk)) {
            return true; // it was never there, good enough
        }

        if (homeChunks.size() == 1) {
            return false; // can't remove last chunk!
        }

        ArrayList<ChunkPosition> list = Lists.newArrayList(homeChunks);
        list.remove(homeChunk);
        homeChunks = ImmutableList.copyOf(list);
        incrementGenerationCount();
        return true;
    }

    /**
     * This method selects one of the home chunks and returns it.
     *
     * @param random The RNG used to pick the chunk.
     * @return One of the home chunks.
     * @throws IllegalStateException If there are no home chunks at all.
     */
    public ChunkPosition pickHomeChunk(Random random) {
        if (homeChunks.isEmpty()) {
            throw new IllegalStateException(
                    "pickHomeChunks can only be used if at least one chunk is assigned tot he player.");
        }

        int index = random.nextInt(homeChunks.size());
        return homeChunks.get(index);
    }

    /**
     * This method finds a place to put the player when he spawns. It will be at
     * the center of the home chunk of this player, but its y position is the
     * result of a search; we look for a non-air, non-liquid block with two air
     * blocks on top.
     *
     * @param server The server in which the player will spawn.
     * @param random The RNG used to pick the starting chunk.
     * @param picky The method fails if the player would be spawned in water or
     * lava.
     * @return The location to spawn him; null if no suitable location could be
     * found.
     */
    public Location findPlayerStartOrNull(Server server, Random random, boolean picky) {
        ChunkPosition pos = pickHomeChunk(random);
        World world = pos.getWorld(server);
        int blockX = pos.x * 16 + 8;
        int blockZ = pos.z * 16 + 8;

        final int startY = 253;

        // we spawn the player a bit in the air, since he falls a bit
        // while the world is loading. We need enough air for him to fall
        // through. 5 is as much as we can have without damaging the player on
        // landing.

        final int spawnHover = 4;
        final int spawnSpaceNeeded = spawnHover + 1;

        int airCount = 0;

        //wondering if we can trust this to always be higher than land, esp. in
        //amplified terrain. I've seen lots of 1.7 terrain far higher than this
        //and of course amplified can go higher still, and have multiple
        //airspaces above ground.
        //If we're using this for snowball targets, higher is better - chris
        for (int y = startY; y > 1; --y) {
            Block bl = world.getBlockAt(blockX, y, blockZ);

            if (bl.getType() == Material.AIR) {
                airCount++;
            } else if (airCount >= spawnSpaceNeeded) {
                if (picky && bl.isLiquid()) {
                    break;
                }

                return new Location(world, blockX, y + spawnHover, blockZ);
            } else {
                break;
            }
        }

        return null;
    }
    ////////////////////////////////
    // Generation Count
    private static final AtomicInteger playerInfoGenerationCount = new AtomicInteger();

    /**
     * This method returns a number that is incremented whenever any PlayerInfo
     * is created or changed. We can regenerated cached data when this changes.
     *
     * @return A number that changes when PlayerInfos do.
     */
    public static int getGenerationCount() {
        return playerInfoGenerationCount.get();
    }

    /**
     * This method increments the value getGenerationCount() returns; we call
     * this when any PlayerInfo is changed or even created.
     */
    private static void incrementGenerationCount() {
        playerInfoGenerationCount.incrementAndGet();
    }

    ////////////////////////////////
    // MapFileMap Storage
    public PlayerInfo(MapFileMap storage) {
        this.homeChunks = ImmutableList.copyOf(storage.getList("homes", ChunkPosition.class));
        incrementGenerationCount();
    }

    @Override
    public Map<?, ?> toMap() {
        Map<String, Object> map = Maps.newHashMap();
        map.put("homes", homeChunks);
        return map;
    }
}
