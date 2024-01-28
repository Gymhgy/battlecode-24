package testingDummy;

import battlecode.common.*;

import java.util.Random;

public class StartPhase {

    public static void play(RobotController rc) throws GameActionException {
        if(!rc.isSpawned()) {
            spawn(rc);
        }
        if(!rc.isSpawned()) {
            return;
        }
        if(rc.canPickupFlag(rc.getLocation())) rc.pickupFlag(rc.getLocation());
    }

    public static void spawn(RobotController rc) throws GameActionException {
        MapLocation[] spawnLocs = rc.getAllySpawnLocations();
        // Pick a random spawn location to attempt spawning in.
        Utils.init(rc);
        MapLocation randomLoc = Utils.spawnCenters[new Random(rc.getID()).nextInt(3)];
        if (rc.canSpawn(randomLoc)) rc.spawn(randomLoc);
    }
}
