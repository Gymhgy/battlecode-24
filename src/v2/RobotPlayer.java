package v2;

import battlecode.common.*;

import java.util.Random;


public strictfp class RobotPlayer {

    static final Random rng = new Random(23123);
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
    static final int BASE_ATTACK = 150;
    static final int[] LEVEL_ATTACK = new int[] {
            BASE_ATTACK,
            (int)(1.05 * BASE_ATTACK),
            (int)(1.10 * BASE_ATTACK),
            (int)(1.15 * BASE_ATTACK),
            (int)(1.20 * BASE_ATTACK),
            (int)(1.30 * BASE_ATTACK),
            (int)(1.50 * BASE_ATTACK),
    };
    static RobotInfo[] enemies, allies;
    static int enemyCnt, allyCnt;
    static int ourStr;
    static MapLocation kiteTarget;
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
        if (rc.getRoundNum() < GameConstants.SETUP_ROUNDS) {
            StartPhase.play(rc, builder);
            return;
        }
        Pathfinding.dig = true;
        if(!rc.isSpawned()) {
            spawn(rc);
            return;
        }

        if(rc.canBuyGlobal(GlobalUpgrade.ACTION)) rc.buyGlobal(GlobalUpgrade.ACTION);
        else if(rc.canBuyGlobal(GlobalUpgrade.HEALING)) rc.buyGlobal(GlobalUpgrade.HEALING);

        //SETUP KNOWN QUANTITIES
        opp = rc.getTeam().opponent();
        enemies = rc.senseNearbyRobots(GameConstants.VISION_RADIUS_SQUARED, opp);
        enemyCnt = enemies.length;
        allies = rc.senseNearbyRobots(GameConstants.VISION_RADIUS_SQUARED, rc.getTeam());
        allyCnt = allies.length;
        ourStr = Math.max(6, allyCnt) - enemyCnt;
        oldLoc = rc.getLocation();
        //MICRO
        if (rc.getRoundNum() < 205) {
            if (rc.canBuild(TrapType.EXPLOSIVE, rc.getLocation())) {
                if (builder || FastMath.rand256()%20==1) {
                    rc.build(TrapType.EXPLOSIVE, rc.getLocation());
                }
            }
        }
        else if(builder && enemyCnt > 4) {
            if (rc.canBuild(TrapType.EXPLOSIVE, rc.getLocation())) {
                if (rc.getCrumbs() > 1000 || FastMath.rand256()%5==1) {
                    rc.build(TrapType.EXPLOSIVE, rc.getLocation());
                }
            }
        }
        if (enemies.length > 0)
            attack(rc);
        else if(allies.length > 0)
            heal(rc);
        kiteOrChase(rc);
        for(FlagInfo f : rc.senseNearbyFlags(2, opp)) {
            if (rc.canPickupFlag(f.getLocation())) {
                rc.pickupFlag(f.getLocation());
                target = null;
                break;
            }
        }

        //MACRO
        stickWithTeam(rc);
        if (rc.isMovementReady() && !builder) {
            flagMove(rc);
        }

        //MICRO, AGAIN
        if (enemies.length > 0)
            attack(rc);
        else if(allies.length > 0)
            heal(rc);
        kiteOrChase(rc);

        Communicator.writeSector(rc, commId, rc.getLocation());
    }

    static void flagMove(RobotController rc) throws GameActionException {
        int minDist = Integer.MAX_VALUE;
        if(rc.hasFlag()) {
            if (target == null) {
                for (MapLocation spawn : rc.getAllySpawnLocations()) {
                    if(spawn.distanceSquaredTo(rc.getLocation()) < minDist) {
                        minDist = spawn.distanceSquaredTo(rc.getLocation());
                        target = spawn;
                    }
                }
            }
        }
        else {
            for (MapLocation loc : rc.senseBroadcastFlagLocations()) {
                if (loc.distanceSquaredTo(rc.getLocation()) < minDist) {
                    target = loc;
                    minDist = loc.distanceSquaredTo(rc.getLocation());
                }
            }
            for (FlagInfo flag : rc.senseNearbyFlags(GameConstants.VISION_RADIUS_SQUARED, opp)) {
                if (flag.getLocation().distanceSquaredTo(rc.getLocation()) < minDist) {
                    target = flag.getLocation();
                    minDist = flag.getLocation().distanceSquaredTo(rc.getLocation());
                }
            }
        }

        int byteCode = Clock.getBytecodesLeft();
        if(target != null)
            indicator += Pathfinding.moveToward(rc, target);
        else
            Pathfinding.randomMove(rc);
        if(Clock.getBytecodesLeft() < 2000) {
            System.out.println(byteCode + " " + Clock.getBytecodesLeft());
        }
    }
    static boolean isDiagonal(Direction dir) {
        return dir.dx * dir.dy != 0;
    }
    private static void kiteOrChase(RobotController rc) throws GameActionException {
        if(ourStr > 2 || kiteTarget == null) return;
        Direction backDir = rc.getLocation().directionTo(kiteTarget).opposite();
        Direction[] dirs = {Direction.CENTER, backDir, backDir.rotateLeft(), backDir.rotateRight(),
                backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
        Direction bestDir = null;
        int minCanSee = Integer.MAX_VALUE;
        for (Direction dir : dirs) {
            if (rc.canMove(dir)) {
                int canSee = 0;
                for (int i = enemyCnt; --i >= 0;){
                    int newDis = rc.getLocation().add(dir).distanceSquaredTo(enemies[i].location);
                    if (newDis <= 6) {
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

    public static void attack(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return;
        RobotInfo target = chooseAttackTarget(rc);
        if (target != null) {
            MapLocation towards = rc.getLocation().add(rc.getLocation().directionTo(target.location));
            if(FastMath.rand256()%3 == 1 && enemyCnt > 5 && rc.canBuild(TrapType.EXPLOSIVE, towards)) {
                rc.build(TrapType.EXPLOSIVE, towards);
            }
            else {
                rc.attack(target.location);
            }
            kiteTarget = target.location;
        }
    }

    public static RobotInfo chooseAttackTarget(RobotController rc) {
        int minHitsReq = Integer.MAX_VALUE;
        int minDist = Integer.MIN_VALUE;
        int byteCode = Clock.getBytecodesLeft();
        RobotInfo ret = null;
        for (int i = 0; i < enemies.length; i++) {
            //if(Clock.getBytecodesLeft() < 1000) {
            //    System.out.println(byteCode + " " + Clock.getBytecodesLeft());
            //}
            RobotInfo enemy = enemies[i];
            int dist = enemy.location.distanceSquaredTo(rc.getLocation());
            if(!rc.canAttack(enemy.location))
                continue;
            if(LEVEL_ATTACK[rc.getLevel(SkillType.ATTACK)] >= enemy.getHealth())
                return enemy;

            int friendDamage = 0;
            int friends = 1;
            for(int j = 0; j < allies.length; j++) {
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
        if (target != null)
            rc.heal(target.location);
    }

    //TODO: make healing better
    public static RobotInfo chooseHealTarget(RobotController rc) {
        int minHp = Integer.MAX_VALUE;
        RobotInfo ret = null;
        for(int i = 0; i < allies.length; i++) {
            if(allies[i].health < minHp && rc.canHeal(allies[i].getLocation())) {
                ret = allies[i];
                minHp = allies[i].health;
            }
        }
        return ret;
    }

    public static void endTurn(RobotController rc) {
        if (target != null)
            indicator += target.toString() + " ";
        rc.setIndicatorString(indicator);
        if (target != null && rc.isSpawned())
            rc.setIndicatorLine(rc.getLocation(), target, 255,0,0);
    }

    public static void stickWithTeam(RobotController rc) throws GameActionException {
        if (allyCnt <= 2 || builder) {
            if(rc.isMovementReady()) {
                Pathfinding.moveToward(rc, rc.getAllySpawnLocations()[FastMath.rand256()%27]);
                //indicator += "[stick: " + Communicator.maxSector(rc) + "] ";
            }
        }

    }

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        if(rc.readSharedArray(0) < 3) {
            builder = true;
            commId = rc.readSharedArray(0)+1;
            rc.writeSharedArray(0, commId);
        }
        FastMath.initRand(rc);
        Communicator.init(rc);
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
