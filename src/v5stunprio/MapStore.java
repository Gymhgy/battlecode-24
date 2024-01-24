package v5stunprio;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class MapStore {

    static int[] map = new int[3600];
    static final int SEEN_BIT = 1;
    static final int WALL_BIT = 1<<1;
    static final int SPAWN_BITS = 0b1100;
    public static void updateMap(RobotController rc) throws GameActionException {
        int w = rc.getMapWidth()-1, h = rc.getMapHeight()-1;
        MapInfo[] infos = rc.senseNearbyMapInfos();
        for (int i = infos.length; --i >= 0; ) {
            MapInfo info = infos[i];
            MapLocation iloc = info.getMapLocation();

            int val = SEEN_BIT;
            if(info.isWall()) val |= WALL_BIT;
            if(info.isSpawnZone()) val |= info.getSpawnZoneTeam() << 2;
            if(!symmetryFound()) {
                if((possibleSymmetry & UPDOWN) == UPDOWN) {
                    int sym = (w - iloc.x) * (h+1) + iloc.y;
                    if(map[sym] > 0 && !mapEqual(map[sym], val)) {
                        possibleSymmetry &= ~UPDOWN;
                    }
                }
                if((possibleSymmetry & LEFTRIGHT) == LEFTRIGHT) {
                    int sym = iloc.x * (h+1) + h - iloc.y;
                    if(map[sym] > 0 && !mapEqual(map[sym], val)) {
                        possibleSymmetry &= ~LEFTRIGHT;
                    }
                }
                if((possibleSymmetry & ROTATIONAL) == ROTATIONAL) {
                    int sym = (w - iloc.x) * (h+1) + h - iloc.y;
                    if(map[sym] > 0 && !mapEqual(map[sym], val)) {
                        possibleSymmetry &= ~ROTATIONAL;
                    }
                }
            }
        }

    }
    public static boolean mapEqual(int a, int b) {
        return (a&3) == (b&3) && ((a>>2 == 1 && b>>2 ==2) || (a>>2 == 2 && b>>2 == 1) || (a>>2 == 0 && b>>2 == 0));
    }
    public static final int UPDOWN = 1;
    public static final int LEFTRIGHT = 2;
    public static final int ROTATIONAL = 4;
    public static int possibleSymmetry = 7;
    static boolean symmetryFound() {
        return (possibleSymmetry == ROTATIONAL || possibleSymmetry == UPDOWN || possibleSymmetry == LEFTRIGHT);
    }

}