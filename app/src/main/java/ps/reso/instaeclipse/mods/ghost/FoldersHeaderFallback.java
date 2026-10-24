package ps.reso.instaeclipse.mods.ghost;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * Hide folders bar, fallback: when Instagram's own "Messages / Requests" header can't take the
 * bar's place on this build (see {@link InboxFoldersHook#needsTextFallback}), a plain "Messages"
 * title is shown above the chat list instead, like a personal account's header without Requests.
 */
public final class FoldersHeaderFallback {

    private static final String TAG = "ie_folders_title";
    private static final Set<View> watched = Collections.newSetFromMap(new WeakHashMap<>());
    private static int listId, threadHeaderId;
    private static boolean placeLogged;

    private FoldersHeaderFallback() {}

    /** Arms the title for {@code a}'s window; safe to call on every resume. */
    public static void attach(Activity a) {
        if (a == null) return;
        try {
            ensureIds(a);
            View decor = a.getWindow().getDecorView();
            if (watched.add(decor)) decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> update(a));
            update(a);
        } catch (Throwable t) {
            ModuleLog.line("(IE|InboxFolders) fallback attach: " + t);
        }
    }

    @SuppressLint("DiscouragedApi")
    private static void ensureIds(Activity a) {
        if (listId != 0) return;
        String pkg = a.getPackageName();
        listId = a.getResources().getIdentifier("inbox_refreshable_thread_list_recyclerview", "id", pkg);
        threadHeaderId = a.getResources().getIdentifier("direct_thread_header", "id", pkg);
    }

    private static void update(Activity a) {
        if (a.isFinishing() || a.isDestroyed() || listId == 0) return;
        View existing = a.getWindow().getDecorView().findViewWithTag(TAG);
        View list = a.findViewById(listId);
        boolean show = InboxFoldersHook.needsTextFallback() && list != null && list.isShown()
                && (threadHeaderId == 0 || a.findViewById(threadHeaderId) == null);
        if (!show) {
            if (existing != null && existing.getParent() instanceof ViewGroup p) p.removeView(existing);
            return;
        }
        if (existing != null) return;
        // The list sits in a refresh layout (one child only): go up to a vertical LinearLayout and
        // put the title just above the list's branch.
        View branch = list;
        for (int depth = 0; depth < 5 && branch.getParent() instanceof ViewGroup parent; depth++) {
            if (parent instanceof LinearLayout ll && ll.getOrientation() == LinearLayout.VERTICAL) {
                ll.addView(title(a), ll.indexOfChild(branch),
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                ModuleLog.line("(IE|InboxFolders) fallback title added");
                return;
            }
            branch = parent;
        }
        if (!placeLogged) {
            placeLogged = true;
            ModuleLog.line("(IE|InboxFolders) fallback title: no vertical layout above the chat list");
        }
    }

    private static TextView title(Activity a) {
        boolean night = (a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        float d = a.getResources().getDisplayMetrics().density;
        TextView t = new TextView(a);
        t.setTag(TAG);
        t.setText(I18n.t(a, R.string.menu_messages));
        t.setTextColor(night ? Color.WHITE : Color.BLACK);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(Math.round(16 * d), Math.round(12 * d), Math.round(16 * d), Math.round(8 * d));
        return t;
    }
}
