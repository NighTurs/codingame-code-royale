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
                int ignore1 = in.nextInt();
                int ignore2 = in.nextInt();
                int structureType = in.nextInt();
                int owner = in.nextInt();
                int param1 = in.nextInt();
                int param2 = in.nextInt();
                buildingSites.add(BuildingSite.create(gameState.getBuildingSiteStaticById(siteId),
                        StructureType.fromId(structureType),
                        Owner.fromId(owner),
                        param1,
                        BarracksType.fromId(param2)));
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

    static class GoToNewSiteRule implements Rule {

        @Override
        public Optional<MoveBuilder> makeMove(GameState gameState) {
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
            List<Rule> queenRules = Arrays.asList(new GoToNewSiteRule());
            Optional<MoveBuilder> queenMoveOpt = bestPriorityMove(gameState, queenRules);
            List<Rule> structureRules = Collections.emptyList(); // TODO: Add rules
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
        private List<BuildingSite> buildingSite;
        private Map<Integer, BuildingSite> buildingSiteById;
        private List<Unit> units;
        private int goldLeft;
        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        private Optional<BuildingSite> touchedSiteOpt;

        public static GameState create(List<BuildingSiteStatic> buildingSiteStatics) {
            return new GameState(buildingSiteStatics);
        }

        public GameState(List<BuildingSiteStatic> buildingSiteStatics) {
            this.buildingSiteStatics = buildingSiteStatics;
            buildingSiteStaticById = buildingSiteStatics.stream()
                    .collect(Collectors.toMap(BuildingSiteStatic::getId, Function.identity()));
        }

        public BuildingSiteStatic getBuildingSiteStaticById(int id) {
            return buildingSiteStaticById.get(id);
        }

        public BuildingSite getBuildingSiteById(int id) {
            return buildingSiteById.get(id);
        }

        public void initTurn(int gold,
                             int touchedSite,
                             List<BuildingSite> buildingSites,
                             @SuppressWarnings("ParameterHidesMemberVariable") List<Unit> units) {
            buildingSiteById =
                    buildingSites.stream().collect(Collectors.toMap(BuildingSite::getId, Function.identity()));
            goldLeft = gold;
            touchedSiteOpt = touchedSite == -1 ? Optional.empty() : Optional.of(getBuildingSiteById(touchedSite));
            buildingSite = buildingSites;
            this.units = units;
        }
    }

    static class Move {

        private final Integer x, y;
        private final Integer siteId;
        private final StructureType structureType;
        private final List<Integer> trainInSites;

        public Move(Integer x, Integer y, Integer siteId, StructureType structureType, List<Integer> trainInSites) {
            this.x = x;
            this.y = y;
            this.siteId = siteId;
            this.structureType = structureType;
            this.trainInSites = trainInSites;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            if (x == null && structureType == null) {
                sb.append("WAIT\n");
            } else if (x != null) {
                sb.append(String.format("MOVE %d %d", x, y));
            } else {
                sb.append(String.format("BUILD %d BARRACKS-%d", siteId, structureType.getId()));
            }
            sb.append("TRAIN");
            trainInSites.forEach(z -> sb.append(" ").append(z));
            return sb.toString();
        }
    }

    static class MoveBuilder {

        private Integer x;
        private Integer y;
        private Integer siteId;
        private Player.StructureType structureType;
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

        public MoveBuilder setStructureType(Player.StructureType structureType) {
            this.structureType = structureType;
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
                    trainInSites == null ? Collections.emptyList() : trainInSites);
        }
    }

    enum StructureType {
        NONE(-1),
        BARRACKS(2);
        private final int id;

        StructureType(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        private static StructureType fromId(int id) {
            switch (id) {
                case -1:
                    return NONE;
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
        ARCHER;

        private static BarracksType fromId(int id) {
            switch (id) {
                case -1:
                    return NONE;
                case 0:
                    return KNIGHT;
                case 1:
                    return ARCHER;
                default:
                    throw new RuntimeException("Unsupported id");
            }
        }
    }

    enum UnitType {
        QUEEN,
        KNIGHT,
        ARCHER;

        private static UnitType fromId(int id) {
            switch (id) {
                case -1:
                    return QUEEN;
                case 0:
                    return KNIGHT;
                case 1:
                    return ARCHER;
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

    static class BuildingSite {

        private final BuildingSiteStatic staticInfo;
        private final StructureType structureType;
        private final Owner owner;
        private final int untilTrain;
        private final BarracksType barracksType;

        public static BuildingSite create(BuildingSiteStatic staticInfo,
                                          StructureType structureType,
                                          Owner owner,
                                          int untilTrain,
                                          BarracksType barracksType) {
            return new BuildingSite(staticInfo, structureType, owner, untilTrain, barracksType);
        }

        public BuildingSite(BuildingSiteStatic staticInfo,
                            StructureType structureType,
                            Owner owner,
                            int untilTrain,
                            BarracksType barracksType) {
            this.staticInfo = staticInfo;
            this.structureType = structureType;
            this.owner = owner;
            this.untilTrain = untilTrain;
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
            return staticInfo.getY();
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