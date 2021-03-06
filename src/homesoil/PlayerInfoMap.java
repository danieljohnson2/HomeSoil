package homesoil;

import static com.google.common.base.Objects.*;
import com.google.common.collect.*;
import java.io.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;

/**
 * This class holds the PlayerInfo objects, keyed by name. It will generate them
 * as needed.
 *
 * @author DanJ
 */
public final class PlayerInfoMap {

    public final static String COMMON_PLAYER_NAME = "COMMONS";
    
    private final Map<String, PlayerInfo> infos = Maps.newHashMap();
    private final Map<ChunkPosition, String> homeChunkOwners = Maps.newHashMap();
    private int homeChunkOwnersGenCount = 0;
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
            pickNewHomeChunk(onlinePlayer.getWorld(), info);
            infos.put(name, info);
        }

        return info;
    }

    /**
     * This method returns a new, immutable set containing all the players for
     * which isKnown() is true, even if they are not logged in or have never
     * played before. (Players who have never played can happen if you have
     * edited the HomeSoil.txt file yourself!)
     *
     * @return An immutable set containing all players.
     */
    public Set<OfflinePlayer> getKnownPlayers() {
        ImmutableSet.Builder<OfflinePlayer> b = ImmutableSet.builder();

        for (String playerName : infos.keySet()) {
            // getOfflinePlayer() returns non-null for any name;
            // you can create arbitrary 'offline player' objects.
            b.add(Bukkit.getOfflinePlayer(playerName));
        }

        return b.build();
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
     */
    public void removeHomeChunk(OfflinePlayer player, ChunkPosition homeChunk) {
        // if player is not yet known, there is no point to resetting his home
        // chunk. This will happen when he logs in!

        if (isKnown(player)) {
            PlayerInfo info = get(player);
            if (!info.tryRemoveHomeChunk(homeChunk)) {
                World world = homeChunk.getWorld();
                pickNewHomeChunk(world, info);
            }
        }
    }

    /**
     * This method adds a new home chunk for a player; if he already has the
     * chunk it does nothing. If the player is not known, we create the
     * PlayerInfo object, and it will then contain only the new chunk. After
     * this method, the player will be known (even if it was previously
     * unknown).
     *
     * @param player The player to give the new chunk to (may be offline).
     * @param newChunk The new chunk to give.
     */
    public void addHomeChunk(OfflinePlayer player, ChunkPosition newChunk) {
        String name = player.getName();

        PlayerInfo info = infos.get(name);

        if (info == null) {
            info = new PlayerInfo();
            infos.put(name, info);
        }

        info.addHomeChunk(newChunk);
    }

    /**
     * This method returns a set containing each chunk that is the home for any
     * player. The set is immutable and lazy allocated; a new one is allocated
     * if the player infos are ever changed.
     *
     * @return An immutable set of chunks that are occupied by a player.
     */
    public Set<ChunkPosition> getHomeChunks() {
        updateHomeChunkOwnersIfNeeded();
        return homeChunkOwners.keySet();
    }

    /**
     * This method returns a set containing each chunk that has ever been a home
     * chunk for anyone, even if it no longer is.
     *
     * @return An immutable set of chunks that have ever been occupied.
     */
    public Set<ChunkPosition> getHistoricalHomeChunks() {
        // This is rare enough that I'm sure we don't need to optimize,
        // so we'll just build the set every time.

        ImmutableSet.Builder<ChunkPosition> b = ImmutableSet.builder();

        for (PlayerInfo info : infos.values()) {
            b.addAll(info.getHistoricalHomeChunks());
        }

        return b.build();
    }

    /**
     * This obtains the name of the owner of the chunk indicated; if nobody owns
     * the chunk this returns the empty string.
     *
     * @param position The chunk to be checked.
     * @return The name of the chunk owner, or "".
     */
    public String identifyChunkOwner(ChunkPosition position) {
        updateHomeChunkOwnersIfNeeded();
        return firstNonNull(homeChunkOwners.get(position), "");
    }

    /**
     * This method checks the gen count to see if the home chunk owners map must
     * be regenerated, and if it needs to, it regenerates that map.
     */
    private void updateHomeChunkOwnersIfNeeded() {
        int currentGenCount = PlayerInfo.getGenerationCount();

        if (homeChunkOwnersGenCount != currentGenCount) {
            homeChunkOwnersGenCount = currentGenCount;

            homeChunkOwners.clear();

            for (Map.Entry<String, PlayerInfo> e : infos.entrySet()) {
                String playerName = e.getKey();
                PlayerInfo info = e.getValue();

                for (ChunkPosition homeChunk : info.getHomeChunks()) {
                    homeChunkOwners.put(homeChunk, playerName);
                }
            }
        }
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
        return getPlayerStart(player, player.getWorld());
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
     * @return The spawn location, or absent() if the player is unknown.
     */
    public Location getPlayerStart(OfflinePlayer player, World world) {
        PlayerInfo info = get(player);

        if (!info.getHomeChunks().isEmpty()) {
            ChunkPosition homeChunk = info.pickHomeChunk(random);
            Location spawn = findPlayerStartOrNull(homeChunk, true);

            if (spawn != null) {
                return spawn;
            }
        }

        return pickNewHomeChunk(world, info);
    }

    /**
     * This method returns all the player starts that can be found; there can be
     * as many as one per home chunk; if some can't be resolved anymore they
     * will be omitted. If the player is not known, this method returns an empty
     * list and does not assign a home chunk.
     *
     * @param player The player whose start locations are wanted.
     * @return The locations, in the order the home chunks were acquired. Each
     * location is newly allocated.
     */
    public List<Location> getPlayerStarts(OfflinePlayer player) {
        ImmutableList.Builder<Location> b = ImmutableList.builder();

        if (isKnown(player)) {
            PlayerInfo info = get(player);
            for (ChunkPosition homeChunk : info.getHomeChunks()) {
                Location spawn = findPlayerStartOrNull(homeChunk, false);

                if (spawn != null) {
                    b.add(spawn);
                }
            }
        }

        return b.build();
    }

    /**
     * This method finds a place to put the player when he spawns. It will be at
     * the center of the home chunk of this player, but its y position is the
     * result of a search; we look for a non-air, non-liquid block with two air
     * blocks on top.
     *
     * @param random The RNG used to pick the starting chunk.
     * @param picky The method fails if the player would be spawned in water or
     * lava.
     * @return The location to spawn him; null if no suitable location could be
     * found. This location is not cached, but a new one is always returned.
     */
    private static Location findPlayerStartOrNull(ChunkPosition homeChunk, boolean picky) {
        World world = homeChunk.getWorld();
        int blockX = homeChunk.x * 16 + 8;
        int blockZ = homeChunk.z * 16 + 8;

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

    /**
     * This method selects a home chunk and assigns it to the 'info' given; it
     * keeps trying to do this until it finds a valid home chunk. If it cannot
     * find one, it will throw an exception.
     *
     * @param world The world in which the home chunk should be found.
     * @param info The player info to be updated.
     * @return The spawn point for the player, as a convenience.
     */
    private Location pickNewHomeChunk(World world, PlayerInfo info) {
        // we'll try many times to find a spawn location
        // with a valid player start.
        for (int limit = 0; limit < 256; ++limit) {
            ChunkPosition homeChunk = getInitialChunkPosition(world);

            // after a while, we'll take what we can get!
            boolean picky = limit < 128;

            Location spawn = findPlayerStartOrNull(homeChunk, picky);
            if (spawn != null) {
                info.setHomeChunk(homeChunk);
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
        Set<ChunkPosition> oldHomes = getHistoricalHomeChunks();
        int numberOfHistoricalHomeChunks = oldHomes.size();
        int spawnRadiusInChunks = Math.max(1, (int) (Math.sqrt(numberOfHistoricalHomeChunks) * 16));

        // 64 output (sixteen discrete homechunks) gives about a -1000 to 1000 maximum range

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
    // Scoring
    //
    /**
     * This method sends messages to the recipient given listing the high
     * scoring players. We report the top three scores, but this may be more
     * than 3 players if there are ties. Also, players with only one chunk are
     * omitted.
     *
     * @param recipient The player to send the messages to.
     */
    public void sendScoresTo(CommandSender recipient) {
        OfflinePlayer[] players = Bukkit.getOfflinePlayers();
        List<Integer> scores = Lists.newArrayListWithCapacity(players.length);

        for (OfflinePlayer p : players) {
            if (isKnown(p) && !p.getName().equals(COMMON_PLAYER_NAME)) {
                int score = get(p).getHomeChunks().size();

                // We ignore players with only one chunk. That's
                // what you start with!

                if (score > 1 && !scores.contains(score)) {
                    scores.add(score);
                }
            }
        }

        Collections.sort(scores, Collections.reverseOrder());

        for (int rank = 0; rank < 3 && rank < scores.size(); ++rank) {
            int rankScore = scores.get(rank);

            for (OfflinePlayer p : players) {
                if (isKnown(p) && !p.getName().equals(COMMON_PLAYER_NAME)) {
                    int score = get(p).getHomeChunks().size();

                    if (score == rankScore) {
                        String msg = getScoreMessage(p, rank, score);
                        recipient.sendMessage(msg);
                    }
                }
            }
        }
    }

    /**
     * This method returns the message to send to report a particular
     * high-scoring player's score. The 'rank' indicates the gold/silver/ bronze
     * distinction; 0 is best and 1 is second best, etc.
     *
     * @param winner The high-scoring player
     * @param rank The rang (gold, silver, etc) of the winner.
     * @param score The score of the winner.
     * @return The message to send (including chat formatting codes).
     */
    private static String getScoreMessage(OfflinePlayer winner, int rank, int score) {
        String[] formats = {
            "§eGold: %s (%d chunks)§r",
            "§7Silver: %s (%d chunks)§r",
            "§6Bronze: %s (%d chunks)§r"
        };

        return String.format(formats[rank], winner.getName(), score);
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
