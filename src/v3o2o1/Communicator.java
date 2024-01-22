package v3o2o1;

import battlecode.common.*;

public class Communicator {

    public static void init(RobotController rc) {
    }

    public static MapLocation approxToActual(RobotController rc, MapLocation approx) throws GameActionException {
        for(int i = 64; i-->61; ) {
            if(getLoc(rc.readSharedArray(i)).equals(approx) && rc.readSharedArray(i-3)!=0) {
                return getLoc(rc.readSharedArray(i-3));
            }
        }
        return approx;
    }

    public static void reportFlag(RobotController rc, MapLocation approx, MapLocation actual) throws GameActionException {
        for(int i = 64; i-->61; ) {
            if(rc.readSharedArray(i) == 0) {
                rc.writeSharedArray(i, approx.x + approx.y*60);
                rc.writeSharedArray(i-3, actual.x + actual.y*60);
                break;
            }
        }
    }


    public static MapLocation getLoc(int e) {
        return new MapLocation(e % 60, e / 60);
    }

}
