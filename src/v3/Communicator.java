package v3;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class Communicator {
    private static MapLocation sectorSize;
    public static int gridDimensions = 5;
    public static int sectorMask = 0b1111111;
    static void init(RobotController rc) {
        int numRows = rc.getMapHeight() / gridDimensions;
        if (rc.getMapHeight() % gridDimensions != 0) {
            numRows++;
        }
        int numCols = rc.getMapWidth() / gridDimensions;
        if (rc.getMapWidth() % gridDimensions != 0) {
            numCols++;
        }
        sectorSize = new MapLocation(numCols, numRows);
    }
    static MapLocation maxSector(RobotController rc) throws GameActionException {
        int[] sectors = new int[128];
        for (int i = 1; i < 51; i++) {
            sectors[rc.readSharedArray(i) & sectorMask] += 1;
        }
        int m = 0, mi = 0;
        for(int i = 0; i < 128; i++) {
            if (sectors[i] > m) {
                m = sectors[i];
                mi = i;
            }
        }
        return new MapLocation(mi % gridDimensions * sectorSize.x, mi / gridDimensions * sectorSize.y);
    }
    static void writeSector(RobotController rc, int i, MapLocation newLoc) throws GameActionException {
        rc.writeSharedArray(i, sectorToInt(newLoc));
    }
    public static int sectorToInt(MapLocation loc) {
        return (loc.x / sectorSize.x) + (loc.y / sectorSize.y) * gridDimensions;
    }

}
