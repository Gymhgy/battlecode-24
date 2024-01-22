package v3o2;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class Utils {

    static void init(RobotController rc) throws GameActionException {
        spawnGroups = groupMapLocations(rc.getAllySpawnLocations());
        commId = rc.readSharedArray(0)+1;
        rc.writeSharedArray(0, commId);
    }
    public static MapLocation[][] spawnGroups;
    public static int commId;

    static MapLocation[][] groupMapLocations(MapLocation[] spawns) {
        MapLocation[][] groups = new MapLocation[3][9];
        MapLocation m1 = null, m2 = null, m3 = null;
        int i1=0,i2=0,i3=0;
        for (MapLocation spawn : spawns) {
            if(m1 == null || spawn.isWithinDistanceSquared(m1, 5)) {
                m1 = spawn;
                groups[0][i1++] = spawn;
            }
            else if(m2 == null || spawn.isWithinDistanceSquared(m2, 5)) {
                m2 = spawn;
                groups[1][i2++] = spawn;
            }
            else if(m3 == null || spawn.isWithinDistanceSquared(m3, 5)) {
                m3 = spawn;
                groups[2][i3++] = spawn;
            }
        }
        return groups;
    }
}
