package v10o4;

import battlecode.common.*;

import java.util.ArrayList;

public class Communicator {

    static RobotController rc;
    public static void init(RobotController rc) {
        Communicator.rc = rc;
    }

    public static MapLocation approxToActual(RobotController rc, MapLocation approx) throws GameActionException {
        for(int i = 64; i-->61; ) {
            if(getLoc(rc.readSharedArray(i)).equals(approx) && rc.readSharedArray(i-3)!=0) {
                return getLoc(rc.readSharedArray(i-3));
            }
        }
        return approx;
    }

    static boolean allFound = false;
    static boolean allFlagsFound() throws GameActionException {
        for(int i = 61; i-->58;) if(rc.readSharedArray(i) == 0) return false;
        /*if(!allFound) {
            System.out.println("All found: " + (allFound = true));
            for(int i = 64;i-->52;) {
                System.out.println(rc.readSharedArray(i) + " " + (i>=58 || i<55? Communicator.getLoc(rc.readSharedArray(i)) : ""));
            }
        }*/
        return true;
    }

    static void reportBroadcast() throws GameActionException {
        MapLocation[] bc = rc.senseBroadcastFlagLocations();
        if(Utils.commId == 1) {
            for (int i = 55; i-- > 52; ) rc.writeSharedArray(i, 0);
        }
        for(int j = bc.length; j-->0;) {
            boolean reported = false;
            for (int i = 55; i-- > 52; ) {
                if (getInt(bc[j]) == rc.readSharedArray(i)) {
                    reported = true;
                }
            }
            if(!reported) {
                for (int i = 55; i-- > 52; ) {
                    if(rc.readSharedArray(i) == 0) {
                        rc.writeSharedArray(i, getInt(bc[j]));
                        break;
                    }
                }
            }
        }
    }
    static ArrayList<MapLocation> getBroadcast() throws GameActionException {
        ArrayList<MapLocation> res = new ArrayList<>();
        for (int i = 55; i-- > 52; ) {
            if (rc.readSharedArray(i) == 0) continue;
            res.add(getLoc(rc.readSharedArray(i)));
        }
        return res;
    }
    static boolean beingCaptured(int fId) throws GameActionException {
        int idx = 0;
        for(int i = 58;i-->55;) {
            if(rc.readSharedArray(i) == fId) {
                //57 --> 6
                //56 --> 5
                //55 --> 4
                idx = i - 51;
            }
        }
        if(idx == 0) {
            System.out.println("report beingCapture check fail");
            System.out.println("beingCapture " + fId);
            for(int i = 64;i-->55;) {
                System.out.println(rc.readSharedArray(i) + " " + (i>=58 ? Communicator.getLoc(rc.readSharedArray(i)) : ""));
            }
        }
        if(rc.getID()==12091 && rc.getRoundNum() == 251) System.out.println("m: " + rc.readSharedArray(idx) + " " + getLoc(rc.readSharedArray(idx)&0b1111_1111_1111) + " " + idx);
        return rc.readSharedArray(idx) > 0;
    }

    public static void reportFlag(RobotController rc, MapLocation approx, MapLocation actual, int id) throws GameActionException {
        if(Communicator.allFlagsFound()) return;
        /*for(int i = 64; i-->61; ) {
            //if(getLoc(rc.readSharedArray(i)).equals(approx)) break;
            if(rc.readSharedArray(i) == 0) {
                rc.writeSharedArray(i, approx.x + approx.y*rc.getMapWidth());
                rc.writeSharedArray(i-3, actual.x + actual.y*rc.getMapWidth());
                rc.writeSharedArray(i-6, id);
                break;
            }
        }
        for(int i = 61; i-->58; ) {
            if(getLoc(rc.readSharedArray(i)).equals(actual)) {
                rc.writeSharedArray(i+3, approx.x + approx.y*rc.getMapWidth());
                break;
            }
        }*/
        ArrayList<MapLocation> approxes = getBroadcast();
        boolean recorded = false;
        for(int i = 61; i-->58;) if(rc.readSharedArray(i-3) == id) recorded = true;
        if(recorded) return;
        if(!recorded) {
            for (int i = 61; i-- > 58; ) {
                if (rc.readSharedArray(i) == CAPTURED) continue;
                if (rc.readSharedArray(i) == 0) {
                    rc.writeSharedArray(i, getInt(actual));
                    rc.writeSharedArray(i - 3, id);
                    break;
                }
            }
        }
        for(int i = 61; i-->58;) {
            if (rc.readSharedArray(i) == CAPTURED) continue;
            int minDist = Integer.MAX_VALUE;
            MapLocation act = getLoc(rc.readSharedArray(i));
            MapLocation app = null;
            for(MapLocation loc : approxes) {
                if(minDist > loc.distanceSquaredTo(act)) {
                    minDist = loc.distanceSquaredTo(act);
                    app = loc;
                }
            }
            if(app!=null) {
                rc.writeSharedArray(i+3, getInt(app));
                approxes.remove(app);
            }
        }
        System.out.println("Reporting flag: " + actual);
        for(int i = 64;i-->52;) {
            System.out.println(rc.readSharedArray(i) + " " + (i>=58 || i<55? Communicator.getLoc(rc.readSharedArray(i)) : ""));
        }
        //for(int i = 64; i-->58;) System.out.println(getLoc(rc.readSharedArray(i)));
    }

    public static void reportCarrying(int prio) throws GameActionException {
        FlagInfo f = rc.senseNearbyFlags(0)[0];
        int idx = 0;
        for(int i = 58;i-->55;) {
            if(rc.readSharedArray(i) == f.getID()) {
                //57 --> 6
                //56 --> 5
                //55 --> 4
                idx = i - 51;
            }
        }
        if(idx == 0) {
            System.out.println("report Carrying fail");
            System.out.println("carrying " + f.getID());
            for(int i = 64;i-->55;) {
                System.out.println(rc.readSharedArray(i) + " " + (i>=58 ? Communicator.getLoc(rc.readSharedArray(i)) : ""));
            }
            return;
        }
        int e = getInt(rc.getLocation());
        e |= (4 << 12);
        rc.writeSharedArray(idx, e);
    }
    public static void decreasePrio(int idx) throws GameActionException {
        int prio = Math.max(0, (rc.readSharedArray(idx) >> 12)-1);
        rc.writeSharedArray(idx, (rc.readSharedArray(idx) & 0b1111_1111_1111) | (prio << 12));
    }
    public static void reportFlagDrop(int fId) throws GameActionException {
        int idx = 0;
        for(int i = 58;i-->55;) {
            if(rc.readSharedArray(i) == fId) {
                //57 --> 6
                //56 --> 5
                //55 --> 4
                idx = i - 51;
            }
        }
        if(idx == 0) {
            System.out.println("report drop fail");
            System.out.println("dropping " + fId);
            for(int i = 64;i-->55;) {
                System.out.println(rc.readSharedArray(i) + " " + (i>=58 ? Communicator.getLoc(rc.readSharedArray(i)) : ""));
            }
            return;
        }
        GlobalUpgrade[] g = rc.getGlobalUpgrades(rc.getTeam());
        int j = 4;
        for(int i = g.length; i-->0;) {
            if(g[i] == GlobalUpgrade.CAPTURING)
                j = 15;
        }
        rc.writeSharedArray(idx, (rc.readSharedArray(idx) & 0b1111_1111_1111) | ((j) << 12));
    }
    public static void reportFlagCapture(int fId) throws GameActionException {
        int idx = 0;
        for (int i = 58; i-- > 55; ) {
            if (rc.readSharedArray(i) == fId) {
                //57 --> 6
                //56 --> 5
                //55 --> 4
                idx = i - 51;
            }
        }
        if(idx == 0) {
            System.out.println("report capture fail");
            System.out.println("capture " + fId);
            for(int i = 64;i-->55;) {
                System.out.println(rc.readSharedArray(i) + " " + (i>=58 ? Communicator.getLoc(rc.readSharedArray(i)) : ""));
            }
            return;
        }
        rc.writeSharedArray(idx, 0);
        for(int i = 58; i-->55;) {
            if(rc.readSharedArray(i) == fId) {
                rc.writeSharedArray(i, 0);
                rc.writeSharedArray(i+3, CAPTURED);
                rc.writeSharedArray(i+6, 0);
            }
        }
    }

    public static void recordNeverSniff(MapLocation loc) throws GameActionException  {
        for(int i = 37; i < 53; i++) {
            if(rc.readSharedArray(i) == 0) {
                rc.writeSharedArray(i, getInt(loc));
                return;
            }
        }
    }
    public static boolean shouldSniff(MapLocation loc) throws GameActionException {
        for(int i = 37; i < 53; i++) {
            if(rc.readSharedArray(i) == getInt(loc)) {
                return false;
            }
        }
        return true;
    }

    static int CAPTURED = 65535;
    static void reportAllyFlagTaken(MapLocation flag, int id) throws GameActionException {
        int idx = Utils.defenseFlagOffset(id) + 1;
        int e = getInt(flag);
        e |= (opponentCapturing() ? 15 : 5)<<12;
        rc.writeSharedArray(idx, e);
    }

    static void cleanup() throws GameActionException {
        if(Utils.commId != 50) return;
        for(int i = 7; i--> 1;) {
            int e = rc.readSharedArray(i);
            if(e == 0) continue;
            int countdown = e >> 12;
            if(countdown == 1) {
                rc.writeSharedArray(i, 0);
                continue;
            }
            e = (e & 0b1111_1111_1111) | ((countdown-1)<<12);
            rc.writeSharedArray(i, e);
        }
        for(int i = 13; i<=36; i++) rc.writeSharedArray(i, 0);
    }

    public static void rememberFlags(RobotController rc) throws GameActionException {
        for(int i = 1; i < 4; i++)
            myFlags[i-1] = getLoc(rc.readSharedArray(i));
    }
    static MapLocation[] myFlags = new MapLocation[3];
    static int myFlagId=-1;
    public static void droppedFlagAt(RobotController rc)throws GameActionException  {
        if(myFlagId != -1) {
            rc.writeSharedArray(myFlagId, getInt(rc.getLocation()));
            return;
        }
        for(int i = 1; i < 64; i++) {
            if(rc.readSharedArray(i) == 0) {
                rc.writeSharedArray(i, getInt(rc.getLocation()));
                myFlagId = i;
                break;
            }
        }
    }
    public static void wipe(RobotController rc) throws GameActionException {
        for (int i = 64; i-->0;) rc.writeSharedArray(i,0);
    }


    static MapLocation[] senseFlagsAtStart(RobotController rc) throws GameActionException {

        if(rc.readSharedArray(63) == 0) {
            int i = 63;
            for(MapLocation b : rc.senseBroadcastFlagLocations()) {
                rc.writeSharedArray(i--, getInt(b));
            }
            return rc.senseBroadcastFlagLocations();
        }
        else {
            int tot = 0;
            for(int i = 63; ; i--) {
                int j = rc.readSharedArray(i);
                if (j == 0) break;
                tot++;
            }
            MapLocation[] ms = new MapLocation[tot];
            for(int i = 63; ; i--) {
                int j = rc.readSharedArray(i);
                ms[--tot] = getLoc(rc.readSharedArray(i));
                if(tot == 0) return ms;
            }
        }

    }

    //13,14,15,16,17,18,19,20
    //21,22,23,24,25,26,27,28
    //29,30,31,32,33,34,35,36
    static void reportCannotPickup(int fid, int myId) throws GameActionException {
        int idx = 0;
        for (int i = 58; i-- > 55; ) {
            if (rc.readSharedArray(i) == fid) {
                //57 --> 29 --> 2
                //56 --> 21 --> 1
                //55 --> 13 --> 0
                idx = 2*(i - 55)+13;
            }
        }
        for(int i = 0; i < 8; i++) {
            if(rc.readSharedArray(i+idx) == 0) {
                rc.writeSharedArray(i+idx, myId);
                break;
            }
        }
    }

    static boolean canPickup(int fid, int theirId) throws GameActionException {
        int idx = 0;
        for (int i = 58; i-- > 55; ) {
            if (rc.readSharedArray(i) == fid) {
                //57 --> 29 --> 2
                //56 --> 21 --> 1
                //55 --> 13 --> 0
                idx = 2*(i - 55)+13;
            }
        }
        for(int i = 0; i < 8; i++) {
            if(rc.readSharedArray(i) == theirId) {
                return false;
            }
        }
        return true;
    }

    static final int SNIFF_CNT = 4;
    static boolean canSniff() throws GameActionException{
        if(rc.readSharedArray(12) < SNIFF_CNT) {
            return true;
        }
        return false;
    }
    static void addSniffer() throws GameActionException{
        rc.writeSharedArray(12, rc.readSharedArray(12) + 1);
    }
    static void removeSniffer() throws GameActionException{
        rc.writeSharedArray(12, rc.readSharedArray(12) - 1);
    }
    static boolean opponentCapturing() {
        GlobalUpgrade[] g= rc.getGlobalUpgrades(rc.getTeam().opponent());
        for(GlobalUpgrade gg: g) if(gg == GlobalUpgrade.CAPTURING) return true;
        return false;
    }
    public static MapLocation getLoc(int e) {
        e--;
        return new MapLocation(e % rc.getMapWidth(), e / rc.getMapWidth());
    }
    public static int getInt(MapLocation loc) { return loc.x + loc.y*rc.getMapWidth() + 1; }
}
