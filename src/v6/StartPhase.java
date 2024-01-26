package v6;

import battlecode.common.*;

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


    static MapLocation[] flags;
    static boolean random = false;
    static MapLocation target = null;
    public static void play(RobotController rc, boolean builder) throws GameActionException {
        if(flags == null)  flags = rc.senseBroadcastFlagLocations();
        if(!rc.isSpawned()) {
            spawn(rc);
            return;
        }

        if (builder) {
            for(Direction d : Direction.allDirections()) {
                MapLocation loc = rc.getLocation().add(d);
                if ((loc.x + loc.y)%2 == 1) {
                    /*if(Pathfinding.adjacentToDam(rc, loc) && rc.canBuild(TrapType.STUN, loc))
                        rc.build(TrapType.STUN, loc);
                    else*/ if(rc.canDig(loc) && rc.getLevel(SkillType.BUILD) != 6 && rc.senseMapInfo(loc).getCrumbs()==0 && !Pathfinding.adjacentToDam(rc, loc)) {
                        rc.dig(loc);
                        break;
                    }

                }
                /*if(rc.canSenseLocation(loc) && rc.getLevel(SkillType.BUILD) >= 4)
                    if(rc.senseMapInfo(loc).getSpawnZoneTeamObject()==rc.getTeam())
                        if(rc.canBuild(TrapType.EXPLOSIVE, loc))
                            rc.build(TrapType.EXPLOSIVE, loc);*/
            }
            if(rc.getRoundNum() < 90) {
                if(rc.getRoundNum()%20 == 0) random = !random;
                if(random) {
                    target = null;
                    Pathfinding.navigateRandomly(rc);
                    for(MapInfo m : rc.senseNearbyMapInfos(10)) {
                        if(m.isDam()) random = false;
                    }
                }
                else {
                    if(target == null) {
                        target = flags[FastMath.rand256()%flags.length];
                        target = new MapLocation(-target.x, -target.y);
                        Pathfinding.moveToward(rc, target);
                    }
                }
            }
            else if (rc.getRoundNum() < 140) {
                Pathfinding.moveToward(rc, mySpawn);
            }
            else moveTowardsEnemy(rc);
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
        if(target == null || rc.getRoundNum() == 140  || rc.getRoundNum() == 90)
            target = flags[FastMath.rand256() % flags.length];
        for(MapInfo mi : rc.senseNearbyMapInfos(2)) {
            if (mi.isDam()) return;
        }
        Pathfinding.moveToward(rc, target);
    }

    static MapLocation mySpawn;
    public static void spawn(RobotController rc) throws GameActionException {
        for(MapLocation spawn : Utils.spawnGroups[Utils.commId%3]) {
            if (rc.canSpawn(spawn)) {
                rc.spawn(spawn);
                mySpawn = spawn;
            }
        }
    }
}
