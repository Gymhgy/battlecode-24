package voff3;

import battlecode.common.*;

import java.util.ArrayList;

public class MicroAttacker {

    final int INF = 1000000;
    boolean shouldPlaySafe = false;
    boolean alwaysInRange = false;
    static int myRange;
    static int myVisionRange;

    static final int RANGE_LAUNCHER = 4;

    //static double myDPS;
    //static double[] DPS = new double[]{0, 0, 0, 0, 0, 0, 0};
    //static int[] rangeExtended = new int[]{0, 0, 0, 0, 0, 0, 0};

    static final Direction[] dirs = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
            Direction.CENTER
    };

    final static int MAX_MICRO_BYTECODE_REMAINING = 2000;

    static RobotController rc;

    MicroAttacker(RobotController rc){
        this.rc = rc;
        myRange = 4;
        myVisionRange = 20;
    }

    boolean severelyHurt = false;
    static double currentDPS = 0;
    static double currentActionRadius;
    static boolean canAttack;

    boolean doMicro(ArrayList<StunInfo> stuns, MapLocation target){
        try {
            if (!rc.isMovementReady()) return false;
            shouldPlaySafe = false;
            severelyHurt = rc.getHealth() < 500;
            RobotInfo[] units = rc.senseNearbyRobots(myVisionRange, rc.getTeam().opponent());
            if (units.length == 0) return false;

            if(units.length == 1 &&  target != null && rc.getLocation().isWithinDistanceSquared(target, 20)) {
                if(!units[0].getLocation().isWithinDistanceSquared(rc.getLocation(), 6))
                    return false;
            }

            canAttack = rc.isActionReady();

            alwaysInRange = false;
            //if (!rc.isActionReady()) alwaysInRange = true;
            if (severelyHurt) alwaysInRange = true;

            MicroInfo[] microInfo = new MicroInfo[9];
            for (int i = 0; i < 9; ++i) microInfo[i] = new MicroInfo(dirs[i]);

            for (RobotInfo unit : units) {
                if (Clock.getBytecodesLeft() < MAX_MICRO_BYTECODE_REMAINING) break;
                currentDPS = RobotPlayer.LEVEL_ATTACK[unit.attackLevel];
                double mod = 1;
                for(int i = stuns.size(); i-->0;)
                    if(stuns.get(i).within(unit.location))
                        mod = Math.min(mod, stuns.get(i).modifier());
                currentDPS *= mod;
                if (currentDPS <= 0) continue;
                //if (danger && Robot.comm.isEnemyTerritory(unit.getLocation())) currentDPS*=1.5;
                currentActionRadius = 4;
                microInfo[0].updateEnemy(unit);
                microInfo[1].updateEnemy(unit);
                microInfo[2].updateEnemy(unit);
                microInfo[3].updateEnemy(unit);
                microInfo[4].updateEnemy(unit);
                microInfo[5].updateEnemy(unit);
                microInfo[6].updateEnemy(unit);
                microInfo[7].updateEnemy(unit);
                microInfo[8].updateEnemy(unit);
            }

            units = rc.senseNearbyRobots(myVisionRange, rc.getTeam());
            for (RobotInfo unit : units) {
                if (Clock.getBytecodesLeft() < MAX_MICRO_BYTECODE_REMAINING) break;
                currentDPS = RobotPlayer.LEVEL_ATTACK[unit.attackLevel];
                if (currentDPS <= 0) continue;
                microInfo[0].updateAlly(unit);
                microInfo[1].updateAlly(unit);
                microInfo[2].updateAlly(unit);
                microInfo[3].updateAlly(unit);
                microInfo[4].updateAlly(unit);
                microInfo[5].updateAlly(unit);
                microInfo[6].updateAlly(unit);
                microInfo[7].updateAlly(unit);
                microInfo[8].updateAlly(unit);
            }

            MicroInfo bestMicro = microInfo[8];
            for (int i = 0; i < 8; ++i) {
                if (microInfo[i].isBetter(bestMicro)) bestMicro = microInfo[i];
            }

            if (bestMicro.dir == Direction.CENTER && bestMicro.minDistanceToEnemy <= 4) return true;

            if (rc.canMove(bestMicro.dir)) {
                rc.move(bestMicro.dir);
                return true;
            }

        } catch (Exception e){
            e.printStackTrace();
        }
        return false;
    }


    class MicroInfo {
        Direction dir;
        MapLocation location;
        int minDistanceToEnemy = INF;
        double DPSreceived = 0;
        double enemiesTargeting = 0;
        double alliesTargeting = 0;
        boolean canMove = true;

        public MicroInfo(Direction dir){
            this.dir = dir;
            this.location = rc.getLocation().add(dir);
            if (dir != Direction.CENTER && !rc.canMove(dir)) {
                minDistanceToEnemy = INF;
                canMove = false;
            }
            else{

            }
        }

        void updateEnemy(RobotInfo unit){
            if (!canMove) return;
            int dist = unit.getLocation().distanceSquaredTo(location);
            if (dist < minDistanceToEnemy)  minDistanceToEnemy = dist;
            if (dist <= currentActionRadius) DPSreceived += currentDPS;
            if (dist <= 10) enemiesTargeting += currentDPS;
        }

        void updateAlly(RobotInfo unit){
            if (!canMove) return;
            if(unit.getLocation().isWithinDistanceSquared(rc.getLocation(), 8))
                alliesTargeting += currentDPS;
        }

        int safe(){
            if (!canMove) return -1;
            if (DPSreceived > 0) return 0;
            if (enemiesTargeting > alliesTargeting) return 1;
            return 2;
        }

        boolean inRange(){
            if (alwaysInRange) return true;
            return minDistanceToEnemy <= myRange;
        }

        //equal => true
        boolean isBetter(MicroInfo M){
            if(canMove && !M.canMove) return true;
            if(!canMove && M.canMove) return false;

            if (inRange() && !M.inRange()) return canAttack;
            if (!inRange() && M.inRange()) return !canAttack;

            if(DPSreceived < M.DPSreceived) return true;
            if(DPSreceived > M.DPSreceived) return false;

            if(!canAttack) {
                if(enemiesTargeting < M.enemiesTargeting) return true;
                if(enemiesTargeting > M.enemiesTargeting) return false;
            }

            if (inRange()) return minDistanceToEnemy >= M.minDistanceToEnemy;
            else return minDistanceToEnemy <= M.minDistanceToEnemy;
        }
    }

}