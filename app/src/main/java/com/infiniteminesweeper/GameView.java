package com.infiniteminesweeper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Random;

public final class GameView extends View {
    private static final int[] NUMBER_COLORS = {
            0, 0xFF70A7FF, 0xFF62D99B, 0xFFFF7379, 0xFFB9A0FF,
            0xFFFFA66E, 0xFF64DDE2, 0xFFF2F6FC, 0xFFAAB6C5
    };

    private final Paint tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path flagPath = new Path();
    private final RectF tileRect = new RectF();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ScaleGestureDetector scaleDetector;
    private final int touchSlop;
    private final int longPressTimeout;
    private final float density;

    private Minefield minefield = new Minefield(new Random().nextLong());
    private Minefield.GameMode gameMode = Minefield.GameMode.SURVIVAL;
    private Listener listener = Listener.NONE;
    private float cellSize;
    private float offsetX;
    private float offsetY;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private boolean initialized;
    private boolean viewportRestored;
    private boolean dragging;
    private boolean multiTouch;
    private boolean longPressTriggered;
    private boolean hapticsEnabled = true;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (!dragging && !multiTouch && !longPressTriggered) {
                longPressTriggered = true;
                toggleFlagAt(downX, downY);
                if (hapticsEnabled) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
            }
        }
    };

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        cellSize = dp(44);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        longPressTimeout = ViewConfiguration.getLongPressTimeout();
        setBackgroundColor(Color.rgb(11, 20, 36));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float focusX = detector.getFocusX();
                float focusY = detector.getFocusY();
                float worldX = (focusX - offsetX) / cellSize;
                float worldY = (focusY - offsetY) / cellSize;
                float newSize = clamp(cellSize * detector.getScaleFactor(), dp(12), dp(120));
                offsetX = focusX - worldX * newSize;
                offsetY = focusY - worldY * newSize;
                cellSize = newSize;
                invalidate();
                return true;
            }
        });
    }

    void setListener(Listener listener) {
        this.listener = listener == null ? Listener.NONE : listener;
        notifyListener(new Minefield.RevealResult());
    }

    void setHapticsEnabled(boolean enabled) {
        hapticsEnabled = enabled;
    }

    Minefield.GameMode getGameMode() {
        return gameMode;
    }

    void writeState(DataOutputStream output) throws IOException {
        minefield.writeState(output);
        output.writeFloat(cellSize);
        output.writeFloat(offsetX);
        output.writeFloat(offsetY);
    }

    void readState(DataInputStream input, int version) throws IOException {
        Minefield restoredMinefield = Minefield.readState(input, version);
        float restoredCellSize = input.readFloat();
        float restoredOffsetX = input.readFloat();
        float restoredOffsetY = input.readFloat();
        if (Float.isNaN(restoredCellSize) || Float.isInfinite(restoredCellSize)
                || Float.isNaN(restoredOffsetX) || Float.isInfinite(restoredOffsetX)
                || Float.isNaN(restoredOffsetY) || Float.isInfinite(restoredOffsetY)) {
            throw new IOException("Invalid saved viewport");
        }
        minefield = restoredMinefield;
        gameMode = restoredMinefield.getMode();
        cellSize = clamp(restoredCellSize, dp(12), dp(120));
        offsetX = restoredOffsetX;
        offsetY = restoredOffsetY;
        viewportRestored = true;
        invalidate();
    }

    void newGame() {
        newGame(gameMode);
    }

    void newGame(Minefield.GameMode mode) {
        gameMode = mode;
        minefield = new Minefield(new Random().nextLong(), gameMode);
        cellSize = dp(44);
        viewportRestored = false;
        centerViewport();
        invalidate();
        notifyListener(new Minefield.RevealResult());
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (!initialized) {
            initialized = true;
            if (!viewportRestored) {
                centerViewport();
            }
        } else {
            offsetX += (width - oldWidth) / 2f;
            offsetY += (height - oldHeight) / 2f;
        }
    }

    private void centerViewport() {
        offsetX = getWidth() / 2f - cellSize / 2f;
        offsetY = getHeight() / 2f - cellSize / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) {
            return;
        }
        int minX = (int) Math.floor(-offsetX / cellSize) - 1;
        int minY = (int) Math.floor(-offsetY / cellSize) - 1;
        int maxX = (int) Math.ceil((getWidth() - offsetX) / cellSize) + 1;
        int maxY = (int) Math.ceil((getHeight() - offsetY) / cellSize) + 1;
        float gap = Math.max(1f, cellSize * 0.035f);
        textPaint.setTextSize(cellSize * 0.48f);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float left = offsetX + x * cellSize;
                float top = offsetY + y * cellSize;
                tileRect.set(left + gap / 2f, top + gap / 2f,
                        left + cellSize - gap / 2f, top + cellSize - gap / 2f);
                drawCell(canvas, x, y, tileRect);
            }
        }
        drawBlockBorders(canvas, minX, minY, maxX, maxY);
        if (minefield.isGameOver()) {
            drawGameOver(canvas);
        }
    }

    private void drawCell(Canvas canvas, int x, int y, RectF rect) {
        boolean revealed = minefield.isRevealed(x, y);
        boolean mine = minefield.isMine(x, y);
        boolean exploded = minefield.isExplodedCell(x, y);
        if (revealed) {
            tilePaint.setColor(exploded ? 0xFFB53D4A : 0xFF142238);
        } else {
            int blockX = Math.floorDiv(x, Minefield.BLOCK_SIZE);
            int blockY = Math.floorDiv(y, Minefield.BLOCK_SIZE);
            boolean alternate = Math.floorMod(blockX + blockY, 2) == 0;
            tilePaint.setColor(alternate ? 0xFF2A405B : 0xFF263A53);
        }
        canvas.drawRoundRect(rect, cellSize * 0.1f, cellSize * 0.1f, tilePaint);

        float centerX = rect.centerX();
        float centerY = rect.centerY();
        if (revealed && !mine) {
            int count = minefield.getAdjacentMineCount(x, y);
            if (count > 0) {
                textPaint.setColor(NUMBER_COLORS[count]);
                Paint.FontMetrics metrics = textPaint.getFontMetrics();
                float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
                canvas.drawText(String.valueOf(count), centerX, baseline, textPaint);
            }
        } else if (minefield.isFlagged(x, y)) {
            drawFlag(canvas, centerX, centerY);
            if (minefield.isGameOver() && !mine) {
                iconPaint.setColor(0xFFFF7379);
                iconPaint.setStrokeWidth(cellSize * 0.07f);
                canvas.drawLine(rect.left + cellSize * 0.2f, rect.top + cellSize * 0.2f,
                        rect.right - cellSize * 0.2f, rect.bottom - cellSize * 0.2f, iconPaint);
                canvas.drawLine(rect.right - cellSize * 0.2f, rect.top + cellSize * 0.2f,
                        rect.left + cellSize * 0.2f, rect.bottom - cellSize * 0.2f, iconPaint);
            }
        } else if (mine && (revealed || minefield.isGameOver())) {
            drawMine(canvas, centerX, centerY, exploded);
        }
    }

    private void drawFlag(Canvas canvas, float centerX, float centerY) {
        float unit = cellSize;
        iconPaint.setColor(0xFFFF7379);
        iconPaint.setStyle(Paint.Style.FILL);
        flagPath.reset();
        flagPath.moveTo(centerX - unit * 0.02f, centerY - unit * 0.25f);
        flagPath.lineTo(centerX - unit * 0.27f, centerY - unit * 0.12f);
        flagPath.lineTo(centerX - unit * 0.02f, centerY + unit * 0.01f);
        flagPath.close();
        canvas.drawPath(flagPath, iconPaint);
        iconPaint.setColor(0xFFF2F6FC);
        iconPaint.setStrokeWidth(Math.max(2f, unit * 0.045f));
        canvas.drawLine(centerX, centerY - unit * 0.27f,
                centerX, centerY + unit * 0.24f, iconPaint);
        canvas.drawLine(centerX - unit * 0.17f, centerY + unit * 0.24f,
                centerX + unit * 0.17f, centerY + unit * 0.24f, iconPaint);
    }

    private void drawMine(Canvas canvas, float centerX, float centerY, boolean exploded) {
        float radius = cellSize * 0.19f;
        iconPaint.setColor(exploded ? 0xFFFFFFFF : 0xFFAAB6C5);
        iconPaint.setStrokeWidth(Math.max(2f, cellSize * 0.04f));
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            canvas.drawLine(
                    centerX + (float) Math.cos(angle) * radius * 0.7f,
                    centerY + (float) Math.sin(angle) * radius * 0.7f,
                    centerX + (float) Math.cos(angle) * radius * 1.55f,
                    centerY + (float) Math.sin(angle) * radius * 1.55f,
                    iconPaint);
        }
        canvas.drawCircle(centerX, centerY, radius, iconPaint);
        tilePaint.setColor(exploded ? 0xFFB53D4A : 0xFF2A405B);
        canvas.drawCircle(centerX - radius * 0.3f, centerY - radius * 0.3f,
                radius * 0.22f, tilePaint);
    }

    private void drawBlockBorders(Canvas canvas, int minX, int minY, int maxX, int maxY) {
        int minBlockX = Math.floorDiv(minX, Minefield.BLOCK_SIZE);
        int minBlockY = Math.floorDiv(minY, Minefield.BLOCK_SIZE);
        int maxBlockX = Math.floorDiv(maxX, Minefield.BLOCK_SIZE);
        int maxBlockY = Math.floorDiv(maxY, Minefield.BLOCK_SIZE);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(Math.max(2f, density * 1.5f));
        for (int blockY = minBlockY; blockY <= maxBlockY; blockY++) {
            for (int blockX = minBlockX; blockX <= maxBlockX; blockX++) {
                float left = offsetX + blockX * Minefield.BLOCK_SIZE * cellSize;
                float top = offsetY + blockY * Minefield.BLOCK_SIZE * cellSize;
                float size = Minefield.BLOCK_SIZE * cellSize;
                linePaint.setColor(minefield.isBlockCleared(blockX, blockY)
                        ? 0xFF63D7C6 : 0xFF56718E);
                canvas.drawRect(left, top, left + size, top + size, linePaint);
            }
        }
    }

    private void drawGameOver(Canvas canvas) {
        canvas.drawColor(0x73050A12);
        float width = Math.min(getWidth() - dp(36), dp(310));
        float height = dp(128);
        float left = (getWidth() - width) / 2f;
        float top = (getHeight() - height) / 2f;
        tilePaint.setColor(0xFF1B2A40);
        canvas.drawRoundRect(left, top, left + width, top + height, dp(18), dp(18), tilePaint);
        textPaint.setColor(0xFFFF7379);
        textPaint.setTextSize(dp(27));
        canvas.drawText("GAME OVER", getWidth() / 2f, top + dp(48), textPaint);
        textPaint.setColor(0xFFF2F6FC);
        textPaint.setTextSize(dp(15));
        canvas.drawText(String.format(Locale.getDefault(), "Score: %d  ·  Blocks: %d",
                minefield.getScore(), minefield.getClearedBlocks()), getWidth() / 2f, top + dp(80), textPaint);
        textPaint.setColor(0xFF91A4BC);
        textPaint.setTextSize(dp(13));
        canvas.drawText("Tap New game for another run", getWidth() / 2f, top + dp(105), textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                dragging = false;
                multiTouch = false;
                longPressTriggered = false;
                handler.postDelayed(longPressRunnable, longPressTimeout);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                multiTouch = true;
                dragging = true;
                handler.removeCallbacks(longPressRunnable);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!multiTouch && !scaleDetector.isInProgress()) {
                    float distanceX = event.getX() - downX;
                    float distanceY = event.getY() - downY;
                    if (!dragging && Math.hypot(distanceX, distanceY) > touchSlop) {
                        dragging = true;
                        handler.removeCallbacks(longPressRunnable);
                    }
                    if (dragging) {
                        offsetX += event.getX() - lastX;
                        offsetY += event.getY() - lastY;
                        invalidate();
                    }
                }
                lastX = event.getX();
                lastY = event.getY();
                return true;
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPressRunnable);
                if (!multiTouch && !dragging && !longPressTriggered && !scaleDetector.isInProgress()) {
                    performClick();
                    handleTap(event.getX(), event.getY());
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressRunnable);
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void handleTap(float screenX, float screenY) {
        int x = screenToCell(screenX, offsetX);
        int y = screenToCell(screenY, offsetY);
        Minefield.RevealResult result = minefield.revealOrChord(x, y);
        if (result.changed) {
            invalidate();
            notifyListener(result);
        }
    }

    private void toggleFlagAt(float screenX, float screenY) {
        int x = screenToCell(screenX, offsetX);
        int y = screenToCell(screenY, offsetY);
        if (minefield.toggleFlag(x, y)) {
            invalidate();
            notifyListener(new Minefield.RevealResult());
        }
    }

    private int screenToCell(float screen, float offset) {
        return (int) Math.floor((screen - offset) / cellSize);
    }

    private void notifyListener(Minefield.RevealResult result) {
        listener.onGameChanged(minefield.getScore(), minefield.getClearedBlocks(),
                minefield.getFlagCount(), minefield.getExtraLives(), minefield.getMistakeCount(),
                result.pointsAwarded, result.mineHit, result.extraLifeEarned, result.extraLifeUsed);
    }

    private float dp(float value) {
        return value * density;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    interface Listener {
        Listener NONE = (score, clearedBlocks, flags, extraLives, mistakes, pointsAwarded, mineHit,
                         extraLifeEarned, extraLifeUsed) -> { };

        void onGameChanged(int score, int clearedBlocks, int flags, int extraLives, int mistakes,
                           int pointsAwarded, boolean mineHit, boolean extraLifeEarned,
                           boolean extraLifeUsed);
    }
}
