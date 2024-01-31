package v10o1;

import battlecode.common.*;

/**
 * This class contains logic / variable that is shared between all units
 * pathfinding logics will be here
 */
public class Pathfinding {

    static int turnsSinceRandomTargetChange = 0;
    static MapLocation target;
    public static boolean navigateRandomly(RobotController rc) throws GameActionException {
        turnsSinceRandomTargetChange++;
        if(target == null || rc.getLocation().distanceSquaredTo(target) < 5 ||
                turnsSinceRandomTargetChange > rc.getMapWidth() + rc.getMapHeight()) {
            int targetX = FastMath.rand256() % rc.getMapWidth();
            int targetY = FastMath.rand256() % rc.getMapHeight();
            target = new MapLocation(targetX, targetY);
            turnsSinceRandomTargetChange = 0;
        }
        moveToward(rc, target);
        if(rc.isMovementReady()) {
            int targetX = FastMath.rand256() % rc.getMapWidth();
            int targetY = FastMath.rand256() % rc.getMapHeight();
            target = new MapLocation(targetX, targetY);
            return false;
        }
        return true;
    }
    static void randomMove(RobotController rc) throws GameActionException {
        int starting_i = FastMath.rand256() % 8;
        for (int i = starting_i; i < starting_i + 8; i++) {
            Direction dir = Direction.allDirections()[i % 8];
            if (rc.canMove(dir)) rc.move(dir);
        }
    }

    static void tryMoveDir(RobotController rc, Direction dir) throws GameActionException {
        if (rc.isMovementReady() && dir != Direction.CENTER) {
            if (rc.canMove(dir) && canPass(rc, dir)) {
                rc.move(dir);
            } else if (rc.canMove(dir.rotateRight()) && canPass(rc, dir.rotateRight(), dir)) {
                rc.move(dir.rotateRight());
            } else if (rc.canMove(dir.rotateLeft()) && canPass(rc, dir.rotateLeft(), dir)) {
                rc.move(dir.rotateLeft());
            } else {
                randomMove(rc);
            }
        }
    }
    static void follow(RobotController rc, MapLocation location) throws GameActionException {
        tryMoveDir(rc, rc.getLocation().directionTo(location));
    }

    static int getClosestID(MapLocation fromLocation, MapLocation[] locations) {
        int dis = Integer.MAX_VALUE;
        int rv = -1;
        for (int i = locations.length; --i >= 0;) {
            MapLocation location = locations[i];
            if (location != null) {
                int newDis = fromLocation.distanceSquaredTo(location);
                if (newDis < dis) {
                    rv = i;
                    dis = newDis;
                }
            }
        }
        assert dis != Integer.MAX_VALUE;
        return rv;
    }
    static int getClosestID(RobotController rc, MapLocation[] locations) {
        return getClosestID(rc.getLocation(), locations);
    }

    static int getClosestDis(MapLocation fromLocation, MapLocation[] locations) {
        int id = getClosestID(fromLocation, locations);
        return fromLocation.distanceSquaredTo(locations[id]);
    }
    static int getClosestDis(RobotController rc, MapLocation[] locations) {
        return getClosestDis(rc.getLocation(), locations);
    }

    static MapLocation getClosestLoc(MapLocation fromLocation, MapLocation[] locations) {
        return locations[getClosestID(fromLocation, locations)];
    }

    static MapLocation getClosestLoc(RobotController rc, MapLocation[] locations) {
        return getClosestLoc(rc.getLocation(), locations);
    }

    // new path finding code from Ray
    private static final int PRV_LENGTH = 60;
    private static Direction[] prv = new Direction[PRV_LENGTH];
    private static int pathingCnt = 0;
    private static MapLocation lastPathingTarget = null;
    private static MapLocation lastLocation = null;
    private static int stuckCnt = 0;
    private static int lastPathingTurn = 0;
    private static int currentTurnDir = FastMath.rand256() % 2;
    public static int disableTurnDirRound = 0;

    private static Direction[] prv_ = new Direction[PRV_LENGTH];
    private static int pathingCnt_ = 0;
    static int MAX_DEPTH = 15;

    static boolean dig = false;

    static String indicator;
    static String moveToward(RobotController rc, MapLocation location) throws GameActionException {
        // reset queue when target location changes or there's gap in between calls
        if (!location.equals(lastPathingTarget) || lastPathingTurn < rc.getRoundNum() - 4) {
            pathingCnt = 0;
            stuckCnt = 0;
        }
        indicator = "";
        indicator += String.format("2%sc%dt%s,", location, pathingCnt, currentTurnDir == 0? "L":"R");
        if (rc.isMovementReady()) {
            // we increase stuck count only if it's a new turn (optim for empty carriers)
            if (rc.getLocation().equals(lastLocation)) {
                if (rc.getRoundNum() != lastPathingTurn) {
                    stuckCnt++;
                }
            } else {
                lastLocation = rc.getLocation();
                stuckCnt = 0;
            }
            lastPathingTarget = location;
            lastPathingTurn = rc.getRoundNum();
            if (stuckCnt >= 3) {
                indicator += "stuck reset";
                randomMove(rc);
                pathingCnt = 0;
            }

            if (pathingCnt == 0) {
                //if free of obstacle: try go directly to target
                Direction dir = rc.getLocation().directionTo(location);
                boolean dirCanPass = canPass(rc, dir);
                boolean dirRightCanPass = canPass(rc, dir.rotateRight(), dir);
                boolean dirLeftCanPass = canPass(rc, dir.rotateLeft(), dir);
                if (dirCanPass || dirRightCanPass || dirLeftCanPass) {
                    if (dirCanPass && canMoveDig(rc, dir)) {
                        moveDig(rc, dir);
                    } else if (dirRightCanPass && canMoveDig(rc, dir.rotateRight())) {
                        moveDig(rc, dir.rotateRight());
                    } else if (dirLeftCanPass && canMoveDig(rc, dir.rotateLeft())) {
                        moveDig(rc, dir.rotateLeft());
                    }
                } else {
                    //encounters obstacle; run simulation to determine best way to go
                    if (rc.getRoundNum() > disableTurnDirRound) {
                        currentTurnDir = getTurnDir(rc, dir, location);
                    }
                    while (!canPass(rc, dir) && pathingCnt != 8) {
//                        rc.setIndicatorLine(rc.getLocation(), rc.getLocation().add(dir), 0, 0, 255);
                        if (!rc.onTheMap(rc.getLocation().add(dir))) {
                            currentTurnDir ^= 1;
                            pathingCnt = 0;
                            indicator += "edge switch";
                            disableTurnDirRound = rc.getRoundNum() + 100;
                            return indicator;
                        }
                        prv[pathingCnt] = dir;
                        pathingCnt++;
                        if (currentTurnDir == 0) dir = dir.rotateLeft();
                        else dir = dir.rotateRight();
                    }
                    if (pathingCnt == 8) {
                        indicator += "permblocked";
                    } else if (canMoveDig(rc, dir)) {
                        moveDig(rc, dir);
                    }
                }
            } else {
                //update stack of past directions, move to next available direction
                if (pathingCnt > 1 && canPass(rc, prv[pathingCnt - 2])) {
                    pathingCnt -= 2;
                }
                while (pathingCnt > 0 && canPass(rc, prv[pathingCnt - 1])) {
//                    rc.setIndicatorLine(rc.getLocation(), rc.getLocation().add(prv[pathingCnt - 1]), 0, 255, 0);
                    pathingCnt--;
                }
                if (pathingCnt == 0) {
                    Direction dir = rc.getLocation().directionTo(location);
                    if (!canPass(rc, dir)) {
                        prv[pathingCnt++] = dir;
                    }
                }
                int pathingCntCutOff = Math.min(PRV_LENGTH, pathingCnt + 8); // if 8 then all dirs blocked
                while (pathingCnt > 0 && !canPass(rc, currentTurnDir == 0?prv[pathingCnt - 1].rotateLeft():prv[pathingCnt - 1].rotateRight())) {
                    prv[pathingCnt] = currentTurnDir == 0?prv[pathingCnt - 1].rotateLeft():prv[pathingCnt - 1].rotateRight();
//                    rc.setIndicatorLine(rc.getLocation(), rc.getLocation().add(prv[pathingCnt]), 255, 0, 0);
                    if (!rc.onTheMap(rc.getLocation().add(prv[pathingCnt]))) {
                        currentTurnDir ^= 1;
                        pathingCnt = 0;
                        indicator += "edge switch";
                        disableTurnDirRound = rc.getRoundNum() + 100;
                        return indicator;
                    }
                    pathingCnt++;
                    if (pathingCnt == pathingCntCutOff) {
                        pathingCnt = 0;
                        indicator += "cutoff";
                        return indicator;
                    }
                }
                Direction moveDir = pathingCnt == 0? prv[pathingCnt] :
                        (currentTurnDir == 0?prv[pathingCnt - 1].rotateLeft():prv[pathingCnt - 1].rotateRight());
                if (canMoveDig(rc, moveDir)) {
                    moveDig(rc, moveDir);
                } else {
                    // a robot blocking us while we are following wall, wait
                    indicator += "blocked";
                }
            }
        }
        lastPathingTarget = location;
        lastPathingTurn = rc.getRoundNum();

        return indicator;
    }

    static int getSteps(MapLocation a, MapLocation b) {
        int xdif = a.x - b.x;
        int ydif = a.y - b.y;
        if (xdif < 0) xdif = -xdif;
        if (ydif < 0) ydif = -ydif;
        if (xdif > ydif) return xdif;
        else return ydif;
    }

    static int getCenterDir(RobotController rc, Direction dir) throws GameActionException {
        double a = rc.getLocation().x - rc.getMapWidth()/2.0;
        double b = rc.getLocation().y - rc.getMapHeight()/2.0;
        double c = dir.dx;
        double d = dir.dy;
        if (a * d - b * c > 0) return 1;
        return 0;
    }

    private static final int BYTECODE_CUTOFF = 10000;
    static int getTurnDir(RobotController rc, Direction direction, MapLocation target) throws GameActionException{
        //int ret = getCenterDir(direction);
        MapLocation now = rc.getLocation();
        int moveLeft = 0;
        int moveRight = 0;

        pathingCnt_ = 0;
        Direction dir = direction;
        while (!canPass(rc, now.add(dir), dir) && pathingCnt_ != 8) {
            prv_[pathingCnt_] = dir;
            pathingCnt_++;
            dir = dir.rotateLeft();
            if (pathingCnt_ > 8) {
                break;
            }
        }
        now = now.add(dir);

        int byteCodeRem = Clock.getBytecodesLeft();
        if (byteCodeRem < BYTECODE_CUTOFF)
            return FastMath.rand256() % 2;
        //simulate turning left
        while (pathingCnt_ > 0) {
            moveLeft++;
            if (moveLeft > MAX_DEPTH) {
                break;
            }
            if (Clock.getBytecodesLeft() < BYTECODE_CUTOFF) {
                moveLeft = -1;
                break;
            }
            while (pathingCnt_ > 0 && canPass(rc, now.add(prv_[pathingCnt_ - 1]), prv_[pathingCnt_ - 1])) {
                pathingCnt_--;
            }
            if (pathingCnt_ > 1 && canPass(rc, now.add(prv_[pathingCnt_ - 1]), prv_[pathingCnt_ - 2])) {
                pathingCnt_-=2;
            }
            while (pathingCnt_ > 0 && !canPass(rc, now.add(prv_[pathingCnt_ - 1].rotateLeft()), prv_[pathingCnt_ - 1].rotateLeft())) {
                prv_[pathingCnt_] = prv_[pathingCnt_ - 1].rotateLeft();
                pathingCnt_++;
                if (pathingCnt_ > 8) {
                    moveLeft = -1;
                    break;
                }
            }
            if (pathingCnt_ > 8 || pathingCnt_ == 0) {
                break;
            }
            Direction moveDir = pathingCnt_ == 0? prv_[pathingCnt_] : prv_[pathingCnt_ - 1].rotateLeft();
            now = now.add(moveDir);
        }
        MapLocation leftend = now;
        pathingCnt_ = 0;
        now = rc.getLocation();
        dir = direction;
        //simulate turning right
        while (!canPass(rc, dir) && pathingCnt_ != 8) {
            prv_[pathingCnt_] = dir;
            pathingCnt_++;
            dir = dir.rotateRight();
            if (pathingCnt_ > 8) {
                break;
            }
        }
        now = now.add(dir);

        while (pathingCnt_ > 0) {
            moveRight++;
            if (moveRight > MAX_DEPTH) {
                break;
            }
            if (Clock.getBytecodesLeft() < BYTECODE_CUTOFF) {
                moveRight = -1;
                break;
            }
            while (pathingCnt_ > 0 && canPass(rc, now.add(prv_[pathingCnt_ - 1]), prv_[pathingCnt_ - 1])) {
                pathingCnt_--;
            }
            if (pathingCnt_ > 1 && canPass(rc, now.add(prv_[pathingCnt_ - 1]), prv_[pathingCnt_ - 2])) {
                pathingCnt_-=2;
            }
            while (pathingCnt_ > 0 && !canPass(rc, now.add(prv_[pathingCnt_ - 1].rotateRight()), prv_[pathingCnt_ - 1].rotateRight())) {
                prv_[pathingCnt_] = prv_[pathingCnt_ - 1].rotateRight();
                pathingCnt_++;
                if (pathingCnt_ > 8) {
                    moveRight = -1;
                    break;
                }
            }
            if (pathingCnt_ > 8 || pathingCnt_ == 0) {
                break;
            }
            Direction moveDir = pathingCnt_ == 0? prv_[pathingCnt_] : prv_[pathingCnt_ - 1].rotateRight();
            now = now.add(moveDir);
        }
        MapLocation rightend = now;
        //find best direction
        if (moveLeft == -1 || moveRight == -1) return FastMath.rand256() % 2;
        if (moveLeft + getSteps(leftend, target) <= moveRight + getSteps(rightend, target)) return 0;
        else return 1;

    }

    static boolean canMoveDig(RobotController rc, Direction d) {
        return rc.canMove(d) || rc.canFill(rc.getLocation().add(d));
    }

    static void moveDig(RobotController rc, Direction d) throws GameActionException {
        MapLocation loc = rc.getLocation().add(d);
        MapLocation myLoc = rc.getLocation();
        if(!rc.canSenseLocation(loc)) return;
        if(rc.canMove(d)) {
            rc.move(d);
        }
        else if(rc.senseMapInfo(loc).isWater()) {

            if(d.dx*d.dy==0 && rc.senseMapInfo(loc).getCrumbs()==0) {
                MapLocation rl = myLoc.add(d.rotateLeft()), rr = myLoc.add(d.rotateRight());
                if(rc.canSenseLocation(rl) && rc.senseMapInfo(rl).isPassable()) {
                    if (rc.canMove(d.rotateLeft())) {
                        rc.move(d.rotateLeft());
                        indicator += "water-RL,";
                    }
                    return;
                }
                else if(rc.canSenseLocation(rr) && rc.senseMapInfo(rr).isPassable()) {
                    if (rc.canMove(d.rotateRight())) {
                        rc.move(d.rotateRight());
                        indicator += "water-RR,";
                    }
                    return;
                }
            }

            if (rc.canFill(loc)) {
                rc.fill(loc);
            }
            if(rc.canMove(d)) {
                rc.move(d);
            }
        }
    }

    static boolean canPass(RobotController rc, MapLocation loc, Direction targetDir) throws GameActionException {
        if (loc.equals(rc.getLocation())) return true;
        if (!rc.canSenseLocation(loc)) return true;
        MapInfo mi = rc.senseMapInfo(loc);
        if(rc.hasFlag() && rc.getRoundNum() < 201) {
            for(FlagInfo f : rc.senseNearbyFlags(20, rc.getTeam())) {
                if (f.getLocation().isWithinDistanceSquared(loc,25)) return false;
            }
        }
        if (mi.isWall() || mi.isDam()) return false;
        if (mi.isWater()) {
            for(MapLocation myFlag : Communicator.myFlags) {
                if(myFlag == null) continue;
                if(myFlag.isWithinDistanceSquared(mi.getMapLocation(), 4)) return false;
            }
            if(rc.hasFlag()) return false;
            if(mi.getCrumbs() > 0) return true;
            if (mi.getTeamTerritory() == rc.getTeam() /*|| mi.getTeamTerritory() == Team.NEUTRAL*/)
                /*if (adjacentToDam(rc, mi.getMapLocation())) return true;
                else*/ if((loc.x + loc.y) % 2 == 1)return false;
                return true;
        }
        if(rc.hasFlag() && rc.getRoundNum() > 201) return true;

        for (Direction d : Direction.allDirections()) {
            if(!rc.canSenseLocation(loc.add(d))) continue;
            RobotInfo r = rc.senseRobotAtLocation(loc.add(d));
            if (r != null && r.hasFlag && r.team == rc.getTeam()) return false;
        }
        RobotInfo robot = rc.senseRobotAtLocation(loc);
        if (robot == null)
            return true;
        return false;
    }
    static boolean adjacentToDam(RobotController rc, MapLocation location) throws GameActionException {
        for (Direction d : Direction.allDirections()) {
            if(!rc.canSenseLocation(location.add(d))) continue;
            if (rc.senseMapInfo(location.add(d)).isDam()) return true;
        }
        return false;
    }
    static boolean canPass(RobotController rc, Direction dir, Direction targetDir) throws GameActionException {
        MapLocation loc = rc.getLocation().add(dir);
        return canPass(rc, loc, targetDir);
    }

    static boolean canPass(RobotController rc, Direction dir) throws GameActionException {
        return canPass(rc, dir, dir);
    }

    static Direction Dxy2dir(int dx, int dy) {
        if (dx == 0 && dy == 0) return Direction.CENTER;
        if (dx == 0 && dy == 1) return Direction.NORTH;
        if (dx == 0 && dy == -1) return Direction.SOUTH;
        if (dx == 1 && dy == 0) return Direction.EAST;
        if (dx == 1 && dy == 1) return Direction.NORTHEAST;
        if (dx == 1 && dy == -1) return Direction.SOUTHEAST;
        if (dx == -1 && dy == 0) return Direction.WEST;
        if (dx == -1 && dy == 1) return Direction.NORTHWEST;
        if (dx == -1 && dy == -1) return Direction.SOUTHWEST;
        assert false; // shouldn't reach here
        return null;
    }


    static Direction mockMoveTowards(RobotController rc, MapLocation location) throws GameActionException {
        // reset queue when target location changes or there's gap in between calls
        if (!location.equals(lastPathingTarget) || lastPathingTurn < rc.getRoundNum() - 4) {
            pathingCnt = 0;
            stuckCnt = 0;
        }
        indicator = "";
        indicator += String.format("2%sc%dt%s,", location, pathingCnt, currentTurnDir == 0? "L":"R");
        if (rc.isMovementReady()) {
            // we increase stuck count only if it's a new turn (optim for empty carriers)
            if (rc.getLocation().equals(lastLocation)) {
                if (rc.getRoundNum() != lastPathingTurn) {
                    stuckCnt++;
                }
            } else {
                lastLocation = rc.getLocation();
                stuckCnt = 0;
            }
            lastPathingTarget = location;
            lastPathingTurn = rc.getRoundNum();
            if (stuckCnt >= 3) {
                indicator += "stuck reset";
                randomMove(rc);
                pathingCnt = 0;
            }

            if (pathingCnt == 0) {
                //if free of obstacle: try go directly to target
                Direction dir = rc.getLocation().directionTo(location);
                boolean dirCanPass = canPass(rc, dir);
                boolean dirRightCanPass = canPass(rc, dir.rotateRight(), dir);
                boolean dirLeftCanPass = canPass(rc, dir.rotateLeft(), dir);
                if (dirCanPass || dirRightCanPass || dirLeftCanPass) {
                    if (dirCanPass && rc.canMove(dir)) {
                        return dir;
                    } else if (dirRightCanPass && rc.canMove(dir.rotateRight())) {
                        return dir.rotateRight();
                    } else if (dirLeftCanPass && rc.canMove(dir.rotateLeft())) {
                        return dir.rotateLeft();
                    }
                } else {
                    //encounters obstacle; run simulation to determine best way to go
                    if (rc.getRoundNum() > disableTurnDirRound) {
                        currentTurnDir = getTurnDir(rc, dir, location);
                    }
                    while (!canPass(rc, dir) && pathingCnt != 8) {
//                        rc.setIndicatorLine(rc.getLocation(), rc.getLocation().add(dir), 0, 0, 255);
                        if (!rc.onTheMap(rc.getLocation().add(dir))) {
                            currentTurnDir ^= 1;
                            pathingCnt = 0;
                            indicator += "edge switch";
                            disableTurnDirRound = rc.getRoundNum() + 100;
                            return Direction.CENTER;
                        }
                        prv[pathingCnt] = dir;
                        pathingCnt++;
                        if (currentTurnDir == 0) dir = dir.rotateLeft();
                        else dir = dir.rotateRight();
                    }
                    if (pathingCnt == 8) {
                        indicator += "permblocked";
                    } else if (rc.canMove( dir)) {
                        return dir;
                    }
                }
            } else {
                //update stack of past directions, move to next available direction
                if (pathingCnt > 1 && canPass(rc, prv[pathingCnt - 2])) {
                    pathingCnt -= 2;
                }
                while (pathingCnt > 0 && canPass(rc, prv[pathingCnt - 1])) {
//                    rc.setIndicatorLine(rc.getLocation(), rc.getLocation().add(prv[pathingCnt - 1]), 0, 255, 0);
                    pathingCnt--;
                }
                if (pathingCnt == 0) {
                    Direction dir = rc.getLocation().directionTo(location);
                    if (!canPass(rc, dir)) {
                        prv[pathingCnt++] = dir;
                    }
                }
                int pathingCntCutOff = Math.min(PRV_LENGTH, pathingCnt + 8); // if 8 then all dirs blocked
                while (pathingCnt > 0 && !canPass(rc, currentTurnDir == 0?prv[pathingCnt - 1].rotateLeft():prv[pathingCnt - 1].rotateRight())) {
                    prv[pathingCnt] = currentTurnDir == 0?prv[pathingCnt - 1].rotateLeft():prv[pathingCnt - 1].rotateRight();
//                    rc.setIndicatorLine(rc.getLocation(), rc.getLocation().add(prv[pathingCnt]), 255, 0, 0);
                    if (!rc.onTheMap(rc.getLocation().add(prv[pathingCnt]))) {
                        currentTurnDir ^= 1;
                        pathingCnt = 0;
                        indicator += "edge switch";
                        disableTurnDirRound = rc.getRoundNum() + 100;
                        return Direction.CENTER;
                    }
                    pathingCnt++;
                    if (pathingCnt == pathingCntCutOff) {
                        pathingCnt = 0;
                        indicator += "cutoff";
                        return Direction.CENTER;
                    }
                }
                Direction moveDir = pathingCnt == 0? prv[pathingCnt] :
                        (currentTurnDir == 0?prv[pathingCnt - 1].rotateLeft():prv[pathingCnt - 1].rotateRight());
                if (rc.canMove(moveDir)) {
                    return moveDir;
                } else {
                    // a robot blocking us while we are following wall, wait
                    indicator += "blocked";
                }
            }
        }
        lastPathingTarget = location;
        lastPathingTurn = rc.getRoundNum();

        return Direction.CENTER;
    }
}