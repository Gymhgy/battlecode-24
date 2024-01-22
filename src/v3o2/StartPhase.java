package v3o2;

import battlecode.common.*;

import java.nio.file.Path;

public class StartPhase {

    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };



    static MapLocation target = null;
    public static void play(RobotController rc, boolean builder) throws GameActionException {

        if(!rc.isSpawned()) {
            spawn(rc);
            return;
        }

        if (builder) {
            for(Direction d : Direction.allDirections()) {
                MapLocation loc = rc.getLocation().add(d);
                if ((loc.x + loc.y)%2 == 1) {
                    if(rc.canDig(loc) && rc.getLevel(SkillType.BUILD) != 6 && rc.senseMapInfo(loc).getCrumbs()==0 && !Pathfinding.adjacentToDam(rc, loc)) {
                        rc.dig(loc);
                        break;
                    }
                }
            }
            if(rc.getRoundNum() < 140)
                Pathfinding.navigateRandomly(rc);
            else
                moveTowardsEnemy(rc);
        }

        if (rc.getRoundNum() < 140) {
            if(target == null) {
                acquireTarget(rc);
            }
            else if (rc.canSenseLocation(target) && rc.senseMapInfo(target).getCrumbs() == 0)
                acquireTarget(rc);
            if(target != null) {
                Pathfinding.moveToward(rc, target);
            }
            else {
                Pathfinding.navigateRandomly(rc);
            }
        }
        else {
            moveTowardsEnemy(rc);
        }
    }

    public static void acquireTarget(RobotController rc) throws GameActionException {
        MapLocation[] c = rc.senseNearbyCrumbs(GameConstants.VISION_RADIUS_SQUARED);
        int dist = Integer.MAX_VALUE;
        MapLocation crumb = null;
        for(int i = c.length; i-->0; ) {
            if(c[i].distanceSquaredTo(rc.getLocation()) < dist) {
                dist = c[i].distanceSquaredTo(rc.getLocation());
                crumb = c[i];
            }
        }
        target = crumb;
    }

    public static void moveTowardsEnemy(RobotController rc) throws GameActionException {
        if(rc.getRoundNum() == 140) {
            MapLocation[] flags = rc.senseBroadcastFlagLocations();
            target = flags[FastMath.rand256() % flags.length];
        }
        for(MapInfo mi : rc.senseNearbyMapInfos(2)) {
            if (mi.isDam()) return;
        }
        Pathfinding.moveToward(rc, target);
    }

    public static void spawn(RobotController rc) throws GameActionException {
        for(MapLocation spawn : Utils.spawnGroups[Utils.commId%3]) {
            if (rc.canSpawn(spawn)) rc.spawn(spawn);
        }
    }
}
