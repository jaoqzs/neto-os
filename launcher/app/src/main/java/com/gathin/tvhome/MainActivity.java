package com.gathin.tvhome;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 * Includes Apple TV Control Center pill and balloon panel.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private View fullScreenHint;
    private DockDecoration dockDeco;
    private boolean isFullScreen = false;
    private int dockOffset = 0;
    private boolean heroShown;
    private TextView greeting;

    // Top capsule bar controls
    private TextView capsuleClock;
    private TextView capsuleDate;
    private View btnCapsuleHeadphones;
    private View btnControlCenterToggle;
    private View btnProfileAvatar;

    private final SimpleDateFormat timeFormat24 = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat timeFormat12 = new SimpleDateFormat("h:mm", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, d MMM", new Locale("pt", "BR"));

    private final Runnable secondClockTicker = new Runnable() {
        @Override
        public void run() {
            updateRealTimeClock();
            long now = System.currentTimeMillis();
            long nextTick = 1000L - (now % 1000L);
            handler.postDelayed(this, nextTick);
        }
    };

    private final BroadcastReceiver timeChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            timeFormat24.setTimeZone(TimeZone.getDefault());
            timeFormat12.setTimeZone(TimeZone.getDefault());
            dateFormat.setTimeZone(TimeZone.getDefault());
            updateRealTimeClock();
        }
    };

    private void updateRealTimeClock() {
        Date now = new Date();
        boolean is24 = DateFormat.is24HourFormat(this);
        if (capsuleClock != null) {
            capsuleClock.setText(is24 ? timeFormat24.format(now) : timeFormat12.format(now));
        }
        if (capsuleDate != null) {
            capsuleDate.setText(dateFormat.format(now));
        }
    }

    // Control Center balloon views
    private View ccScrim;
    private LinearLayout controlCenterPanel;
    private View panelControlCenter;
    private View panelAudio;
    private View tilePower, tileWifi, tileDnd, tileBluetooth, tileScreensaver;
    private View btnQuickGame, btnQuickAccessibility, btnQuickLock, btnQuickSearch;
    private TextView textPowerTitle, textWifiTitle, textWifiSub, textDndTitle, textAudioTitle, textSleepTitle;

    // Audio panel views
    private View tileAudioHeader, tileNoiseControl, tileConversation, tileSpatialAudio, tileShareAudio;
    private TextView textNoiseSub, textConversationSub, textSpatialSub;

    private static final int TAB_NONE = 0;
    private static final int TAB_CONTROL_CENTER = 1;
    private static final int TAB_AUDIO = 2;
    private int currentTab = TAB_NONE;

    private boolean isDndEnabled = false;
    private int noiseModeIndex = 0;
    private final String[] noiseModes = new String[]{"Adaptive", "Desativado", "Cancelamento Ativo"};
    private boolean conversationOn = true;
    private int spatialModeIndex = 0;
    private final String[] spatialModes = new String[]{"Head Tracked", "Fixed", "Off"};

    // Static so they survive the activity being destroyed while another app is open
    private static int lastFocused = 0;
    private static String lastFocusedKey;
    private float density;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateWifiLabel();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        COLUMNS = Math.max(1, getResources().getInteger(R.integer.columns));
        liveTvPkg = getString(R.string.live_tv_package).trim();

        root = findViewById(R.id.root);
        fullScreenHint = findViewById(R.id.fullScreenHint);
        if (fullScreenHint != null) {
            fullScreenHint.setOnClickListener(v -> enterFullScreen(COLUMNS));
        }
        greeting = null;
        btnCapsuleHeadphones = null;
        btnControlCenterToggle = findViewById(R.id.btnControlCenterToggle);
        btnProfileAvatar = findViewById(R.id.btnProfileAvatar);
        ImageView pillAvatar = findViewById(R.id.pillAvatar);

        // Circular clipping for avatar image
        ViewOutlineProvider circleOutline = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        };
        if (pillAvatar != null) {
            pillAvatar.setOutlineProvider(circleOutline);
            pillAvatar.setClipToOutline(true);
        }

        TextView capsuleUserName = findViewById(R.id.capsuleUserName);
        if (capsuleUserName != null) {
            capsuleUserName.setText("Olá, João");
        }

        capsuleClock = findViewById(R.id.capsuleClock);
        capsuleDate = findViewById(R.id.capsuleDate);
        if (capsuleClock != null) {
            capsuleClock.setOnClickListener(v -> open(new Intent(Settings.ACTION_DATE_SETTINGS)));
        }

        IntentFilter timeFilter = new IntentFilter();
        timeFilter.addAction(Intent.ACTION_TIME_TICK);
        timeFilter.addAction(Intent.ACTION_TIME_CHANGED);
        timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeChangeReceiver, timeFilter);

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        dockDeco = new DockDecoration(COLUMNS, density);
        grid.addItemDecoration(dockDeco);
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        adapter = new AppAdapter(this, this, COLUMNS);
        grid.setAdapter(adapter);

        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            int dockRowHeight = (int) ((101 + 16 + 18) * density);
            int targetY = Math.max(0, h - dockRowHeight - (int) (24 * density));
            dockOffset = Math.max(0, targetY - (int) (16 * density));
            if (!isFullScreen) {
                grid.setTranslationY(dockOffset);
                setNonDockAlpha(0f);
            }
        });

        // Setup Control Center & Audio elements
        ccScrim = findViewById(R.id.ccScrim);
        controlCenterPanel = findViewById(R.id.controlCenterPanel);
        panelControlCenter = findViewById(R.id.panelControlCenter);
        panelAudio = findViewById(R.id.panelAudio);

        // Control Center tiles
        tilePower = findViewById(R.id.tilePower);
        tileWifi = findViewById(R.id.tileWifi);
        tileDnd = findViewById(R.id.tileDnd);
        tileBluetooth = findViewById(R.id.tileBluetooth);
        tileScreensaver = findViewById(R.id.tileScreensaver);
        textPowerTitle = findViewById(R.id.textPowerTitle);
        textWifiTitle = findViewById(R.id.textWifiTitle);
        textWifiSub = findViewById(R.id.textWifiSub);
        textDndTitle = findViewById(R.id.textDndTitle);
        textAudioTitle = findViewById(R.id.textAudioTitle);
        textSleepTitle = findViewById(R.id.textSleepTitle);

        btnQuickGame = findViewById(R.id.btnQuickGame);
        btnQuickAccessibility = findViewById(R.id.btnQuickAccessibility);
        btnQuickLock = findViewById(R.id.btnQuickLock);
        btnQuickSearch = findViewById(R.id.btnQuickSearch);

        // Audio panel views
        tileAudioHeader = findViewById(R.id.tileAudioHeader);
        tileNoiseControl = findViewById(R.id.tileNoiseControl);
        tileConversation = findViewById(R.id.tileConversation);
        tileSpatialAudio = findViewById(R.id.tileSpatialAudio);
        tileShareAudio = findViewById(R.id.tileShareAudio);
        textNoiseSub = findViewById(R.id.textNoiseSub);
        textConversationSub = findViewById(R.id.textConversationSub);
        textSpatialSub = findViewById(R.id.textSpatialSub);

        // Click listeners on capsule buttons
        if (btnCapsuleHeadphones != null) {
            btnCapsuleHeadphones.setOnClickListener(v -> openTab(TAB_AUDIO));
        }
        if (btnControlCenterToggle != null) {
            btnControlCenterToggle.setOnClickListener(v -> openTab(TAB_CONTROL_CENTER));
        }
        if (btnProfileAvatar != null) {
            btnProfileAvatar.setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS)));
        }

        if (ccScrim != null) ccScrim.setOnClickListener(v -> closeControlCenter());
        if (tilePower != null) tilePower.setOnClickListener(v -> triggerSleep());
        if (tileWifi != null) tileWifi.setOnClickListener(v -> {
            closeControlCenter();
            open(new Intent(Settings.ACTION_WIFI_SETTINGS));
        });
        if (tileDnd != null) tileDnd.setOnClickListener(v -> toggleDnd());
        if (tileBluetooth != null) tileBluetooth.setOnClickListener(v -> {
            closeControlCenter();
            open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        });
        if (tileScreensaver != null) tileScreensaver.setOnClickListener(v -> {
            closeControlCenter();
            startScreensaver();
        });

        if (tilePower != null) tilePower.setOnFocusChangeListener((v, hasFocus) -> {
            if (textPowerTitle != null) textPowerTitle.setTextColor(hasFocus ? Color.BLACK : 0xCCFFFFFF);
        });
        if (tileWifi != null) tileWifi.setOnFocusChangeListener((v, hasFocus) -> {
            if (textWifiTitle != null) textWifiTitle.setTextColor(hasFocus ? Color.BLACK : 0xCCFFFFFF);
            if (textWifiSub != null) textWifiSub.setTextColor(hasFocus ? 0xFF8E8E93 : 0x99FFFFFF);
        });
        if (tileDnd != null) tileDnd.setOnFocusChangeListener((v, hasFocus) -> {
            if (textDndTitle != null) textDndTitle.setTextColor(hasFocus ? Color.BLACK : 0xCCFFFFFF);
        });
        if (tileBluetooth != null) tileBluetooth.setOnFocusChangeListener((v, hasFocus) -> {
            if (textAudioTitle != null) textAudioTitle.setTextColor(hasFocus ? Color.BLACK : 0xCCFFFFFF);
        });
        if (tileScreensaver != null) tileScreensaver.setOnFocusChangeListener((v, hasFocus) -> {
            if (textSleepTitle != null) textSleepTitle.setTextColor(hasFocus ? Color.BLACK : 0xCCFFFFFF);
        });

        // Quick squircle buttons
        if (btnQuickGame != null) btnQuickGame.setOnClickListener(v -> {
            closeControlCenter();
            Intent i = getPackageManager().getLeanbackLaunchIntentForPackage("com.google.android.play.games");
            if (i != null) open(i);
            else open(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        });
        if (btnQuickAccessibility != null) btnQuickAccessibility.setOnClickListener(v -> {
            closeControlCenter();
            open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        if (btnQuickLock != null) btnQuickLock.setOnClickListener(v -> {
            closeControlCenter();
            open(new Intent(Settings.ACTION_SECURITY_SETTINGS));
        });
        if (btnQuickSearch != null) btnQuickSearch.setOnClickListener(v -> {
            closeControlCenter();
            try {
                startActivity(new Intent(Intent.ACTION_ASSIST));
            } catch (Exception e) {
                open(new Intent(Settings.ACTION_SEARCH_SETTINGS));
            }
        });

        // Audio panel tile actions
        if (tileAudioHeader != null) tileAudioHeader.setOnClickListener(v -> {
            closeControlCenter();
            open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        });
        if (tileNoiseControl != null) tileNoiseControl.setOnClickListener(v -> {
            noiseModeIndex = (noiseModeIndex + 1) % noiseModes.length;
            if (textNoiseSub != null) textNoiseSub.setText(noiseModes[noiseModeIndex]);
        });
        if (tileConversation != null) tileConversation.setOnClickListener(v -> {
            conversationOn = !conversationOn;
            if (textConversationSub != null) textConversationSub.setText(conversationOn ? "On" : "Off");
        });
        if (tileSpatialAudio != null) tileSpatialAudio.setOnClickListener(v -> {
            spatialModeIndex = (spatialModeIndex + 1) % spatialModes.length;
            if (textSpatialSub != null) textSpatialSub.setText(spatialModes[spatialModeIndex]);
        });
        if (tileShareAudio != null) tileShareAudio.setOnClickListener(v -> {
            closeControlCenter();
            open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        });

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(networkCallback);
    }

    // ---- Control Center methods ------------------------------------------------------------------

    private boolean isControlCenterOpen() {
        return controlCenterPanel != null && controlCenterPanel.getVisibility() == View.VISIBLE;
    }

    private void openTab(int tab) {
        if (controlCenterPanel == null) return;
        if (isControlCenterOpen() && currentTab == tab) {
            closeControlCenter();
            return;
        }

        currentTab = tab;
        boolean wasOpen = isControlCenterOpen();

        if (!wasOpen) {
            controlCenterPanel.setVisibility(View.VISIBLE);
            controlCenterPanel.setPivotY(0f);
            controlCenterPanel.setScaleX(0.96f);
            controlCenterPanel.setScaleY(0.96f);
            controlCenterPanel.setTranslationY(-12f * density);
            controlCenterPanel.setAlpha(0f);
            controlCenterPanel.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(220)
                    .setInterpolator(new DecelerateInterpolator(2.0f))
                    .start();

            if (ccScrim != null) {
                ccScrim.setVisibility(View.VISIBLE);
                ccScrim.setAlpha(0f);
                ccScrim.animate().alpha(1f).setDuration(220).start();
            }
        } else {
            controlCenterPanel.setAlpha(0.6f);
            controlCenterPanel.animate().alpha(1f).setDuration(160).start();
        }

        if (tab == TAB_CONTROL_CENTER) {
            if (btnControlCenterToggle != null) {
                btnControlCenterToggle.setActivated(true);
                btnControlCenterToggle.setNextFocusDownId(R.id.tilePower);
            }
            if (btnCapsuleHeadphones != null) {
                btnCapsuleHeadphones.setActivated(false);
                btnCapsuleHeadphones.setNextFocusDownId(R.id.tilePower);
            }
            if (panelControlCenter != null) panelControlCenter.setVisibility(View.VISIBLE);
            if (panelAudio != null) panelAudio.setVisibility(View.GONE);
            if (tilePower != null) {
                tilePower.post(() -> tilePower.requestFocus());
            }
        } else if (tab == TAB_AUDIO) {
            if (btnCapsuleHeadphones != null) {
                btnCapsuleHeadphones.setActivated(true);
                btnCapsuleHeadphones.setNextFocusDownId(R.id.tileAudioHeader);
            }
            if (btnControlCenterToggle != null) {
                btnControlCenterToggle.setActivated(false);
                btnControlCenterToggle.setNextFocusDownId(R.id.tileAudioHeader);
            }
            if (panelControlCenter != null) panelControlCenter.setVisibility(View.GONE);
            if (panelAudio != null) panelAudio.setVisibility(View.VISIBLE);
            if (tileAudioHeader != null) {
                tileAudioHeader.post(() -> tileAudioHeader.requestFocus());
            }
        }
    }

    private void closeControlCenter() {
        if (!isControlCenterOpen()) return;
        final int previousTab = currentTab;
        currentTab = TAB_NONE;
        if (btnControlCenterToggle != null) {
            btnControlCenterToggle.setActivated(false);
            btnControlCenterToggle.setNextFocusDownId(R.id.grid);
        }
        if (btnCapsuleHeadphones != null) {
            btnCapsuleHeadphones.setActivated(false);
            btnCapsuleHeadphones.setNextFocusDownId(R.id.grid);
        }

        controlCenterPanel.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .translationY(-12f * density)
                .alpha(0f)
                .setDuration(180)
                .setInterpolator(new AccelerateInterpolator(2.0f))
                .withEndAction(() -> {
                    controlCenterPanel.setVisibility(View.GONE);
                    if (previousTab == TAB_AUDIO && btnCapsuleHeadphones != null) {
                        btnCapsuleHeadphones.requestFocus();
                    } else if (btnControlCenterToggle != null) {
                        btnControlCenterToggle.requestFocus();
                    }
                })
                .start();

        if (ccScrim != null) {
            ccScrim.animate().alpha(0f).setDuration(180).withEndAction(() -> ccScrim.setVisibility(View.GONE)).start();
        }
    }

    private void toggleDnd() {
        isDndEnabled = !isDndEnabled;
        if (textDndTitle != null) {
            textDndTitle.setText(isDndEnabled ? "Do Not\nDisturb (On)" : "Do Not\nDisturb");
        }
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.isNotificationPolicyAccessGranted()) {
                nm.setInterruptionFilter(isDndEnabled ? NotificationManager.INTERRUPTION_FILTER_NONE : NotificationManager.INTERRUPTION_FILTER_ALL);
            }
        } catch (Exception ignored) {}
    }

    private void triggerSleep() {
        closeControlCenter();
        new Thread(() -> {
            try {
                Runtime.getRuntime().exec(new String[]{"input", "keyevent", "KEYCODE_SLEEP"}).waitFor();
            } catch (Exception e) {
                try {
                    Runtime.getRuntime().exec(new String[]{"input", "keyevent", "KEYCODE_POWER"});
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void startScreensaver() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(getPackageName(), "com.gathin.tvhome.ClockDream");
            startActivity(intent);
        } catch (Exception e) {
            open(new Intent(Settings.ACTION_DREAM_SETTINGS));
        }
    }

    private void updateCardTextColors(View root, int titleColor, int subColor) {
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                updateCardTextColors(group.getChildAt(i), titleColor, subColor);
            }
        } else if (root instanceof TextView) {
            TextView tv = (TextView) root;
            if (tv.getId() == R.id.textWifiSub) {
                tv.setTextColor(subColor);
            } else {
                tv.setTextColor(titleColor);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        heroShown = false;
        isFullScreen = false;
        if (adapter != null) adapter.setFullScreen(false);
        grid.setTranslationY(dockOffset);
        setNonDockAlpha(0f);
        if (dockDeco != null) dockDeco.setBandAlpha(1f);
        if (fullScreenHint != null) fullScreenHint.setAlpha(1f);
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        adapter.reload();
        grid.scheduleLayoutAnimation();
        updateGreeting();
        updateWifiLabel();
        handler.removeCallbacks(secondClockTicker);
        handler.post(secondClockTicker);
        handler.postDelayed(minuteTick, MINUTE_MS);

        grid.post(() -> {
            int pos = adapter.positionOf(returnKey);
            if (pos >= COLUMNS) pos = 0;
            focusTile(pos >= 0 ? pos : returnSlot);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(secondClockTicker);
        handler.removeCallbacks(minuteTick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(timeChangeReceiver); } catch (Exception ignored) {}
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.unregisterNetworkCallback(networkCallback);
    }

    @Override
    public void onBackPressed() {
        if (isControlCenterOpen()) {
            closeControlCenter();
            return;
        }
        if (isFullScreen) {
            exitFullScreen();
            return;
        }
        // Home screen: Back goes nowhere.
    }

    @Override
    public void onTileFocused(int position, View card) {
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);

        if (fullScreenHint != null) {
            if (!isFullScreen && position < COLUMNS) {
                fullScreenHint.animate().alpha(1f).setDuration(200).start();
            } else {
                fullScreenHint.animate().alpha(0f).setDuration(160).start();
            }
        }
    }

    private void setNonDockAlpha(float alpha) {
        if (grid == null) return;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            int pos = grid.getChildAdapterPosition(child);
            if (pos >= COLUMNS) {
                child.setAlpha(alpha);
            }
        }
    }

    private void enterFullScreen(int targetPos) {
        if (isFullScreen) {
            focusTile(targetPos);
            return;
        }
        isFullScreen = true;
        if (adapter != null) adapter.setFullScreen(true);
        if (dockDeco != null) {
            dockDeco.setBandAlpha(0f);
            grid.invalidate();
        }
        if (fullScreenHint != null) {
            fullScreenHint.animate().alpha(0f).setDuration(160).start();
        }
        setNonDockAlpha(1f);
        grid.animate()
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .withEndAction(() -> focusTile(targetPos))
                .start();
    }

    private void exitFullScreen() {
        if (!isFullScreen) return;
        isFullScreen = false;
        if (adapter != null) adapter.setFullScreen(false);
        if (dockDeco != null) {
            dockDeco.setBandAlpha(1f);
            grid.invalidate();
        }
        if (fullScreenHint != null) {
            fullScreenHint.animate().alpha(1f).setDuration(240).start();
        }
        int dockTarget = Math.max(0, Math.min(lastFocused % COLUMNS, COLUMNS - 1));
        grid.scrollToPosition(0);
        grid.animate()
                .translationY(dockOffset)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .withEndAction(() -> {
                    setNonDockAlpha(0f);
                    focusTile(dockTarget);
                })
                .start();
    }

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.getItemCount();
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (position < COLUMNS && !isFullScreen) {
                int target = Math.min(position + COLUMNS, count - 1);
                enterFullScreen(target);
                return true;
            }
            int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
            if (position >= lastRowStart) return true;
            int target = Math.min(position + COLUMNS, count - 1);
            focusTile(target);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) {
                if (isFullScreen) {
                    exitFullScreen();
                    return true;
                }
                if (btnControlCenterToggle != null) {
                    btnControlCenterToggle.requestFocus();
                    return true;
                }
                return false;
            }
            int target = position - COLUMNS;
            focusTile(target);
            return true;
        }
        return false;
    }

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) return;
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            View card = vh.itemView.findViewById(R.id.card);
            if (card.isFocused()) onTileFocused(pos, card);
            else card.requestFocus();
            return;
        }
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
                if (target != null) target.requestFocus();
            }
        });
        grid.smoothScrollToPosition(pos);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        if (greeting != null) greeting.setText(res);
    }

    /** Shows the Wi-Fi network name on the status pill and control center. */
    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm != null ? cm.getActiveNetwork() : null;
            NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
            if (caps == null) {
                text = getString(R.string.offline);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = getString(R.string.ethernet);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        if (textWifiSub != null) textWifiSub.setText(text);
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    /** Dropdown menu under Inputs: HDMI ports, AV and Live TV. */
    private void showInputs() {
        View anchor = btnControlCenterToggle != null ? btnControlCenterToggle : root;
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = Math.max(anchor.getWidth(), (int) (180 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(8f * density);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        String tunerId = null;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            int hdmiPorts = 0, hdmiConnected = 0;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
                hdmiPorts++;
                if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
            }
            boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;
            for (TvInputInfo info : inputs) {
                if (info.getType() == TvInputInfo.TYPE_TUNER
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (tunerId == null || info.getId().compareTo(tunerId) < 0)) {
                    tunerId = info.getId();
                }
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase().contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = hdmi && hdmiStateBogus ? -1 : tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                                TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")));
            }
        }
        Intent liveTv = liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (liveTv != null && tunerId != null) {
            liveTv = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(tunerId), "vnd.android.cursor.item/channel")
                    .setPackage(liveTvPkg);
        }
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            final Intent liveTvIntent = liveTv;
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTvIntent));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max(width, panel.getMeasuredWidth()));

        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.START);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }
}
