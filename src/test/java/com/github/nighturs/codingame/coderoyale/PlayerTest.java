package com.github.nighturs.codingame.coderoyale;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;

@SuppressWarnings("DynamicRegexReplaceableByCompiledPattern")
public class PlayerTest {

    @Test
    public void sanityTest() {
        Player.GameState gameState =
                Player.GameState.create(Arrays.asList(Player.BuildingSiteStatic.create(0, 100, 200, 100),
                        Player.BuildingSiteStatic.create(1, 1000, 500, 100)));
        gameState.initTurn(100,
                -1,
                Arrays.asList(Player.BuildingSite.create(gameState.getBuildingSiteStaticById(0),
                        Player.StructureType.NONE,
                        Player.Owner.NONE,
                        0,
                        Optional.empty(),
                        Optional.empty(),
                        0,
                        0,
                        Player.BarracksType.NONE),
                        Player.BuildingSite.create(gameState.getBuildingSiteStaticById(1),
                                Player.StructureType.NONE,
                                Player.Owner.NONE,
                                0,
                                Optional.empty(),
                                Optional.empty(),
                                0,
                                0,
                                Player.BarracksType.NONE)),
                Collections.singletonList(Player.Unit.create(500,
                        500,
                        Player.Owner.FRIENDLY,
                        Player.UnitType.QUEEN,
                        100)));
        Assert.assertTrue(Player.TurnEngine.findMove(gameState).toString().matches("(WAIT|MOVE).*?\nTRAIN"));
    }
}