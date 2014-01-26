package homesoil;

import com.google.common.collect.*;
import java.io.*;
import java.util.*;
import java.util.logging.*;
import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.plugin.java.*;

// TODO: eye of ender for each player
// TODO: eye of ender leads (or teleports) to player home chunk
// TODO: players can take over other players chunks
/**
 * This is the plugin class itself, which acts as the main entry point for a
 * Bukkit plugin. This also doubles as the listener, and handles events for us.
 *
 * @author DanJ
 */
public class HomeSoilPlugin extends JavaPlugin implements Listener {

    private final Set<ChunkPosition> alreadyLoadedOnce = Sets.newHashSet();
    private final PlayerInfoMap playerInfos = new PlayerInfoMap(
            new File("HomeSoil.txt"),
            this);

    @Override
    public void onEnable() {
        super.onEnable();

        playerInfos.load();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (!playerInfos.isKnown(e.getPlayer())) {
            PlayerInfo info = playerInfos.get(e.getPlayer());
            Location spawn = info.findPlayerStart(getServer());
            e.getPlayer().teleport(spawn);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        e.setRespawnLocation(playerInfos.get(e.getPlayer()).findPlayerStart(getServer()));
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        Chunk ch = e.getChunk();
        ChunkPosition pos = ChunkPosition.of(ch);

        if (alreadyLoadedOnce.add(pos)) {
            Logger logger = getLogger();

            if (!e.isNewChunk()) {
                if (playerInfos.getHomeChunks().contains(pos)) {
                    logger.info(String.format("Unable to regenerate [%s] as it is a home chunk.", pos));
                } else {
                    ch.getWorld().regenerateChunk(pos.x, pos.z);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        PlayerInfo info = playerInfos.get(e.getPlayer());
        ChunkPosition home = info.getHomeChunk();

        boolean wasHome = home.contains(e.getFrom());
        boolean isHome = home.contains(e.getTo());

        if (wasHome != isHome) {
            if (isHome) {
                e.getPlayer().chat("You have entered your home chunk");
            } else {
                e.getPlayer().chat("You have exited your home chunk");
            }
        }
    }
}
