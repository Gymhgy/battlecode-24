package MicroV1;

import battlecode.common.*;
import v1.FastMath;

import java.util.HashSet;
import java.util.Random;

public strictfp class RobotPlayer {

    static final Random rng = new Random(23123);
    private static final int HEALING_CUTOFF = 500;
    static String indicator = "";
    /** Array containing all the possible movement directions. */
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
    static int BASE_ATTACK = 150;
    static int[] LEVEL_ATTACK = new int[] {
            BASE_ATTACK,
            (int)(1.05 * BASE_ATTACK),
            (int)(1.10 * BASE_ATTACK),
            (int)(1.15 * BASE_ATTACK),
            (int)(1.20 * BASE_ATTACK),
            (int)(1.30 * BASE_ATTACK),
            (int)(1.50 * BASE_ATTACK),
    };

    static int commId = 0;
    public static void spawn(RobotController rc) throws GameActionException {
        MapLocation[] spawnLocs = rc.getAllySpawnLocations();
        // Pick a random spawn location to attempt spawning in.
        MapLocation randomLoc = spawnLocs[rng.nextInt(spawnLocs.length)];
        if (rc.canSpawn(randomLoc)) rc.spawn(randomLoc);
    }


    static MapLocation target;
    static boolean builder = false;
    static MapLocation oldLoc;
    static Team opp;
    public static void play(RobotController rc) throws GameActionException {
        indicator = "";
        if (rc.getRoundNum() <= GameConstants.SETUP_ROUNDS) {
            return;
        }
        Pathfinding.dig = true;
        if(!rc.isSpawned()) {
            spawn(rc);
            return;
        }

        if(rc.canBuyGlobal(GlobalUpgrade.ACTION)) {
            rc.buyGlobal(GlobalUpgrade.ACTION);
            BASE_ATTACK += 75;
            LEVEL_ATTACK = new int[] {
                    BASE_ATTACK,
                    (int)(1.05 * BASE_ATTACK),
                    (int)(1.10 * BASE_ATTACK),
                    (int)(1.15 * BASE_ATTACK),
                    (int)(1.20 * BASE_ATTACK),
                    (int)(1.30 * BASE_ATTACK),
                    (int)(1.50 * BASE_ATTACK),
            };
        }
        else if(rc.canBuyGlobal(GlobalUpgrade.CAPTURING)) {
            rc.buyGlobal(GlobalUpgrade.CAPTURING);
        }
        else if(rc.canBuyGlobal(GlobalUpgrade.HEALING)) rc.buyGlobal(GlobalUpgrade.HEALING);

        sense(rc);
        for(FlagInfo f : rc.senseNearbyFlags(2, opp)) {
            if (rc.canPickupFlag(f.getLocation()) && rc.getRoundNum() % 2 == 0) {
                rc.pickupFlag(f.getLocation());
                target = null;
                break;
            }
        }
        micro(rc);
        macro(rc);

        if(rc.isActionReady()) {
            sense(rc);
            micro(rc);
        }

    }
    static boolean hadFlag = false;
    static MapLocation builderTarget = null;

    static void build(RobotController rc) throws GameActionException  {
        if(closeFriendsSize > 2 &&
                (enemyCnt > 1 || rc.senseMapInfo(rc.getLocation()).getTeamTerritory()==rc.getTeam().opponent()) &&
                rc.canBuild(TrapType.STUN, rc.getLocation()))
            rc.build(TrapType.STUN, rc.getLocation());

        else if ((enemyCnt > 1 || FastMath.rand256()%6==1 || rc.senseMapInfo(rc.getLocation()).getTeamTerritory()==rc.getTeam().opponent())
                && rc.canBuild(TrapType.EXPLOSIVE, rc.getLocation())) {
            rc.build(TrapType.EXPLOSIVE, rc.getLocation());
        }

    }

    static MapLocation myClosestSpawn = null;
    private static void macro(RobotController rc) throws GameActionException {
        findFlag(rc);

        if (!rc.isMovementReady())
            return;

        int minDist = Integer.MAX_VALUE;
        if(rc.hasFlag()) {
            if (myClosestSpawn == null) {
                for (MapLocation spawn : rc.getAllySpawnLocations()) {
                    if(spawn.distanceSquaredTo(rc.getLocation()) < minDist) {
                        minDist = spawn.distanceSquaredTo(rc.getLocation());
                        myClosestSpawn = spawn;
                    }
                }
            }
            Pathfinding.moveToward(rc, myClosestSpawn);
            return;
        }

        if(closeFriendsSize < 3 && (rc.getRoundNum() - lastLauncherAttackRound) < 10) {
            if (rc.isMovementReady() && groupingTarget != null ) {
                indicator += "group,";
                if (!rc.getLocation().isAdjacentTo(groupingTarget.location)) {
                    Pathfinding.follow(rc, groupingTarget.location);
                } else if (rc.getHealth() < groupingTarget.health) { // allowing healthier target to move away first
                    indicator += "stop";
                    rc.setIndicatorDot(rc.getLocation(), 0, 255, 0);
                    return;
                }
                rc.setIndicatorLine(rc.getLocation(), groupingTarget.location, 0, 255, 0);
            } else if (rc.isMovementReady()
                    && groupingTarget == null
                    && cachedGroupingTarget != null
                    && rc.getRoundNum() - cachedGroupingRound < 6
                    && !rc.getLocation().isAdjacentTo(cachedGroupingTarget.location)){
                indicator += String.format("cacheGroup%s,",cachedGroupingTarget.location);
                Pathfinding.follow(rc, cachedGroupingTarget.location);
                rc.setIndicatorLine(rc.getLocation(), cachedGroupingTarget.location, 0, 255, 0);
            }
        }

        int byteCode = Clock.getBytecodesLeft();
        if(wander > 0) Pathfinding.navigateRandomly(rc);
        else if(flagLoc != null && !(pickedUp && rc.getRoundNum() % 2 == 0))
            indicator += Pathfinding.moveToward(rc, flagLoc);
        else if (approxFlagLog != null)
            indicator += Pathfinding.moveToward(rc, approxFlagLog);
        else
            Pathfinding.randomMove(rc);
        if(Clock.getBytecodesLeft() < 2000) {
            System.out.println(byteCode + " " + Clock.getBytecodesLeft());
        }
    }

    static MapLocation flagLoc = null;
    static MapLocation approxFlagLog = null;
    static boolean pickedUp = false;
    static void findFlag(RobotController rc) throws GameActionException {
        int minDist = Integer.MAX_VALUE;
        MapLocation potentialFlagLoc = null;
        boolean flagFound = false;
        boolean approxFound = false;
        boolean pickedUp = false;
        MapLocation oldApprox = approxFlagLog;
        if(flagLoc != null &&
                rc.getLocation().isWithinDistanceSquared(flagLoc, 20) &&
                !existsFlagAt(rc,flagLoc)) {
            flagLoc = null;
        }
        for (FlagInfo flag : rc.senseNearbyFlags(GameConstants.VISION_RADIUS_SQUARED, rc.getTeam().opponent())) {
            approxFlagLog = null;
            if (flag.getLocation().distanceSquaredTo(rc.getLocation()) < minDist) {
                potentialFlagLoc = flag.getLocation();
                pickedUp = flag.isPickedUp();
                minDist = flag.getLocation().distanceSquaredTo(rc.getLocation());
            }
            if (flagLoc != null && flag.getLocation().equals(flagLoc)) {
                flagFound = true;
            }
        }

        if(!flagFound || flagLoc == null) {
            flagLoc = potentialFlagLoc;
            wander = 0;
            //if(oldApprox != null && flagLoc != null) Communicator.reportFlag(rc, oldApprox, potentialFlagLoc);
        }
        if(wander > 0) {wander--; return;}
        if(approxFlagLog != null &&
                rc.getLocation().isWithinDistanceSquared(approxFlagLog, 20) &&
                !existsFlagAt(rc,approxFlagLog)) {
            blacklist.add(approxFlagLog);
            wander = 10;
            approxFlagLog = null;
        }
        minDist = Integer.MAX_VALUE;
        for (MapLocation loc : rc.senseBroadcastFlagLocations()) {
            //if(blacklist.contains(loc)) continue;
            if (loc.distanceSquaredTo(rc.getLocation()) < minDist) {
                approxFlagLog = loc;
                minDist = loc.distanceSquaredTo(rc.getLocation()) + 50;
            }
        }
        if(flagLoc == null && approxFlagLog != null) {
            //MapLocation potential = Communicator.approxToActual(rc, approxFlagLog);
            //if(!potential.equals(approxFlagLog)) {
              //  flagLoc = potential;
            //}
        }
        if(flagLoc != null &&
                rc.getLocation().isWithinDistanceSquared(flagLoc, 20) &&
                !existsFlagAt(rc,flagLoc)) {
            flagLoc = null;
        }
    }
    static int wander;
    static HashSet<MapLocation> blacklist = new HashSet<>();
    static boolean existsFlagAt(RobotController rc, MapLocation loc) throws GameActionException {
        for (FlagInfo flag : rc.senseNearbyFlags(GameConstants.VISION_RADIUS_SQUARED, rc.getTeam().opponent())) {
            if(flag.getLocation() == loc) return true;
        }
        return false;
    }

    static boolean isDiagonal(Direction dir) {
        return dir.dx * dir.dy != 0;
    }
    private static void chase(RobotController rc, MapLocation location) throws GameActionException {
        Direction forwardDir = rc.getLocation().directionTo(location);
        Direction[] dirs = {forwardDir, forwardDir.rotateLeft(), forwardDir.rotateRight(),
                forwardDir.rotateLeft().rotateLeft(), forwardDir.rotateRight().rotateRight()};
        Direction bestDir = null;
        int minCanSee = Integer.MAX_VALUE;
        // pick a direction to chase to minimize the number of enemy launchers that can see us
        for (Direction dir : dirs) {
            if (rc.canMove(dir) && rc.getLocation().add(dir).distanceSquaredTo(location) <= 4) {
                int canSee = 0;
                for (int i = enemyCnt; --i >= 0;){
                    int newDis = rc.getLocation().add(dir).distanceSquaredTo(enemies[i].location);
                    if (newDis <= 20) {
                        canSee++;
                    }
                }
                if (minCanSee > canSee) {
                    bestDir = dir;
                    minCanSee = canSee;
                } else if (minCanSee == canSee && isDiagonal(bestDir) && !isDiagonal(dir)) {
                    // from Cow: we prefer non-diagonal moves to preserve formation
                    bestDir = dir;
                }
            }
        }
        if (bestDir != null) {
            indicator += String.format("chase%s,", location);
            rc.move(bestDir);
        } else {
            indicator += "failchase,";
        }
    }

    //region <vars>
    static RobotInfo attackTarget = null;
    static RobotInfo backupTarget = null;
    static RobotInfo chaseTarget = null;

    private static final int MAX_ENEMY_CNT = 8;
    static RobotInfo[] enemies = new RobotInfo[MAX_ENEMY_CNT];
    static int enemyCnt;
    private static final int MAX_FRIENDLY_CNT = 6;
    static RobotInfo[] allies = new RobotInfo[MAX_FRIENDLY_CNT];
    static int friendCnt;

    static RobotInfo groupingTarget = null;
    static RobotInfo cachedGroupingTarget = null;
    static int cachedGroupingRound = -1000;
    static int lastLauncherAttackRound = -100;
    static int ourTeamStrength = 1;
    static MapLocation cachedEnemyLocation = null;
    static int cachedRound = 0;
    private static int closeFriendsSize = 0;
    //endregion
    static void sense(RobotController rc) throws GameActionException {
        attackTarget = null;
        chaseTarget = null;
        groupingTarget = null;
        backupTarget = null;
        ourTeamStrength = 1;
        friendCnt = 0;
        enemyCnt = 0;
        closeFriendsSize = 0;
        int backupTargetDis = Integer.MAX_VALUE;
        MapLocation loc = flagLoc != null ? flagLoc : approxFlagLog != null ? approxFlagLog : null;
        for (RobotInfo robot : rc.senseNearbyRobots()) {
            if (robot.team == rc.getTeam()) {
                if (friendCnt >= MAX_FRIENDLY_CNT) {
                    continue;
                }
                if (groupingTarget == null
                        || robot.hasFlag
                        || robot.getHealth() > groupingTarget.getHealth()
                        || (loc != null && robot.getHealth() == groupingTarget.getHealth() &&
                                robot.location.distanceSquaredTo(loc) < groupingTarget.location.distanceSquaredTo(loc))
                ) {
                    groupingTarget = robot;
                }
                allies[friendCnt++] = robot;
                ourTeamStrength += 1;
                if (robot.location.distanceSquaredTo(rc.getLocation()) <= 8){
                    closeFriendsSize++;
                }
            }
            else {
                if (enemyCnt >= MAX_ENEMY_CNT) {
                    continue;
                }
                enemies[enemyCnt++] = robot;
                ourTeamStrength -= 1;
                if (robot.location.distanceSquaredTo(rc.getLocation()) > GameConstants.ATTACK_RADIUS_SQUARED) {
                    chaseTarget = robot;
                }
            }
        }
        attackTarget = chooseAttackTarget(rc);
    }

    static void micro(RobotController rc) throws GameActionException {
        RobotInfo target = attackTarget == null? backupTarget : attackTarget;
        if (target != null) {
            if (target == attackTarget) {
                lastLauncherAttackRound = rc.getRoundNum();
            }
            RobotInfo deadTarget = null;
            if (rc.canAttack(target.location)) {
                cachedRound = rc.getRoundNum();
                if (target.health <= LEVEL_ATTACK[rc.getLevel(SkillType.ATTACK)]) {
                    deadTarget = target;
                }
                rc.attack(target.location);
            }
            // find the closest guy alive, cache him and kite back
            int minDis = Integer.MAX_VALUE;
            cachedEnemyLocation = null;
            for (int i = enemyCnt; --i >= 0;) {
                RobotInfo enemy = enemies[i];
                int dis = enemy.location.distanceSquaredTo(rc.getLocation());
                if (enemy != deadTarget && dis < minDis) {
                    cachedEnemyLocation = enemy.location;
                    minDis = dis;
                }
            }
            if (cachedEnemyLocation == null && backupTarget != null && backupTarget != deadTarget) {
                cachedEnemyLocation = backupTarget.location;
            }
            if (cachedEnemyLocation != null && rc.isMovementReady()) {
                kite(rc, cachedEnemyLocation);
            }
        }
        if(rc.isActionReady()) heal(rc);
        if (rc.getHealth() < HEALING_CUTOFF) {
            // go back to heal if possible, no chasing
            return;
        }
        if (rc.isMovementReady() && rc.isActionReady()) {
            if (chaseTarget != null) {
                cachedEnemyLocation = chaseTarget.location;
                cachedRound = rc.getRoundNum();
                if (rc.getHealth() > chaseTarget.health || ourTeamStrength > 2) {
                    chase(rc, chaseTarget.location);
                } else { // we are at disadvantage, pull back
                    kite(rc, chaseTarget.location);
                }
            } else if (cachedEnemyLocation != null && rc.getRoundNum() - cachedRound <= 2) {
                chase(rc, cachedEnemyLocation);
            }
        }
    }
    static void kite(RobotController rc, MapLocation location) throws GameActionException {

        Direction backDir = rc.getLocation().directionTo(location).opposite();
        Direction[] dirs = {Direction.CENTER, backDir, backDir.rotateLeft(), backDir.rotateRight(),
                backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
        Direction bestDir = null;
        int minCanSee = Integer.MAX_VALUE;
        for (Direction dir : dirs) {
            if (rc.canMove(dir)) {
                int canSee = 0;
                for (int i = enemyCnt; --i >= 0;){
                    int newDis = rc.getLocation().add(dir).distanceSquaredTo(enemies[i].location);
                    if (newDis <= 10) {
                        canSee++;
                    }
                }
                if (minCanSee > canSee) {
                    bestDir = dir;
                    minCanSee = canSee;
                }  else if (minCanSee == canSee && isDiagonal(bestDir) && !isDiagonal(dir)) {
                    // from Cow: we prefer non-diagonal moves to preserve formation
                    bestDir = dir;
                }
            }
        }
        if (bestDir != null && bestDir != Direction.CENTER){
            indicator += "kite,";
            rc.move(bestDir);
        }
    }

    public static RobotInfo chooseAttackTarget(RobotController rc) {
        int minHitsReq = Integer.MAX_VALUE;
        int minDist = Integer.MIN_VALUE;
        int byteCode = Clock.getBytecodesLeft();
        RobotInfo ret = null;
        for (int i = enemyCnt; i-->0;) {
            //if(Clock.getBytecodesLeft() < 1000) {
            //    System.out.println(byteCode + " " + Clock.getBytecodesLeft());
            //}
            RobotInfo enemy = enemies[i];
            int dist = enemy.location.distanceSquaredTo(rc.getLocation());
            if(!rc.canAttack(enemy.location))
                continue;
            if(enemy.hasFlag) return enemy;
            if(LEVEL_ATTACK[rc.getLevel(SkillType.ATTACK)] >= enemy.getHealth())
                return enemy;

            int friendDamage = 0;
            int friends = 1;
            for(int j = friendCnt; j-- > 0;) {
                int friendDist = allies[j].location.distanceSquaredTo(enemy.location);
                if (friendDist <= GameConstants.ATTACK_RADIUS_SQUARED) {
                    friendDamage += LEVEL_ATTACK[allies[j].attackLevel];
                    friends ++;
                }
            }
            int hitsReq = (enemy.health + friendDamage - 1) / LEVEL_ATTACK[rc.getLevel(SkillType.ATTACK)] / friends;

            if (hitsReq < minHitsReq || (hitsReq == minHitsReq && dist < minDist)) {
                minHitsReq = hitsReq;
                ret = enemy;
                minDist = dist;
            }
        }
        return ret;

    }

    public static void heal(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return;
        RobotInfo target = chooseHealTarget(rc);
        if (target != null) {
            rc.heal(target.location);
        }
    }

    public static RobotInfo chooseHealTarget(RobotController rc) {
        int minHp = Integer.MAX_VALUE;
        RobotInfo ret = null;
        for(int i = 0; i < friendCnt; i++) {
            if(allies[i].health < minHp && rc.canHeal(allies[i].getLocation())) {
                ret = allies[i];
                minHp = allies[i].health;
            }
        }
        return ret;
    }

    public static void endTurn(RobotController rc) {
        if(!builder && flagLoc != null) indicator+="flagLoc: "+flagLoc.toString()+",";
        if(!builder && approxFlagLog != null) indicator+="approxFlagLog: "+approxFlagLog.toString()+",";
        if(!builder && flagLoc == null && approxFlagLog == null) indicator += "null,";
        rc.setIndicatorString(indicator);
        if (builderTarget != null && rc.isSpawned())
            rc.setIndicatorLine(rc.getLocation(), builderTarget, 255,0,0);
        else if (flagLoc != null && rc.isSpawned())
            rc.setIndicatorLine(rc.getLocation(), flagLoc, 255,0,0);
        else if (approxFlagLog != null && rc.isSpawned())
            rc.setIndicatorLine(rc.getLocation(), approxFlagLog, 255,0,0);
    }

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        FastMath.initRand(rc);
        while (true) {

            try {
                play(rc);
                endTurn(rc);
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();

            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();

            } finally {
                Clock.yield();
            }
        }

    }
}
