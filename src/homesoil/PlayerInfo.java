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
    private final List<ChunkPosition> homeChunks = Lists.newArrayList();
    private final Set<ChunkPosition> historicalHomeChunks = Sets.newHashSet();

    /**
     * This method returns an immutable list that contains each home chunk
     * belonging to this player; this is empty only if the player info is not
     * yet initialized, and the chunks are in the order they were acquired.
     *
     * @return The list of home chunks of the list.
     */
    public List<ChunkPosition> getHomeChunks() {
        return Collections.unmodifiableList(homeChunks);
    }

    /**
     * This method returns a set containing every chunk this player has ever
     * owned, even if he does not own the chunk anymore.
     *
     * @return An unmodifiable set of the chunks.
     */
    public Set<ChunkPosition> getHistoricalHomeChunks() {
        return Collections.unmodifiableSet(historicalHomeChunks);
    }

    /**
     * This method assigns a single home chunk to the player; if he had any
     * chunks, they will be removed in favor of this new one.
     *
     * @param pos The new home chunk.
     */
    public void setHomeChunk(ChunkPosition homeChunk) {
        homeChunks.clear();
        homeChunks.add(homeChunk);
        historicalHomeChunks.add(homeChunk);
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
            homeChunks.add(homeChunk);
            historicalHomeChunks.add(homeChunk);

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

        homeChunks.remove(homeChunk);
        // we do not alter historicalHomeChunks; the whole point of that
        // is to remember what we used to own.
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
                    "pickHomeChunks can only be used if at least one chunk is assigned to the player.");
        }

        int index = random.nextInt(homeChunks.size());
        return homeChunks.get(index);
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
        if (storage.containsKey("homes")) {
            this.homeChunks.addAll(storage.getList("homes", ChunkPosition.class));
        }

        if (storage.containsKey("historicalHomes")) {
            this.historicalHomeChunks.addAll(storage.getSet("historicalHomes", ChunkPosition.class));
        }

        incrementGenerationCount();
    }

    @Override
    public Map<?, ?> toMap() {
        Map<String, Object> map = Maps.newHashMap();
        map.put("homes", homeChunks);
        map.put("historicalHomes", historicalHomeChunks);
        return map;
    }
}
