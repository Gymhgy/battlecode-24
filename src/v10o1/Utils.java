package v10o1;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class Utils {

    static RobotController rc;
    static void init(RobotController rc) throws GameActionException {
        Utils.rc = rc;
        spawnGroups = groupMapLocations(rc.getAllySpawnLocations());
        spawnCenters = spawnCenters();
        commId = rc.readSharedArray(0)+1;
        rc.writeSharedArray(0, commId);
    }
    public static MapLocation[][] spawnGroups;
    public static MapLocation[] spawnCenters;
    public static int commId;

    static MapLocation[] spawnCenters () {
        MapLocation[] centers = new MapLocation[3];
        for(int i = 3; i-->0;) {
            int x=0,y=0;
            for(int j=9;j-->0;) {
                x+=spawnGroups[i][j].x;
                y+=spawnGroups[i][j].y;
            }
            centers[i] = new MapLocation(x/9,y/9);
        }
        return centers;
    }
    static int defenseFlagOffset(int id) {
        for(int i = 3; i-->0;) {
            MapLocation loc = spawnCenters[i];
            if(id == loc.x+loc.y*rc.getMapWidth()) {
                return i;
            }
        }
        System.out.println("error w/ flag offset");
        for(int i = 3; i-->0;) {
            System.out.println(i + " " + spawnCenters[i]);
        }
        return 0;
    }

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
