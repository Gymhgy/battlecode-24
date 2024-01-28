package v8;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Random;

public strictfp class RobotPlayer {

    static final Random rng = new Random(23123);
    private static final int HEALING_CUTOFF = 500;
    static String indicator = "";
    /** Array containing all the possible movement directions. */
    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };
    static int BASE_ATTACK = 150;
    static int[] LEVEL_ATTACK = new int[] {
            BASE_ATTACK,
            (int)(1.05 * BASE_ATTACK),
            (int)(1.10 * BASE_ATTACK),
            (int)(1.15 * BASE_ATTACK),
            (int)(1.20 * BASE_ATTACK),
            (int)(1.30 * BASE_ATTACK),
            (int)(1.50 * BASE_ATTACK),
    };

    static int commId = 0;
    public static void spawn(RobotController rc) throws GameActionException {
        MapLocation[] spawnLocs = rc.getAllySpawnLocations();

        int minDist = 10000000;
        MapLocation spawn = null;
        for(MapLocation spawnLoc : spawnLocs) {
            if(!rc.canSpawn(spawnLoc)) continue;
            getTask(rc, spawnLoc);
            if(task != null) {
                if (spawnLoc.distanceSquaredTo(task) < minDist) {
                    minDist = spawnLoc.distanceSquaredTo(task);
                    spawn = spawnLoc;
                }
            }
            else if(spawn == null) {
                spawn = spawnLoc;
            }
        }
        // Pick a random spawn location to attempt spawning in.
        //MapLocation randomLoc = spawnLocs[rng.nextInt(spawnLocs.length)];
        //if (rc.canSpawn(randomLoc)) rc.spawn(spawn);
        if(spawn != null && rc.canSpawn(spawn))
            rc.spawn(spawn);
    }


    static MapLocation target;
    static boolean builder = false;
    static boolean sentry = false;
    static MapLocation oldLoc;
    static Team opp;
    static FlagInfo myFlag = null;
    public static void play(RobotController rc) throws GameActionException {
        indicator = "";
        noMacro = false;
        if (rc.getRoundNum() <= GameConstants.SETUP_ROUNDS) {
            StartPhase.play(rc, builder, sentry);
            return;
        }
        if(rc.getRoundNum() % 100 == 1) Communicator.reportBroadcast();

        Pathfinding.dig = true;
        if(!rc.isSpawned()) {
            if (myFlag != null) {
                Communicator.reportFlagDrop(myFlag.getID());
                myFlag = null;
            }
            spawn(rc);
            if(!rc.isSpawned())
                return;
        }

        if(rc.canBuyGlobal(GlobalUpgrade.ACTION)) {
            rc.buyGlobal(GlobalUpgrade.ACTION);
            BASE_ATTACK += 75;
            LEVEL_ATTACK = new int[] {
                    BASE_ATTACK,
                    (int)(1.05 * BASE_ATTACK),
                    (int)(1.10 * BASE_ATTACK),
                    (int)(1.15 * BASE_ATTACK),
                    (int)(1.20 * BASE_ATTACK),
                    (int)(1.30 * BASE_ATTACK),
                    (int)(1.50 * BASE_ATTACK),
            };
        }
        else if(rc.canBuyGlobal(GlobalUpgrade.HEALING)) {
            rc.buyGlobal(GlobalUpgrade.HEALING);
        }
        else if(rc.canBuyGlobal(GlobalUpgrade.CAPTURING)) rc.buyGlobal(GlobalUpgrade.CAPTURING);
        macroMoved = false;
        if(builder) {
            builderPlay(rc);
            return;
        }
        compareStuns(rc);
        sense(rc);
        for(FlagInfo f : rc.senseNearbyFlags(2, opp)) {
            if (rc.canPickupFlag(f.getLocation())) {
                rc.pickupFlag(f.getLocation());
                if(rc.senseMapInfo(rc.getLocation()).getSpawnZoneTeamObject()==rc.getTeam())break;
                myFlag = f;
                target = null;
                break;
            }
        }
        if(rc.isMovementReady() && rc.hasFlag()) {
            for(Direction d : Direction.allDirections()) {
                if(rc.canMove(d) && rc.senseMapInfo(rc.getLocation().add(d)).getSpawnZoneTeamObject()==rc.getTeam()) {
                    rc.move(d);
                    Communicator.reportFlagCapture(myFlag.getID());
                    myFlag = null;
                }
            }
        }
        micro(rc);
        macro(rc);

        if(rc.isActionReady()) {
            sense(rc);
            micro(rc);
        }
        checkNearbyFlag(rc);
        if(myFlag != null) {
            Communicator.reportCarrying((int)Math.min(Math.max(0, 5 - ourTeamStrength), 15));
        }
        updateStuns(rc);
        recordStuns(rc);
    }
    static boolean hadFlag = false;
    static MapLocation builderTarget = null;
    static void builderPlay(RobotController rc) throws GameActionException {
        sense(rc);
        //build(rc);
        if(inDanger) {
            int minDis = Integer.MAX_VALUE;
            cachedEnemyLocation = null;
            for (int i = enemyCnt; --i >= 0; ) {
                RobotInfo enemy = enemies[i];
                int dis = enemy.location.distanceSquaredTo(rc.getLocation());
                if (dis < minDis) {
                    cachedEnemyLocation = enemy.location;
                    minDis = dis;
                }
            }
            kite(rc, cachedEnemyLocation);
        }

        macro(rc);

        build(rc);
        if(rc.isActionReady()) {
            RobotInfo target = attackTarget == null? backupTarget : attackTarget;
            if(target != null && rc.canAttack(target.location))
                rc.attack(target.location);
        }
        if(rc.isActionReady()) {
            heal(rc);
        }
    }

    static void build(RobotController rc) throws GameActionException  {
        int minDis = Integer.MAX_VALUE;
        cachedEnemyLocation = null;
        for (int i = enemyCnt; --i >= 0; ) {
            RobotInfo enemy = enemies[i];
            int dis = enemy.location.distanceSquaredTo(rc.getLocation());
            if (dis < minDis) {
                cachedEnemyLocation = enemy.location;
                minDis = dis;
            }
        }
        cachedEnemyLocation = cachedEnemyLocation != null ? cachedEnemyLocation : taskOrTargetOrWhatever();
        Direction backDir = rc.getLocation().directionTo(
                cachedEnemyLocation == null ? rc.adjacentLocation(directions[v5stunagress.FastMath.rand256()%8]) : cachedEnemyLocation).opposite();
        Direction[] dirs = {Direction.CENTER, backDir, backDir.rotateLeft(), backDir.rotateRight(),
                backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
        boolean built = true;
        actionLoop:
        while(rc.isActionReady()) {
            if(cachedEnemyLocation != null) {
                inner:
                for(Direction d : dirs) {
                    if(!rc.onTheMap(rc.getLocation().add(d))) continue ;
                    if(!rc.canBuild(TrapType.STUN, rc.getLocation().add(d))) continue;
                    for(Direction dd : Direction.allDirections()) {
                        if(!rc.onTheMap(rc.getLocation().add(d).add(dd))) continue ;
                        if(rc.senseMapInfo(rc.getLocation().add(d).add(dd)).getTrapType() == TrapType.STUN) continue inner;
                    }
                    if (closeFriendsSize > 2 && enemyCnt > 4) {
                        rc.build(TrapType.STUN, rc.getLocation().add(d));
                        continue actionLoop;
                    }
                }

            }
            else {

                if(builder && macroMoved) {
                    inner:
                    for(Direction d : dirs) {
                        if(!rc.onTheMap(rc.getLocation().add(d))) continue ;
                        if(!rc.canBuild(TrapType.STUN, rc.getLocation().add(d))) continue;
                        for(Direction dd : Direction.allDirections()) {
                            if(!rc.onTheMap(rc.getLocation().add(d).add(dd))) continue ;
                            if(rc.senseMapInfo(rc.getLocation().add(d).add(dd)).getTrapType() == TrapType.STUN) continue inner;
                        }
                        if (friendCnt + enemyCnt*0.5 > 10) {
                            rc.build(TrapType.STUN, rc.getLocation().add(d));
                            continue actionLoop;
                        }
                    }
                }

            }

            if(builder && macroMoved) {
                if (friendCnt + enemyCnt > 20 ||
                        (rc.getCrumbs() > 1000 && friendCnt + 1.5*enemyCnt > 15)) {
                    for (Direction d : dirs) {
                        if (rc.canBuild(TrapType.EXPLOSIVE, rc.getLocation().add(d))) {
                            if(rc.senseMapInfo(rc.getLocation().add(d)).isWater()) continue;
                            rc.build(TrapType.EXPLOSIVE, rc.getLocation().add(d));
                            continue actionLoop;
                        }
                    }
                }
            }
            break;
        }
    }

    static MapLocation myClosestSpawn = null;
    static MapLocation task = null;
    static MapLocation taskOrTargetOrWhatever() {
        return (task!=null?task : (flagLoc != null ? flagLoc : approxFlagLog));
    }

    static void hasFlagBehavior(RobotController rc) throws GameActionException {
        int minDist = Integer.MAX_VALUE;
        if (myClosestSpawn == null) {
            for (MapLocation spawn : rc.getAllySpawnLocations()) {
                if(spawn.distanceSquaredTo(rc.getLocation()) < minDist) {
                    minDist = spawn.distanceSquaredTo(rc.getLocation());
                    myClosestSpawn = spawn;
                }
            }
        }
        if(!rc.isMovementReady()) {

        }
        Pathfinding.moveToward(rc, myClosestSpawn);
        return;
    }

    private static void macro(RobotController rc) throws GameActionException {
        findFlag(rc);
        getTask(rc);
        if (!rc.isMovementReady())
            return;

        if(rc.hasFlag()) {
            hasFlagBehavior(rc);
        }
        macroMoved = true;
        if(task != null) {
            Pathfinding.moveToward(rc, task);
            rc.setIndicatorLine(rc.getLocation(), task, 0,255,255);
            indicator += "t:"+task+",";
            return;
        }
        if(closeFriendsSize < 3 && (rc.getRoundNum() - lastLauncherAttackRound) < 10) {
            if (rc.isMovementReady() && groupingTarget != null ) {
                indicator += "group,";
                if (!rc.getLocation().isAdjacentTo(groupingTarget.location)) {
                    Pathfinding.follow(rc, groupingTarget.location);
                } else if (rc.getHealth() < groupingTarget.health) { // allowing healthier target to move away first
                    indicator += "stop";
                    rc.setIndicatorDot(rc.getLocation(), 0, 255, 0);
                    return;
                }
                rc.setIndicatorLine(rc.getLocation(), groupingTarget.location, 0, 255, 0);
            } else if (rc.isMovementReady()
                    && groupingTarget == null
                    && cachedGroupingTarget != null
                    && rc.getRoundNum() - cachedGroupingRound < 6
                    && !rc.getLocation().isAdjacentTo(cachedGroupingTarget.location)){
                indicator += String.format("cacheGroup%s,",cachedGroupingTarget.location);
                Pathfinding.follow(rc, cachedGroupingTarget.location);
                rc.setIndicatorLine(rc.getLocation(), cachedGroupingTarget.location, 0, 255, 0);
            }
        }

        if(noMacro) return;
        int byteCode = Clock.getBytecodesLeft();
        if(wander != null) {
            indicator+="wander,";
            rc.setIndicatorLine(rc.getLocation(), wander, 0,0,255);
            Pathfinding.moveToward(rc, wander);
        }
        else if(flagLoc != null && !(pickedUp && rc.getRoundNum() % 2 == 0))
            indicator += Pathfinding.moveToward(rc, flagLoc);
        else if (approxFlagLog != null)
            indicator += Pathfinding.moveToward(rc, approxFlagLog);
        else
            Pathfinding.randomMove(rc);
        if(Clock.getBytecodesLeft() < 2000) {
            System.out.println(byteCode + " " + Clock.getBytecodesLeft());
        }
    }

    static void getTask(RobotController rc) throws GameActionException {
        getTask(rc, rc.getLocation());
    }
    static void getTask(RobotController rc, MapLocation fromLoc) throws GameActionException {
        task = null;
        int minDist = Integer.MAX_VALUE;
        for(int i = 7; i-->4;) {
            if(rc.readSharedArray(i) == 0) continue;
            MapLocation loc = Communicator.getLoc(rc.readSharedArray(i) & 0b1111_1111_1111);
            int dist = loc.distanceSquaredTo(fromLoc);
            if(dist >= 15) continue;

            if(minDist > dist && dist < rc.getMapWidth()*rc.getMapWidth()/9) {
                minDist = loc.distanceSquaredTo(fromLoc);
                task = loc;
            }
        }
        if(task != null) {
            for(Direction d : Direction.allDirections()) {
                if(fromLoc.add(d).isAdjacentTo(task)) {
                    if(rc.canFill(fromLoc.add(d)))
                        rc.canFill(fromLoc.add(d));
                }
            }
        }
        if(task == null) {
            minDist = Integer.MAX_VALUE;
            for (int i = 4; i-- > 1; ) {
                if (rc.readSharedArray(i) == 0) continue;
                MapLocation loc = Communicator.getLoc(rc.readSharedArray(i) & 0b1111_1111_1111);
                if (minDist > loc.distanceSquaredTo(fromLoc)) {
                    minDist = loc.distanceSquaredTo(fromLoc);
                    task = loc;
                }
            }
        }
        //if(minDist > rc.getMapWidth()*rc.getMapWidth()/9) task = null;
        if(task == null) {
            minDist = Integer.MAX_VALUE;
            for(int i = 7; i-->4;) {
                if(rc.readSharedArray(i) == 0) continue;
                MapLocation loc = Communicator.getLoc(rc.readSharedArray(i) & 0b1111_1111_1111);
                int dist = loc.distanceSquaredTo(fromLoc);
                if(rc.readSharedArray(i) >> 12 == 0) continue;

                if(minDist > dist /*&& dist < rc.getMapWidth()*rc.getMapWidth()/9*/) {
                    minDist = loc.distanceSquaredTo(fromLoc);
                    task = loc;
                }
            }
        }
        if(flagLoc != null && task != null && fromLoc.distanceSquaredTo(task)*1.5 > fromLoc.distanceSquaredTo(flagLoc)) {
            task = null;
        }
    }

    static MapLocation flagLoc = null;
    static MapLocation approxFlagLog = null;
    static boolean pickedUp = false;
    static void checkNearbyFlag(RobotController rc) throws GameActionException {
        MapLocation potentialFlagLoc = null;
        int potentialId = 0;
        int minDist = Integer.MAX_VALUE;
        boolean flagFound = false;
        for (FlagInfo flag : rc.senseNearbyFlags(GameConstants.VISION_RADIUS_SQUARED, rc.getTeam().opponent())) {
            if(flag.isPickedUp()) continue;
            if (flag.getLocation().distanceSquaredTo(rc.getLocation()) < minDist) {
                potentialFlagLoc = flag.getLocation();
                potentialId = flag.getID();
                pickedUp = flag.isPickedUp();
                minDist = flag.getLocation().distanceSquaredTo(rc.getLocation());
            }
            if (flagLoc != null && flag.getLocation().equals(flagLoc)) {
                flagFound = true;
            }
        }
        if(potentialFlagLoc != null )
            if(!flagFound || flagLoc == null) {
                flagLoc = potentialFlagLoc;
                wander = null;
                if(approxFlagLog != null) {
                    indicator+="reportedFlag,";
                    Communicator.reportFlag(rc, approxFlagLog, potentialFlagLoc, potentialId);
                }
            }
    }
    static void findFlag(RobotController rc) throws GameActionException {

        if(Communicator.allFlagsFound()) {
            approxFlagLog = null;
            flagLoc = null;
            int minDist = Integer.MAX_VALUE;
            for(int i = 61; i-->58;) {
                if(rc.readSharedArray(i) == Communicator.CAPTURED) continue;
                if(Communicator.beingCaptured(rc.readSharedArray(i-3))) continue;
                MapLocation loc = Communicator.getLoc(rc.readSharedArray(i));
                if(minDist > rc.getLocation().distanceSquaredTo(loc)) {
                    minDist = rc.getLocation().distanceSquaredTo(loc);
                    flagLoc = loc;
                }
            }
            return;
        }

        if(approxFlagLog != null && stillBroadcasted(rc, approxFlagLog)) {indicator+="bc,";}
        if(flagLoc != null &&
                rc.canSenseLocation(flagLoc) &&
                !existsFlagAt(rc,flagLoc)) {
            flagLoc = null;
        }
        checkNearbyFlag(rc);
        if(flagLoc == null && approxFlagLog != null && stillBroadcasted(rc, approxFlagLog)) {
            MapLocation potential = Communicator.approxToActual(rc, approxFlagLog);
            if (!potential.equals(approxFlagLog)) {
                flagLoc = potential;
                indicator += "received";
                wander = null;
            }
        }
        if(wander != null) {
            if(rc.getLocation().distanceSquaredTo(wander) <= 2 && flagLoc == null || teammateAroundWander(rc)) {
                indicator+="updateWander,";
                generateWander(rc, approxFlagLog);
            }
            return;
        }
        if(approxFlagLog != null &&
                rc.getLocation().isWithinDistanceSquared(approxFlagLog, 20) &&
                flagLoc == null) {
            //Generate wander to some place on a circle
            if(wander == null) {
                indicator+="setWander,";

                generateWander(rc, approxFlagLog);
            }
        }

        if(approxFlagLog == null || rc.getRoundNum()%100==0 || (!stillBroadcasted(rc, approxFlagLog) && flagLoc==null)) {
            int minDist = Integer.MAX_VALUE;
            for (MapLocation loc : rc.senseBroadcastFlagLocations()) {
                if (loc.distanceSquaredTo(rc.getLocation()) < minDist) {
                    approxFlagLog = loc;
                    minDist = loc.distanceSquaredTo(rc.getLocation()) + 50;
                }
            }
        }
        if(flagLoc == null && approxFlagLog != null && stillBroadcasted(rc, approxFlagLog)) {
            MapLocation potential = Communicator.approxToActual(rc, approxFlagLog);
            if (!potential.equals(approxFlagLog)) {
                flagLoc = potential;
                indicator += "received";
                wander = null;
            }
        }
        if(flagLoc != null &&
                rc.canSenseLocation(flagLoc) &&
                !existsFlagAt(rc,flagLoc)) {
            indicator += "fLNULL,";
            flagLoc = null;
        }
    }
    static MapLocation wander;
    static void generateWander(RobotController rc, MapLocation center) {
        do  {
            int i = FastMath.rand256() % 8;
            int x = new int[]{-6,0,6,-6,6,-6,0,6}[i];
            int y = new int[]{-6,-6,-6,0,0,6,6,6}[i];
            wander = new MapLocation(center.x + x, center.y + y);
        } while(!rc.onTheMap(wander));
    }
    static boolean teammateAroundWander(RobotController rc) throws GameActionException {
        for(Direction d : Direction.allDirections()) {
            MapLocation p = wander.add(d);
            if(rc.canSenseLocation(p) && rc.senseRobotAtLocation(p) != null && rc.senseRobotAtLocation(p).team == rc.getTeam()) {
                return true;
            }
        }
        return false;
    }
    static boolean stillBroadcasted(RobotController rc, MapLocation approx) throws GameActionException {
        MapLocation[] b = rc.senseBroadcastFlagLocations();
        for(int i = b.length; i-->0; ) if(approx.equals(b[i])) return true;
        return false;
    }
    static boolean existsFlagAt(RobotController rc, MapLocation loc) throws GameActionException {
        for (FlagInfo flag : rc.senseNearbyFlags(GameConstants.VISION_RADIUS_SQUARED, rc.getTeam().opponent())) {
            if(flag.getLocation() == loc) return true;
        }
        return false;
    }

    static boolean isDiagonal(Direction dir) {
        return dir.dx * dir.dy != 0;
    }
    private static void chase(RobotController rc, MapLocation location) throws GameActionException {
        Direction forwardDir = rc.getLocation().directionTo(location);
        Direction[] dirs = {forwardDir, forwardDir.rotateLeft(), forwardDir.rotateRight(),
                forwardDir.rotateLeft().rotateLeft(), forwardDir.rotateRight().rotateRight()};
        Direction bestDir = null;
        int minCanSee = Integer.MAX_VALUE;
        // pick a direction to chase to minimize the number of enemy launchers that can see us
        for (Direction dir : dirs) {
            if (rc.canMove(dir) && rc.getLocation().add(dir).distanceSquaredTo(location) <= 4) {
                int canSee = 0;
                for (int i = enemyCnt; --i >= 0;){
                    int newDis = rc.getLocation().add(dir).distanceSquaredTo(enemies[i].location);
                    if (newDis <= 14) {
                        canSee++;
                    }
                }
                if (minCanSee > canSee) {
                    bestDir = dir;
                    minCanSee = canSee;
                } else if (minCanSee == canSee && isDiagonal(bestDir) && !isDiagonal(dir)) {
                    // from Cow: we prefer non-diagonal moves to preserve formation
                    bestDir = dir;
                }
            }
        }
        if (bestDir != null) {
            indicator += String.format("chase%s,", location);
            rc.move(bestDir);
            noMacro = true;
        } else {
            indicator += "failchase,";
        }
    }

    //region <vars>
    static RobotInfo attackTarget = null;
    static RobotInfo backupTarget = null;
    static RobotInfo chaseTarget = null;

    private static final int MAX_ENEMY_CNT = 25;
    static RobotInfo[] enemies = new RobotInfo[MAX_ENEMY_CNT];
    static int enemyCnt;
    private static final int MAX_FRIENDLY_CNT = 25;
    static RobotInfo[] allies = new RobotInfo[MAX_FRIENDLY_CNT];
    static int friendCnt;

    static RobotInfo groupingTarget = null;
    static RobotInfo cachedGroupingTarget = null;
    static int cachedGroupingRound = -1000;
    static int lastLauncherAttackRound = -100;
    static double ourTeamStrength = 1;
    static MapLocation cachedEnemyLocation = null;
    static int cachedRound = 0;
    private static int closeFriendsSize = 0;
    static boolean inDanger = false;
    //endregion
    static void sense(RobotController rc) throws GameActionException {
        attackTarget = null;
        chaseTarget = null;
        groupingTarget = null;
        backupTarget = null;
        ourTeamStrength = 1;
        friendCnt = 0;
        enemyCnt = 0;
        closeFriendsSize = 0;
        inDanger = false;
        int backupTargetDis = Integer.MAX_VALUE;
        MapLocation loc = flagLoc != null ? flagLoc : approxFlagLog != null ? approxFlagLog : null;
        for (RobotInfo robot : rc.senseNearbyRobots()) {
            if (robot.team == rc.getTeam()) {
                if (friendCnt >= MAX_FRIENDLY_CNT) {
                    continue;
                }
                if (groupingTarget == null
                        || robot.hasFlag
                        || robot.getHealth() > groupingTarget.getHealth()
                        || (loc != null && robot.getHealth() == groupingTarget.getHealth() &&
                                robot.location.distanceSquaredTo(loc) < groupingTarget.location.distanceSquaredTo(loc))
                ) {
                    if(robot.buildLevel < 2 && (groupingTarget==null || !groupingTarget.hasFlag))
                        groupingTarget = robot;
                }
                if(robot.buildLevel < 4)
                    allies[friendCnt++] = robot;
                if(robot.buildLevel < 4)
                    ourTeamStrength += 1;
                if (robot.location.distanceSquaredTo(rc.getLocation()) <= 8){
                    closeFriendsSize++;
                }
            }
            else {
                if (enemyCnt >= MAX_ENEMY_CNT) {
                    continue;
                }
                enemies[enemyCnt++] = robot;
                double minMod = 1;
                for(int i = boomedStunInfos.size(); i-->0;)
                    if(boomedStunInfos.get(i).within(robot.location))
                        minMod=Math.min(minMod, boomedStunInfos.get(i).modifier());
                if(!robot.hasFlag) {
                    ourTeamStrength -= 1 * minMod;
                    rc.setIndicatorDot(robot.location, (int)(255*minMod), 0, 0);
                }
                if (robot.location.distanceSquaredTo(rc.getLocation()) > GameConstants.ATTACK_RADIUS_SQUARED) {
                    chaseTarget = robot;
                }
                if(robot.location.isWithinDistanceSquared(rc.getLocation(), 8)) inDanger = true;
            }
        }
        attackTarget = chooseAttackTarget(rc);

        //Sense if allied flags are taken
        checkFlag:
        for(FlagInfo f : rc.senseNearbyFlags(20, rc.getTeam())) {
            for(int i = 3; i-->0;) {
                if (Communicator.myFlags[i].equals(f.getLocation())) break checkFlag;
            }
            Communicator.reportAllyFlagTaken(f.getLocation(), f.getID());
        }
    }

    static void recordStuns(RobotController rc) {
        stunInfos = new ArrayList<>();
        for(MapInfo mi : rc.senseNearbyMapInfos()) {
            if(mi.getTrapType() == TrapType.STUN) {
                stunInfos.add(new StunInfo(mi.getMapLocation()));
            }
        }
    }
    static ArrayList<StunInfo> boomedStunInfos = new ArrayList<>();
    static ArrayList<StunInfo> stunInfos = new ArrayList<>();
    static void compareStuns(RobotController rc) throws GameActionException {
        for(int i = stunInfos.size(); i-->0; ) {
            if(!rc.canSenseLocation(stunInfos.get(i).location)) {
                continue;
            }
            if(rc.senseMapInfo(stunInfos.get(i).location).getTrapType()!=TrapType.STUN) {
                stunInfos.get(i).boom();
                boomedStunInfos.add(stunInfos.get(i));
            }
        }
    }
    static void updateStuns(RobotController rc) throws GameActionException {
        for(int i = boomedStunInfos.size(); i-->0; ) {
            if(!rc.canSenseLocation(boomedStunInfos.get(i).location)) {
                boomedStunInfos.remove(i);
                continue;
            }
            boomedStunInfos.get(i).tick();
            if(boomedStunInfos.get(i).turnsLeft == 0) boomedStunInfos.remove(i);
        }
    }

    static void micro(RobotController rc) throws GameActionException {
        RobotInfo target = attackTarget == null? backupTarget : attackTarget;
        if (target != null) {
            if (target == attackTarget) {
                lastLauncherAttackRound = rc.getRoundNum();
            }
            RobotInfo deadTarget = null;
            if (rc.canAttack(target.location)) {
                cachedRound = rc.getRoundNum();
                if (target.health <= LEVEL_ATTACK[rc.getLevel(SkillType.ATTACK)]) {
                    deadTarget = target;
                }
                rc.attack(target.location);
            }
            inDanger = false;
            // find the closest guy alive, cache him and kite back
            int minDis = Integer.MAX_VALUE;
            cachedEnemyLocation = null;
            for (int i = enemyCnt; --i >= 0;) {
                RobotInfo enemy = enemies[i];
                int dis = enemy.location.distanceSquaredTo(rc.getLocation());
                if (enemy != deadTarget && dis < minDis) {
                    cachedEnemyLocation = enemy.location;
                    minDis = dis;
                }
            }
            if(minDis <= 4) inDanger = true;
            if (cachedEnemyLocation != null && rc.isMovementReady() && inDanger) {
                kite(rc, cachedEnemyLocation);
            }
        }
        if(friendCnt > 10 && enemyCnt > 10) build(rc);
        if(rc.isActionReady()) heal(rc);
        if (rc.getHealth() < HEALING_CUTOFF && inDanger && chaseTarget != null) {
            kite(rc, chaseTarget.location);
            return;
        }
        if (rc.isMovementReady() /*&& rc.isActionReady()*/) {
            if (chaseTarget != null) {
                cachedEnemyLocation = chaseTarget.location;
                cachedRound = rc.getRoundNum();
                if (ourTeamStrength > 2) {
                    chase(rc, chaseTarget.location);
                } else { // we are at disadvantage, pull back
                    if(inDanger)
                        kite(rc, chaseTarget.location);
                }
            } else if (cachedEnemyLocation != null && rc.getRoundNum() - cachedRound <= 2) {
                chase(rc, cachedEnemyLocation);
            }
        }
    }
    static boolean noMacro = false;
    static boolean macroMoved = false;
    static void kite(RobotController rc, MapLocation location) throws GameActionException {

        Direction backDir = rc.getLocation().directionTo(location).opposite();
        Direction[] dirs = {Direction.CENTER, backDir, backDir.rotateLeft(), backDir.rotateRight(),
                backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
        Direction bestDir = null;
        int minCanSee = Integer.MAX_VALUE;
        int maxFriends = Integer.MIN_VALUE;
        for (Direction dir : dirs) {
            if (rc.canMove(dir)) {
                int canSee = 0;
                int friends = 0;
                for (int i = enemyCnt; --i >= 0;){
                    int newDis = rc.getLocation().add(dir).distanceSquaredTo(enemies[i].location);
                    if (newDis <= 10) {
                        canSee++;
                    }
                }
                for (int i = friendCnt; --i >= 0;){
                    int newDis = rc.getLocation().add(dir).distanceSquaredTo(allies[i].location);
                    if (newDis <= 4) {
                        friends++;
                    }
                }
                if (minCanSee > canSee) {
                    bestDir = dir;
                    minCanSee = canSee;
                }
                else if (minCanSee == canSee && friends > maxFriends) {
                    maxFriends = friends;
                    bestDir = dir;
                } else if (minCanSee == canSee && friends == maxFriends && isDiagonal(bestDir) && !isDiagonal(dir)) {
                    bestDir = dir;
                }
            }
        }
        if (bestDir != null){
            indicator += "kite,";
            noMacro = true;
            rc.move(bestDir);
        }
    }

    public static RobotInfo chooseAttackTarget(RobotController rc) {
        int minHitsReq = Integer.MAX_VALUE;
        int minDist = Integer.MIN_VALUE;
        int byteCode = Clock.getBytecodesLeft();
        RobotInfo ret = null;
        for (int i = enemyCnt; i-->0;) {
            //if(Clock.getBytecodesLeft() < 1000) {
            //    System.out.println(byteCode + " " + Clock.getBytecodesLeft());
            //}
            RobotInfo enemy = enemies[i];
            int dist = enemy.location.distanceSquaredTo(rc.getLocation());
            if(!rc.canAttack(enemy.location))
                continue;
            if(enemy.hasFlag) return enemy;
            if(LEVEL_ATTACK[rc.getLevel(SkillType.ATTACK)] >= enemy.getHealth())
                return enemy;

            int friendDamage = 0;
            int friends = 1;
            for(int j = friendCnt; j-- > 0;) {
                int friendDist = allies[j].location.distanceSquaredTo(enemy.location);
                if (friendDist <= GameConstants.ATTACK_RADIUS_SQUARED) {
                    friendDamage += LEVEL_ATTACK[allies[j].attackLevel];
                    friends ++;
                }
            }
            int hitsReq = (enemy.health + friendDamage - 1) / LEVEL_ATTACK[rc.getLevel(SkillType.ATTACK)] / friends;

            if (hitsReq < minHitsReq || (hitsReq == minHitsReq && dist < minDist)) {
                minHitsReq = hitsReq;
                ret = enemy;
                minDist = dist;
            }
        }
        return ret;

    }

    public static void heal(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return;
        RobotInfo target = chooseHealTarget(rc);
        if (target != null) {
            rc.heal(target.location);
        }
    }

    public static RobotInfo chooseHealTarget(RobotController rc) {
        int minHp = Integer.MAX_VALUE;
        RobotInfo ret = null;
        for(int i = 0; i < friendCnt; i++) {
            if(allies[i].health < minHp && rc.canHeal(allies[i].getLocation())) {
                ret = allies[i];
                minHp = allies[i].health;
            }
        }
        return ret;
    }

    public static void endTurn(RobotController rc) {
        if(!builder && flagLoc != null) indicator+="fL:"+flagLoc.toString()+",";
        if(!builder && approxFlagLog != null) indicator+="aFL:"+approxFlagLog.toString()+",";
        if(!builder && flagLoc == null && approxFlagLog == null) indicator += "null,";
        rc.setIndicatorString(indicator);
        if (builderTarget != null && rc.isSpawned())
            rc.setIndicatorLine(rc.getLocation(), builderTarget, 255,0,0);
        else if (flagLoc != null && rc.isSpawned())
            rc.setIndicatorLine(rc.getLocation(), flagLoc, 255,0,0);
        else if (approxFlagLog != null && rc.isSpawned())
            rc.setIndicatorLine(rc.getLocation(), approxFlagLog, 255,0,0);
    }

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        if(rc.readSharedArray(0) < 3) {
            builder = true;
        }
        FastMath.initRand(rc);
        Communicator.init(rc);
        Utils.init(rc);
        if(Utils.commId <= 6 && Utils.commId > 3) sentry = true;
        while (true) {

            try {
                play(rc);
                endTurn(rc);
                if(rc.getRoundNum() > 200)
                    Communicator.cleanup();
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();

            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();

            } finally {
                Clock.yield();
            }
        }

    }
}
