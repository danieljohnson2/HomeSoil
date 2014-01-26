/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package homesoil;

import com.google.common.base.*;
import com.google.common.collect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;

/**
 * This class holds the HomeSoil data for a particular player and is saved to
 * the HomeSoil file.
 *
 * @author DanJ
 */
public final class PlayerInfo implements MapFileMap.Storable {

    private ChunkPosition homeChunk;

    public PlayerInfo(ChunkPosition homeChunk) {
        this.homeChunk = Preconditions.checkNotNull(homeChunk);
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
    }

    /**
     * this method finds a place to put the player when he spawns. It will be at
     * the center of the home chunk of this player, but its y position is the
     * result of a search; we look for a non-air block with two air blocks on
     * top.
     *
     * @param server The server in which the player will spawn.
     * @return The location to spawn him.
     */
    public Location findPlayerStart(Server server) {
        ChunkPosition pos = getHomeChunk();
        World world = pos.getWorld(server);
        int blockX = pos.x * 16 + 8;
        int blockZ = pos.z * 16 + 8;

        final int startY = 100;
        int airCount = 0;

        for (int y = startY; y > 1; --y) {
            Block bl = world.getBlockAt(blockX, y, blockZ);

            if (bl.getType() == Material.AIR) {
                airCount++;
            } else if (airCount >= 2) {
                return new Location(world, blockX, y + 1, blockZ);
            }
        }

        airCount = 0;

        for (int y = startY - 2; y < 255; ++y) {
            Block bl = world.getBlockAt(blockX, y, blockZ);

            if (bl.getType() == Material.AIR) {
                airCount++;
            } else if (airCount >= 2) {
                return new Location(world, blockX, y - 2, blockZ);
            }
        }

        return new Location(world, blockX, startY, blockZ);
    }

    ////////////////////////////////
    // MapFileMap Storage
    public PlayerInfo(MapFileMap storage) {
        this.homeChunk = storage.getValue("home", ChunkPosition.class);
    }

    @Override
    public Map<?, ?> toMap() {
        Map<String, Object> map = Maps.newHashMap();
        map.put("home", getHomeChunk());
        return map;
    }
}