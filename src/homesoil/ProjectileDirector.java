/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package homesoil;

import com.google.common.base.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.plugin.*;
import org.bukkit.scheduler.*;
import org.bukkit.util.Vector;

import static java.lang.Math.*;

/**
 * This class runs 'in the background' and updates the velocity of a projectile
 * so it head to a particular position. It sort of bobbles because of the effect
 * of gravity; we keep 'bouncing' the projectile up so it does not hit the
 * ground.
 *
 * The begin() method creates one of these for a given projectile; when the
 * projectile hits something, or when it gets close enough to the destination,
 * we cancel this object so the projectile can then continue normally.
 *
 * @author DanJ
 */
public final class ProjectileDirector extends BukkitRunnable implements Listener {

    private final Projectile projectile;
    private final Location destination;
    private final double speed;

    private ProjectileDirector(Projectile projectile, Location destination, double speed) {
        this.projectile = Preconditions.checkNotNull(projectile);
        this.destination = Preconditions.checkNotNull(destination);
        this.speed = speed;
    }

    /**
     * This method starts up a directory to manage the projectile and send it to
     * the destination indicated.
     *
     * @param projectile The projectil to guide.
     * @param destination The place to send the projectile.
     * @param speed The speed (in blocks per tick, I think).
     * @param plugin Our plugin. There can be only one, probably.
     */
    public static void begin(Projectile projectile, Location destination, double speed, Plugin plugin) {
        ProjectileDirector director = new ProjectileDirector(projectile, destination, speed);
        plugin.getServer().getPluginManager().registerEvents(director, plugin);
        director.runTaskTimer(plugin, 1, 1);
    }

    @Override
    public void run() {
        // if the projectile has been removed from the game,
        // we'll give up on it.

        if (!projectile.isValid() || projectile.isDead()) {
            cancel();
            return;
        }

        Vector vec = projectile.getVelocity().clone();
        Location loc = projectile.getLocation();
        vec.setY(0);
        projectile.teleport(new Location(projectile.getWorld(), loc.getX(), destination.getY(), loc.getZ()));

        double dx = destination.getX() - loc.getX();
        double dz = destination.getZ() - loc.getZ();

        // If the projectile is close enough to the destination,
        // we'll give up on it.
        if (abs(dx) < speed && abs(dz) < speed) {
            cancel();
            return;
        }

        dx = max(-speed, dx);
        dx = min(speed, dx);
        vec.setX(dx);

        dz = max(-speed, dz);
        dz = min(speed, dz);
        vec.setZ(dz);

        projectile.setVelocity(vec);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (e.getEntity() == projectile) {
            this.cancel();
        }
    }
}
