package voff6;

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

        if(FastMath.rand256()%4==0) {
            for(MapLocation spawnLoc : Utils.spawnGroups[Utils.commId%3]) {
                if (rc.canSpawn(spawnLoc)) {
                    rc.spawn(spawnLoc);
                    return;
                }
            }
        }

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
        }
        // Pick a random spawn location to attempt spawning in.
        //MapLocation randomLoc = spawnLocs[rng.nextInt(spawnLocs.length)];
        //if (rc.canSpawn(randomLoc)) rc.spawn(spawn);
        if(spawn == null || !rc.canSpawn(spawn)) {
            for(MapLocation spawnLoc : Utils.spawnGroups[Utils.commId%3]) {
                if (rc.canSpawn(spawnLoc)) {
                    rc.spawn(spawnLoc);
                    return;
                }
            }
        }
        if(spawn != null && rc.canSpawn(spawn))
            rc.spawn(spawn);
    }
    static int sentryTurns = 0;

    static MapLocation cTarget;
    static boolean builder = false;
    static boolean sentry = false;
    static MapLocation oldLoc;
    static Team opp;
    static int spawnRound;
    static boolean defender;
    static FlagInfo myFlag = null;
    static int sniffTurns = 0;
    static int acqSniffAt = 0;
    static boolean sniff = false;
    public static void play(RobotController rc) throws GameActionException {
        indicator = "";
        indicator+= Utils.commId+":";
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
            spawnRound = rc.getRoundNum();
        }

        defender = (rc.getRoundNum() - spawnRound < rc.getMapWidth()/2 || rc.getID()%3 == 0);
        if (defender) indicator += "DEF,";
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

        /*if(sentry) {
            sense(rc);
            micro(rc);
            if(!noMacro) Pathfinding.moveToward(rc, StartPhase.dropSpot);
            if(rc.getLocation().isWithinDistanceSquared(StartPhase.dropSpot, 4)) {
                boolean flagStillHere = false;
                for(FlagInfo f : rc.senseNearbyFlags(20, rc.getTeam())) {
                    if(f.getID() == StartPhase.flagId) {
                        flagStillHere = true;
                    }
                }
                sentryTurns++;
                sense(rc);
                if(enemyCnt > 0) {
                    int idx = Utils.defenseFlagOffset(StartPhase.flagId) + 1;
                    if(rc.readSharedArray(idx) > 0) return;

                    if(!flagStillHere)
                        Communicator.reportAllyFlagTaken(StartPhase.dropSpot, StartPhase.flagId);
                    if(flagStillHere) sentryTurns = 0;
                }

            }
            else {
                sentryTurns = 0;
            }
            if(sentryTurns > 100) sentry = false;
            indicator += "sentryTurns:"+sentryTurns+",";
            indicator += "sentryID:" + StartPhase.flagId +",";
            return;
        }*/
        justDropped = false;
        /*if(builder) {
            builderPlay(rc);
            return;
        }*/
        compareStuns(rc);
        sense(rc);
        tryPickFlag(rc);
        if(rc.isMovementReady() && rc.hasFlag()) {
            for(Direction d : Direction.allDirections()) {
                if(rc.canMove(d) && rc.senseMapInfo(rc.getLocation().add(d)).getSpawnZoneTeamObject()==rc.getTeam()) {
                    rc.move(d);
                    Communicator.reportFlagCapture(myFlag.getID());
                    myFlag = null;
                }
            }
        }
        if(rc.hasFlag()) {
            hasFlagBehavior(rc);
        }
        micro(rc);

        MapLocation[] c = rc.senseNearbyCrumbs(GameConstants.VISION_RADIUS_SQUARED);
        if(c.length > 0 && Communicator.canSniff() && (!sniff) && !builder) {
            sniff = true;
            Communicator.addSniffer();
        }
        if(sniff) {
            int dist = Integer.MAX_VALUE;
            if (cTarget == null || (rc.canSenseLocation(cTarget) && rc.senseMapInfo(cTarget).getCrumbs() == 0)) {
                cTarget = null;
                for (int i = c.length; i-- > 0; ) {
                    if(Communicator.shouldSniff(c[i])) {
                        if (c[i].distanceSquaredTo(rc.getLocation()) < dist) {
                            dist = c[i].distanceSquaredTo(rc.getLocation());
                            cTarget = c[i];
                        }
                    }
                }
                acqSniffAt = rc.getRoundNum();
            }
            if(cTarget == null) {
                sniffTurns++;
            }
            else {
                if(rc.getRoundNum()-acqSniffAt > 15) {
                    sniff = false;
                    sniffTurns = 0;
                    Communicator.recordNeverSniff(cTarget);
                }
                if(!noMacro) {
                    Pathfinding.moveToward(rc, cTarget);
                }
                noMacro = true;
                sniffTurns = 0;
            }
            if(sniffTurns > 5) {
                Communicator.removeSniffer();
                sniff = false;
            }
            indicator += "sniff:" + sniffTurns + "/" + cTarget + ",";

            rc.setIndicatorDot(rc.getLocation(), 0,0,0);
        }

        macro(rc);

        if(rc.isActionReady()) {
            sense(rc);
            micro(rc);
        }
        if(rc.isActionReady()) heal(rc);
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
                cachedEnemyLocation == null ? rc.adjacentLocation(directions[FastMath.rand256()%8]) : cachedEnemyLocation).opposite();
        Direction[] dirs = {Direction.CENTER, backDir, backDir.rotateLeft(), backDir.rotateRight(),
                backDir.rotateLeft().rotateLeft(), backDir.rotateRight().rotateRight()};
        boolean built = true;
        actionLoop:
        while(rc.isActionReady()) {
            if(!macroMoved || entourage) {
                inner:
                for(Direction d : dirs) {
                    if(!rc.onTheMap(rc.getLocation().add(d))) continue ;
                    if(!rc.canBuild(TrapType.STUN, rc.getLocation().add(d))) continue;
                    for(Direction dd : Direction.allDirections()) {
                        if(!rc.onTheMap(rc.getLocation().add(d).add(dd))) continue ;
                        if(rc.senseMapInfo(rc.getLocation().add(d).add(dd)).getTrapType() == TrapType.STUN) continue inner;
                    }
                    if ((closeFriendsSize > 2 && enemyCnt > 6) || (entourage && enemyCnt > 4)) {
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
                if (friendCnt + enemyCnt > 15 ||
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
    static MapLocation actualFlagLoc;
    static void tryPickFlag(RobotController rc) throws GameActionException {
        /*boolean picked = false;
        if(!rc.isActionReady()) return;
        for(FlagInfo f : rc.senseNearbyFlags(2, opp)) {
            if(!rc.canPickupFlag(f.getLocation())) continue;
            int idx = 0;
            for (int i = 58; i-- > 55; ) {
                if (rc.readSharedArray(i) == f.getID()) {
                    //57 --> 29 --> 2
                    //56 --> 21 --> 1
                    //55 --> 13 --> 0
                    idx = i-22;
                    break;
                }
            }
            if(rc.readSharedArray(idx) == 0) {
                rc.pickupFlag(f.getLocation());
                picked = true;
            }
            if(f.getLocation().add(Communicator.readThrowDirection(f.getID())).equals(rc.getLocation())) {
                rc.pickupFlag(f.getLocation());
                picked = true;
            }
            if(picked) {
                if (rc.senseMapInfo(rc.getLocation()).getSpawnZoneTeamObject() == rc.getTeam()) {
                    Communicator.reportFlagCapture(f.getID());
                    break;
                }
                myFlag = f;
                break;
            }
        }*/


        boolean picked = false;
        for(FlagInfo f : rc.senseNearbyFlags(2, opp)) {
            if(!rc.canPickupFlag(f.getLocation())) continue;
            int idx = 0;
            for(int i = 58;i-->55;) {
                if(rc.readSharedArray(i) == f.getID()) {
                    idx = i - 51;
                    break;
                }
            }
            if(rc.readSharedArray(idx) >> 12 == 1) {
                rc.pickupFlag(f.getLocation());
                picked = true;
            }
            else if (!justDropped) {
                if(f.getLocation() == actualFlagLoc) {
                    rc.pickupFlag(f.getLocation());
                    picked = true;
                }
                else {
                    boolean shouldpickup = true;
                    int minDist = Integer.MAX_VALUE;
                    MapLocation closest = null;
                    for (MapLocation spawn : rc.getAllySpawnLocations()) {
                        if(spawn.distanceSquaredTo(rc.getLocation()) < minDist) {
                            minDist = spawn.distanceSquaredTo(rc.getLocation());
                            closest = spawn;
                        }
                    }

                    for(int i = friendCnt; i-->0;){
                        RobotInfo friend = allies[i];
                        if(!Communicator.canPickup(f.getID(), friend.ID)) continue;
                        if(friend.getLocation().isWithinDistanceSquared(f.getLocation(), 2)) {
                            if(friend.getLocation().distanceSquaredTo(closest) < rc.getLocation().distanceSquaredTo(closest)) {
                                shouldpickup = false;
                                break;
                            }
                        }
                    }

                    if(shouldpickup) {
                        rc.pickupFlag(f.getLocation());
                        picked = true;
                    }
                    else {
                        Communicator.reportCannotPickup(f.getID(), rc.getID());
                        indicator+= "nopick,";
                    }
                }
            }
            if(picked) {
                if (rc.senseMapInfo(rc.getLocation()).getSpawnZoneTeamObject() == rc.getTeam()) {
                    Communicator.reportFlagCapture(f.getID());
                    break;
                }
                myFlag = f;
                break;
            }
        }
    }

    static int assignedSpawnAt;
    /*static void hasFlagBehavior(RobotController rc) throws GameActionException {
        int minDist = Integer.MAX_VALUE;
        if (myClosestSpawn == null || rc.getRoundNum() - assignedSpawnAt > 50) {
            MapLocation pot = null;
            for (MapLocation spawn : rc.getAllySpawnLocations()) {
                if(myClosestSpawn != null && spawn.isWithinDistanceSquared(myClosestSpawn, 2)) continue;
                if(spawn.distanceSquaredTo(rc.getLocation()) < minDist) {
                    minDist = spawn.distanceSquaredTo(rc.getLocation());
                    pot = spawn;
                    assignedSpawnAt = rc.getRoundNum();
                }
            }
            myClosestSpawn = pot;
        }
        indicator += "S:" + myClosestSpawn + ",";

        Direction d1 = Pathfinding.mockMoveTowards(rc, rc.getLocation(), myClosestSpawn);
        if(!rc.canMove(d1)) {
            if(rc.canDropFlag(rc.getLocation().add(d1))) {
                Direction d2 = Pathfinding.mockMoveTowards(rc, rc.getLocation().add(d1), myClosestSpawn);
                Communicator.encodeThrowDirection(myFlag.getID(), d1, d2);
                Communicator.reportFlagDrop(myFlag.getID());
                myFlag = null;
                justDropped = true;
                rc.dropFlag(rc.getLocation().add(d1));
                Pathfinding.resetAfterMock();
            }
        }
        else {
            rc.move(d1);
        }
    }*/

    static void hasFlagBehavior(RobotController rc) throws GameActionException {
        int minDist = Integer.MAX_VALUE;
        if (myClosestSpawn == null || rc.getRoundNum() - assignedSpawnAt > 50) {
            MapLocation pot = null;
            for (MapLocation spawn : rc.getAllySpawnLocations()) {
                if(myClosestSpawn != null && spawn.isWithinDistanceSquared(myClosestSpawn, 2)) continue;
                if(spawn.distanceSquaredTo(rc.getLocation()) < minDist) {
                    minDist = spawn.distanceSquaredTo(rc.getLocation());
                    pot = spawn;
                    assignedSpawnAt = rc.getRoundNum();
                }
            }
            myClosestSpawn = pot;
        }

        indicator += "S:" + myClosestSpawn + ",";
        Direction d = Pathfinding.mockMoveTowards(rc, rc.getLocation(), myClosestSpawn);
        if(d == Direction.CENTER || !rc.canMove(d)) {
            tryThrowFlag(rc, d);
        }
        indicator += "d:" + d + ",";
        if(/*rc.isMovementReady()*/ rc.getHealth() < 350) {
            tryThrowFlag(rc, d);
        }
        if(rc.canMove(d) && rc.hasFlag()) {
            rc.move(d);
        }

    }
    static boolean justDropped = false;
    static void tryThrowFlag(RobotController rc, Direction d) throws GameActionException{
        if(!rc.isActionReady()) return;
        if(rc.getLocation().add(d).distanceSquaredTo(myClosestSpawn) > rc.getLocation().distanceSquaredTo(myClosestSpawn)) {
            indicator+="throw-far,";
            return;
        }
        if(Direction.CENTER == d) {
            for (int i = friendCnt; i-- > 0; ) {
                RobotInfo friend = allies[i];
                if (friend.getLocation().isWithinDistanceSquared(rc.getLocation(), 8)) {
                    if (friend.getLocation().distanceSquaredTo(myClosestSpawn) < rc.getLocation().distanceSquaredTo(myClosestSpawn)) {
                        if (rc.canDropFlag(rc.getLocation().add(rc.getLocation().directionTo(friend.location)))) {
                            rc.dropFlag(rc.getLocation().add(rc.getLocation().directionTo(friend.location)));
                            Communicator.reportFlagDrop(myFlag.getID());
                            myFlag = null;
                            justDropped = true;
                            break;
                        }
                    }
                }
            }
        }
        else if(rc.canDropFlag(rc.getLocation().add(d))){
            for (Direction dd : Direction.allDirections()) {
                MapLocation n = rc.getLocation().add(d).add(dd);
                if(n.equals(rc.getLocation())) continue;;
                if(rc.canSenseLocation(n)) {
                    RobotInfo j = rc.senseRobotAtLocation(n);
                    if(j != null && j.team == rc.getTeam()) {
                        rc.dropFlag(rc.getLocation().add(d));
                        Communicator.reportFlagDrop(myFlag.getID());
                        myFlag = null;
                        justDropped = true;
                        break;
                    }
                }
            }
        }
    }
    private static void macro(RobotController rc) throws GameActionException {
        findFlag(rc);
        getTask(rc);
        if (!rc.isMovementReady())
            return;

        if(rc.hasFlag()) {
            hasFlagBehavior(rc);
            return;
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
        if(wander != null && flagLoc == null) {
            indicator+="wander,";
            rc.setIndicatorLine(rc.getLocation(), wander, 0,0,255);
            Pathfinding.moveToward(rc, wander);
        }
        else if(flagLoc != null)
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
    static boolean entourage = false;
    static void getTask(RobotController rc, MapLocation fromLoc) throws GameActionException {
        task = null;
        entourage = false;
        int minDist = Integer.MAX_VALUE;
        boolean defending = false;
        for(int i = 7; i-->4;) {
            if(rc.readSharedArray(i) == 0) continue;
            MapLocation loc = Communicator.getLoc(rc.readSharedArray(i) & 0b1111_1111_1111);
            int dist = loc.distanceSquaredTo(fromLoc);
            if(dist >= 36) continue;

            if(minDist > dist) {
                minDist = loc.distanceSquaredTo(fromLoc);
                task = loc;
                entourage = true;
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
                    actualFlagLoc = loc;
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
                actualFlagLoc = potential;
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
                actualFlagLoc = potential;
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
        for(FlagInfo f : rc.senseNearbyFlags(16, rc.getTeam())) {
            for(int i = 3; i-->0;) {
                if (Communicator.myFlags[i].equals(f.getLocation())) break checkFlag;
            }
            for (int j = enemyCnt; j-- > 0; ) {
                if (enemies[j].getLocation().isWithinDistanceSquared(f.getLocation(), 20)) {
                    Communicator.reportAllyFlagTaken(f.getLocation(), f.getID());
                    break checkFlag;
                }
            }
            Communicator.reportAllyFlagTaken(f.getLocation(), f.getID());
        }
        /*
        checkFlag2:
        for(int i = 3; i-->0;) {
            int id = Communicator.myFlagIds[i];
            MapLocation floc = Communicator.myFlags[i];

            if(rc.readSharedArray(Utils.defenseFlagOffset(id) + 1) > 0) continue;
            for (int j = enemyCnt; j-- > 0; ) {
                if (enemies[j].getLocation().isWithinDistanceSquared(floc, 16)) {
                    Communicator.reportAllyFlagTaken(floc, id);
                    break checkFlag2;
                }
            }
        }*/
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
        }

        if(friendCnt + enemyCnt *1.25 > 13 || entourage || builder) build(rc);
        /*if (rc.getHealth() < HEALING_CUTOFF && inDanger && chaseTarget != null) {
            kite(rc, chaseTarget.location);
            return;
        }*/
        //if (rc.isMovementReady() /*&& rc.isActionReady()*/) {
            /*if (chaseTarget != null) {
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
        }*/
        noMacro = microAttacker.doMicro(stunInfos, taskOrTargetOrWhatever());
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
        boolean maxInRange = false;
        for (Direction dir : dirs) {
            if (rc.canMove(dir)) {
                int canSee = 0;
                int friends = 0;
                boolean inRange = false;
                for (int i = enemyCnt; --i >= 0;){
                    int newDis = rc.getLocation().add(dir).distanceSquaredTo(enemies[i].location);
                    if (newDis <= 10) {
                        canSee++;
                    }
                    if(newDis <= 4) inRange = true;
                }
                for (int i = friendCnt; --i >= 0;){
                    int newDis = rc.getLocation().add(dir).distanceSquaredTo(allies[i].location);
                    if (newDis <= 4) {
                        friends++;
                    }
                }
                /*if(inRange && !maxInRange) {
                    bestDir = dir;
                    minCanSee = canSee;
                    maxInRange = inRange;
                    maxFriends = friends;
                }
                else if(!inRange && !maxInRange) {}
                else {*/
                    if (minCanSee > canSee) {
                        bestDir = dir;
                        minCanSee = canSee;
                        maxInRange = inRange;
                        maxFriends = friends;
                    }
                    else if (minCanSee == canSee && friends > maxFriends) {
                        bestDir = dir;
                        minCanSee = canSee;
                        maxInRange = inRange;
                        maxFriends = friends;
                    } else if (minCanSee == canSee && friends == maxFriends && isDiagonal(bestDir) && !isDiagonal(dir)) {
                        bestDir = dir;
                        minCanSee = canSee;
                        maxInRange = inRange;
                        maxFriends = friends;
                    }
                //}
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
        int minHp = 100000;
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
            int hitsReq = (enemy.health - friendDamage) / LEVEL_ATTACK[rc.getLevel(SkillType.ATTACK)];

            if(enemy.health < minHp) {
                minHp = enemy.health;
                ret = enemy;
            }
            /*if (hitsReq < minHitsReq || (hitsReq == minHitsReq && dist < minDist)) {
                minHitsReq = hitsReq;
                ret = enemy;
                minDist = dist;
            }*/
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

    static MicroAttacker microAttacker;
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        if(rc.readSharedArray(0) < 3) {
            sentry = true;
        }
        microAttacker = new MicroAttacker(rc);
        FastMath.initRand(rc);
        Communicator.init(rc);
        Utils.init(rc);
        if(Utils.commId <= 6 && Utils.commId > 3) builder = true;
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
