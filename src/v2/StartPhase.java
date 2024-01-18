package v1;

import battlecode.common.*;

import static v1.RobotPlayer.directions;
import static v1.RobotPlayer.rng;

public class StartPhase {

    public static void play(RobotController rc) throws GameActionException {
        if(!rc.isSpawned()) {
            spawn(rc);
            return;
        }
        // Move and attack randomly if no objective.
        Direction dir = directions[rng.nextInt(directions.length)];
        MapLocation nextLoc = rc.getLocation().add(dir);
        if (rc.canMove(dir)){
            rc.move(dir);
        }

        if (rc.canBuild(TrapType.STUN, rc.getLocation()) && rng.nextInt() % 30 == 1)
            rc.build(TrapType.STUN, rc.getLocation());
    }

    public static void spawn(RobotController rc) throws GameActionException {
        MapLocation[] spawnLocs = rc.getAllySpawnLocations();
        // Pick a random spawn location to attempt spawning in.
        MapLocation randomLoc = spawnLocs[rng.nextInt(spawnLocs.length)];
        if (rc.canSpawn(randomLoc)) rc.spawn(randomLoc);
    }
}
