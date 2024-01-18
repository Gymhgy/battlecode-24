package v3.v2;

import battlecode.common.*;

import static v2.RobotPlayer.directions;
import static v2.RobotPlayer.rng;

public class StartPhase {


    static MapLocation target = null;
    public static void play(RobotController rc, boolean builder) throws GameActionException {

        if(!rc.isSpawned()) {
            spawn(rc);
            return;
        }
        // Move randomly if no objective.
        Direction dir = directions[rng.nextInt(directions.length)];

        if (builder) {
            for(Direction d : Direction.allDirections()) {
                MapLocation loc = rc.getLocation().add(d);
                if ((loc.x + loc.y)%2 == 1) {
                    if(rc.canDig(loc)) {
                        rc.dig(loc);
                        break;
                    }
                }
            }
            if (rc.canMove(dir)) {
                rc.move(dir);
            }
            return;
        }

        if (rc.getRoundNum() < 180) {
            for (MapLocation loc : rc.senseNearbyCrumbs(GameConstants.VISION_RADIUS_SQUARED)) {
                target = loc;
                break;
            }
            if(target != null) {
                Pathfinding.moveToward(rc, target);
            }
            else {
                if (rc.canMove(dir)) {
                    rc.move(dir);
                }
            }
        }
        else {
            moveTowardsEnemy(rc);
        }



    }

    public static void moveTowardsEnemy(RobotController rc) throws GameActionException {
        int minDist = Integer.MAX_VALUE;
        for (MapLocation loc : rc.senseBroadcastFlagLocations()) {
            if (loc.distanceSquaredTo(rc.getLocation()) < minDist) {
                target = loc;
                minDist = loc.distanceSquaredTo(rc.getLocation());
            }
        }
        for(MapInfo mi : rc.senseNearbyMapInfos(2)) {
            if (mi.isDam()) return;
        }
        Pathfinding.moveToward(rc, target);
    }

    public static void spawn(RobotController rc) throws GameActionException {
        MapLocation[] spawnLocs = rc.getAllySpawnLocations();
        for (MapLocation loc : spawnLocs) {
            if(rc.canSpawn(loc)) {
                rc.spawn(loc);
                return;
            }
        }
    }
}
