/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package homesoil;

import static java.lang.Math.*;

import com.google.common.base.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.plugin.*;
import org.bukkit.scheduler.*;
import org.bukkit.inventory.meta.*;

import org.bukkit.util.Vector;
import org.bukkit.FireworkEffect.Type;

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

    private ProjectileDirector(Projectile projectile, Location destination) {
        this.projectile = Preconditions.checkNotNull(projectile);
        this.destination = Preconditions.checkNotNull(destination);
    }

    /**
     * This method starts up a directory to manage the projectile and send it to
     * the destination indicated.
     *
     * @param projectile The projectile to guide.
     * @param destination The place to send the projectile.
     * @param plugin Our plugin. There can be only one, probably.
     */
    public static void begin(Projectile projectile, Location destination, Plugin plugin) {
        ProjectileDirector director = new ProjectileDirector(projectile, destination);
        plugin.getServer().getPluginManager().registerEvents(director, plugin);
        director.runTaskTimer(plugin, 1, 1);
    }

    @Override
    public void run() {
        if (!projectile.isValid() || projectile.isDead()) {
            cancel();
            return;
        }
        // if the projectile has been removed from the game,
        // we'll give up on it.

        Vector vec = projectile.getVelocity().clone();
        Location loc = projectile.getLocation();

        double dx = destination.getX() - loc.getX();
        double dy = (destination.getY() + 2) - loc.getY(); //target two blocks off the ground: head height!
        double dz = destination.getZ() - loc.getZ();

        // If the projectile is close enough to the destination,
        // we'll give up on it.
        double dlength = sqrt((dx * dx) + (dy * dy) + (dz * dz));
        if (dlength < 8) { //we have this distance number, so we'll use that
            //we're ditching the snowball at 8 as it slows down the closer you get
            // make something cool happen when the end is reached; visible from 128 blocks away.
            // This is just a burst of fire (goes with the fireticks effect, good)
            projectile.getWorld().playEffect(loc, Effect.MOBSPAWNER_FLAMES, null, 128);

            //If possible I would like to have just the burst at ground level (perhaps combined with
            //the flames effect, perhaps instead of) but I admit the firework is cool :)
            //You can launch tons of them by firing normal snowballs inside your home chunk

            cancel();
            return;
        }

        double dfast = sqrt(dlength) / 100;
        //speed is an indicator of how far you are. We tweak that here, not when calling it
        //apparently Java's sqrt is already competitive with gamer inverse sqrt

        dx = dx / dlength;
        dy = dy / dlength;
        dz = dz / dlength;
        //just make it scaled to length of 'one'

        dx = dx * dfast;
        dy = dy * dfast;
        dz = dz * dfast;
        //restore velocity after normalizing

        vec.setX(dx);
        vec.setY(dy);
        vec.setZ(dz);

        projectile.setVelocity(vec);
       //attempt to read projectile for name, didn't work because
       //no projectile has a name: this is a routine linked to the specific
       //projectile. I don't want to just duplicate it needlessly, we need to pass
        //in a 'named target' boolean or something. If there was a name when
        //it was launched, we also do
        projectile.setFireTicks(100);
        
        //if there was no name, the snowball homes to destination but is not on fire.
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (e.getEntity() == projectile) {
            this.cancel();
        }
    }
}
