package ps.reso.instaeclipse.mods.ui.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import ps.reso.instaeclipse.xposedcompat.XposedHelpers;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * Shortcut gestures on Instagram's own buttons (bottom-nav Search / Home / Messages, the feed
 * header's inbox button): {@link #LONG_PRESS} or {@link #DOUBLE_TAP_LONG_PRESS} (two taps, then a
 * press held on the third).
 *
 * <p>Two detectors run side by side, so the gesture works whichever way Instagram dispatches the
 * touch: a touch listener with its own long-press timer (also counts the taps), and
 * {@link #onPerformLongClick} for the framework's long-click path (UIHookManager hooks
 * View.performLongClick). Instagram's own touch listener is kept and called first-in-line, never
 * replaced, and once the shortcut fired the rest of that press is swallowed so the tap underneath
 * doesn't also switch tab.
 */
public final class NavGestureBinder {

    public static final int OFF = 0;
    public static final int LONG_PRESS = 1;
    public static final int DOUBLE_TAP_LONG_PRESS = 2;

    /** Max gap between one tap's release and the next press for them to count as a sequence. */
    private static final int TAP_GAP_MS = 350;

    private static final WeakHashMap<View, Binding> BINDINGS = new WeakHashMap<>();

    private NavGestureBinder() {}

    /**
     * Binds (or re-binds, after Instagram replaced its listener) the shortcut on {@code view}.
     * Safe to call on every resume. {@code mode} is read at touch time, so a settings change
     * applies without re-binding.
     */
    public static void bind(Activity activity, View view, IntSupplier mode, Consumer<Activity> action) {
        if (view == null) return;
        Binding b = BINDINGS.get(view);
        if (b == null) {
            b = new Binding();
            BINDINGS.put(view, b);
        }
        b.activity = new WeakReference<>(activity);
        b.mode = mode;
        b.action = action;

        View.OnTouchListener current = touchListenerOf(view);
        if (current != b.listener) {
            // Keep Instagram's listener (it may run its own double-tap / scroll-to-top detector).
            b.original = current instanceof Binding.Listener ? null : current;
            view.setOnTouchListener(b.listener);
        }
    }

    /** True if {@code view} carries a shortcut. */
    public static boolean isBound(View view) {
        return view != null && BINDINGS.containsKey(view);
    }

    /**
     * Called from the View.performLongClick hook. True when the long press belongs to a bound
     * shortcut (fired now, or already fired by the timer): the caller must then consume it.
     */
    public static boolean onPerformLongClick(View view) {
        Binding b = view != null ? BINDINGS.get(view) : null;
        if (b == null) return false;
        if (b.firedThisPress) return true;
        if (!b.armed || b.currentMode() == OFF) return false;
        b.fire(view, false);
        return true;
    }

    private static View.OnTouchListener touchListenerOf(View view) {
        try {
            Object info = XposedHelpers.getObjectField(view, "mListenerInfo");
            if (info == null) return null;
            return (View.OnTouchListener) XposedHelpers.getObjectField(info, "mOnTouchListener");
        } catch (Throwable t) {
            return null;
        }
    }

    private static Activity activityOf(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity a) return a;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private static final class Binding {
        WeakReference<Activity> activity = new WeakReference<>(null);
        IntSupplier mode = () -> OFF;
        Consumer<Activity> action;
        View.OnTouchListener original;

        final Listener listener = new Listener();
        final Handler handler = new Handler(Looper.getMainLooper());

        int taps;
        long lastUpTime;
        boolean armed;
        boolean firedThisPress;
        float downX, downY;
        WeakReference<View> pressed = new WeakReference<>(null);

        final Runnable timer = () -> {
            View v = pressed.get();
            if (v != null && armed && !firedThisPress) fire(v, true);
        };

        int currentMode() {
            try {
                return mode.getAsInt();
            } catch (Throwable t) {
                return OFF;
            }
        }

        void fire(View v, boolean fromTimer) {
            firedThisPress = true;
            armed = false;
            taps = 0;
            handler.removeCallbacks(timer);
            if (fromTimer) cancelPress(v);
            Activity a = activityOf(v.getContext());
            if (a == null) a = activity.get();
            if (a == null || action == null) return;
            try {
                VibrationUtil.vibrate(a);
                action.accept(a);
            } catch (Throwable t) {
                ModuleLog.line("(InstaEclipse|NavGesture) action failed: " + t);
            }
        }

        /** Ends Instagram's handling of the press (pressed state, pending long-press, its own listener). */
        void cancelPress(View v) {
            long now = SystemClock.uptimeMillis();
            MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, downX, downY, 0);
            try {
                if (original != null) original.onTouch(v, cancel);
                v.onTouchEvent(cancel);
            } catch (Throwable ignored) {
            } finally {
                cancel.recycle();
            }
            v.setPressed(false);
            v.cancelLongPress();
        }

        final class Listener implements View.OnTouchListener {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                int m = currentMode();
                if (m != OFF) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN: {
                            long now = SystemClock.uptimeMillis();
                            taps = now - lastUpTime <= TAP_GAP_MS ? taps + 1 : 1;
                            firedThisPress = false;
                            downX = e.getX();
                            downY = e.getY();
                            pressed = new WeakReference<>(v);
                            // taps counts this press too: the 3rd press after two quick taps
                            armed = m == LONG_PRESS || taps >= 3;
                            handler.removeCallbacks(timer);
                            if (armed) handler.postDelayed(timer, ViewConfiguration.getLongPressTimeout());
                            break;
                        }
                        case MotionEvent.ACTION_MOVE: {
                            if (firedThisPress) return true;
                            int slop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
                            if (armed && Math.hypot(e.getX() - downX, e.getY() - downY) > slop) {
                                armed = false;
                                taps = 0;
                                handler.removeCallbacks(timer);
                            }
                            break;
                        }
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL: {
                            handler.removeCallbacks(timer);
                            armed = false;
                            if (e.getActionMasked() == MotionEvent.ACTION_UP) lastUpTime = SystemClock.uptimeMillis();
                            else taps = 0;
                            if (firedThisPress) {
                                // The shortcut ran: no tab switch / click for this release, and
                                // Instagram's own handling of the press is ended with a cancel.
                                firedThisPress = false;
                                taps = 0;
                                cancelPress(v);
                                return true;
                            }
                            break;
                        }
                        default:
                            if (firedThisPress) return true;
                    }
                }
                return original != null && original.onTouch(v, e);
            }
        }
    }
}
