package com.infiniteminesweeper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public final class MinefieldTest {
    @Test
    public void generatedBlocksHaveExactMineCountIncludingNegativeCoordinates() {
        Minefield field = new Minefield(42L);
        int[] blockCoordinates = {-3, -1, 0, 4};
        for (int blockX : blockCoordinates) {
            for (int blockY : blockCoordinates) {
                int mines = 0;
                for (int y = 0; y < Minefield.BLOCK_SIZE; y++) {
                    for (int x = 0; x < Minefield.BLOCK_SIZE; x++) {
                        if (field.isMine(blockX * Minefield.BLOCK_SIZE + x,
                                blockY * Minefield.BLOCK_SIZE + y)) {
                            mines++;
                        }
                    }
                }
                assertEquals(10, mines);
            }
        }
    }

    @Test
    public void blockGenerationIsDeterministic() {
        Minefield first = new Minefield(918273L);
        Minefield second = new Minefield(918273L);
        for (int y = -20; y <= 20; y++) {
            for (int x = -20; x <= 20; x++) {
                assertEquals(first.isMine(x, y), second.isMine(x, y));
            }
        }
    }

    @Test
    public void firstRevealProtectsThreeByThreeAcrossBlockEdges() {
        Minefield field = new Minefield(7L);
        field.isMine(0, 0);
        field.toggleFlag(8, 8);

        Minefield.RevealResult result = field.revealCell(7, 7);

        assertTrue(result.changed);
        assertFalse(result.mineHit);
        for (int y = 6; y <= 8; y++) {
            for (int x = 6; x <= 8; x++) {
                assertFalse(field.isMine(x, y));
            }
        }
        assertTrue(field.isFlagged(8, 8));
        assertEquals(10, countMines(field, 0, 0));
        assertEquals(10, countMines(field, 1, 1));
    }

    @Test
    public void flagsPreventRevealAndCanBeRemoved() {
        Minefield field = new Minefield(19L);
        assertTrue(field.toggleFlag(0, 0));

        Minefield.RevealResult blocked = field.revealCell(0, 0);
        assertFalse(blocked.changed);
        assertFalse(field.isRevealed(0, 0));
        assertEquals(1, field.getFlagCount());

        assertTrue(field.toggleFlag(0, 0));
        Minefield.RevealResult revealed = field.revealCell(0, 0);
        assertTrue(revealed.changed);
        assertTrue(field.isRevealed(0, 0));
        assertEquals(0, field.getFlagCount());
    }

    @Test
    public void clearingBlocksAwardsPointsOnlyOnce() {
        Minefield field = new Minefield(123L);
        revealAllSafeCells(field, 0, 0);

        assertTrue(field.isBlockCleared(0, 0));
        assertEquals(1, field.getClearedBlocks());
        assertEquals(Minefield.POINTS_PER_BLOCK, field.getScore());

        revealAllSafeCells(field, 0, 0);
        assertEquals(Minefield.POINTS_PER_BLOCK, field.getScore());

        revealAllSafeCells(field, 1, 0);
        assertTrue(field.isBlockCleared(1, 0));
        assertEquals(2, field.getClearedBlocks());
        assertEquals(2 * Minefield.POINTS_PER_BLOCK, field.getScore());
    }

    @Test
    public void neighborCountsIncludeAdjacentBlocks() {
        Minefield field = new Minefield(555L);
        for (int y = 6; y <= 9; y++) {
            for (int x = 6; x <= 9; x++) {
                int expected = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if ((dx != 0 || dy != 0) && field.isMine(x + dx, y + dy)) {
                            expected++;
                        }
                    }
                }
                assertEquals(expected, field.getAdjacentMineCount(x, y));
            }
        }
    }

    @Test
    public void correctChordRevealsAllUnflaggedSafeNeighbors() {
        Minefield field = new Minefield(77L);
        field.revealCell(0, 0);
        int[] cell = findChordCell(field, false);
        assertTrue("Expected an open number with hidden safe neighbors", cell != null);
        int x = cell[0];
        int y = cell[1];
        flagMineNeighbors(field, x, y);

        Minefield.RevealResult result = field.revealOrChord(x, y);

        assertTrue(result.changed);
        assertFalse(result.mineHit);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (!field.isMine(x + dx, y + dy)) {
                    assertTrue(field.isRevealed(x + dx, y + dy));
                }
            }
        }
    }

    @Test
    public void incorrectChordHitsAnUnflaggedMine() {
        Minefield field = new Minefield(91L);
        field.revealCell(0, 0);
        int[] cell = findChordCell(field, true);
        assertTrue("Expected an open number with enough hidden safe neighbors", cell != null);
        int x = cell[0];
        int y = cell[1];
        int flagsNeeded = field.getAdjacentMineCount(x, y);
        for (int dy = -1; dy <= 1 && flagsNeeded > 0; dy++) {
            for (int dx = -1; dx <= 1 && flagsNeeded > 0; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if ((dx != 0 || dy != 0) && !field.isMine(nx, ny) && !field.isRevealed(nx, ny)) {
                    field.toggleFlag(nx, ny);
                    flagsNeeded--;
                }
            }
        }

        Minefield.RevealResult result = field.revealOrChord(x, y);

        assertTrue(result.mineHit);
        assertTrue(field.isGameOver());
    }

    @Test
    public void hittingMineEndsRunAndFreezesBoard() {
        Minefield field = new Minefield(9L);
        field.revealCell(0, 0);
        int mineX = -1;
        int mineY = -1;
        for (int y = 0; y < Minefield.BLOCK_SIZE && mineX < 0; y++) {
            for (int x = 0; x < Minefield.BLOCK_SIZE; x++) {
                if (field.isMine(x, y)) {
                    mineX = x;
                    mineY = y;
                    break;
                }
            }
        }
        assertTrue(mineX >= 0);

        Minefield.RevealResult hit = field.revealCell(mineX, mineY);
        assertTrue(hit.mineHit);
        assertTrue(field.isGameOver());
        assertTrue(field.isExplodedCell(mineX, mineY));
        assertFalse(field.revealCell(20, 20).changed);
        assertFalse(field.toggleFlag(20, 20));
    }

    @Test
    public void extraLifeIsAwardedEveryFiveClearedBlocks() {
        Minefield field = new Minefield(2026L);
        for (int blockX = 0; blockX < 10; blockX++) {
            revealAllSafeCells(field, blockX, 0);
            assertEquals(field.getClearedBlocks() / Minefield.BLOCKS_PER_EXTRA_LIFE,
                    field.getExtraLives());
        }
        assertTrue(field.getClearedBlocks() >= 10);
        assertTrue(field.getExtraLives() >= 2);
    }

    @Test
    public void extraLifeFlagsMineAndAllowsRunToContinue() {
        Minefield field = new Minefield(500L);
        for (int blockX = 0; blockX < 5; blockX++) {
            revealAllSafeCells(field, blockX, 0);
        }
        assertTrue(field.getExtraLives() > 0);
        int livesBefore = field.getExtraLives();
        int flagsBefore = field.getFlagCount();
        int mineX = 40;
        int mineY = 40;
        while (!field.isMine(mineX, mineY)) {
            mineX++;
        }

        Minefield.RevealResult saved = field.revealCell(mineX, mineY);

        assertTrue(saved.mineHit);
        assertTrue(saved.extraLifeUsed);
        assertFalse(field.isGameOver());
        assertEquals(livesBefore - 1, field.getExtraLives());
        assertEquals(flagsBefore + 1, field.getFlagCount());
        assertTrue(field.isFlagged(mineX, mineY));
        assertFalse(field.isRevealed(mineX, mineY));
        assertFalse(field.revealCell(mineX, mineY).changed);
        assertEquals(livesBefore - 1, field.getExtraLives());
    }

    @Test
    public void exploreModeCountsMistakesAndRevealsMinesWithoutEnding() throws Exception {
        Minefield field = new Minefield(777L, Minefield.GameMode.EXPLORE);
        for (int blockX = 0; blockX < 5; blockX++) {
            revealAllSafeCells(field, blockX, 0);
        }
        assertTrue(field.getClearedBlocks() >= 5);
        assertTrue(field.getScore() >= 500);
        assertEquals(0, field.getExtraLives());
        assertEquals(0, field.getMistakeCount());
        int flagsBefore = field.getFlagCount();

        int hits = 0;
        int lastMineX = -1;
        int lastMineY = -1;
        search:
        for (int y = 40; y < 48; y++) {
            for (int x = 40; x < 48; x++) {
                if (!field.isMine(x, y)) {
                    continue;
                }
                Minefield.RevealResult result = field.revealCell(x, y);
                assertTrue(result.mineHit);
                assertFalse(result.extraLifeUsed);
                assertFalse(field.isGameOver());
                assertFalse(field.isFlagged(x, y));
                assertTrue(field.isRevealed(x, y));
                hits++;
                assertEquals(hits, field.getMistakeCount());
                lastMineX = x;
                lastMineY = y;
                if (hits == 3) {
                    break search;
                }
            }
        }
        assertEquals(3, hits);
        assertEquals(flagsBefore, field.getFlagCount());
        assertEquals(0, field.getExtraLives());
        assertFalse(field.isGameOver());
        assertTrue(field.isExplodedCell(lastMineX, lastMineY));
        assertFalse(field.revealCell(lastMineX, lastMineY).changed);
        assertEquals(3, field.getMistakeCount());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        field.writeState(output);
        output.flush();
        Minefield restored = Minefield.readState(
                new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(3, restored.getMistakeCount());
        assertFalse(restored.isGameOver());
        for (int y = 40; y < 48; y++) {
            for (int x = 40; x < 48; x++) {
                assertEquals(field.isRevealed(x, y), restored.isRevealed(x, y));
                assertEquals(field.isFlagged(x, y), restored.isFlagged(x, y));
            }
        }
    }

    @Test
    public void gauntletDensityIncreasesWithDistanceAndIsCapped() {
        Minefield gauntlet = new Minefield(81L, Minefield.GameMode.GAUNTLET);
        assertEquals(10, countMines(gauntlet, 0, 0));
        assertEquals(11, countMines(gauntlet, 1, 0));
        assertEquals(11, countMines(gauntlet, -1, 0));
        assertEquals(11, countMines(gauntlet, 1, 1));
        assertEquals(12, countMines(gauntlet, 2, 0));
        assertEquals(13, countMines(gauntlet, 0, -3));
        assertEquals(24, countMines(gauntlet, 20, 0));

        Minefield survival = new Minefield(81L, Minefield.GameMode.SURVIVAL);
        Minefield explore = new Minefield(81L, Minefield.GameMode.EXPLORE);
        assertEquals(10, countMines(survival, 20, 0));
        assertEquals(10, countMines(explore, 20, 0));
    }

    @Test
    public void savedGameRoundTripPreservesCompleteRun() throws Exception {
        Minefield original = new Minefield(345L, Minefield.GameMode.GAUNTLET);
        original.revealCell(0, 0);
        original.toggleFlag(30, 30);
        revealAllSafeCells(original, 0, 0);
        revealAllSafeCells(original, 1, 0);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        original.writeState(output);
        output.flush();

        Minefield restored = Minefield.readState(
                new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

        assertEquals(Minefield.GameMode.GAUNTLET, restored.getMode());
        assertEquals(original.getScore(), restored.getScore());
        assertEquals(original.getClearedBlocks(), restored.getClearedBlocks());
        assertEquals(original.getFlagCount(), restored.getFlagCount());
        assertEquals(original.getExtraLives(), restored.getExtraLives());
        assertEquals(original.isGameOver(), restored.isGameOver());
        for (int y = -4; y <= 35; y++) {
            for (int x = -4; x <= 35; x++) {
                assertEquals(original.isMine(x, y), restored.isMine(x, y));
                assertEquals(original.isRevealed(x, y), restored.isRevealed(x, y));
                assertEquals(original.isFlagged(x, y), restored.isFlagged(x, y));
            }
        }
        assertTrue(restored.toggleFlag(31, 31));
        assertEquals(original.getFlagCount() + 1, restored.getFlagCount());
    }

    private static int[] findChordCell(Minefield field, boolean incorrectChord) {
        for (int y = -16; y <= 16; y++) {
            for (int x = -16; x <= 16; x++) {
                if (!field.isRevealed(x, y) || field.isMine(x, y)) {
                    continue;
                }
                int mines = field.getAdjacentMineCount(x, y);
                if (mines == 0) {
                    continue;
                }
                int hiddenSafe = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (!field.isMine(x + dx, y + dy) && !field.isRevealed(x + dx, y + dy)) {
                            hiddenSafe++;
                        }
                    }
                }
                if ((!incorrectChord && hiddenSafe > 0) || (incorrectChord && hiddenSafe >= mines)) {
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private static void flagMineNeighbors(Minefield field, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dy != 0) && field.isMine(x + dx, y + dy)) {
                    field.toggleFlag(x + dx, y + dy);
                }
            }
        }
    }

    private static void revealAllSafeCells(Minefield field, int blockX, int blockY) {
        int startX = blockX * Minefield.BLOCK_SIZE;
        int startY = blockY * Minefield.BLOCK_SIZE;
        for (int y = 0; y < Minefield.BLOCK_SIZE; y++) {
            for (int x = 0; x < Minefield.BLOCK_SIZE; x++) {
                int worldX = startX + x;
                int worldY = startY + y;
                if (!field.isMine(worldX, worldY)) {
                    field.revealCell(worldX, worldY);
                }
            }
        }
    }

    private static int countMines(Minefield field, int blockX, int blockY) {
        int count = 0;
        for (int y = 0; y < Minefield.BLOCK_SIZE; y++) {
            for (int x = 0; x < Minefield.BLOCK_SIZE; x++) {
                if (field.isMine(blockX * Minefield.BLOCK_SIZE + x,
                        blockY * Minefield.BLOCK_SIZE + y)) {
                    count++;
                }
            }
        }
        return count;
    }
}
