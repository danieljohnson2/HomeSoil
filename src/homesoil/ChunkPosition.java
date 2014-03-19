package homesoil;

import com.google.common.base.*;
import com.google.common.collect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;

/**
 * This class holds a position of a chunk in the world; it identifies the world
 * by name just so this class can just hold simple data, not references to big
 * complex objects. The plain data fields are directly exposed; there's no point
 * in pretending that this class is object-oriented. It's a record.
 *
 * @author DanJ
 */
public final class ChunkPosition implements MapFileMap.Storable {

    public final int x;
    public final int z;
    public final String worldName;

    public ChunkPosition(int x, int z, World world) {
        this(x, z, world.getName());
    }

    public ChunkPosition(int x, int z, String worldName) {
        this.x = x;
        this.z = z;
        this.worldName = Preconditions.checkNotNull(worldName);
    }

    /**
     * This method returns the position of the chunk given.
     *
     * @param chunk The chunk whose position is wanted.
     * @return A new position object.
     */
    public static ChunkPosition of(Chunk chunk) {
        return new ChunkPosition(chunk.getX(), chunk.getZ(), chunk
                .getWorld());
    }

    /**
     * This method returns the chunk that contains the location given.
     *
     * @param location The location that identifies the chunk.
     * @return The chunk position of the chunk that contains the location.
     */
    public static ChunkPosition of(Location location) {
        return of(location.getChunk());
    }

    /**
     * This method returns the world that contains the chunk named.
     *
     * @param server THe server that contains the world objects.
     * @return The specific world that contains this position.
     * @throws IllegalStateException If the world named does not exist at all.
     */
    public World getWorld(Server server) {
        World world = server.getWorld(worldName);

        if (world == null) {
            throw new IllegalStateException(String.format(
                    "The world '%s' could not be found.", worldName));
        }

        return world;
    }

    /**
     * This method returns true if the entity given is in the chunk indicated by
     * this position.
     *
     * @param entity The entity to check.
     * @return True if the entity is in the chunk.
     */
    public boolean contains(Entity entity) {
        if (entity.getWorld().getName().equals(worldName)) {
            Chunk ch = entity.getLocation().getChunk();
            return ch.getX() == x && ch.getZ() == z;
        }

        return false;
    }

    /**
     * This method returns true if the location given is in the chunk indicated
     * by this position.
     *
     * @param entity The entity to check.
     * @return True if the entity is in the chunk.
     */
    public boolean contains(Location location) {
        if (location.getWorld().getName().equals(worldName)) {
            Chunk ch = location.getChunk();
            return ch.getX() == x && ch.getZ() == z;
        }

        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + x;
        result = prime * result + z;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof ChunkPosition) {
            ChunkPosition other = (ChunkPosition) obj;

            return this.x == other.x && this.z == other.z
                    && this.worldName.equals(other.worldName);
        }

        return false;
    }

    @Override
    public String toString() {
        return String.format("%d, %d, %s", x, z, worldName);
    }

    ////////////////////////////////
    // MapFileMap Storage
    public ChunkPosition(MapFileMap map) {
        this.x = map.getInteger("x");
        this.z = map.getInteger("z");
        this.worldName = map.getString("world");
    }

    @Override
    public Map<?, ?> toMap() {
        Map<String, Object> map = Maps.newHashMap();
        map.put("x", x);
        map.put("z", z);
        map.put("world", worldName);
        return map;
    }
}
