package v7;

import battlecode.common.MapLocation;
public class StunInfo {

    int turnsLeft = -1;
    MapLocation location;
    boolean boomed = false;
    public StunInfo(MapLocation loc) {
        location = loc;
    }

    void boom() {
        turnsLeft = 4;
        boomed = true;
    }
    void tick() {
        if(turnsLeft > 0) turnsLeft--;
    }
    boolean within(MapLocation l) {
        return l.isWithinDistanceSquared(location, 13);
    }
    double modifier() {
        if(!boomed) return 1;
        return new double[] {1, 0.6, 0.4, 0.3, 0.2}[turnsLeft];
    }
}
