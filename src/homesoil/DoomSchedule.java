/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package homesoil;

import com.google.common.base.*;
import com.google.common.collect.*;
import java.io.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.event.*;
import org.bukkit.event.world.*;
import org.bukkit.scheduler.*;

/**
 * DoomSchedule regenerates the world, a chunk at a time. Each chunk is filled
 * with a Warning- presently a pillar of glowstone- before it is regenerated.
 * Home chunks are skipped.
 *
 * The dooms schedule passes through loaded chunks in straight lines. It is not
 * guaranteed to hit every chunk; it passes through a random x or z row, then
 * picks another. It will hit nether chunks if they are loaded, but not end
 * chunks.
 *
 * @author DanJ and Applejinx
 */
public final class DoomSchedule implements Listener {

    private final File regenFile;
    private final HomeSoilPlugin plugin;
    private BukkitTask nextDoomPillar;

    public DoomSchedule(HomeSoilPlugin plugin, File regenFile) {
        this.plugin = Preconditions.checkNotNull(plugin);
        this.regenFile = Preconditions.checkNotNull(regenFile);
    }
    ////////////////////////////////////////////////////////////////
    // Scheduling
    //
    /**
     * This delay is how long we wait between doom pillars; we compute this to
     * be long enough that only one pillar at a time is in play.
     */
    private final int doomChunkDelay = 256;
    private final int doomChunkLifetime = 128;

    /**
     * This method is called to start the task; it schedules it to run
     * regularly, and also hooks up events so we can tell what chunks are
     * loaded.
     *
     * @param plugin The plugin object for home soil; we register events with
     * this.
     */
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        runDoomScheduleLater();

        for (ChunkPosition where : loadDoomedChunks()) {
            System.out.println(String.format(
                    "Regenerating leftover chunk at %d, %d (%s)",
                    where.x * 16 + 8, where.z * 16 + 8, where.worldName));

            World world = where.getWorld();
            world.regenerateChunk(where.x, where.z);
        }
    }

    /**
     * This method is called to stop the task, when the plugin is disabled; it
     * also unregisters the events, so its safe to call start() again to begin
     * all over again.
     */
    public void stop() {
        if (nextDoomPillar != null) {
            nextDoomPillar.cancel();
        }

        HandlerList.unregisterAll(this);
        saveDoomedChunks();
    }

    /**
     * This method returns the number of ticks to wait between placing pillars.
     * This varies depending on how many chunks are loaded.
     *
     * @return The delay in ticks.
     */
    private long getDoomChunkDelay() {
        final long expectedChunksPerPlayer = 550;
        return (doomChunkDelay * expectedChunksPerPlayer) / Math.max(expectedChunksPerPlayer, loadedChunks.size());
        //The delay between pillars is a factor of how many players are online using up chunks in the game:
        //with multiple players in distinct locations, the pillar has less delay and moves faster.
        //Scales up to hundreds of players without overloading the server and causing lag.
        //We can go to nearly 4 and still run, but mob AI gets real jerky.
    }

    /**
     * This method schedules the next step in the doom schedule to run after the
     * delay that getDoomChunkDelay() provides.
     */
    private void runDoomScheduleLater() {
        long delay = getDoomChunkDelay();

        nextDoomPillar = new BukkitRunnable() {
            @Override
            public void run() {
                nextDoomPillar = null;
                runDoomSchedule();
            }
        }.runTaskLater(plugin, delay);
    }

    /**
     * This method runs the next step of the doom schedule; if we don't have
     * one, this will create a doom schedule and also do the first chunk in it.
     *
     * This method also calls runDoomScheduleLater() to schedule the next doom
     * chunk after this one.
     */
    private void runDoomSchedule() {
        runDoomScheduleLater();

        if (!loadedChunks.isEmpty()) {
            if (doomSchedule.isEmpty()) {
                prepareDoomSchedule();
            }

            if (!doomSchedule.isEmpty()) {
                ChunkPosition where = doomSchedule.get(0);
                if (!plugin.getPlayerInfos().getHomeChunks().contains(where)) {
                    beginPillarOfDoom(where);
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
    private final Set<ChunkPosition> doomedChunks = Sets.newHashSet();

    /**
     * This method starts the process of regenerating a chunk; it records the
     * chunk as 'doomed' in the doomed chunk file, and kicks off the lava
     * pillar.
     *
     * @param where The chunk that is doomed.
     */
    private void beginPillarOfDoom(ChunkPosition where) {
        if (doomedChunks.add(where)) {
            System.out.println(String.format(
                    "Doom at %d, %d (%s)", where.x * 16 + 8, where.z * 16 + 8, where.worldName));

            saveDoomedChunks();

            placePillarOfDoom(where);
            regenerateChunkLater(where, doomChunkLifetime);
        }
    }

    /**
     * This writes the set of doomed chunks out to a file. Recording these
     * chunks lets us regenerate them when the server restarts; this way we
     * don't leave abandoned doom pillars all over.
     */
    private void saveDoomedChunks() {
        MapFileMap map = new MapFileMap();
        map.put("doomed", doomedChunks);
        MapFileMap.write(regenFile, map);
    }

    /**
     * This reads the set of doomed chunks from the regen file; this does not
     * update 'doomedChunks', however; we instead regenerate them at once. This
     * is used at plugin startup.
     *
     * We do this so we don't leave abandoned doom pillars behind when the
     * server restarts.
     *
     * @return The retrieved chunks that are doomed.
     */
    private Set<ChunkPosition> loadDoomedChunks() {
        if (regenFile.exists()) {
            MapFileMap map = MapFileMap.read(regenFile);
            return map.getSet("doomed", ChunkPosition.class);
        } else {
            return Collections.emptySet();
        }
    }

    /**
     * This method places a doom pillar, a tall pillar of glowstone to make a
     * place that we will regenerate soon.
     *
     * @param where The chunk to be filled with doom.
     */
    private void placePillarOfDoom(ChunkPosition where) {
        World world = where.getWorld();

        int centerX = (where.x * 16) + 8;
        int centerZ = (where.z * 16) + 8;

        for (int y = 1; y < 255; ++y) {
            Location loc = new Location(world, centerX, y, centerZ);
            Block block = world.getBlockAt(loc);
            block.setType(Material.GLOWSTONE);
        }

        Location thunderLoc = new Location(world, centerX, 140, centerZ);
        float thunderPitch = 2.0f;
        world.playSound(thunderLoc, Sound.AMBIENCE_THUNDER, 9.0f, thunderPitch);
        thunderLoc = new Location(world, centerX, 1, centerZ);
        thunderPitch = 0.5f;
        world.playSound(thunderLoc, Sound.AMBIENCE_THUNDER, 13.0f, thunderPitch);
    }

    /**
     * This method regenerates a specified chunk, but does so after a specified
     * delay.
     *
     * @param where The chunk to regenerate.
     * @param delay The number of ticks to wait before doing so.
     */
    private void regenerateChunkLater(final ChunkPosition where, long delay) {
        new BukkitRunnable() {
            @Override
            public void run() {
                regenerateChunk(where);
            }
        }.runTaskLater(plugin, delay);
    }

    /**
     * This method regenerates a specific chunk. This will remove the chunk from
     * the list of doomed chunks, since it will not regenerate on server
     * restart.
     *
     * @param where The chunk to regenerate.
     */
    private void regenerateChunk(ChunkPosition where) {
        World world = where.getWorld();
        world.regenerateChunk(where.x, where.z);
        if (doomedChunks.remove(where)) {
            saveDoomedChunks();
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
            getLoadedChunkXRow(doomSchedule, origin);
        } else {
            getLoadedChunkZRow(doomSchedule, origin);
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
     * @param origin A chunk that is in the row we want.
     */
    private void getLoadedChunkXRow(Collection<ChunkPosition> destination, ChunkPosition origin) {
        for (ChunkPosition pos : loadedChunks) {
            if (pos.x == origin.x && pos.worldName.equals(origin.worldName)) {
                destination.add(pos);
            }
        }
    }

    /**
     * This method finds every loaded chunk whose 'z' co-ordinate matches the
     * parameter, and adds each one to 'destination'.
     *
     * @param destination The collection to populate.
     * @param origin A chunk that is in the row we want.
     */
    private void getLoadedChunkZRow(Collection<ChunkPosition> destination, ChunkPosition origin) {
        for (ChunkPosition pos : loadedChunks) {
            if (pos.z == origin.z && pos.worldName.equals(origin.worldName)) {
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

        if (chunk.getWorld().getEnvironment() != World.Environment.THE_END) {
            loadedChunks.add(ChunkPosition.of(chunk));
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        Chunk chunk = e.getChunk();

        if (chunk.getWorld().getEnvironment() != World.Environment.THE_END) {
            loadedChunks.remove(ChunkPosition.of(chunk));
        }
    }
}