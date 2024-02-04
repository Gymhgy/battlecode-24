package v10off2;

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
    static int flagId = 0;
    static MapLocation dropSpot;
    public static void play(RobotController rc, boolean builder, boolean sentry) throws GameActionException {
        if(flags == null)  flags = Communicator.senseFlagsAtStart(rc);
        if(!rc.isSpawned()) {
            spawn(rc, sentry);
            if(!rc.isSpawned())
                return;
        }

        if(rc.getRoundNum() == 199) Communicator.rememberFlags(rc);
        if (sentry) {
            rc.setIndicatorDot(rc.getLocation(), 255,255,255);
            if(rc.getRoundNum() < 198) {
                MapLocation closestFlag = null;
                for(MapLocation f : Communicator.myFlags) {
                    if (f == null) continue;
                    if (f.isWithinDistanceSquared(rc.getLocation(), 2)) continue;
                    if (f.isWithinDistanceSquared(rc.getLocation(),36)) closestFlag = f;
                }
                for(Direction d : Direction.allDirections()) {
                    if (rc.canPickupFlag(rc.getLocation().add(d))) {
                        rc.pickupFlag(rc.getLocation().add(d));
                        flagId = Communicator.getInt(rc.getLocation().add(d))-1;
                    }
                    if(!rc.onTheMap(rc.getLocation().add(d)) && closestFlag == null) {
                        dropSpot = rc.getLocation();
                        Communicator.droppedFlagAt(rc);
                        Communicator.rememberFlags(rc);
                        return;
                    }
                }
                if(closestFlag == null) {
                    target = flags[0];
                    for (MapLocation enemy : flags) {
                        if (target.distanceSquaredTo(rc.getLocation()) > enemy.distanceSquaredTo(rc.getLocation())) {
                            target = enemy;
                        }
                    }
                }
                else {
                    target = closestFlag;
                }

                MapLocation myLoc = rc.getLocation();
                int targetX = myLoc.x * 2 - target.x;
                int targetY = myLoc.y * 2 - target.y;
                target = new MapLocation(targetX, targetY);
                rc.setIndicatorLine(rc.getLocation(), target, 255,252,123);
                navigateToLocationFuzzy(rc, target);
            }
            else {
                switch(rc.getRoundNum()) {
                    case 198:
                        rc.dropFlag(rc.getLocation());
                        break;
                    case 199:
                        /*if(rc.canBuild(TrapType.STUN, rc.getLocation())) {
                            rc.build(TrapType.STUN, rc.getLocation());
                        }*/
                        break;
                    case 200:
                        if(rc.canBuild(TrapType.WATER, rc.getLocation().add(rc.getLocation().directionTo(flags[0])))) {
                            rc.build(TrapType.WATER, rc.getLocation().add(rc.getLocation().directionTo(flags[0])));
                        }
                        Communicator.wipe(rc);
                        return;
                    default:
                        break;
                }
            }
            dropSpot = rc.getLocation();
            Communicator.droppedFlagAt(rc);
            Communicator.rememberFlags(rc);
            return;
        }


        if (builder) {
            for(Direction d : Direction.allDirections()) {
                MapLocation loc = rc.getLocation().add(d);
                if ((loc.x + loc.y)%2 == 1) {
                    /*if(Pathfinding.adjacentToDam(rc, loc) && rc.canBuild(TrapType.STUN, loc))
                        rc.build(TrapType.STUN, loc);
                    else*/ if(rc.canDig(loc) && rc.getLevel(SkillType.BUILD) != 6 && rc.senseMapInfo(loc).getCrumbs()==0) {
                        boolean shouldBuild = true;
                        for (Direction dd : Direction.allDirections()) {
                            if(!rc.canSenseLocation(loc.add(dd))) continue;
                            if (rc.senseMapInfo(loc.add(dd)).isDam() || rc.senseMapInfo(loc.add(dd)).isWall())
                                shouldBuild = false;
                        }
                        if(shouldBuild) {
                            rc.dig(loc);
                            break;
                        }
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
                        MapLocation myLoc = rc.getLocation();
                        int targetX = myLoc.x * 2 - target.x;
                        int targetY = myLoc.y * 2 - target.y;
                        target = new MapLocation(targetX, targetY);
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
    public static void spawn(RobotController rc, boolean sentry) throws GameActionException {
        if(sentry) {
            rc.spawn(Utils.spawnCenters[Utils.commId%3]);
            return;
        }
        for(MapLocation spawn : Utils.spawnGroups[Utils.commId%3]) {
            if (rc.canSpawn(spawn)) {
                rc.spawn(spawn);
                mySpawn = spawn;
            }
        }
    }

    static boolean canPass(RobotController rc, Direction d) throws GameActionException {
        for(MapLocation f : Communicator.myFlags) {
            if (f == null) continue;
            if (f.isWithinDistanceSquared(rc.getLocation(), 2)) continue;
            if (f.isWithinDistanceSquared(rc.getLocation().add(d),36)) return false;
        }
        return rc.canMove(d);
    }
    public static boolean navigateToLocationFuzzy(RobotController rc, MapLocation targetLoc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        if (canPass(rc, myLoc.directionTo(targetLoc))) {
            rc.move(myLoc.directionTo(targetLoc));
        } else if (canPass(rc, myLoc.directionTo(targetLoc).rotateLeft())) {
            rc.move(myLoc.directionTo(targetLoc).rotateLeft());
        } else if (canPass(rc, myLoc.directionTo(targetLoc).rotateRight())) {
            rc.move(myLoc.directionTo(targetLoc).rotateRight());
        } else if (canPass(rc, myLoc.directionTo(targetLoc).rotateLeft().rotateLeft())) {
            rc.move(myLoc.directionTo(targetLoc).rotateLeft().rotateLeft());
        } else if (canPass(rc, myLoc.directionTo(targetLoc).rotateRight().rotateRight())) {
            rc.move(myLoc.directionTo(targetLoc).rotateRight().rotateRight());
        } else if (canPass(rc, myLoc.directionTo(targetLoc).rotateLeft().rotateLeft().rotateLeft())) {
            rc.move(myLoc.directionTo(targetLoc).rotateLeft().rotateLeft().rotateLeft());
        } else if (canPass(rc, myLoc.directionTo(targetLoc).rotateRight().rotateRight().rotateRight())) {
            rc.move(myLoc.directionTo(targetLoc).rotateRight().rotateRight().rotateRight());
        } else if (canPass(rc, myLoc.directionTo(targetLoc).rotateRight().rotateRight().rotateRight().rotateRight())) {
            rc.move(myLoc.directionTo(targetLoc).rotateRight().rotateRight().rotateRight().rotateRight());
        }
        return true;
    }

}
