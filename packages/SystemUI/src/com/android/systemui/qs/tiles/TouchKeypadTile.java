/*
 * Copyright (C) 2020-2025 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.tiles;

import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.provider.Settings;
import android.service.quicksettings.Tile;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.res.R;

import org.lineageos.internal.logging.LineageMetricsLogger;

import javax.inject.Inject;

public class TouchKeypadTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "touchkeypad";

    private static final String PROP_SCROLLING_DISABLED =
            "persist.vendor.touchkeypad.scrolling_disabled";
    private static final String PROP_TOUCHPAD_POWER_DISABLED =
            "persist.vendor.touchkeypad.disabled";
    private static final String SETTING_TOUCHPAD_ENABLED =
            "keyboard_touchpad_enabled";

    private final ContentObserver mTouchpadPowerObserver;
    private boolean mTouchpadPowerObserverRegistered;

    @Inject
    public TouchKeypadTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger
    ) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);

        // Use the tile's worker handler so post-boot SystemUI main-thread
        // activity does not unnecessarily delay the tile refresh.
        mTouchpadPowerObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                refreshState();
            }
        };
    }

    private boolean isScrollingEnabled() {
        return !"1".equals(SystemProperties.get(PROP_SCROLLING_DISABLED, "0"));
    }

    private boolean isTouchpadPowered() {
        return !SystemProperties.getBoolean(PROP_TOUCHPAD_POWER_DISABLED, false);
    }

    @Override
    public boolean isAvailable() {
        // Only return true if device is Athena, since Luna does not have capacitive keyboard.
        String device = android.os.SystemProperties.get("ro.product.device", "");
        String sysName = android.os.SystemProperties.get("ro.product.system.name", "");
        String linDev = android.os.SystemProperties.get("ro.lineage.device", "");
        return "bbf100".equalsIgnoreCase(device)
               || "athena".equalsIgnoreCase(device)
               || "lineage_athena".equalsIgnoreCase(sysName)
               || "athena".equalsIgnoreCase(linDev);
    }

    @Override
    public BooleanState newTileState() {
        BooleanState state = new BooleanState();
        state.handlesLongClick = true;
        return state;
    }

    @Override
    public void handleClick(@Nullable Expandable expandable) {
        // The scrolling setting cannot be changed while the capacitive
        // keyboard hardware is powered off.
        if (!isTouchpadPowered()) {
            return;
        }

        boolean newEnabled = !isScrollingEnabled();
        SystemProperties.set(PROP_SCROLLING_DISABLED, newEnabled ? "0" : "1");
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent().setClassName(
                "com.blackberry.settings",
                "com.blackberry.settings.DeviceSettingsActivity");
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_touchkeypad_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final boolean touchpadPowered = isTouchpadPowered();
        final boolean scrollingEnabled = isScrollingEnabled();

        state.icon = ResourceIcon.get(touchpadPowered
                ? R.drawable.ic_qs_touchkeypad
                : R.drawable.ic_qs_touchkeypad_unavailable);
        state.hasLongClickEffect = true;
        state.value = touchpadPowered && scrollingEnabled;
        state.label = mContext.getString(R.string.quick_settings_touchkeypad_label);
        state.contentDescription = state.label;

        if (!touchpadPowered) {
            state.state = Tile.STATE_UNAVAILABLE;
            state.secondaryLabel = mContext.getString(
                    R.string.quick_settings_touchkeypad_unavailable);
            state.stateDescription = state.secondaryLabel;
        } else {
            state.state = scrollingEnabled
                    ? Tile.STATE_ACTIVE
                    : Tile.STATE_INACTIVE;
            state.secondaryLabel = null;
            state.stateDescription = state.state == Tile.STATE_INACTIVE
                    ? ""
                    : null;
        }
    }

    @Override
    public int getMetricsCategory() {
        return LineageMetricsLogger.TILE_POWERSHARE;
    }

    @Override
    public void handleSetListening(boolean listening) {
        super.handleSetListening(listening);

        if (listening && !mTouchpadPowerObserverRegistered) {
            mContext.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(SETTING_TOUCHPAD_ENABLED),
                    false,
                    mTouchpadPowerObserver);
            mTouchpadPowerObserverRegistered = true;
        } else if (!listening && mTouchpadPowerObserverRegistered) {
            mContext.getContentResolver().unregisterContentObserver(
                    mTouchpadPowerObserver);
            mTouchpadPowerObserverRegistered = false;
        }

        if (listening) {
            // Also cover changes made while the tile was not listening.
            refreshState();
        }
    }
}
