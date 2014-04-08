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
import org.bukkit.event.*;
import org.bukkit.event.world.*;
import org.bukkit.plugin.*;
import org.bukkit.scheduler.*;

/**
 * DoomSchedule regenerates the world, a chunk at a time. Each chunk is filled
 * with a Warning- presently a stream of lava- before it is regenerated. Home
 * chunks are skipped.
 *
 * The dooms schedule passes through loaded chunks in straight lines. It is not
 * guaranteed to hit every chunk; it passes through a random x or z row, then
 * picks another.
 *
 * @author DanJ and AppleJinx
 */
public final class DoomSchedule extends BukkitRunnable implements Listener {

    private final HomeSoilPlugin plugin;
    private final List<ChunkPosition> loadedChunks = Lists.newArrayList();
    private final List<ChunkPosition> doomSchedule = Lists.newArrayList();
    private final Random regenRandom = new Random();

    public DoomSchedule(HomeSoilPlugin plugin) {
        this.plugin = Preconditions.checkNotNull(plugin);
    }

    public void start(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        runTaskTimer(plugin, 10, doomChunkDelay);
    }

    public void stop() {
        cancel();
        HandlerList.unregisterAll(this);
    }

    @Override
    public void run() {
        placeNextPillarOfDoom();
    }

    private void placeNextPillarOfDoom() {
        if (!loadedChunks.isEmpty()) {
            if (doomSchedule.isEmpty()) {
                prepareDoomSchedule();
            }

            if (!doomSchedule.isEmpty()) {
                ChunkPosition where = doomSchedule.get(0);
                if (!plugin.getPlayerInfos().getHomeChunks().contains(where)) {
                    placePillarOfDoomLater(where);
                }

                //we need to remove the entry whether or not we placed a pillar
                //because if it's a home chunk, otherwise it freezes
                doomSchedule.remove(0);
            }
        }
    }

    private void prepareDoomSchedule() {
        switch (regenRandom.nextInt(4)) {
            case 0:
                prepareDoomScheduleX(true);
                break;

            case 1:
                prepareDoomScheduleX(false);
                break;

            case 2:
                prepareDoomScheduleZ(true);
                break;

            case 3:
                prepareDoomScheduleZ(false);
                break;

            default:
                throw new IllegalStateException("This can't happen!");
        }
    }

    private void prepareDoomScheduleX(boolean reversed) {
        int index = regenRandom.nextInt(loadedChunks.size());

        int z = loadedChunks.get(index).z;

        for (ChunkPosition pos : loadedChunks) {
            if (pos.z == z) {
                doomSchedule.add(pos);
            }
        }

        if (reversed) {
            Collections.sort(doomSchedule, Collections.reverseOrder());
        } else {
            Collections.sort(doomSchedule);
        }
    }

    private void prepareDoomScheduleZ(boolean reversed) {
        int index = regenRandom.nextInt(loadedChunks.size());

        int x = loadedChunks.get(index).x;

        for (ChunkPosition pos : loadedChunks) {
            if (pos.x == x) {
                doomSchedule.add(pos);
            }
        }

        if (reversed) {
            Collections.sort(doomSchedule, Collections.reverseOrder());
        } else {
            Collections.sort(doomSchedule);
        }
    }

    private void placePillarOfDoomLater(ChunkPosition where) {
        System.out.println(String.format(
                "Doom at %d, %d", where.x * 16 + 8, where.z * 16 + 8));

        placeSegmentOfDoomLater(where, 16);
    }

    private void placeSegmentOfDoomLater(final ChunkPosition where, final int chunkY) {
        new BukkitRunnable() {
            @Override
            public void run() {
                placeSegmentOfDoom(where, chunkY);
            }
        }.runTaskLater(plugin, doomCubeDelay);
    }

    private void placeSegmentOfDoom(ChunkPosition where, int chunkY) {
        World world = where.getWorld(plugin.getServer());

        if (chunkY <= 0) {
            world.regenerateChunk(where.x, where.z);
        } else {
            int top = chunkY * 16;
            int startX = (where.x * 16) + 8;
            int startZ = (where.z * 16) + 8;

            for (int y = top - 16; y < top; ++y) {
                Location loc = new Location(world, startX, y, startZ);
                Block block = world.getBlockAt(loc);
                block.setType(Material.LAVA);
                world.playSound(loc, Sound.AMBIENCE_THUNDER, 0.5f, 10f);
            }

            placeSegmentOfDoomLater(where, chunkY - 1);
        }
    }
    private final int doomCubeDelay = 8;
    private final int doomChunkDelay = doomCubeDelay * 32;

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        Chunk chunk = e.getChunk();

        if (chunk.getWorld().getEnvironment() == World.Environment.NORMAL) {
            loadedChunks.add(ChunkPosition.of(chunk));
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        Chunk chunk = e.getChunk();

        if (chunk.getWorld().getEnvironment() == World.Environment.NORMAL) {
            loadedChunks.remove(ChunkPosition.of(chunk));
        }
    }    
}