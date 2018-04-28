package com.github.nighturs.codingame.coderoyale;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings({"NonFinalUtilityClass", "UtilityClassWithoutPrivateConstructor"})
class Player {

    static final int KNIGHT_COST = 80;
    static final int ARCHER_COST = 100;
    static final int GIANT_COST = 140;
    static final int GRID_WIDTH = 1920;
    static final int GRID_HEIGHT = 1000;
    static final int QUEEN_SPEED = 60;
    static final int KNIGHT_SPEED = 100;
    static final int CONTACT_RANGE = 5;
    static final int QUEEN_RADIUS = 30;
    static final int KNIGHT_RADIUS = 20;
    static final int MAX_TOWER_HP = 800;
    static final int QUEEN_TOWER_UP = 100;
    static final int KNIGHT_TRAIN_TURNS = 4;
    static final int ARCHER_TRAIN_TURNS = 7;
    static final int GIANT_TRAIN_TURNS = 9;

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);

        List<BuildingSiteStatic> buildingSiteStatics = new ArrayList<>();
        int numSites = in.nextInt();
        for (int i = 0; i < numSites; i++) {
            int siteId = in.nextInt();
            int x = in.nextInt();
            int y = in.nextInt();
            int radius = in.nextInt();
            buildingSiteStatics.add(BuildingSiteStatic.create(siteId, x, y, radius));
        }

        GameState gameState = GameState.create(buildingSiteStatics);

        //noinspection InfiniteLoopStatement
        while (true) {
            int gold = in.nextInt();
            int touchedSite = in.nextInt();
            List<BuildingSite> buildingSites = new ArrayList<>();
            for (int i = 0; i < numSites; i++) {
                int siteId = in.nextInt();
                int mineGold = in.nextInt();
                int maxMineSize = in.nextInt();
                int structureType = in.nextInt();
                int owner = in.nextInt();
                int param1 = in.nextInt();
                int param2 = in.nextInt();
                StructureType stType = StructureType.fromId(structureType);
                buildingSites.add(BuildingSite.create(gameState.getBuildingSiteStaticById(siteId),
                        stType,
                        Owner.fromId(owner),
                        stType == StructureType.BARRACKS ? param1 : 0,
                        mineGold == -1 ? Optional.empty() : Optional.of(mineGold),
                        maxMineSize == -1 ? Optional.empty() : Optional.of(maxMineSize),
                        stType == StructureType.TOWER ? param1 : 0,
                        stType == StructureType.TOWER ? param2 : 0,
                        stType == StructureType.MINE ? param1 : 0,
                        stType == StructureType.BARRACKS ? BarracksType.fromId(param2) : BarracksType.NONE));
            }
            List<Unit> units = new ArrayList<>();
            int numUnits = in.nextInt();
            for (int i = 0; i < numUnits; i++) {
                int x = in.nextInt();
                int y = in.nextInt();
                int owner = in.nextInt();
                int unitType = in.nextInt();
                int health = in.nextInt();
                units.add(Unit.create(x, y, Owner.fromId(owner), UnitType.fromId(unitType), health));
            }

            gameState.initTurn(gold, touchedSite, buildingSites, units);
            System.out.println(TurnEngine.findMove(gameState));
        }
    }

    interface Rule {

        Optional<MoveBuilder> makeMove(GameState gameState);

        int priority();
    }

    static class RunFromKnightsRule implements Rule {

        private static final int PANIC_MODE_DIST = 300;

        @SuppressWarnings("SuspiciousNameCombination")
        @Override
        public Optional<MoveBuilder> makeMove(GameState gameState) {

            if (!isPanicMode(gameState)) {
                return Optional.empty();
            }

            int enemiesCount = 0;
            double sumX = 0;
            double sumY = 0;
            for (Unit unit : gameState.getUnits()) {
                if (unit.getOwner() != Owner.ENEMY || unit.getUnitType() != UnitType.KNIGHT) {
                    continue;
                }
                if (Utils.dist(gameState.getMyQueen().getX(), gameState.getMyQueen().getY(), unit.getX(), unit.getY())
                        < PANIC_MODE_DIST) {
                    enemiesCount++;
                    sumX += unit.getX();
                    sumY += unit.getY();
                }
            }

            double enemyCenterX = sumX / enemiesCount;
            double enemyCenterY = sumY / enemiesCount;

            double maxDist = Double.MIN_VALUE;
            BuildingSite targetTower = null;
            Optional<BuildingSite> enemyOrigin = BuildStructureRule.closestToStructure(StructureType.BARRACKS,
                    BarracksType.KNIGHT,
                    Owner.ENEMY,
                    gameState.getMyQueen().getX(),
                    gameState.getMyQueen().getY(),
                    gameState);
            Unit enemyQueen = gameState.getEnemyQueen();
            for (BuildingSite site : gameState.getBuildingSites()) {
                boolean isMyTower = site.getOwner() == Owner.FRIENDLY && site.getStructureType() == StructureType.TOWER;
                boolean isVacantCoveredByTower = false;
                if (site.getStructureType() == StructureType.NONE) {
                    for (BuildingSite siteB : gameState.getBuildingSites()) {
                        if (siteB.getOwner() == Owner.FRIENDLY && siteB.getStructureType() == StructureType.TOWER
                                && Utils.dist(siteB.getX(), siteB.getY(), site.getX(), site.getY())
                                <= siteB.getTowerRange()) {
                            isVacantCoveredByTower = true;
                        }
                    }
                }
                if (!isMyTower && !isVacantCoveredByTower) {
                    continue;
                }

                double dist = Utils.dist(enemyOrigin.map(BuildingSite::getX).orElse(enemyQueen.getX()),
                        enemyOrigin.map(BuildingSite::getY).orElse(enemyQueen.getY()),
                        site.getX(),
                        site.getY());
                if (maxDist < dist) {
                    maxDist = dist;
                    targetTower = site;
                }
            }
            if (targetTower == null) {
                return Optional.empty();
            }

            GoToNewSiteRule.Path path = GoToNewSiteRule.findPath(gameState.getMyQueen().getX(),
                    gameState.getMyQueen().getY(),
                    targetTower,
                    Optional.empty(),
                    gameState);
            if (path.isGoingRound()) {
                return Optional.of(new MoveBuilder().setX(path.getStepX()).setY(path.getStepY()));
            }

            double targetTowerX = targetTower.getX();
            double targetTowerY = targetTower.getY();
            double queenX = gameState.getMyQueen().getX() - targetTowerX;
            double queenY = gameState.getMyQueen().getY() - targetTowerY;
            double k = (targetTower.getRadius() + QUEEN_RADIUS) / Utils.dist(0, 0, queenX, queenY);
            queenX = queenX * k;
            queenY = queenY * k;
            enemyCenterX = enemyCenterX - targetTowerX;
            enemyCenterY = enemyCenterY - targetTowerY;
            double targetX;
            double targetY;
            if (Utils.dist(-queenY, queenX, enemyCenterX, enemyCenterY) > Utils.dist(queenY,
                    -queenX,
                    enemyCenterX,
                    enemyCenterY)) {
                targetX = -queenY;
                targetY = queenX;
            } else {
                targetX = queenY;
                targetY = -queenX;
            }
            targetX += targetTowerX;
            targetY += targetTowerY;
            return Optional.of(new MoveBuilder().setX((int) targetX).setY((int) targetY));
        }

        public static boolean isPanicMode(GameState gameState) {
            for (Unit unit : gameState.getUnits()) {
                if (unit.getOwner() != Owner.ENEMY || unit.getUnitType() != UnitType.KNIGHT) {
                    continue;
                }
                if (Utils.dist(gameState.getMyQueen().getX(), gameState.getMyQueen().getY(), unit.getX(), unit.getY())
                        < PANIC_MODE_DIST) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int priority() {
            return 2;
        }
    }

    static class GoToNewSiteRule implements Rule {

        private static final int ENEMY_TERRITORY = 450;

        private static class Path {

            private final double dist;
            private final int stepX, stepY;
            private final boolean goingRound;

            public Path(double dist, int stepX, int stepY, boolean goingRound) {
                this.dist = dist;
                this.stepX = stepX;
                this.stepY = stepY;
                this.goingRound = goingRound;
            }

            public double getDist() {
                return dist;
            }

            public int getStepX() {
                return stepX;
            }

            public int getStepY() {
                return stepY;
            }

            public boolean isGoingRound() {
                return goingRound;
            }
        }

        public static Path findPath(int curX,
                                    int curY,
                                    BuildingSite firstSite,
                                    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
                                            Optional<BuildingSite> secondSite,
                                    GameState gameState) {

            // Search for obstacles in straight line path
            double obstDist = Double.MAX_VALUE;
            BuildingSite obstSite = null;
            for (BuildingSite site : gameState.getBuildingSites()) {
                if (site.getId() == firstSite.getId()) {
                    continue;
                }
                double dist = Utils.dist(curX, curY, site.getX(), site.getY());
                if (Utils.isObstackle(curX,
                        curY,
                        firstSite.getX(),
                        firstSite.getY(),
                        site.getX(),
                        site.getY(),
                        site.getRadius() + QUEEN_RADIUS) && obstDist > dist) {
                    obstDist = dist;
                    obstSite = site;
                }
            }
            if (obstSite != null) {
                double centCurX = curX - obstSite.getX();
                double centCurY = curY - obstSite.getY();
                double k = (obstSite.getRadius() + QUEEN_RADIUS) / obstDist;
                centCurX *= k;
                centCurY *= k;

                int newX;
                int newY;
                //noinspection SuspiciousNameCombination
                if (Utils.dist(-centCurY,
                        centCurX,
                        firstSite.getX() - obstSite.getX(),
                        firstSite.getY() - obstSite.getY()) > Utils.dist(centCurY,
                        -centCurX,
                        firstSite.getX() - obstSite.getX(),
                        firstSite.getY() - obstSite.getY())) {
                    newX = (int) (centCurY + obstSite.getX());
                    newY = (int) (-centCurX + obstSite.getY());
                } else {
                    newX = (int) (-centCurY + obstSite.getX());
                    newY = (int) (centCurX + obstSite.getY());
                }
                Path path = findPath(newX, newY, firstSite, secondSite, gameState);
                return new Path(path.getDist() + Utils.dist(curX, curY, newX, newY), newX, newY, true);
            }

            // Find if it is possible to approach in one turn and make next vacant site less far away
            double bestFuture = Double.MAX_VALUE;
            int moveX = -1;
            int moveY = -1;
            for (int x1 = -QUEEN_SPEED; x1 <= QUEEN_SPEED; x1++) {
                for (int y1 = -QUEEN_SPEED; y1 <= QUEEN_SPEED; y1++) {
                    int newX = curX + x1;
                    int newY = curY + y1;
                    if (newX < 0 || newX > GRID_WIDTH || newY < 0 || newY > GRID_HEIGHT) {
                        continue;
                    }
                    double d = Utils.dist(curX, curY, newX, newY);
                    if (d > QUEEN_SPEED) {
                        continue;
                    }
                    if (!Utils.inContact(newX,
                            newY,
                            QUEEN_RADIUS,
                            firstSite.getX(),
                            firstSite.getY(),
                            firstSite.getRadius())) {
                        continue;
                    }

                    double secondDist = Utils.dist(newX,
                            newY,
                            secondSite.map(BuildingSite::getX).orElse(gameState.getMyCornerX()),
                            secondSite.map(BuildingSite::getY).orElse(gameState.getMycornerY())) - secondSite.map(
                            BuildingSite::getRadius).orElse(0) - QUEEN_RADIUS - CONTACT_RANGE;

                    if (secondDist < bestFuture) {
                        bestFuture = secondDist;
                        moveX = newX;
                        moveY = newY;
                    }
                }
            }
            if (moveX != -1) {
                return new Path(QUEEN_SPEED + bestFuture, moveX, moveY, false);
            }

            if (!secondSite.isPresent()) {
                return new Path(Utils.dist(curX, curY, firstSite.getX(), firstSite.getY()) - firstSite.getRadius()
                        - QUEEN_RADIUS - CONTACT_RANGE, firstSite.getX(), firstSite.getY(), false);
            }

            // If first site is on the way to second, then find path directly to second site
            if (Utils.isObstackle(curX,
                    curY,
                    secondSite.get().getX(),
                    secondSite.get().getY(),
                    firstSite.getX(),
                    firstSite.getY(),
                    firstSite.getRadius() + QUEEN_RADIUS)) {
                return findPath(curX, curY, secondSite.get(), Optional.empty(), gameState);
            }

            double turnX = secondSite.get().getX() - firstSite.getX();
            double turnY = secondSite.get().getY() - firstSite.getY();
            double k = (firstSite.getRadius() + QUEEN_RADIUS) / Utils.dist(secondSite.get().getX(),
                    secondSite.get().getY(),
                    firstSite.getX(),
                    firstSite.getY());
            turnX = (turnX * k) + firstSite.getX();
            turnY = (turnY * k) + firstSite.getY();

            return new Path(Utils.dist(curX, curY, turnX, turnY) + Utils.dist(turnX,
                    turnY,
                    secondSite.get().getX(),
                    secondSite.get().getY()) - secondSite.get().getRadius() - QUEEN_RADIUS - CONTACT_RANGE,
                    (int) turnX,
                    (int) turnY,
                    false);
        }

        @Override
        public Optional<MoveBuilder> makeMove(GameState gameState) {
            List<SimpleEntry<BuildingSite, Double>> vacantSitesFirst = new ArrayList<>();
            List<SimpleEntry<BuildingSite, Double>> vacantSitesSecond = new ArrayList<>();
            Unit myQueen = gameState.getMyQueen();
            for (BuildingSite site : gameState.getBuildingSites()) {
                if (gameState.getTouchedSiteOpt().isPresent()
                        && gameState.getTouchedSiteOpt().get().getId() == site.getId()) {
                    continue;
                }
                if (Math.abs(site.getX() - (GRID_WIDTH - gameState.getMyCornerX())) <= ENEMY_TERRITORY) {
                    continue;
                }
                boolean inEnemyTowerRange = false;
                for (BuildingSite siteB : gameState.getBuildingSites()) {
                    if (siteB.getOwner() != Owner.ENEMY || siteB.getStructureType() != StructureType.TOWER) {
                        continue;
                    }
                    if (Utils.dist(site.getX(), site.getY(), siteB.getX(), siteB.getY()) + siteB.getRadius()
                            <= siteB.getTowerRange()) {
                        inEnemyTowerRange = true;
                    }
                }
                if (inEnemyTowerRange) {
                    continue;
                }
                Optional<BuildStructureRule.BuildingDecision> buildingDecisionFirst =
                        BuildStructureRule.buildingDecision(site, gameState, false);
                if (!buildingDecisionFirst.isPresent()) {
                    continue;
                }
                Optional<BuildStructureRule.BuildingDecision> buildingDecisionSecond =
                        BuildStructureRule.buildingDecision(site, gameState, true);
                vacantSitesFirst.add(new SimpleEntry<>(site, buildingDecisionFirst.get().getDistBonus()));
                buildingDecisionSecond.ifPresent(buildingDecision -> vacantSitesSecond.add(new SimpleEntry<>(site,
                        buildingDecision.getDistBonus())));
            }

            vacantSitesFirst.sort(Comparator.comparingDouble(x ->
                    Utils.dist(myQueen.getX(), myQueen.getY(), x.getKey().getX(), x.getKey().getY()) - x.getValue()));
            vacantSitesSecond.sort(Comparator.comparingDouble(x ->
                    Utils.dist(myQueen.getX(), myQueen.getY(), x.getKey().getX(), x.getKey().getY()) - x.getValue()));

            for (int i = 0; i < Math.min(vacantSitesFirst.size(), 4); i++) {
                BuildingSite s = vacantSitesFirst.get(i).getKey();
                System.err.println(String.format("Rate id=%d pen=%f d=%f o=%f",
                        s.getId(),
                        vacantSitesFirst.get(i).getValue(),
                        Utils.dist(myQueen.getX(), myQueen.getY(), s.getX(), s.getY()),
                        Utils.dist(myQueen.getX(), myQueen.getY(), s.getX(), s.getY()) - vacantSitesFirst.get(i)
                                .getValue()));
            }

            if (vacantSitesFirst.isEmpty()) {
                return Optional.empty();
            }

            double minDist = Double.MAX_VALUE;
            int moveX = 0;
            int moveY = 0;
            for (int i = 0; i < Math.min(vacantSitesFirst.size(), 1); i++) {
                for (int h = i + 1; h < Math.min(vacantSitesSecond.size(), 5); h++) {
                    if (vacantSitesFirst.get(i).getKey().getId() == vacantSitesSecond.get(h).getKey().getId()) {
                        continue;
                    }
                    Path path = findPath(myQueen.getX(),
                            myQueen.getY(),
                            vacantSitesFirst.get(i).getKey(),
                            Optional.of(vacantSitesSecond.get(h).getKey()),
                            gameState);
                    if (path.getDist() - vacantSitesFirst.get(i).getValue() - vacantSitesSecond.get(h).getValue()
                            < minDist) {
                        minDist = path.getDist() - vacantSitesFirst.get(i).getValue() - vacantSitesSecond.get(h)
                                .getValue();
                        moveX = path.getStepX();
                        moveY = path.getStepY();
                    }
                }
            }
            //noinspection FloatingPointEquality
            if (minDist == Double.MAX_VALUE) {
                Path path = findPath(myQueen.getX(),
                        myQueen.getY(),
                        vacantSitesFirst.get(0).getKey(),
                        Optional.empty(),
                        gameState);
                return Optional.of(new MoveBuilder().setX(path.getStepX()).setY(path.getStepY()));
            }
            return Optional.of(new MoveBuilder().setX(moveX).setY(moveY));
        }

        @Override
        public int priority() {
            return 0;
        }
    }

    static class BuildStructureRule implements Rule {

        private static final int BARRACKS_REPLACEMENT_THRESHOLD_DIST = 400;
        private static final int COMFORT_TOWERS_NUMBER = 2;

        private static class BuildingDecision {

            private final StructureType structureType;
            private final BarracksType barracksType;
            private final double distBonus;

            public BuildingDecision(StructureType structureType, BarracksType barracksType, double distBonus) {
                this.structureType = structureType;
                this.barracksType = barracksType;
                this.distBonus = distBonus;
            }

            public StructureType getStructureType() {
                return structureType;
            }

            public BarracksType getBarracksType() {
                return barracksType;
            }

            public double getDistBonus() {
                return distBonus;
            }
        }

        public static Optional<BuildingDecision> buildingDecision(BuildingSite site,
                                                                  GameState gameState,
                                                                  boolean second) {
            if (site.getOwner() == Owner.ENEMY && site.getStructureType() == StructureType.TOWER) {
                return Optional.empty();
            }

            Unit myQueen = gameState.getMyQueen();
            Unit enemyQueen = gameState.getEnemyQueen();
            int myBarracksCount = 0;
            int myGiantCount = 0;
            int enemyBarracksCount = 0;
            int myMinesCount = 0;
            double closestEnemyBarracksDist = Double.MAX_VALUE;
            double closestMyBarracksDist = Double.MAX_VALUE;
            double dist;
            BuildingSite closestEnemyBarracksOpt = null;
            BuildingSite closestMyBarracksOpt = null;
            for (BuildingSite s : gameState.getBuildingSites()) {
                if (s.getStructureType() == StructureType.BARRACKS && s.getBarracksType() == BarracksType.KNIGHT
                        && s.getOwner() == Owner.FRIENDLY) {
                    myBarracksCount++;
                    dist = Utils.dist(enemyQueen.getX(), enemyQueen.getY(), s.getX(), s.getY());
                    if (dist < closestMyBarracksDist) {
                        closestMyBarracksDist = dist;
                        closestMyBarracksOpt = s;
                    }
                } else if (s.getStructureType() == StructureType.BARRACKS && s.getBarracksType() == BarracksType.KNIGHT
                        && s.getOwner() == Owner.ENEMY) {
                    enemyBarracksCount++;
                    dist = Utils.dist(myQueen.getX(), myQueen.getY(), s.getX(), s.getY());
                    if (dist < closestEnemyBarracksDist) {
                        closestEnemyBarracksDist = dist;
                        closestEnemyBarracksOpt = s;
                    }
                } else if (s.getStructureType() == StructureType.BARRACKS && s.getBarracksType() == BarracksType.GIANT
                        && s.getOwner() == Owner.FRIENDLY) {
                    myGiantCount++;
                } else if (s.getStructureType() == StructureType.MINE && s.getOwner() == Owner.FRIENDLY) {
                    myMinesCount++;
                }
            }

            Optional<BuildingSite> closestEnemyBarracks = Optional.ofNullable(closestEnemyBarracksOpt);
            Optional<BuildingSite> closestMyBarracks = Optional.ofNullable(closestMyBarracksOpt);
            double distToEnemyQueen = Utils.dist(gameState.getEnemyQueen().getX(),
                    gameState.getEnemyQueen().getY(),
                    site.getX(),
                    site.getY());

            double maxDistToEnemyQueen = Utils.dist(gameState.getMyCornerX(),
                    gameState.getMycornerY(),
                    enemyQueen.getX(),
                    enemyQueen.getY());
            Optional<Double> myBarracksDistToEnemyQueen =
                    closestMyBarracks.map(buildingSite -> Utils.dist(buildingSite.getX(),
                            buildingSite.getY(),
                            enemyQueen.getX(),
                            enemyQueen.getY()));

            double enemyKnightsBonus = Double.MAX_VALUE;
            Optional<Unit> closestEnemyKnight = Optional.empty();
            boolean applyKnightBonus = false;
            for (Unit unit : gameState.getUnits()) {
                if (unit.getUnitType() != UnitType.KNIGHT || unit.getOwner() != Owner.ENEMY) {
                    continue;
                }
                double bonus =
                        Utils.dist(unit.getX(), unit.getY(), site.getX(), site.getY()) / KNIGHT_SPEED * QUEEN_SPEED;
                if (enemyKnightsBonus > bonus) {
                    enemyKnightsBonus = bonus;
                    closestEnemyKnight = Optional.of(unit);
                    applyKnightBonus = true;
                }
            }

            if (!applyKnightBonus) {
                enemyKnightsBonus = 0;
            }

            if (site.getStructureType() == StructureType.MINE) {
                if (applyKnightBonus && towersOnPath(gameState,
                        gameState.getMyQueen().getX(),
                        gameState.getMyQueen().getY(),
                        closestEnemyBarracks.map(BuildingSite::getX).orElse(closestEnemyKnight.get().getX()),
                        closestEnemyBarracks.map(BuildingSite::getY).orElse(closestEnemyKnight.get().getY()),
                        null) < COMFORT_TOWERS_NUMBER) {
                    return Optional.of(new BuildingDecision(StructureType.TOWER,
                            null,
                            -(site.getIncomeRate() + 1) * 2 * QUEEN_SPEED + enemyKnightsBonus));
                } else if (myBarracksCount > 0 && myGiantCount == 0
                        && gameState.getGoldLeft() > GIANT_COST + KNIGHT_COST / 2 && !second) {
                    return Optional.of(new BuildingDecision(StructureType.BARRACKS,
                            BarracksType.GIANT,
                            (maxDistToEnemyQueen - distToEnemyQueen) / 2 + enemyKnightsBonus));
                }
            }

            boolean uselessTower = (site.getStructureType() == StructureType.TOWER) && closestEnemyBarracks.isPresent()
                    && !applyKnightBonus && towersOnPath(gameState,
                    myQueen.getX(),
                    myQueen.getY(),
                    closestEnemyBarracks.get().getX(),
                    closestEnemyBarracks.get().getY(),
                    site) >= COMFORT_TOWERS_NUMBER && towersOnPath(gameState,
                    site.getX(),
                    site.getY(),
                    closestEnemyBarracks.get().getX(),
                    closestEnemyBarracks.get().getY(),
                    site) >= COMFORT_TOWERS_NUMBER && site.getGold().orElse(1) > 0;

            if (site.getStructureType() == StructureType.TOWER) {
                // Integer division intended
                if ((MAX_TOWER_HP - site.getTowerHP()) / QUEEN_TOWER_UP > 2 && !uselessTower) {
                    return Optional.of(new BuildingDecision(StructureType.TOWER,
                            null,
                            -QUEEN_SPEED + enemyKnightsBonus));
                }
            }

            boolean uselessBarracks =
                    (site.getStructureType() == StructureType.BARRACKS && site.getBarracksType() == BarracksType.KNIGHT
                            && myBarracksCount > 1 && closestMyBarracks.get().getId() != site.getId());

            //noinspection ConstantConditions
            if (site.getStructureType() == StructureType.NONE || uselessBarracks || uselessTower) {
                //noinspection IfStatementWithIdenticalBranches
                if (myBarracksCount == 0 && (myMinesCount >= 2 || enemyBarracksCount != 0) && !second) {
                    return Optional.of(new BuildingDecision(StructureType.BARRACKS,
                            BarracksType.KNIGHT,
                            (maxDistToEnemyQueen - distToEnemyQueen) / 2 + enemyKnightsBonus));
                } else if (myBarracksCount > 0 && myBarracksDistToEnemyQueen.isPresent()
                        && myBarracksDistToEnemyQueen.get() - distToEnemyQueen >= BARRACKS_REPLACEMENT_THRESHOLD_DIST) {
                    return Optional.of(new BuildingDecision(StructureType.BARRACKS,
                            BarracksType.KNIGHT,
                            0 + enemyKnightsBonus));
                } else if ((enemyBarracksCount > 0 || applyKnightBonus) && closestEnemyBarracks.isPresent() &&
                        towersOnPath(gameState,
                                gameState.getMyQueen().getX(),
                                gameState.getMyQueen().getY(),
                                closestEnemyBarracks.get().getX(),
                                closestEnemyBarracks.get().getY(),
                                null) < COMFORT_TOWERS_NUMBER) {
                    return Optional.of(new BuildingDecision(StructureType.TOWER, null, 0 + enemyKnightsBonus));
                } else if (RunFromKnightsRule.isPanicMode(gameState)) {
                    return Optional.of(new BuildingDecision(StructureType.TOWER, null, 0 + enemyKnightsBonus));
                } else if (myBarracksCount > 0 && myGiantCount == 0
                        && gameState.getGoldLeft() > GIANT_COST + KNIGHT_COST / 2 && !second) {
                    return Optional.of(new BuildingDecision(StructureType.BARRACKS,
                            BarracksType.GIANT,
                            (maxDistToEnemyQueen - distToEnemyQueen) / 2 + enemyKnightsBonus));
                } else if (myBarracksCount > 0 && myBarracksDistToEnemyQueen.isPresent()
                        && myBarracksDistToEnemyQueen.get() - distToEnemyQueen >= BARRACKS_REPLACEMENT_THRESHOLD_DIST) {
                    return Optional.of(new BuildingDecision(StructureType.BARRACKS,
                            BarracksType.KNIGHT,
                            (maxDistToEnemyQueen - distToEnemyQueen) / 2 + enemyKnightsBonus));
                } else if (site.getGold().orElse(1) > 0) {
                    return Optional.of(new BuildingDecision(StructureType.MINE,
                            null,
                            (site.getMaxMineSize().orElse(1) - 1) * QUEEN_SPEED + enemyKnightsBonus));
                } else {
                    return Optional.of(new BuildingDecision(StructureType.TOWER, null, 0 + enemyKnightsBonus));
                }
            }
            return Optional.empty();
        }

        public static Optional<BuildingSite> closestToStructure(StructureType type,
                                                                BarracksType barracksType,
                                                                Owner owner,
                                                                int x,
                                                                int y,
                                                                GameState gameState) {
            double minDist = Double.MAX_VALUE;
            BuildingSite closest = null;
            for (BuildingSite site : gameState.getBuildingSites()) {
                if (site.getStructureType() != type || site.getBarracksType() != barracksType
                        || site.getOwner() != owner) {
                    continue;
                }
                double dist = Utils.dist(x, y, site.getX(), site.getY());
                if (dist < minDist) {
                    minDist = dist;
                    closest = site;
                }
            }
            return Optional.ofNullable(closest);
        }

        public static int towersOnPath(GameState gameState, int x1, int y1, int x2, int y2, BuildingSite ignoreTower) {
            int count = 0;
            for (BuildingSite site : gameState.getBuildingSites()) {
                if (site.getStructureType() != StructureType.TOWER || site.getOwner() != Owner.FRIENDLY || (
                        ignoreTower != null && site.getId() == ignoreTower.getId())) {
                    continue;
                }
                if (Utils.dist(x1, y1, site.getX(), site.getY()) <= site.getTowerRange()
                        || Utils.dist(x2, y2, site.getX(), site.getY()) <= site.getTowerRange() || Utils.isObstackle(x1,
                        y1,
                        x2,
                        y2,
                        site.getX(),
                        site.getY(),
                        site.getTowerRange())) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public Optional<MoveBuilder> makeMove(GameState gameState) {

            Optional<BuildingSite> touchSite = gameState.getTouchedSiteOpt();

            if (!touchSite.isPresent()) {
                return Optional.empty();
            }

            BuildingSite site = touchSite.get();
            Optional<BuildingDecision> decision = buildingDecision(site, gameState, false);

            if (!decision.isPresent() || decision.get().getStructureType() == site.getStructureType()) {
                if (site.getOwner() == Owner.FRIENDLY && site.getStructureType() == StructureType.MINE
                        && site.getMaxMineSize().orElse(0) > site.getIncomeRate() && !RunFromKnightsRule.isPanicMode(
                        gameState)) {
                    return Optional.of(new MoveBuilder().setSiteId(touchSite.get().getId())
                            .setStructureType(StructureType.MINE));
                }
                if (site.getOwner() == Owner.FRIENDLY && site.getStructureType() == StructureType.TOWER
                        && MAX_TOWER_HP - site.getTowerHP() >= QUEEN_TOWER_UP / 2 && !RunFromKnightsRule.isPanicMode(
                        gameState)) {
                    return Optional.of(new MoveBuilder().setSiteId(touchSite.get().getId())
                            .setStructureType(StructureType.TOWER));
                }
                return Optional.empty();
            }

            return Optional.of(new MoveBuilder().setStructureType(decision.get().getStructureType())
                    .setBarracksType(decision.get().getBarracksType())
                    .setSiteId(site.getId()));
        }

        @Override
        public int priority() {
            return 3;
        }
    }

    static class GiveWayToKnightRule {

        private static final double STEP_CHUNKS = 5;

        private static boolean isInWay(Unit knight, Unit queen, int nextX, int nextY, int steps) {
            boolean result = false;
            for (int step = 1; step <= steps; step++) {
                double mod = KNIGHT_SPEED * step * 1.0 / STEP_CHUNKS / Utils.dist(queen.getX(),
                        queen.getY(),
                        knight.getX(),
                        knight.getY());
                int kNextX = knight.getX() + (int) (mod * (queen.getX() - knight.getX()));
                int kNextY = knight.getY() + (int) (mod * (queen.getY() - knight.getY()));
                result = result || (Utils.dist(kNextX, kNextY, nextX, nextY) < KNIGHT_RADIUS + QUEEN_RADIUS);
            }
            return result;
        }

        public Optional<MoveBuilder> makeMove(Move preferedMove, GameState gameState) {
            if (preferedMove.getX() == null) {
                return Optional.empty();
            }
            Unit myQueen = gameState.getMyQueen();
            double k =
                    QUEEN_SPEED / Utils.dist(myQueen.getX(), myQueen.getY(), preferedMove.getX(), preferedMove.getY());
            int preferedX = myQueen.getX() + (int) ((preferedMove.getX() - myQueen.getX()) * k);
            int preferedY = myQueen.getY() + (int) ((preferedMove.getY() - myQueen.getY()) * k);

            Unit enemyQueen = gameState.getEnemyQueen();
            for (Unit unit : gameState.getUnits()) {
                if (unit.getOwner() != Owner.FRIENDLY || unit.getUnitType() != UnitType.KNIGHT) {
                    continue;
                }

                if (isInWay(unit, enemyQueen, preferedX, preferedY, 5)) {
                    double minDist = Double.MAX_VALUE;
                    int bestNextX = 0;
                    int bestNextY = 0;
                    for (int x1 = -QUEEN_SPEED; x1 <= QUEEN_SPEED; x1++) {
                        for (int y1 = -QUEEN_SPEED; y1 <= QUEEN_SPEED; y1++) {
                            int newX = myQueen.getX() + x1;
                            int newY = myQueen.getY() + y1;
                            if (newX < 0 || newX > GRID_WIDTH || newY < 0 || newY > GRID_HEIGHT
                                    || Utils.dist(myQueen.getX(), myQueen.getY(), newX, newY) > QUEEN_SPEED) {
                                continue;
                            }
                            if (isInWay(unit, enemyQueen, newX, newY, 5)) {
                                continue;
                            }
                            double dist = Utils.dist(newX, newY, preferedX, preferedY);
                            if (minDist > dist) {
                                minDist = dist;
                                bestNextX = newX;
                                bestNextY = newY;
                            }
                        }
                    }
                    //noinspection FloatingPointEquality
                    if (minDist != Double.MAX_VALUE) {
                        return Optional.of(new MoveBuilder().setX(bestNextX).setY(bestNextY));
                    }
                }
            }
            return Optional.empty();
        }
    }

    static class TrainUnitsRule implements Rule {

        @Override
        public Optional<MoveBuilder> makeMove(GameState gameState) {
            List<Integer> trainSites = new ArrayList<>();
            int gold = gameState.getGoldLeft();

            List<BuildingSite> knightBarracks = gameState.getBuildingSites()
                    .stream()
                    .filter(x -> x.getOwner() == Owner.FRIENDLY && x.getBarracksType() == BarracksType.KNIGHT)
                    .sorted(Comparator.comparingDouble(x -> Utils.dist(x.getX(),
                            x.getY(),
                            gameState.getEnemyQueen().getX(),
                            gameState.getEnemyQueen().getY())))
                    .collect(Collectors.toList());
            for (BuildingSite site : knightBarracks) {
                if (gold >= KNIGHT_COST) {
                    gold -= KNIGHT_COST;
                    trainSites.add(site.getId());
                }
            }
            for (BuildingSite site : gameState.getBuildingSites()) {
                if (site.getOwner() != Owner.FRIENDLY || site.getBarracksType() != BarracksType.GIANT) {
                    continue;
                }
                if (gold >= GIANT_COST) {
                    gold -= GIANT_COST;
                    trainSites.add(site.getId());
                }
            }
            if (!trainSites.isEmpty()) {
                return Optional.of(new MoveBuilder().setTrainInSites(trainSites));
            }
            return Optional.empty();
        }

        @Override
        public int priority() {
            return 0;
        }
    }

    static class TurnEngine {

        private static GiveWayToKnightRule giveWayToKnightRule = new GiveWayToKnightRule();
        private static List<Rule> queenRules =
                Arrays.asList(new GoToNewSiteRule(), new BuildStructureRule(), new RunFromKnightsRule());

        private static Optional<MoveBuilder> bestPriorityMove(GameState gameState, List<Rule> rules) {
            int currentPriority = Integer.MIN_VALUE;
            MoveBuilder moveBuilder = null;
            for (Rule rule : rules) {
                if (currentPriority > rule.priority()) {
                    continue;
                }
                Optional<MoveBuilder> move = rule.makeMove(gameState);
                if (move.isPresent()) {
                    currentPriority = rule.priority();
                    moveBuilder = move.get();
                }
            }
            return Optional.ofNullable(moveBuilder);
        }

        public static Move findMove(GameState gameState) {
            Optional<MoveBuilder> queenMoveOpt = bestPriorityMove(gameState, queenRules);
            if (queenMoveOpt.isPresent()) {
                Optional<MoveBuilder> subMove =
                        giveWayToKnightRule.makeMove(queenMoveOpt.get().createMove(), gameState);
                if (subMove.isPresent()) {
                    queenMoveOpt = subMove;
                }
            }
            List<Rule> structureRules = Collections.singletonList(new TrainUnitsRule());
            Optional<MoveBuilder> structureMoveOpt = bestPriorityMove(gameState, structureRules);
            MoveBuilder queenMove = queenMoveOpt.orElse(new MoveBuilder());
            queenMove.setTrainInSites(structureMoveOpt.map(MoveBuilder::getTrainInSites)
                    .orElse(Collections.emptyList()));
            return queenMove.createMove();
        }
    }

    static class GameState {

        private final List<BuildingSiteStatic> buildingSiteStatics;
        private final Map<Integer, BuildingSiteStatic> buildingSiteStaticById;
        private List<BuildingSite> buildingSites;
        private Map<Integer, BuildingSite> buildingSiteById;
        private List<Unit> units;
        private int goldLeft;
        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        private Optional<BuildingSite> touchedSiteOpt;
        private Unit myQueen;
        private Unit enemyQueen;
        private int myCornerX = -1;
        private int mycornerY = -1;
        private int overallIncome = 0;
        private int enemyGold = 100;
        private Map<Integer, Integer> incomeRate = new HashMap<>();
        private int enemyOverallIncome = 0;

        public static GameState create(List<BuildingSiteStatic> buildingSiteStatics) {
            return new GameState(buildingSiteStatics);
        }

        public GameState(List<BuildingSiteStatic> buildingSiteStatics) {
            this.buildingSiteStatics = buildingSiteStatics;
            buildingSiteStaticById = buildingSiteStatics.stream()
                    .collect(Collectors.toMap(BuildingSiteStatic::getId, Function.identity()));
        }

        public List<BuildingSite> getBuildingSites() {
            return buildingSites;
        }

        public List<Unit> getUnits() {
            return units;
        }

        public int getGoldLeft() {
            return goldLeft;
        }

        public Optional<BuildingSite> getTouchedSiteOpt() {
            return touchedSiteOpt;
        }

        public BuildingSiteStatic getBuildingSiteStaticById(int id) {
            return buildingSiteStaticById.get(id);
        }

        public BuildingSite getBuildingSiteById(int id) {
            return buildingSiteById.get(id);
        }

        public Unit getMyQueen() {
            return myQueen;
        }

        public Unit getEnemyQueen() {
            return enemyQueen;
        }

        public int getMyCornerX() {
            return myCornerX;
        }

        public int getMycornerY() {
            return mycornerY;
        }

        public int getOverallIncome() {
            return overallIncome;
        }

        public int getEnemyGold() {
            return enemyGold;
        }

        public int getEnemyOverallIncome() {
            return enemyOverallIncome;
        }

        private void updateEnemyGold(Unit prevEnemyQueen, Unit curEnemyQueen) {
            this.enemyOverallIncome = 0;
            for (BuildingSite site : buildingSites) {
                if (site.getStructureType() == StructureType.MINE && site.getOwner() == Owner.ENEMY) {
                    if (incomeRate.getOrDefault(site.getId(), 0) == 0) {
                        incomeRate.put(site.getId(), 1);
                    } else {
                        if (prevEnemyQueen.getX() == curEnemyQueen.getX()
                                && prevEnemyQueen.getY() == curEnemyQueen.getY() &&
                                Utils.dist(curEnemyQueen.getX(), curEnemyQueen.getY(), site.getX(), site.getY())
                                        - site.getRadius() - QUEEN_RADIUS < CONTACT_RANGE) {
                            incomeRate.put(site.getId(), incomeRate.get(site.getId()) + 1);
                        }
                    }
                    enemyGold += incomeRate.get(site.getId());
                    this.enemyOverallIncome += site.getIncomeRate();
                } else {
                    incomeRate.put(site.getId(), 0);
                }
                if (site.getStructureType() == StructureType.BARRACKS && site.getOwner() == Owner.ENEMY) {
                    switch (site.getBarracksType()) {
                        case KNIGHT:
                            if (KNIGHT_TRAIN_TURNS == site.getUntilTrain()) {
                                enemyGold -= KNIGHT_COST;
                            }
                            break;
                        case ARCHER:
                            if (ARCHER_TRAIN_TURNS == site.getUntilTrain()) {
                                enemyGold -= ARCHER_COST;
                            }
                            break;
                        case GIANT:
                            if (GIANT_TRAIN_TURNS == site.getUntilTrain()) {
                                enemyGold -= GIANT_COST;
                            }
                            break;
                        default:
                            throw new RuntimeException("Unknown barracks type");
                    }
                }
                enemyGold = Math.max(enemyGold, 0);
            }
        }

        public void initTurn(int gold,
                             int touchedSite,
                             @SuppressWarnings("ParameterHidesMemberVariable") List<BuildingSite> buildingSites,
                             @SuppressWarnings("ParameterHidesMemberVariable") List<Unit> units) {
            buildingSiteById =
                    buildingSites.stream().collect(Collectors.toMap(BuildingSite::getId, Function.identity()));
            goldLeft = gold;
            this.buildingSites = buildingSites;
            this.units = units;
            //noinspection ConstantConditions
            this.myQueen = units.stream()
                    .filter(x -> x.getOwner() == Owner.FRIENDLY && x.getUnitType() == UnitType.QUEEN)
                    .findFirst()
                    .get();
            if (touchedSite == -1) {
                touchedSiteOpt = Optional.empty();
                for (BuildingSite site : buildingSites) {
                    if (Utils.dist(myQueen.getX(), myQueen.getY(), site.getX(), site.getY()) - site.getRadius()
                            - QUEEN_RADIUS < CONTACT_RANGE) {
                        touchedSiteOpt = Optional.of(site);
                    }
                }
            } else {
                touchedSiteOpt = Optional.of(getBuildingSiteById(touchedSite));
            }
            Unit prevEnemyQueen = this.enemyQueen;
            //noinspection ConstantConditions
            this.enemyQueen = units.stream()
                    .filter(x -> x.getOwner() == Owner.ENEMY && x.getUnitType() == UnitType.QUEEN)
                    .findFirst()
                    .get();
            //noinspection ConstantConditions
            this.overallIncome = buildingSites.stream().map(BuildingSite::getIncomeRate).reduce((a, b) -> a + b).get();
            if (myCornerX == -1) {
                if (myQueen.getX() < GRID_WIDTH / 2) {
                    myCornerX = 0;
                    mycornerY = 0;
                } else {
                    myCornerX = GRID_WIDTH;
                    mycornerY = GRID_HEIGHT;
                }
            }
            updateEnemyGold(prevEnemyQueen, enemyQueen);
            System.err.println(String.format("Enemy gold %d", enemyGold));
        }
    }

    static class Utils {

        public static boolean inContact(int x1, int y1, int r1, int x2, int y2, int r2) {
            return dist(x1, y1, x2, y2) - r1 - r2 < CONTACT_RANGE;
        }

        public static double dist(int x1, int y1, int x2, int y2) {
            return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
        }

        public static double dist(double x1, double y1, double x2, double y2) {
            return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
        }

        public static boolean isProjectedPointOnLineSegment(int x1, int y1, int x2, int y2, int x0, int y0) {
            int e1x = x2 - x1;
            int e1y = y2 - y1;
            int recArea = e1x * e1x + e1y * e1y;
            int e2x = x0 - x1;
            int e2y = y0 - y1;
            double val = e1x * e2x + e1y * e2y;
            return (val > 0 && val < recArea);
        }

        public static double distLinePoint(double x1, double y1, double x2, double y2, double x0, double y0) {
            return Math.abs((y2 - y1) * x0 - (x2 - x1) * y0 + x2 * y1 - y2 * x1) / Math.sqrt(
                    (y2 - y1) * (y2 - y1) + (x2 - x1) * (x2 - x1));
        }

        public static boolean isObstackle(int x1, int y1, int x2, int y2, int x0, int y0, int radius) {
            return distLinePoint(x1, y1, x2, y2, x0, y0) < radius && isProjectedPointOnLineSegment(x1,
                    y1,
                    x2,
                    y2,
                    x0,
                    y0);
        }
    }

    static class Move {

        private final Integer x, y;
        private final Integer siteId;
        private final StructureType structureType;
        private final BarracksType barracksType;
        private final List<Integer> trainInSites;

        public Move(Integer x,
                    Integer y,
                    Integer siteId,
                    StructureType structureType,
                    BarracksType barracksType,
                    List<Integer> trainInSites) {
            this.x = x;
            this.y = y;
            this.siteId = siteId;
            this.structureType = structureType;
            this.barracksType = barracksType;
            this.trainInSites = trainInSites;
        }

        public Integer getX() {
            return x;
        }

        public Integer getY() {
            return y;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            if (x == null && structureType == null) {
                sb.append("WAIT");
            } else if (x != null) {
                sb.append(String.format("MOVE %d %d", x, y));
            } else {
                sb.append(String.format("BUILD %d %s", siteId, structureType));
                if (structureType == StructureType.BARRACKS) {
                    sb.append(String.format("-%s", barracksType));
                }
            }
            sb.append("\nTRAIN");
            trainInSites.forEach(z -> sb.append(" ").append(z));
            return sb.toString();
        }
    }

    static class MoveBuilder {

        private Integer x;
        private Integer y;
        private Integer siteId;
        private StructureType structureType;
        private BarracksType barracksType;
        private List<Integer> trainInSites;

        public MoveBuilder setX(Integer x) {
            this.x = x;
            return this;
        }

        public MoveBuilder setY(Integer y) {
            this.y = y;
            return this;
        }

        public MoveBuilder setSiteId(Integer siteId) {
            this.siteId = siteId;
            return this;
        }

        public MoveBuilder setStructureType(StructureType structureType) {
            this.structureType = structureType;
            return this;
        }

        public MoveBuilder setBarracksType(BarracksType barracksType) {
            this.barracksType = barracksType;
            return this;
        }

        public MoveBuilder setTrainInSites(List<Integer> trainInSites) {
            this.trainInSites = trainInSites;
            return this;
        }

        public List<Integer> getTrainInSites() {
            return trainInSites;
        }

        public Player.Move createMove() {
            return new Player.Move(x,
                    y,
                    siteId,
                    structureType,
                    barracksType,
                    trainInSites == null ? Collections.emptyList() : trainInSites);
        }
    }

    enum StructureType {
        NONE,
        MINE,
        TOWER,
        BARRACKS;

        private static StructureType fromId(int id) {
            switch (id) {
                case -1:
                    return NONE;
                case 0:
                    return MINE;
                case 1:
                    return TOWER;
                case 2:
                    return BARRACKS;
                default:
                    throw new RuntimeException("Unsupported id");
            }
        }
    }

    enum Owner {
        NONE,
        FRIENDLY,
        ENEMY;

        private static Owner fromId(int id) {
            switch (id) {
                case -1:
                    return NONE;
                case 0:
                    return FRIENDLY;
                case 1:
                    return ENEMY;
                default:
                    throw new RuntimeException("Unsupported id");
            }
        }
    }

    enum BarracksType {
        NONE,
        KNIGHT,
        ARCHER,
        GIANT;

        public static BarracksType fromId(int id) {
            switch (id) {
                case -1:
                    return NONE;
                case 0:
                    return KNIGHT;
                case 1:
                    return ARCHER;
                case 2:
                    return GIANT;
                default:
                    throw new RuntimeException("Unsupported id");
            }
        }
    }

    enum UnitType {
        QUEEN,
        KNIGHT,
        ARCHER,
        GIANT;

        private static UnitType fromId(int id) {
            switch (id) {
                case -1:
                    return QUEEN;
                case 0:
                    return KNIGHT;
                case 1:
                    return ARCHER;
                case 2:
                    return GIANT;
                default:
                    throw new RuntimeException("Unsupported id");
            }
        }
    }

    static class Unit {

        private final int x, y;
        private final Owner owner;
        private final UnitType unitType;
        private final int hp;

        public static Unit create(int x, int y, Owner owner, UnitType unitType, int hp) {
            return new Unit(x, y, owner, unitType, hp);
        }

        public Unit(int x, int y, Owner owner, UnitType unitType, int hp) {
            this.x = x;
            this.y = y;
            this.owner = owner;
            this.unitType = unitType;
            this.hp = hp;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public Owner getOwner() {
            return owner;
        }

        public UnitType getUnitType() {
            return unitType;
        }

        public int getHp() {
            return hp;
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    static class BuildingSite {

        private final BuildingSiteStatic staticInfo;
        private final StructureType structureType;
        private final Owner owner;
        private final int untilTrain;
        private final Optional<Integer> gold;
        private final Optional<Integer> maxMineSize;
        private final int towerHP;
        private final int towerRange;
        private final int incomeRate;
        private final BarracksType barracksType;

        public static BuildingSite create(BuildingSiteStatic staticInfo,
                                          StructureType structureType,
                                          Owner owner,
                                          int untilTrain,
                                          Optional<Integer> gold,
                                          Optional<Integer> maxMineSize,
                                          int towerHP,
                                          int towerRange,
                                          int incomeRate,
                                          BarracksType barracksType) {
            return new BuildingSite(staticInfo,
                    structureType,
                    owner,
                    untilTrain,
                    gold,
                    maxMineSize,
                    towerHP,
                    towerRange,
                    incomeRate,
                    barracksType);
        }

        public BuildingSite(BuildingSiteStatic staticInfo,
                            StructureType structureType,
                            Owner owner,
                            int untilTrain,
                            Optional<Integer> gold,
                            Optional<Integer> maxMineSize,
                            int towerHP,
                            int towerRange,
                            int incomeRate,
                            BarracksType barracksType) {
            this.staticInfo = staticInfo;
            this.structureType = structureType;
            this.owner = owner;
            this.untilTrain = untilTrain;
            this.gold = gold;
            this.maxMineSize = maxMineSize;
            this.towerHP = towerHP;
            this.towerRange = towerRange;
            this.incomeRate = incomeRate;
            this.barracksType = barracksType;
        }

        public int getId() {
            return staticInfo.getId();
        }

        public int getX() {
            return staticInfo.getX();
        }

        public int getY() {
            return staticInfo.getY();
        }

        public int getRadius() {
            return staticInfo.getRadius();
        }

        public StructureType getStructureType() {
            return structureType;
        }

        public Owner getOwner() {
            return owner;
        }

        public int getUntilTrain() {
            return untilTrain;
        }

        public Optional<Integer> getGold() {
            return gold;
        }

        public Optional<Integer> getMaxMineSize() {
            return maxMineSize;
        }

        public int getIncomeRate() {
            return incomeRate;
        }

        public int getTowerHP() {
            return towerHP;
        }

        public int getTowerRange() {
            return towerRange;
        }

        public BarracksType getBarracksType() {
            return barracksType;
        }
    }

    static class BuildingSiteStatic {

        private final int id;
        private final int x, y;
        private final int radius;

        public static BuildingSiteStatic create(int id, int x, int y, int radius) {
            return new BuildingSiteStatic(id, x, y, radius);
        }

        public BuildingSiteStatic(int id, int x, int y, int radius) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.radius = radius;
        }

        public int getId() {
            return id;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getRadius() {
            return radius;
        }
    }
}