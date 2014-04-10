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

// TODO: we need to respawn the chunk with the lava pillar in it on server restart.
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

    public DoomSchedule(HomeSoilPlugin plugin) {
        this.plugin = Preconditions.checkNotNull(plugin);
    }
    ////////////////////////////////////////////////////////////////
    // Scheduling
    //
    /**
     * This delay is how many ticks we wait between segments of the doom
     * pillars.
     */
    private final int doomSegmentDelay = 16;
    /**
     * This delay is how long we wait between doom pillars; we compute this to
     * be long enough that only one pillar at a time is in play.
     */
    private final int doomChunkDelay = doomSegmentDelay * 16;

    /**
     * This method is called to start the task; it schedules it to run
     * regularly, and also hooks up events so we can tell what chunks are
     * loaded.
     *
     * @param plugin The plugin object for home soil; we register events with
     * this.
     */
    public void start(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        runTaskTimer(plugin, 10, doomChunkDelay);
    }

    /**
     * This method is called to stop the task, when the plugin is disabled; it
     * also unregisters the events, so its safe to call start() again to begin
     * all over again.
     */
    public void stop() {
        cancel();
        HandlerList.unregisterAll(this);
    }

    @Override
    public void run() {
        if (!loadedChunks.isEmpty()) {
            if (doomSchedule.isEmpty()) {
                prepareDoomSchedule();
            }

            if (!doomSchedule.isEmpty()) {
                ChunkPosition where = doomSchedule.get(0);
                if (!plugin.getPlayerInfos().getHomeChunks().contains(where)) {
                    //System.out.println(String.format(
                    //      "Doom at %d, %d", where.x * 16 + 8, where.z * 16 + 8));
                    // Removed server log message because placing is so constant
                    placeSegmentOfDoomLater(where, 15);
                }
                //we need to remove the entry whether or not we placed a pillar
                //because if it's a home chunk, otherwise it freezes
                doomSchedule.remove(0);
            }
        }
    }
    ////////////////////////////////////////////////////////////////
    // Pillars of Doom
    //

    /**
     * This method does not do anything now, but schedules a segment of the
     * pillar; this segment fills a 16x16x16 area, specified in chunk
     * co-ordinates. The topmost chunk is at chunkY=15.
     *
     * We start the doom pillar by passing 15 here. After a delay, the chunk
     * will be updated, and then this method is called again to do the next
     * lower chunk. When we hit 0, we regenerate instead of building a doom
     * pillar.
     *
     * @param where The chunk to be filled with doom.
     * @param chunkY The y-chunk to affect. If 0, we regenerate instead.
     */
    private void placeSegmentOfDoomLater(final ChunkPosition where, final int chunkY) {
        new BukkitRunnable() {
            @Override
            public void run() {
                placeSegmentOfDoom(where, chunkY);
            }
        }.runTaskLater(plugin, doomSegmentDelay);
    }

    /**
     * This method places a doom pillar segment; if chunkY is 0, this
     * regenerates the chunk.
     *
     * If it does not regenerate the chunk, then it will schedule the next chunk
     * of the pillar. This means we don't need to keep so many runnables alive
     * at once.
     *
     * @param where The chunk to be filled with doom.
     * @param chunkY The y-chunk to affect. If 0, we regenerate instead.
     */
    private void placeSegmentOfDoom(ChunkPosition where, int chunkY) {
        World world = where.getWorld(plugin.getServer());

        if (chunkY <= 0) {
            world.regenerateChunk(where.x, where.z);
        } else {
            int top = chunkY * 16;
            int centerX = (where.x * 16) + 8;
            int centerZ = (where.z * 16) + 8;

            for (int y = top - 16; y < top; ++y) {
                Location loc = new Location(world, centerX, y, centerZ);
                Block block = world.getBlockAt(loc);
                block.setType(Material.LAVA);
            }

            Location thunderLoc = new Location(world, centerX, top, centerZ);
            float thunderPitch = (0.5f + (top / 512));
            world.playSound(thunderLoc, Sound.AMBIENCE_THUNDER, 8.0f, thunderPitch);

            placeSegmentOfDoomLater(where, chunkY - 1);
        }
    }
    ////////////////////////////////////////////////////////////////
    // Path of Doom
    //
    private final List<ChunkPosition> doomSchedule = Lists.newArrayList();
    private final Random regenRandom = new Random();

    /**
     * This method generates the doomSchedule, the list of chunks we mean to
     * visit. Once a chunk is doomed, nothing (but a server reset) can save it.
     * We pick randomly which direction to run the schedule.
     */
    private void prepareDoomSchedule() {
        boolean isX = regenRandom.nextBoolean();
        boolean reversed = regenRandom.nextBoolean();
        int index = regenRandom.nextInt(loadedChunks.size());
        ChunkPosition origin = loadedChunks.get(index);

        doomSchedule.clear();

        if (isX) {
            getLoadedChunkXRow(doomSchedule, origin.x);
        } else {
            getLoadedChunkZRow(doomSchedule, origin.z);
        }

        if (reversed) {
            Collections.sort(doomSchedule, Collections.reverseOrder());
        } else {
            Collections.sort(doomSchedule);
        }
    }

    /**
     * This method finds every loaded chunk whose 'x' co-ordinate matches the
     * parameter, and adds each one to 'destination'.
     *
     * @param destination The collection to populate.
     * @param x The chunk-x co-ordinate for the row you want.
     */
    private void getLoadedChunkXRow(Collection<ChunkPosition> destination, int x) {
        for (ChunkPosition pos : loadedChunks) {
            if (pos.x == x) {
                destination.add(pos);
            }
        }
    }

    /**
     * This method finds every loaded chunk whose 'z' co-ordinate matches the
     * parameter, and adds each one to 'destination'.
     *
     * @param destination The collection to populate.
     * @param z The chunk-z co-ordinate for the row you want.
     */
    private void getLoadedChunkZRow(Collection<ChunkPosition> destination, int z) {
        for (ChunkPosition pos : loadedChunks) {
            if (pos.z == z) {
                destination.add(pos);
            }
        }
    }
    ////////////////////////////////////////////////////////////////
    // Chunk tracking
    //
    private final List<ChunkPosition> loadedChunks = Lists.newArrayList();

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