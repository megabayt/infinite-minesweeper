package com.infiniteminesweeper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

final class Minefield {
    static final int BLOCK_SIZE = 8;
    static final int MINES_PER_BLOCK = 10;
    static final int POINTS_PER_BLOCK = 100;
    static final int BLOCKS_PER_EXTRA_LIFE = 5;
    private static final int MAX_GAUNTLET_MINES = 24;
    private static final int CELL_COUNT = BLOCK_SIZE * BLOCK_SIZE;

    private final long seed;
    private final GameMode mode;
    private final Map<BlockKey, Block> blocks = new HashMap<>();
    private final Set<Long> protectedCells = new HashSet<>();
    private boolean firstReveal = true;
    private boolean gameOver;
    private int score;
    private int clearedBlocks;
    private int flagCount;
    private int mistakeCount;
    private int extraLives;
    private boolean hasExplodedCell;
    private int explodedX;
    private int explodedY;

    Minefield(long seed) {
        this(seed, GameMode.SURVIVAL);
    }

    Minefield(long seed, GameMode mode) {
        this.seed = seed;
        this.mode = mode;
    }

    RevealResult revealOrChord(int x, int y) {
        Block block = getBlockForCell(x, y);
        int index = localIndex(x, y);
        if (block.revealed[index] && !block.mines[index]) {
            return chord(x, y);
        }
        return revealCell(x, y);
    }

    RevealResult revealCell(int x, int y) {
        if (gameOver) {
            return new RevealResult();
        }
        Block block = getBlockForCell(x, y);
        int index = localIndex(x, y);
        if (block.revealed[index] || block.flagged[index]) {
            return new RevealResult();
        }
        if (firstReveal) {
            prepareFirstReveal(x, y);
        }
        return revealInternal(x, y);
    }

    boolean toggleFlag(int x, int y) {
        if (gameOver) {
            return false;
        }
        Block block = getBlockForCell(x, y);
        int index = localIndex(x, y);
        if (block.revealed[index]) {
            return false;
        }
        block.flagged[index] = !block.flagged[index];
        flagCount += block.flagged[index] ? 1 : -1;
        return true;
    }

    boolean isRevealed(int x, int y) {
        Block block = getBlockForCell(x, y);
        return block.revealed[localIndex(x, y)];
    }

    boolean isFlagged(int x, int y) {
        Block block = getBlockForCell(x, y);
        return block.flagged[localIndex(x, y)];
    }

    boolean isMine(int x, int y) {
        Block block = getBlockForCell(x, y);
        return block.mines[localIndex(x, y)];
    }

    boolean isBlockCleared(int blockX, int blockY) {
        return getBlock(blockX, blockY).cleared;
    }

    int getAdjacentMineCount(int x, int y) {
        int count = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dy != 0) && isMine(x + dx, y + dy)) {
                    count++;
                }
            }
        }
        return count;
    }

    boolean isGameOver() {
        return gameOver;
    }

    boolean isExplodedCell(int x, int y) {
        return hasExplodedCell && explodedX == x && explodedY == y;
    }

    int getScore() {
        return score;
    }

    int getClearedBlocks() {
        return clearedBlocks;
    }

    int getFlagCount() {
        return flagCount;
    }

    int getExtraLives() {
        return extraLives;
    }

    int getMistakeCount() {
        return mistakeCount;
    }

    GameMode getMode() {
        return mode;
    }

    void writeState(DataOutputStream output) throws IOException {
        output.writeLong(seed);
        output.writeUTF(mode.name());
        output.writeBoolean(firstReveal);
        output.writeBoolean(gameOver);
        output.writeInt(score);
        output.writeInt(clearedBlocks);
        output.writeInt(flagCount);
        output.writeInt(mistakeCount);
        output.writeInt(extraLives);
        output.writeBoolean(hasExplodedCell);
        output.writeInt(explodedX);
        output.writeInt(explodedY);
        output.writeInt(protectedCells.size());
        for (Long cell : protectedCells) {
            output.writeLong(cell);
        }
        output.writeInt(blocks.size());
        for (Map.Entry<BlockKey, Block> entry : blocks.entrySet()) {
            Block block = entry.getValue();
            output.writeInt(entry.getKey().x);
            output.writeInt(entry.getKey().y);
            output.writeInt(block.mineCount);
            output.writeInt(block.revealedSafeCells);
            output.writeBoolean(block.cleared);
            output.writeLong(toBitMask(block.revealed));
            output.writeLong(toBitMask(block.flagged));
        }
    }

    static Minefield readState(DataInputStream input) throws IOException {
        return readState(input, 2);
    }

    static Minefield readState(DataInputStream input, int version) throws IOException {
        if (version < 1 || version > 2) {
            throw new IOException("Unsupported minefield state");
        }
        Minefield field = new Minefield(input.readLong(), GameMode.valueOf(input.readUTF()));
        field.firstReveal = input.readBoolean();
        field.gameOver = input.readBoolean();
        field.score = input.readInt();
        field.clearedBlocks = input.readInt();
        field.flagCount = input.readInt();
        if (version >= 2) {
            field.mistakeCount = input.readInt();
        }
        field.extraLives = input.readInt();
        field.hasExplodedCell = input.readBoolean();
        field.explodedX = input.readInt();
        field.explodedY = input.readInt();
        int protectedCount = readCount(input, 9);
        for (int i = 0; i < protectedCount; i++) {
            field.protectedCells.add(input.readLong());
        }
        int blockCount = readCount(input, 100000);
        for (int i = 0; i < blockCount; i++) {
            int blockX = input.readInt();
            int blockY = input.readInt();
            int mineCount = input.readInt();
            if (mineCount != field.getMineCountForBlock(blockX, blockY)) {
                throw new IOException("Invalid block difficulty");
            }
            int revealedSafeCells = input.readInt();
            if (revealedSafeCells < 0 || revealedSafeCells > CELL_COUNT - mineCount) {
                throw new IOException("Invalid revealed cell count");
            }
            boolean cleared = input.readBoolean();
            long revealed = input.readLong();
            long flagged = input.readLong();
            if ((revealed & flagged) != 0) {
                throw new IOException("Invalid cell state");
            }
            Block block = new Block(field.generateMines(blockX, blockY, mineCount), mineCount);
            block.revealedSafeCells = revealedSafeCells;
            block.cleared = cleared;
            fromBitMask(revealed, block.revealed);
            fromBitMask(flagged, block.flagged);
            field.blocks.put(new BlockKey(blockX, blockY), block);
        }
        return field;
    }

    private static int readCount(DataInputStream input, int maximum) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > maximum) {
            throw new IOException("Invalid saved collection size");
        }
        return count;
    }

    private static long toBitMask(boolean[] cells) {
        long mask = 0;
        for (int i = 0; i < CELL_COUNT; i++) {
            if (cells[i]) {
                mask |= 1L << i;
            }
        }
        return mask;
    }

    private static void fromBitMask(long mask, boolean[] cells) {
        for (int i = 0; i < CELL_COUNT; i++) {
            cells[i] = (mask & (1L << i)) != 0;
        }
    }

    private RevealResult chord(int x, int y) {
        RevealResult result = new RevealResult();
        if (gameOver) {
            return result;
        }
        int adjacentMines = getAdjacentMineCount(x, y);
        if (adjacentMines == 0 || countAdjacentFlags(x, y) != adjacentMines) {
            return result;
        }
        for (int dy = -1; dy <= 1 && !result.mineHit; dy++) {
            for (int dx = -1; dx <= 1 && !result.mineHit; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int nx = x + dx;
                int ny = y + dy;
                Block block = getBlockForCell(nx, ny);
                int index = localIndex(nx, ny);
                if (!block.revealed[index] && !block.flagged[index]) {
                    result.add(revealInternal(nx, ny));
                }
            }
        }
        return result;
    }

    private int countAdjacentFlags(int x, int y) {
        int count = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dy != 0) && isFlagged(x + dx, y + dy)) {
                    count++;
                }
            }
        }
        return count;
    }

    private RevealResult revealInternal(int startX, int startY) {
        RevealResult result = new RevealResult();
        Block startBlock = getBlockForCell(startX, startY);
        int startIndex = localIndex(startX, startY);
        if (startBlock.revealed[startIndex] || startBlock.flagged[startIndex]) {
            return result;
        }
        if (startBlock.mines[startIndex]) {
            result.mineHit = true;
            result.changed = true;
            if (mode == GameMode.EXPLORE) {
                startBlock.revealed[startIndex] = true;
                mistakeCount++;
                hasExplodedCell = true;
                explodedX = startX;
                explodedY = startY;
                return result;
            }
            if (extraLives > 0) {
                extraLives--;
                startBlock.flagged[startIndex] = true;
                flagCount++;
                result.extraLifeUsed = true;
                return result;
            }
            startBlock.revealed[startIndex] = true;
            gameOver = true;
            hasExplodedCell = true;
            explodedX = startX;
            explodedY = startY;
            return result;
        }

        ArrayDeque<Long> pending = new ArrayDeque<>();
        pending.add(pack(startX, startY));
        while (!pending.isEmpty()) {
            long packed = pending.removeFirst();
            int x = unpackX(packed);
            int y = unpackY(packed);
            Block block = getBlockForCell(x, y);
            int index = localIndex(x, y);
            if (block.revealed[index] || block.flagged[index] || block.mines[index]) {
                continue;
            }

            block.revealed[index] = true;
            block.revealedSafeCells++;
            result.changed = true;
            if (!block.cleared && block.revealedSafeCells == CELL_COUNT - block.mineCount) {
                block.cleared = true;
                clearedBlocks++;
                score += POINTS_PER_BLOCK;
                result.pointsAwarded += POINTS_PER_BLOCK;
                if (mode != GameMode.EXPLORE
                        && clearedBlocks % BLOCKS_PER_EXTRA_LIFE == 0) {
                    extraLives++;
                    result.extraLifeEarned = true;
                }
            }

            if (getAdjacentMineCount(x, y) == 0) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) {
                            continue;
                        }
                        int nx = x + dx;
                        int ny = y + dy;
                        Block neighbor = getBlockForCell(nx, ny);
                        int neighborIndex = localIndex(nx, ny);
                        if (!neighbor.revealed[neighborIndex]
                                && !neighbor.flagged[neighborIndex]
                                && !neighbor.mines[neighborIndex]) {
                            pending.addLast(pack(nx, ny));
                        }
                    }
                }
            }
        }
        return result;
    }

    private void prepareFirstReveal(int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                protectedCells.add(pack(x + dx, y + dy));
            }
        }
        for (Map.Entry<BlockKey, Block> entry : blocks.entrySet()) {
            entry.getValue().mines = generateMines(entry.getKey().x, entry.getKey().y,
                    entry.getValue().mineCount);
        }
        firstReveal = false;
    }

    private Block getBlockForCell(int x, int y) {
        return getBlock(Math.floorDiv(x, BLOCK_SIZE), Math.floorDiv(y, BLOCK_SIZE));
    }

    private Block getBlock(int blockX, int blockY) {
        BlockKey key = new BlockKey(blockX, blockY);
        Block block = blocks.get(key);
        if (block == null) {
            int mineCount = getMineCountForBlock(blockX, blockY);
            block = new Block(generateMines(blockX, blockY, mineCount), mineCount);
            blocks.put(key, block);
        }
        return block;
    }

    private boolean[] generateMines(int blockX, int blockY, int mineCount) {
        List<Integer> candidates = new ArrayList<>(CELL_COUNT);
        int startX = blockX * BLOCK_SIZE;
        int startY = blockY * BLOCK_SIZE;
        for (int y = 0; y < BLOCK_SIZE; y++) {
            for (int x = 0; x < BLOCK_SIZE; x++) {
                if (!protectedCells.contains(pack(startX + x, startY + y))) {
                    candidates.add(y * BLOCK_SIZE + x);
                }
            }
        }
        Collections.shuffle(candidates, new Random(blockSeed(blockX, blockY)));
        boolean[] mines = new boolean[CELL_COUNT];
        for (int i = 0; i < mineCount; i++) {
            mines[candidates.get(i)] = true;
        }
        return mines;
    }

    private int getMineCountForBlock(int blockX, int blockY) {
        if (mode != GameMode.GAUNTLET) {
            return MINES_PER_BLOCK;
        }
        long distance = Math.max(Math.abs((long) blockX), Math.abs((long) blockY));
        return (int) Math.min(MAX_GAUNTLET_MINES, MINES_PER_BLOCK + distance);
    }

    private long blockSeed(int blockX, int blockY) {
        long value = seed;
        value ^= (long) blockX * 0x632BE59BD9B4E019L;
        value ^= (long) blockY * 0x9E3779B97F4A7C15L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static int localIndex(int x, int y) {
        return Math.floorMod(y, BLOCK_SIZE) * BLOCK_SIZE + Math.floorMod(x, BLOCK_SIZE);
    }

    private static long pack(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackY(long packed) {
        return (int) packed;
    }

    enum GameMode {
        SURVIVAL,
        EXPLORE,
        GAUNTLET
    }

    static final class RevealResult {
        int pointsAwarded;
        boolean mineHit;
        boolean changed;
        boolean extraLifeEarned;
        boolean extraLifeUsed;

        void add(RevealResult other) {
            pointsAwarded += other.pointsAwarded;
            mineHit |= other.mineHit;
            changed |= other.changed;
            extraLifeEarned |= other.extraLifeEarned;
            extraLifeUsed |= other.extraLifeUsed;
        }
    }

    private static final class Block {
        final boolean[] revealed = new boolean[CELL_COUNT];
        final boolean[] flagged = new boolean[CELL_COUNT];
        final int mineCount;
        boolean[] mines;
        int revealedSafeCells;
        boolean cleared;

        Block(boolean[] mines, int mineCount) {
            this.mines = mines;
            this.mineCount = mineCount;
        }
    }

    private static final class BlockKey {
        final int x;
        final int y;

        BlockKey(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof BlockKey)) {
                return false;
            }
            BlockKey key = (BlockKey) other;
            return x == key.x && y == key.y;
        }

        @Override
        public int hashCode() {
            return 31 * x + y;
        }
    }
}
