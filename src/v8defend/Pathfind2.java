package v8defend;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class Pathfind2 {

    RobotController rc;

    static int H,W;

    Pathfind2(RobotController rc){
        this.rc = rc;
        H = rc.getMapHeight(); W = rc.getMapWidth();
        states = new int[W][];
    }

    int bugPathIndex = 0;

    int stateIndex = 0;

    boolean isReady(){
        return stateIndex >= W;
    }

    void fill(){
        while(stateIndex < W){
            if (Clock.getBytecodesLeft() < 1000) return;
            states[stateIndex++] = new int[H];
        }
    }

    Boolean rotateRight = null; //if I should rotate right or left
    //Boolean rotateRightAux = null;
    MapLocation lastObstacleFound = null; //latest obstacle I've found in my way

    static int INF = 1000000000;
    int minDistToTarget = INF; //minimum distance I've been to the enemy while going around an obstacle
    MapLocation minLocationToTarget = null;
    MapLocation prevTarget = null; //previous target
    Direction[] dirs = Direction.values();
    //HashSet<Integer> states = new HashSet<>();

    int[][] states;

    MapLocation myLoc;
    boolean[] canMoveArray;
    int round;

    int turnsMovingToObstacle = 0;
    final int MAX_TURNS_MOVING_TO_OBSTACLE = 2;
    final int MIN_DIST_RESET = 3;

    void update(MapLocation target){
        if (!rc.isMovementReady()) return;
        myLoc = rc.getLocation();
        round = rc.getRoundNum();
        generateCanMove(target);
    }

    void generateCanMove(MapLocation target){
        canMoveArray = new boolean[9];
        for (Direction dir : dirs){
            switch (dir){
                case CENTER:
                    canMoveArray[dir.ordinal()] = true;
                    break;
                default:
                    canMoveArray[dir.ordinal()] = rc.canMove(dir);
                    break;
            }
        }

/*
        try {

            if (lastObstacleFound == null) {
                for (Direction dir : dirs) {
                    if (!canMoveArray[dir.ordinal()]) continue;
                    MapLocation newLoc = rc.getLocation().add(dir);
                    if (newLoc.distanceSquaredTo(target) <= 2) continue;
                    Direction cur = rc.senseMapInfo(newLoc).getCurrentDirection();
                    if (cur == null || cur == Direction.CENTER) continue;
                    MapLocation newLoc2 = newLoc.add(cur);
                    if (newLoc2.distanceSquaredTo(target) >= rc.getLocation().distanceSquaredTo(target)){
                        canMoveArray[dir.ordinal()] = false;
                    }
                }
            }
        } catch (GameActionException e){
            e.printStackTrace();
        }
*/

    }

    void debugMovement(){
        try{
            for (Direction dir : dirs){
                MapLocation newLoc = myLoc.add(dir);
                if (rc.canSenseLocation(newLoc) && canMoveArray[dir.ordinal()]) rc.setIndicatorDot(newLoc, 0, 0, 255);
            }
        } catch (Throwable t){
            t.printStackTrace();
        }
    }


    void moveTo(MapLocation target){

        //Robot.bytecodeDebug += "BC_BUG_BEGIN = " + Clock.getBytecodeNum() + " ";

        //No target? ==> bye!
        if (!rc.isMovementReady()) return;
        if (target == null) target = rc.getLocation();
        //if (Constants.DEBUG == 1)
        //rc.setIndicatorLine(rc.getLocation(), target, 255, 0, 255);

        update(target);
        //if (target == null) return;


        //different target? ==> previous data does not help!
        if (prevTarget == null){
            resetPathfinding();
            rotateRight = null;
            //rotateRightAux = null;
        }


        else {
            int distTargets = target.distanceSquaredTo(prevTarget);
            if (distTargets > 0) {
                if (distTargets >= MIN_DIST_RESET){
                    rotateRight = null;
                    //rotateRightAux = null;
                    resetPathfinding();
                }
                else{
                    softReset(target);
                }
            }
        }

        //Robot.bytecodeDebug += "BC_BUG_1 = " + Clock.getBytecodeNum() + " ";

        //Update data
        prevTarget = target;

        checkState();
        myLoc = rc.getLocation();

        //Robot.bytecodeDebug += "BC_BUG_12 = " + Clock.getBytecodeNum() + " ";


        int d = myLoc.distanceSquaredTo(target);
        if (d == 0){
            return;
        }

        //If I'm at a minimum distance to the target, I'm free!
        if (d < minDistToTarget){
            resetPathfinding();
            minDistToTarget = d;
            minLocationToTarget = myLoc;
        }

        //If there's an obstacle I try to go around it [until I'm free] instead of going to the target directly
        Direction dir = myLoc.directionTo(target);
        if (lastObstacleFound == null){
            if (tryGreedyMove()){
                resetPathfinding();
                return;
            }
        }
        else{
            dir = myLoc.directionTo(lastObstacleFound);
            rc.setIndicatorDot(lastObstacleFound, 0, 255, 0);
        }

        //Robot.bytecodeDebug += "BC_BUG_2 = " + Clock.getBytecodeNum() + " ";

        try {

            if (canMoveArray[dir.ordinal()]){
                rc.move(dir);
                if (lastObstacleFound != null) {
                    ++turnsMovingToObstacle;
                    lastObstacleFound = rc.getLocation().add(dir);
                    if (turnsMovingToObstacle >= MAX_TURNS_MOVING_TO_OBSTACLE){
                        resetPathfinding();
                    } else if (!rc.onTheMap(lastObstacleFound)){
                        resetPathfinding();
                    }
                }
                return;
            } else turnsMovingToObstacle = 0;

            checkRotate(dir);


            //Robot.bytecodeDebug += "BC_BUG = " + Clock.getBytecodeNum() + " ";

            //I rotate clockwise or counterclockwise (depends on 'rotateRight'). If I try to go out of the map I change the orientation
            //Note that we have to try at most 16 times since we can switch orientation in the middle of the loop. (It can be done more efficiently)
            int i = 16;
            while (i-- > 0) {
                if (canMoveArray[dir.ordinal()]) {
                    rc.move(dir);
                    //Robot.bytecodeDebug += "BC_BUG_END = " + i + " " + Clock.getBytecodeNum() + " ";
                    return;
                }
                MapLocation newLoc = myLoc.add(dir);
                if (!rc.onTheMap(newLoc)) rotateRight = !rotateRight;
                    //If I could not go in that direction and it was not outside of the map, then this is the latest obstacle found
                else lastObstacleFound = newLoc;
                if (rotateRight) dir = dir.rotateRight();
                else dir = dir.rotateLeft();
            }

            //Robot.bytecodeDebug += "BC_BUG_END = " + i + " " + Clock.getBytecodeNum() + " ";

            if (canMoveArray[dir.ordinal()]){
                rc.move(dir);
                return;
            }
        } catch (Throwable t){
            t.printStackTrace();
        }
    }

    boolean tryGreedyMove(){
        try {
            //if (rotateRightAux != null) return false;
            MapLocation myLoc = rc.getLocation();
            Direction dir = myLoc.directionTo(prevTarget);
            if (canMoveArray[dir.ordinal()]) {
                rc.move(dir);
                return true;
            }
            int dist = myLoc.distanceSquaredTo(prevTarget);
            int dist1 = INF, dist2 = INF;
            Direction dir1 = dir.rotateRight();
            MapLocation newLoc = myLoc.add(dir1);
            if (canMoveArray[dir1.ordinal()]) dist1 = newLoc.distanceSquaredTo(prevTarget);
            Direction dir2 = dir.rotateLeft();
            newLoc = myLoc.add(dir2);
            if (canMoveArray[dir2.ordinal()]) dist2 = newLoc.distanceSquaredTo(prevTarget);
            if (dist1 < dist && dist1 < dist2) {
                //rotateRightAux = true;
                rc.move(dir1);
                return true;
            }
            if (dist2 < dist && dist2 < dist1) {
                ;//rotateRightAux = false;
                rc.move(dir2);
                return true;
            }
        } catch(Throwable t){
            t.printStackTrace();
        }
        return false;
    }

    //TODO: check remaining cases
    //TODO: move obstacle if can move to obstacle lol
    void checkRotate(Direction dir){
        if (rotateRight != null) return;
        Direction dirLeft = dir;
        Direction dirRight = dir;
        int i = 8;
        while (--i >= 0) {
            if (!canMoveArray[dirLeft.ordinal()]) dirLeft = dirLeft.rotateLeft();
            else break;
        }
        i = 8;
        while (--i >= 0){
            if (!canMoveArray[dirRight.ordinal()]) dirRight = dirRight.rotateRight();
            else break;
        }
        int distLeft = myLoc.add(dirLeft).distanceSquaredTo(prevTarget), distRight = myLoc.add(dirRight).distanceSquaredTo(prevTarget);
        if (distRight < distLeft) rotateRight = true;
        else rotateRight = false;
    }

    //clear some of the previous data
    void resetPathfinding(){
        lastObstacleFound = null;
        minDistToTarget = INF;
        ++bugPathIndex;
        turnsMovingToObstacle = 0;
    }

    void softReset(MapLocation target){

        if (minLocationToTarget != null) minDistToTarget = minLocationToTarget.distanceSquaredTo(target);
        else resetPathfinding();
    }

    void checkState(){
        if (!isReady()) return;
        if (lastObstacleFound == null) return;
        int state = (bugPathIndex << 14) | (lastObstacleFound.x << 8) |  (lastObstacleFound.y << 2);
        if (rotateRight != null) {
            if (rotateRight) state |= 1;
            else state |= 2;
        }
        if (states[myLoc.x][myLoc.y] == state){
            resetPathfinding();
        }

        states[myLoc.x][myLoc.y] = state;
    }

}