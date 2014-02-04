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

    private ChunkPosition homeChunk = new ChunkPosition(0, 0, "world");

    public PlayerInfo() {
        incrementGenerationCount();
    }

    /**
     * This method returns the position of the home chunk of the player.
     *
     * @return The home chunk of this player.
     */
    public ChunkPosition getHomeChunk() {
        return homeChunk;
    }

    /**
     * this method updates the position of the home chunk of this player.
     *
     * @param pos The new home chunk of this player.
     */
    public void setHomeChunk(ChunkPosition pos) {
        homeChunk = Preconditions.checkNotNull(pos);
        incrementGenerationCount();
    }

    /**
     * This method finds a place to put the player when he spawns. It will be at
     * the center of the home chunk of this player, but its y position is the
     * result of a search; we look for a non-air, non-liquid block with two air
     * blocks on top.
     *
     * @param server The server in which the player will spawn.
     * @param picky The method fails if the player would be spawned in water or
     * lava.
     * @return The location to spawn him; absent if no suitable location could
     * be found.
     */
    public Optional<Location> findPlayerStart(Server server, boolean picky) {
        ChunkPosition pos = getHomeChunk();
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

                return Optional.of(new Location(world, blockX, y + spawnHover, blockZ));
            } else {
                break;
            }
        }

        return Optional.absent();
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
        this.homeChunk = storage.getValue("home", ChunkPosition.class);
        incrementGenerationCount();
    }

    @Override
    public Map<?, ?> toMap() {
        Map<String, Object> map = Maps.newHashMap();
        map.put("home", getHomeChunk());
        return map;
    }
}
