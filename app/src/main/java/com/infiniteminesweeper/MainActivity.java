package com.infiniteminesweeper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.AtomicFile;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class MainActivity extends Activity implements GameView.Listener {
    private static final String TAG = "InfiniteMinesweeper";
    private static final String PREFERENCES = "progress";
    private static final String SURVIVAL_BEST_SCORE = "best_score";
    private static final String EXPLORE_BEST_SCORE = "best_score_explore";
    private static final String GAUNTLET_BEST_SCORE = "best_score_gauntlet";
    private static final String HAPTICS_ENABLED = "haptics_enabled";
    private static final String STATE_FILE = "game_state.bin";
    private static final int STATE_MAGIC = 0x494D5346;
    private static final int STATE_VERSION = 2;

    private GameView gameView;
    private TextView scoreValue;
    private TextView blocksValue;
    private TextView flagsValue;
    private TextView livesLabel;
    private TextView livesValue;
    private TextView bestValue;
    private TextView bonusProgress;
    private SharedPreferences preferences;
    private Minefield.GameMode gameMode = Minefield.GameMode.SURVIVAL;
    private int bestScore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(11, 20, 36));
        getWindow().setNavigationBarColor(Color.rgb(11, 20, 36));
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.root);
        int initialLeft = root.getPaddingLeft();
        int initialTop = root.getPaddingTop();
        int initialRight = root.getPaddingRight();
        int initialBottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                    initialLeft + insets.getSystemWindowInsetLeft(),
                    initialTop + insets.getSystemWindowInsetTop(),
                    initialRight + insets.getSystemWindowInsetRight(),
                    initialBottom + insets.getSystemWindowInsetBottom());
            return insets;
        });

        preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        gameView = findViewById(R.id.gameView);
        scoreValue = findViewById(R.id.scoreValue);
        blocksValue = findViewById(R.id.blocksValue);
        flagsValue = findViewById(R.id.flagsValue);
        livesLabel = findViewById(R.id.livesLabel);
        livesValue = findViewById(R.id.livesValue);
        bestValue = findViewById(R.id.bestValue);
        bonusProgress = findViewById(R.id.bonusProgress);
        gameView.setHapticsEnabled(preferences.getBoolean(HAPTICS_ENABLED, true));
        loadGameState();
        bestScore = preferences.getInt(bestScoreKey(), 0);
        bestValue.setText(String.valueOf(bestScore));
        gameView.setListener(this);

        findViewById(R.id.newGameButton).setOnClickListener(view -> gameView.newGame());
        findViewById(R.id.settingsButton).setOnClickListener(view -> showSettingsDialog());
        findViewById(R.id.helpButton).setOnClickListener(view -> showHelpDialog());
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.rules_title)
                .setMessage(R.string.rules)
                .setPositiveButton(R.string.got_it, null)
                .show();
    }

    private void showSettingsDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_settings, null, false);
        RadioButton exploreOption = content.findViewById(R.id.exploreOption);
        RadioButton survivalOption = content.findViewById(R.id.survivalOption);
        RadioButton gauntletOption = content.findViewById(R.id.gauntletOption);
        CheckBox hapticsOption = content.findViewById(R.id.hapticsOption);
        if (gameMode == Minefield.GameMode.EXPLORE) {
            exploreOption.setChecked(true);
        } else if (gameMode == Minefield.GameMode.GAUNTLET) {
            gauntletOption.setChecked(true);
        } else {
            survivalOption.setChecked(true);
        }
        hapticsOption.setChecked(preferences.getBoolean(HAPTICS_ENABLED, true));
        content.findViewById(R.id.resetScoresButton).setOnClickListener(
                view -> showResetScoresDialog());
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.apply, (dialog, which) -> {
                    boolean hapticsEnabled = hapticsOption.isChecked();
                    preferences.edit().putBoolean(HAPTICS_ENABLED, hapticsEnabled).apply();
                    gameView.setHapticsEnabled(hapticsEnabled);
                    Minefield.GameMode selectedMode = exploreOption.isChecked()
                            ? Minefield.GameMode.EXPLORE : survivalOption.isChecked()
                            ? Minefield.GameMode.SURVIVAL : Minefield.GameMode.GAUNTLET;
                    if (selectedMode != gameMode) {
                        setGameMode(selectedMode);
                    }
                })
                .show();
    }

    private void showResetScoresDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.reset_scores_title)
                .setMessage(R.string.reset_scores_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.reset, (dialog, which) -> {
                    preferences.edit()
                            .remove(SURVIVAL_BEST_SCORE)
                            .remove(EXPLORE_BEST_SCORE)
                            .remove(GAUNTLET_BEST_SCORE)
                            .apply();
                    bestScore = 0;
                    bestValue.setText(R.string.zero);
                    Toast.makeText(this, R.string.scores_reset, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void setGameMode(Minefield.GameMode mode) {
        gameMode = mode;
        bestScore = preferences.getInt(bestScoreKey(), 0);
        bestValue.setText(String.valueOf(bestScore));
        gameView.newGame(mode);
    }

    private String bestScoreKey() {
        if (gameMode == Minefield.GameMode.EXPLORE) {
            return EXPLORE_BEST_SCORE;
        }
        if (gameMode == Minefield.GameMode.GAUNTLET) {
            return GAUNTLET_BEST_SCORE;
        }
        return SURVIVAL_BEST_SCORE;
    }

    private int modeNameResource() {
        if (gameMode == Minefield.GameMode.EXPLORE) {
            return R.string.explore;
        }
        if (gameMode == Minefield.GameMode.GAUNTLET) {
            return R.string.gauntlet;
        }
        return R.string.survival;
    }

    @Override
    public void onGameChanged(int score, int clearedBlocks, int flags, int extraLives, int mistakes,
                              int pointsAwarded, boolean mineHit, boolean extraLifeEarned,
                              boolean extraLifeUsed) {
        scoreValue.setText(String.valueOf(score));
        blocksValue.setText(String.valueOf(clearedBlocks));
        flagsValue.setText(String.valueOf(flags));
        String progress;
        if (gameMode == Minefield.GameMode.EXPLORE) {
            livesLabel.setText(R.string.mistakes);
            livesValue.setText(String.valueOf(mistakes));
            progress = getString(R.string.explore_progress);
        } else {
            livesLabel.setText(R.string.lives);
            livesValue.setText(String.valueOf(extraLives));
            int blocksRemaining = Minefield.BLOCKS_PER_EXTRA_LIFE
                    - clearedBlocks % Minefield.BLOCKS_PER_EXTRA_LIFE;
            progress = getResources().getQuantityString(
                    R.plurals.next_life, blocksRemaining, blocksRemaining);
        }
        bonusProgress.setText(getString(R.string.mode_progress,
                getString(modeNameResource()), progress));
        if (score > bestScore) {
            bestScore = score;
            bestValue.setText(String.valueOf(bestScore));
            preferences.edit().putInt(bestScoreKey(), bestScore).apply();
        }
        if (mineHit) {
            int message = gameMode == Minefield.GameMode.EXPLORE ? R.string.explore_mine_revealed
                    : extraLifeUsed ? R.string.extra_life_used : R.string.mine_hit;
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } else if (extraLifeEarned) {
            Toast.makeText(this, R.string.extra_life_earned, Toast.LENGTH_LONG).show();
        } else if (pointsAwarded > 0) {
            Toast.makeText(this, getString(R.string.block_cleared, pointsAwarded), Toast.LENGTH_SHORT)
                    .show();
        }
        persistGameState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        persistGameState();
    }

    private void loadGameState() {
        AtomicFile stateFile = new AtomicFile(new File(getFilesDir(), STATE_FILE));
        File backupFile = new File(getFilesDir(), STATE_FILE + ".bak");
        if (!stateFile.getBaseFile().exists() && !backupFile.exists()) {
            return;
        }
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(stateFile.openRead()))) {
            if (input.readInt() != STATE_MAGIC) {
                throw new IOException("Unsupported saved game");
            }
            int version = input.readInt();
            if (version < 1 || version > STATE_VERSION) {
                throw new IOException("Unsupported saved game version");
            }
            gameView.readState(input, version);
            gameMode = gameView.getGameMode();
        } catch (IOException | RuntimeException error) {
            Log.e(TAG, "Could not restore game", error);
            stateFile.delete();
            gameView.newGame(gameMode);
            Toast.makeText(this, R.string.restore_error, Toast.LENGTH_LONG).show();
        }
    }

    private void persistGameState() {
        AtomicFile stateFile = new AtomicFile(new File(getFilesDir(), STATE_FILE));
        FileOutputStream fileOutput = null;
        try {
            fileOutput = stateFile.startWrite();
            DataOutputStream output = new DataOutputStream(new BufferedOutputStream(fileOutput));
            output.writeInt(STATE_MAGIC);
            output.writeInt(STATE_VERSION);
            gameView.writeState(output);
            output.flush();
            stateFile.finishWrite(fileOutput);
        } catch (IOException error) {
            if (fileOutput != null) {
                stateFile.failWrite(fileOutput);
            }
            Log.e(TAG, "Could not save game", error);
        }
    }
}
