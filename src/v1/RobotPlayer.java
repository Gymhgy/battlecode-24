package v1;

import battlecode.common.*;

import java.awt.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;


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
    public static void spawn(RobotController rc) throws GameActionException {
        MapLocation[] spawnLocs = rc.getAllySpawnLocations();
        // Pick a random spawn location to attempt spawning in.
        MapLocation randomLoc = spawnLocs[rng.nextInt(spawnLocs.length)];
        if (rc.canSpawn(randomLoc)) rc.spawn(randomLoc);
    }


    static MapLocation target;
    public static void play(RobotController rc) throws GameActionException {
        indicator = "";
        if (rc.getRoundNum() < GameConstants.SETUP_ROUNDS) {
            StartPhase.play(rc);
            return;
        }

        if(!rc.isSpawned()) {
            spawn(rc);
            return;
        }

        if(rc.canBuyGlobal(GlobalUpgrade.ACTION)) rc.buyGlobal(GlobalUpgrade.ACTION);
        else if(rc.canBuyGlobal(GlobalUpgrade.HEALING)) rc.buyGlobal(GlobalUpgrade.HEALING);

        //SETUP KNOWN QUANTITIES
        Team opp = rc.getTeam().opponent();
        enemies = rc.senseNearbyRobots(GameConstants.VISION_RADIUS_SQUARED, opp);
        allies = rc.senseNearbyRobots(GameConstants.VISION_RADIUS_SQUARED, rc.getTeam());




        //MICRO
        if (enemies.length > 0)
            attack(rc);
        else if(allies.length > 0)
            heal(rc);
        for(FlagInfo f : rc.senseNearbyFlags(2, opp)) {
            if (rc.canPickupFlag(f.getLocation())) {
                rc.pickupFlag(f.getLocation());
                target = null;
                break;
            }
        }
        MapStore.updateMap(rc);
        rc.writeSharedArray(0, rc.readSharedArray(0) & MapStore.possibleSymmetry);
        //MACRO
        int minDist = Integer.MAX_VALUE;
        MapLocation bestLoc;

        if (rc.isMovementReady()) {
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
                for (FlagInfo flag : rc.senseNearbyFlags(GameConstants.VISION_RADIUS_SQUARED, opp)) {
                    if (flag.getLocation().distanceSquaredTo(rc.getLocation()) < minDist) {
                        target = flag.getLocation();
                        minDist = flag.getLocation().distanceSquaredTo(rc.getLocation());
                    }
                }
                if (target == null) {
                    for (MapLocation loc : rc.senseBroadcastFlagLocations()) {
                        if (loc.distanceSquaredTo(rc.getLocation()) < minDist) {
                            target = loc;
                            minDist = loc.distanceSquaredTo(rc.getLocation());
                        }
                    }
                }
            }

            //int byteCode = Clock.getBytecodesLeft();

            if(target != null)
                indicator += Pathfinding.moveToward(rc, target);
            else
                Pathfinding.randomMove(rc);

            //if(Clock.getBytecodesLeft() < 2000) {
            //    System.out.println(byteCode + " " + Clock.getBytecodesLeft());
            //}
        }

        //MICRO, AGAIN
        if (enemies.length > 0)
            attack(rc);
        else if(allies.length > 0)
            heal(rc);
        else {
            if (rc.canBuild(TrapType.EXPLOSIVE, rc.getLocation()) && rng.nextInt() % 4 == 1)
                rc.build(TrapType.EXPLOSIVE, rc.getLocation());
        }
    }

    public static void attack(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return;
        RobotInfo target = chooseAttackTarget(rc);
        if (target != null)
            rc.attack(target.location);
    }

    public static RobotInfo chooseAttackTarget(RobotController rc) {
        int minHitsReq = Integer.MAX_VALUE;
        int minDist = Integer.MIN_VALUE;
        RobotInfo ret = null;
        for (int i = 0; i < enemies.length; i++) {
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
        indicator += "(Sym " + MapStore.possibleSymmetry + ")";
        rc.setIndicatorString(indicator);
        if (target != null && rc.isSpawned())
            rc.setIndicatorLine(rc.getLocation(), target, 255,0,0);
    }

    public static void report(RobotController rc) {

    }

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        while (true) {

            try {
                if (!rc.isSpawned())
                    spawn(rc);
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
