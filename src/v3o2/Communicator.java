package v3o2;

import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.*;

import java.util.HashSet;
import java.util.Map;

public class Communicator {

    public static void init(RobotController rc) {
    }



    public static HashSet<MapLocation> getSeenFlags(RobotController rc) throws GameActionException {
        HashSet<MapLocation> seens = new HashSet<>();

        for(int i = 64; i -- > 61;){
            int flag = rc.readSharedArray(i);
            if (flag != 0 && flag != CAPTURED && rc.readSharedArray(i-3) == 0) {

                seens.add(getLoc(flag & 0b111111111111));
            }
        }
        for(FlagInfo flag : rc.senseNearbyFlags(GameConstants.VISION_RADIUS_SQUARED, rc.getTeam().opponent())) {
            if (!flag.isPickedUp() && !seens.contains(flag.getLocation()))
                for(int i = 64; i -- > 61;){
                    if(rc.readSharedArray(i) == 0) {
                        rc.writeSharedArray(i, flag.getLocation().y * 60+ flag.getLocation().x);
                        seens.add(flag.getLocation());
                        break;
                    }
                }
        }
        return seens;
    }

    public static int flagIdx;
    public static void dropFlag(RobotController rc, MapLocation oldLoc) throws GameActionException {
        rc.writeSharedArray(flagIdx-3, oldLoc.y * 60 + oldLoc.y);
        rc.writeSharedArray(flagIdx-6, rc.getRoundNum());
        System.out.println(oldLoc);
        for(int i = 64; i-->58;) System.out.println(i + " " + getLoc(rc.readSharedArray(i)));
    }
    public static void pickupFlag(RobotController rc, MapLocation loc) throws GameActionException {
        if(getLoc(rc.readSharedArray(63)).equals(loc)) {
            flagIdx = 63;
            rc.writeSharedArray(60, loc.y*60+loc.x);
        }
        else if(getLoc(rc.readSharedArray(62)).equals(loc)) {
            flagIdx = 62;
            rc.writeSharedArray(59, loc.y*60+loc.x);
        }
        else if(getLoc(rc.readSharedArray(61)).equals(loc)) {
            flagIdx = 61;
            rc.writeSharedArray(58, loc.y*60+loc.x);
        }
        else if(getLoc(rc.readSharedArray(60)).equals(loc)) {
            flagIdx = 63;
            rc.writeSharedArray(60, loc.y*60+loc.x);
        }
        else if(getLoc(rc.readSharedArray(59)).equals(loc)) {
            flagIdx = 62;
            rc.writeSharedArray(59, loc.y*60+loc.x);
        }
        else if(getLoc(rc.readSharedArray(58)).equals(loc)) {
            flagIdx = 61;
            rc.writeSharedArray(58, loc.y*60+loc.x);
        }
        if(flagIdx == 0) {
            System.out.println(loc);
            System.out.println(rc.getLocation());
            for(int i = 64; i-->58;) System.out.println(i + " " + getLoc(rc.readSharedArray(i)));
        }
        rc.writeSharedArray(flagIdx-6, 2000);
    }
    public static void updateFlag(RobotController rc, MapLocation loc) throws GameActionException {
        rc.writeSharedArray(flagIdx - 3, loc.y*60 + loc.x);
        rc.writeSharedArray(flagIdx-6, 2000);
    }
    static boolean capturing = false;
    public static void cleanUp(RobotController rc) throws GameActionException  {
        if(Utils.commId == 50) {
            int threshold = capturing ? 12 : 4;
            for(int i = 57; i>=55;i--){
                if(rc.getRoundNum() - rc.readSharedArray(i) < threshold) {
                }
                else {
                    rc.writeSharedArray(i+3, 0);
                    rc.writeSharedArray(i, 2000);
                }
            }
        }
    }
    static final int CAPTURED = 111;

    public static void capturedFlag(RobotController rc) throws GameActionException {
        rc.writeSharedArray(flagIdx, CAPTURED);
    }

    public static MapLocation getLoc(int e) {
        return new MapLocation(e % 60, e / 60);
    }

}
