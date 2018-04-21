package com.github.nighturs.codingame.coderoyale;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    static final int GRID_WIDTH = 1920;
    static final int GRID_HEIGHT = 1000;
    static final int QUEEN_SPEED = 60;
    static final int CONTACT_RANGE = 5;
    static final int QUEEN_RADIUS = 30;

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

    static class RunFromKnights implements Rule {

        private static final int PANIC_MODE_DIST = 100;
        private static final int TOWER_CIRCLING_RADIUS = 100;

        @SuppressWarnings("SuspiciousNameCombination")
        @Override
        public Optional<MoveBuilder> makeMove(GameState gameState) {

            boolean panicMode = false;
            int enemiesCount = 0;
            double sumX = 0;
            double sumY = 0;
            for (Unit unit : gameState.getUnits()) {
                if (unit.getOwner() != Owner.ENEMY || unit.getUnitType() != UnitType.KNIGHT) {
                    continue;
                }
                if (Utils.dist(gameState.getMyQueen().getX(), gameState.getMyQueen().getY(), unit.getX(), unit.getY())
                        < PANIC_MODE_DIST) {
                    panicMode = true;
                    enemiesCount++;
                    sumX += unit.getX();
                    sumY += unit.getY();
                }
            }
            if (!panicMode) {
                return Optional.empty();
            }
            double enemyCenterX = sumX / enemiesCount;
            double enemyCenterY = sumY / enemiesCount;

            double minCumDist = Double.MAX_VALUE;
            BuildingSite targetTower = null;
            for (BuildingSite siteA : gameState.getBuildingSites()) {
                if (siteA.getOwner() != Owner.FRIENDLY || siteA.getStructureType() != StructureType.TOWER) {
                    continue;
                }
                double sumDist = 0;
                for (BuildingSite siteB : gameState.getBuildingSites()) {
                    if (siteB.getOwner() != Owner.FRIENDLY || siteB.getStructureType() != StructureType.TOWER
                            || siteA.getId() == siteB.getId()) {
                        continue;
                    }
                    sumDist += Utils.dist(siteA.getX(), siteA.getY(), siteB.getX(), siteB.getY());
                }
                if (minCumDist > sumDist) {
                    minCumDist = sumDist;
                    targetTower = siteA;
                }
            }
            if (targetTower == null) {
                return Optional.empty();
            }

            double targetTowerX = targetTower.getX();
            double targetTowerY = targetTower.getY();
            double queenX = gameState.getMyQueen().getX() - targetTowerX;
            double queenY = gameState.getMyQueen().getY() - targetTowerY;
            double k = TOWER_CIRCLING_RADIUS / Utils.dist(0, 0, queenX, queenY);
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

        @Override
        public int priority() {
            return 2;
        }
    }

    static class GoToNewSiteRule implements Rule {

        @Override
        public Optional<MoveBuilder> makeMove(GameState gameState) {
            if (gameState.getTouchedSiteOpt().isPresent()
                    && gameState.getTouchedSiteOpt().get().getOwner() != Owner.FRIENDLY) {
                return Optional.empty();
            }

            // Find nearest vacant building site
            double dist = Double.MAX_VALUE;
            BuildingSite nearestSite = null;
            for (BuildingSite site : gameState.getBuildingSites()) {
                if (site.getOwner() == Owner.FRIENDLY || site.getStructureType() == StructureType.TOWER) {
                    continue;
                }
                double curDist = Utils.dist(site.getX(),
                        site.getY(),
                        gameState.getMyQueen().getX(),
                        gameState.getMyQueen().getY());
                if (curDist < dist) {
                    dist = curDist;
                    nearestSite = site;
                }
            }
            if (nearestSite == null) {
                return Optional.empty();
            }

            // Find if it is possible to approach in one turn and make next vacant site less far away
            int queenX = gameState.getMyQueen().getX();
            int queenY = gameState.getMyQueen().getY();

            double bestFuture = Double.MAX_VALUE;
            int moveX = 0;
            int moveY = 0;
            for (int x1 = -QUEEN_SPEED; x1 <= QUEEN_SPEED; x1++) {
                for (int y1 = -QUEEN_SPEED; y1 <= QUEEN_SPEED; y1++) {
                    int newX = queenX + x1;
                    int newY = queenY + y1;
                    if (newX < 0 || newX > GRID_WIDTH || newY < 0 || newY > GRID_HEIGHT) {
                        continue;
                    }
                    double d = Utils.dist(queenX, queenY, newX, newY);
                    if (d > QUEEN_SPEED) {
                        continue;
                    }
                    if (!Utils.inContact(newX,
                            newY,
                            QUEEN_RADIUS,
                            nearestSite.getX(),
                            nearestSite.getY(),
                            nearestSite.getRadius())) {
                        continue;
                    }
                    double closestSite = Double.MAX_VALUE - 1;
                    for (BuildingSite site : gameState.getBuildingSites()) {
                        if (site.getOwner() == Owner.FRIENDLY || site.getStructureType() == StructureType.TOWER
                                || site.getId() == nearestSite.getId()) {
                            continue;
                        }
                        double nextD = Utils.dist(newX, newY, site.getX(), site.getY());
                        if (closestSite > nextD) {
                            closestSite = nextD;
                        }
                    }
                    if (closestSite < bestFuture) {
                        bestFuture = closestSite;
                        moveX = newX;
                        moveY = newY;
                    }
                }
            }

            if (moveX != 0 && moveY != 0) {
                return Optional.of(new MoveBuilder().setX(moveX).setY(moveY));
            }

            // Find approach point considering future vacant building sites
            double wiseX = -1;
            double wiseY = -1;
            double minDist = Double.MAX_VALUE;
            for (BuildingSite site : gameState.getBuildingSites()) {
                if (site.getOwner() == Owner.FRIENDLY || site.getStructureType() == StructureType.TOWER
                        || site.getId() == nearestSite.getId()) {
                    continue;
                }
                double turnX = site.getX() - nearestSite.getX();
                double turnY = site.getY() - nearestSite.getY();
                double k = (nearestSite.getRadius() + QUEEN_RADIUS) / Utils.dist(site.getX(),
                        site.getY(),
                        nearestSite.getX(),
                        nearestSite.getY());
                turnX = (turnX * k) + nearestSite.getX();
                turnY = (turnY * k) + nearestSite.getY();

                double siteD =
                        Utils.dist(queenX, queenY, turnX, turnY) + Utils.dist(turnX, turnY, site.getX(), site.getY());
                if (siteD < minDist) {
                    minDist = siteD;
                    wiseX = turnX;
                    wiseY = turnY;
                }
            }

            //noinspection FloatingPointEquality
            if (minDist == Double.MAX_VALUE) {
                return Optional.of(new MoveBuilder().setX(nearestSite.getX()).setY(nearestSite.getY()));
            }

            return Optional.of(new MoveBuilder().setX((int) wiseX).setY((int) wiseY));
        }

        @Override
        public int priority() {
            return 0;
        }
    }

    static class BuildStructureRule implements Rule {

        private static final int OPTIMAL_MINE_COUNT = 3;

        @Override
        public Optional<MoveBuilder> makeMove(GameState gameState) {

            int countKnightBarracks = 0;
            int countMines = 0;
            for (BuildingSite site : gameState.getBuildingSites()) {
                if (site.getOwner() != Owner.FRIENDLY) {
                    continue;
                }
                if (site.getBarracksType() == BarracksType.KNIGHT) {
                    countKnightBarracks++;
                }
                if (site.getStructureType() == StructureType.MINE) {
                    countMines++;
                }
            }

            Optional<BuildingSite> touchSite = gameState.getTouchedSiteOpt();

            if (touchSite.isPresent() && touchSite.get().getOwner() == Owner.FRIENDLY
                    && touchSite.get().getStructureType() == StructureType.MINE
                    && touchSite.get().getMaxMineSize().orElse(0) > touchSite.get().getIncomeRate()) {
                return Optional.of(new MoveBuilder().setSiteId(touchSite.get().getId())
                        .setStructureType(StructureType.MINE));
            }

            if (touchSite.isPresent() && touchSite.get().getOwner() != Owner.FRIENDLY) {
                if (countMines < OPTIMAL_MINE_COUNT) {
                    return Optional.of(new MoveBuilder().setSiteId(touchSite.get().getId())
                            .setStructureType(StructureType.MINE));
                } else if (countKnightBarracks == 0) {
                    return Optional.of(new MoveBuilder().setSiteId(touchSite.get().getId())
                            .setStructureType(StructureType.BARRACKS)
                            .setBarracksType(BarracksType.KNIGHT));
                } else {
                    return Optional.of(new MoveBuilder().setSiteId(touchSite.get().getId())
                            .setStructureType(StructureType.TOWER));
                }
            }
            return Optional.empty();
        }

        @Override
        public int priority() {
            return 3;
        }
    }

    static class TrainUnitsRule implements Rule {

        @Override
        public Optional<MoveBuilder> makeMove(GameState gameState) {
            List<Integer> trainSites = new ArrayList<>();
            int gold = gameState.getGoldLeft();
            for (BuildingSite site : gameState.getBuildingSites()) {
                if (site.getOwner() != Owner.FRIENDLY || site.getStructureType() != StructureType.BARRACKS
                        || site.getUntilTrain() > 0) {
                    continue;
                }
                int cost = site.getBarracksType() == BarracksType.ARCHER ? ARCHER_COST : KNIGHT_COST;
                if (gold >= cost) {
                    gold -= cost;
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
            List<Rule> queenRules =
                    Arrays.asList(new GoToNewSiteRule(), new BuildStructureRule(), new RunFromKnights());
            Optional<MoveBuilder> queenMoveOpt = bestPriorityMove(gameState, queenRules);
            List<Rule> structureRules = Arrays.asList(new TrainUnitsRule());
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

        public void initTurn(int gold,
                             int touchedSite,
                             @SuppressWarnings("ParameterHidesMemberVariable") List<BuildingSite> buildingSites,
                             @SuppressWarnings("ParameterHidesMemberVariable") List<Unit> units) {
            buildingSiteById =
                    buildingSites.stream().collect(Collectors.toMap(BuildingSite::getId, Function.identity()));
            goldLeft = gold;
            touchedSiteOpt = touchedSite == -1 ? Optional.empty() : Optional.of(getBuildingSiteById(touchedSite));
            this.buildingSites = buildingSites;
            this.units = units;
            //noinspection ConstantConditions
            this.myQueen = units.stream()
                    .filter(x -> x.getOwner() == Owner.FRIENDLY && x.getUnitType() == UnitType.QUEEN)
                    .findFirst()
                    .get();
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
        private final int incomeRate;
        private final BarracksType barracksType;

        public static BuildingSite create(BuildingSiteStatic staticInfo,
                                          StructureType structureType,
                                          Owner owner,
                                          int untilTrain,
                                          Optional<Integer> gold,
                                          Optional<Integer> maxMineSize,
                                          int towerHP,
                                          int incomeRate,
                                          BarracksType barracksType) {
            return new BuildingSite(staticInfo,
                    structureType,
                    owner,
                    untilTrain,
                    gold,
                    maxMineSize,
                    towerHP,
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
                            int incomeRate,
                            BarracksType barracksType) {
            this.staticInfo = staticInfo;
            this.structureType = structureType;
            this.owner = owner;
            this.untilTrain = untilTrain;
            this.gold = gold;
            this.maxMineSize = maxMineSize;
            this.towerHP = towerHP;
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