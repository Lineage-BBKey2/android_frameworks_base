/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.android.systemui.res.R;

/** A short, non-interactive Cast message that remains visible over SystemUI's shade and dialogs. */
final class CastWifiStartupPopup {
    private static final String TAG = "CastWifiStartupPopup";
    private static final int DURATION_MS = 2_000;

    private final Context mContext;
    private final Handler mMainHandler;
    private final Runnable mHide = this::hide;
    @Nullable private View mView;
    private boolean mDestroyed;

    CastWifiStartupPopup(Context context, Handler mainHandler) {
        mContext = context;
        mMainHandler = mainHandler;
    }

    @MainThread
    void show(@StringRes int messageResId) {
        if (mDestroyed) {
            return;
        }
        hide();

        // Reuse the standard SystemUI toast frame, typography, padding and elevation.
        final View view = LayoutInflater.from(mContext).inflate(R.layout.text_toast, null);
        view.findViewById(R.id.icon).setVisibility(View.GONE);
        final TextView text = view.findViewById(R.id.text);
        text.setText(messageResId);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                // This layer is above TYPE_NOTIFICATION_SHADE and TYPE_STATUS_BAR_SUB_PANEL.
                // SystemUI already holds INTERNAL_SYSTEM_WINDOW; no policy change is needed.
                WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.getAbsoluteGravity(
                mContext.getResources().getInteger(
                        com.android.internal.R.integer.config_toastDefaultGravity),
                mContext.getResources().getConfiguration().getLayoutDirection());
        params.y = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.toast_y_offset);
        params.setFitInsetsIgnoringVisibility(true);
        params.setTrustedOverlay();
        params.setTitle("Cast Wi-Fi startup");

        final WindowManager windowManager = mContext.getSystemService(WindowManager.class);
        try {
            windowManager.addView(view, params);
        } catch (WindowManager.BadTokenException | WindowManager.InvalidDisplayException e) {
            Log.w(TAG, "Unable to show Wi-Fi startup popup", e);
            return;
        }
        mView = view;
        Log.d(TAG, "Showing Wi-Fi startup popup above the shade");
        view.announceForAccessibility(text.getText());

        final AccessibilityManager accessibilityManager =
                mContext.getSystemService(AccessibilityManager.class);
        final int timeout = accessibilityManager == null ? DURATION_MS
                : accessibilityManager.getRecommendedTimeoutMillis(
                        DURATION_MS, AccessibilityManager.FLAG_CONTENT_TEXT);
        // Let the message finish its own display time even if Wi-Fi enables immediately.
        mMainHandler.postDelayed(mHide, timeout);
    }

    @MainThread
    void hide() {
        mMainHandler.removeCallbacks(mHide);
        if (mView == null) {
            return;
        }
        final View view = mView;
        mView = null;
        try {
            mContext.getSystemService(WindowManager.class).removeViewImmediate(view);
        } catch (IllegalArgumentException e) {
            // The display or window may already have been removed.
            Log.d(TAG, "Wi-Fi startup popup was already removed", e);
        }
    }

    @MainThread
    void destroy() {
        mDestroyed = true;
        hide();
    }
}
