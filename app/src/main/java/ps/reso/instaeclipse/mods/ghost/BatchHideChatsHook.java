package ps.reso.instaeclipse.mods.ghost;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.mods.ui.utils.CleanUiManager;
import ps.reso.instaeclipse.utils.dialog.DialogUtils;
import ps.reso.instaeclipse.utils.dialog.IeDialog;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.ghost.CurrentConversation;
import ps.reso.instaeclipse.utils.ghost.HiddenThreadStore;
import ps.reso.instaeclipse.utils.ghost.HiddenThreads;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.log.ModuleLog;
import ps.reso.instaeclipse.utils.toast.IeToast;

/**
 * Batch hide (Hide Specific Chats): a button in the DM inbox header opens a list of the chats the
 * inbox has loaded, with a check box each; the ticked ones are hidden in one go.
 *
 * <p>The old version waited for Instagram's own multi-select bar, found by the English labels
 * "Mute" / "Delete", and guessed the selection by scanning fragments on every layout: with
 * Instagram in another language (or a Compose bar) the button never appeared. Now:
 * <ul>
 *   <li>the button sits in {@code direct_inbox_action_bar}, the inbox header Lock DMs already
 *   detects the inbox by;</li>
 *   <li>the chats come from the inbox rows {@link HiddenInboxFilter} sees (the same thread ids it
 *   filters on), recorded per account before filtering;</li>
 *   <li>each chat is named by the row text shown in the inbox, else by its members' usernames
 *   (see {@link #titleOf}).</li>
 * </ul>
 * Hidden by Clean UI (not added while it is on, tracked so an existing one goes at once).
 */
public final class BatchHideChatsHook {

    private static final String BTN_TAG = "ie_batch_hide_btn";
    private static final String TAG = "(IE|BatchHide) ";
    private static final int MAX_LISTS = 6;   // recent inbox lists per account (one per folder)

    /** account -> recent unfiltered inbox row lists, newest first */
    private static final Map<String, Deque<List<?>>> recentRows = new ConcurrentHashMap<>();
    private static volatile String lastAccount;

    private static final Set<View> watchedDecors =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static int actionBarId, threadHeaderId, threadListId;

    private BatchHideChatsHook() {}

    // ── data: the inbox rows, from HiddenInboxFilter ──

    /** Called with each inbox list the store builds, before hidden chats are filtered out. */
    static void recordInbox(Object store, List<?> rows) {
        if (!FeatureFlags.hideSpecificChats || !FeatureFlags.enableBatchHideChats || rows == null || rows.isEmpty()) return;
        String account = CurrentConversation.accountIdOf(store);
        if (account == null) return;
        Deque<List<?>> lists = recentRows.computeIfAbsent(account, k -> new ArrayDeque<>());
        synchronized (lists) {
            lists.addFirst(new ArrayList<>(rows));
            while (lists.size() > MAX_LISTS) lists.removeLast();
        }
        lastAccount = account;
    }

    // ── the inbox header button ──

    /** Arms the button for {@code a}'s window; safe to call on every resume. */
    public static void attach(Activity a, View ignoredRoot) {
        if (a == null) return;
        try {
            ensureIds(a);
            View decor = a.getWindow().getDecorView();
            if (!watchedDecors.add(decor)) { update(a); return; }
            // The inbox is a tab of the main activity: it appears without an activity resume.
            decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> update(a));
            update(a);
        } catch (Throwable t) {
            ModuleLog.line(TAG + "attach: " + t);
        }
    }

    @SuppressLint("DiscouragedApi")
    private static void ensureIds(Activity a) {
        if (actionBarId != 0) return;
        String pkg = a.getPackageName();
        actionBarId = a.getResources().getIdentifier("direct_inbox_action_bar", "id", pkg);
        threadHeaderId = a.getResources().getIdentifier("direct_thread_header", "id", pkg);
        threadListId = a.getResources().getIdentifier("inbox_refreshable_thread_list_recyclerview", "id", pkg);
    }

    private static void update(Activity a) {
        if (a.isFinishing() || a.isDestroyed()) return;
        View decor = a.getWindow().getDecorView();
        View existing = decor.findViewWithTag(BTN_TAG);
        boolean wanted = FeatureFlags.hideSpecificChats && FeatureFlags.enableBatchHideChats
                && !FeatureFlags.isCleanUiActive;
        View bar = wanted && actionBarId != 0 ? a.findViewById(actionBarId) : null;
        boolean onInbox = bar instanceof ViewGroup && bar.isShown()
                && (threadHeaderId == 0 || a.findViewById(threadHeaderId) == null);
        if (!onInbox) {
            if (existing != null && existing.getParent() instanceof ViewGroup p) p.removeView(existing);
            return;
        }
        if (existing != null) return;
        addButton(a, (ViewGroup) bar);
    }

    /** Just left of the header's right-most button (IG's "new message"), sized like it. */
    private static void addButton(Activity a, ViewGroup bar) {
        View anchor = rightMostButton(bar);
        if (anchor == null || anchor.getWidth() == 0) return; // header still laying out: next layout pass
        if (!(anchor.getParent() instanceof ViewGroup host)) return;
        boolean night = (a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        android.graphics.drawable.Drawable icon =
                DialogUtils.moduleIcon(R.drawable.ic_eye_off, night ? Color.WHITE : Color.BLACK);
        if (icon == null) return;

        ImageView btn = new ImageView(a);
        btn.setTag(BTN_TAG);
        btn.setImageDrawable(icon);
        btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = Math.round(10 * a.getResources().getDisplayMetrics().density);
        btn.setPadding(pad, pad, pad, pad);
        btn.setContentDescription(I18n.t(a, R.string.ig_batch_hide_title));
        btn.setOnClickListener(v -> showPicker(a));

        int size = Math.max(anchor.getWidth(), anchor.getHeight());
        ViewGroup.LayoutParams lp;
        if (host instanceof LinearLayout) {
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(size, size);
            llp.gravity = Gravity.CENTER_VERTICAL;
            lp = llp;
        } else {
            try {
                lp = anchor.getLayoutParams().getClass().getConstructor(anchor.getLayoutParams().getClass())
                        .newInstance(anchor.getLayoutParams());
                lp.width = size;
                lp.height = size;
            } catch (Throwable t) {
                lp = new ViewGroup.LayoutParams(size, size);
            }
        }
        host.addView(btn, host.indexOfChild(anchor), lp);
        // A right-aligned anchor in a FrameLayout-like host: put ours just left of it.
        if (!(host instanceof LinearLayout)) btn.setTranslationX(-size);
        CleanUiManager.trackInjectedView(btn);
        ModuleLog.line(TAG + "✅ button added in " + host.getClass().getSimpleName());
    }

    private static View rightMostButton(ViewGroup bar) {
        View best = null;
        int bestRight = Integer.MIN_VALUE;
        int[] loc = new int[2];
        ArrayDeque<View> stack = new ArrayDeque<>();
        stack.push(bar);
        while (!stack.isEmpty()) {
            View v = stack.pop();
            if (v != bar && v.isClickable() && v.isShown() && v.getWidth() > 0 && !BTN_TAG.equals(v.getTag())) {
                v.getLocationOnScreen(loc);
                int right = loc[0] + v.getWidth();
                if (right > bestRight) { bestRight = right; best = v; }
                continue; // a clickable group is one button
            }
            if (v instanceof ViewGroup g) for (int i = 0; i < g.getChildCount(); i++) stack.push(g.getChildAt(i));
        }
        if (best == null) return null;
        bar.getLocationOnScreen(loc);
        // Only a button on the right half: the left side holds the account switcher / title.
        return bestRight > loc[0] + bar.getWidth() / 2 ? best : null;
    }

    // ── the picker ──

    private static void showPicker(Activity a) {
        String account = CurrentConversation.accountId(a);
        if (account == null || !recentRows.containsKey(account)) account = lastAccount;
        if (account == null) { toast(a, I18n.t(a, R.string.ig_hide_chat_no_thread)); return; }

        Set<String> onScreen = visibleTexts(a);
        LinkedHashMap<String, String> chats = new LinkedHashMap<>(); // id -> title
        Deque<List<?>> lists = recentRows.get(account);
        if (lists != null) {
            synchronized (lists) {
                for (List<?> list : lists) for (Object row : list) {
                    String id = HiddenInboxFilter.threadIdOfRow(row);
                    if (id == null || chats.containsKey(id) || HiddenThreads.isHidden(account, id)) continue;
                    chats.put(id, titleOf(row, id, account, onScreen));
                }
            }
        }
        if (chats.isEmpty()) { toast(a, I18n.t(a, R.string.ig_batch_hide_empty)); return; }
        int unnamed = 0;
        for (Map.Entry<String, String> e : chats.entrySet()) if (e.getValue().equals(fallbackName(e.getKey()))) unnamed++;
        ModuleLog.line(TAG + "picker chats=" + chats.size() + " unnamed=" + unnamed
                + " onScreenTexts=" + onScreen.size());

        final String acc = account;
        IeDialog dialog = new IeDialog(a)
                .title(I18n.t(a, R.string.ig_batch_hide_title))
                .message(I18n.t(a, R.string.ig_batch_hide_hint));
        List<CheckBox> boxes = new ArrayList<>();
        List<String> ids = new ArrayList<>(chats.keySet());
        for (String id : ids) boxes.add(dialog.checkBox(chats.get(id), false));
        dialog.primary(I18n.t(a, R.string.ig_batch_hide_action), () -> {
            int hidden = 0;
            for (int i = 0; i < boxes.size(); i++) {
                if (!boxes.get(i).isChecked()) continue;
                HiddenThreadStore.Result r = HiddenThreads.setHidden(acc, ids.get(i), chats.get(ids.get(i)), true);
                if (r.success) hidden++;
            }
            if (hidden == 0) return;
            HideChatsHook.refreshAccount(acc);
            HideChatsHook.refreshActiveInbox(a);
            ConversationMenuHook.refreshAll();
            toast(a, I18n.t(a, R.string.ig_batch_hide_done, hidden));
        });
        dialog.secondary(a.getString(android.R.string.cancel), null);
        dialog.show();
    }

    /** Texts of the inbox rows on screen: a row model string equal to one of them is its name. */
    private static Set<String> visibleTexts(Activity a) {
        Set<String> out = new HashSet<>();
        View list = threadListId != 0 ? a.findViewById(threadListId) : null;
        ArrayDeque<View> stack = new ArrayDeque<>();
        if (list != null) stack.push(list);
        while (!stack.isEmpty()) {
            View v = stack.pop();
            if (v instanceof TextView t && t.getText() != null) {
                String s = t.getText().toString().trim();
                if (!s.isEmpty()) out.add(s);
            }
            CharSequence cd = v.getContentDescription();
            if (cd != null && cd.length() > 0) out.add(cd.toString().trim());
            if (v instanceof ViewGroup g) for (int i = 0; i < g.getChildCount(); i++) stack.push(g.getChildAt(i));
        }
        return out;
    }

    /**
     * The chat's name, best source first:
     * 1. a row string that is shown in the inbox right now (the row's own label);
     * 2. the usernames of the chat's members: the {@code User} objects the row holds, read with
     *    the module's username getter, leaving out the account itself;
     * 3. a row string that looks like a display / group name (not a status word like "unknown");
     * 4. "Chat …" + the end of the thread id.
     */
    static String titleOf(Object row, String id, String account, Set<String> onScreen) {
        List<String> strings = new ArrayList<>();
        collectStrings(row, strings, Collections.newSetFromMap(new java.util.IdentityHashMap<>()), 0);
        for (String s : strings) if (onScreen.contains(s.trim()) && looksLikeName(s, id)) return s.trim();

        List<String> members = memberUsernames(row, account);
        if (!members.isEmpty()) {
            String names = String.join(", ", members.subList(0, Math.min(3, members.size())));
            return members.size() > 3 ? names + " +" + (members.size() - 3) : names;
        }
        for (String s : strings) if (looksLikeName(s, id)) return s.trim();
        return fallbackName(id);
    }

    private static String fallbackName(String id) {
        return "Chat " + (id.length() > 6 ? "…" + id.substring(id.length() - 6) : id);
    }

    /** Status / enum words Instagram keeps as plain strings in its models. */
    private static final Set<String> NOT_NAMES = new HashSet<>(java.util.Arrays.asList(
            "unknown", "null", "none", "default", "inbox", "primary", "general", "other", "pending",
            "true", "false", "private", "group", "thread", "direct", "active", "inactive", "undefined"));

    static boolean looksLikeName(String s, String id) {
        if (s == null) return false;
        String t = s.trim();
        if (t.isEmpty() || t.length() > 64 || t.equals(id) || t.contains("://") || t.contains("\n")) return false;
        if (NOT_NAMES.contains(t.toLowerCase(java.util.Locale.ROOT))) return false;
        boolean letter = false;
        for (int i = 0; i < t.length(); i++) if (Character.isLetter(t.charAt(i))) { letter = true; break; }
        if (!letter) return false;
        if (t.matches("[A-Z0-9_]+")) return false; // enum-style constants
        // A bare lowercase word is a key or status value; members' usernames come from step 2.
        return !t.matches("[a-z]+");
    }

    private static final String USER = "com.instagram.user.model.User";

    /** Usernames of the User objects reachable from the row (members), without the account itself. */
    private static List<String> memberUsernames(Object row, String account) {
        List<Object> users = new ArrayList<>();
        collectUsers(row, users, Collections.newSetFromMap(new java.util.IdentityHashMap<>()), 0);
        List<String> out = new ArrayList<>();
        for (Object u : users) {
            if (account != null && hasId(u, account)) continue;
            String name = ps.reso.instaeclipse.utils.users.UserUtils.callUsernameGetter(u);
            if (name != null && !NOT_NAMES.contains(name) && !out.contains(name)) out.add(name);
        }
        return out;
    }

    private static void collectUsers(Object obj, List<Object> out, Set<Object> seen, int depth) {
        if (obj == null || depth > 4 || out.size() > 16 || !seen.add(obj)) return;
        if (obj.getClass().getName().equals(USER)) { out.add(obj); return; }
        if (obj instanceof java.util.Collection<?> c) {
            int n = 0;
            for (Object o : c) { if (n++ > 32) break; collectUsers(o, out, seen, depth + 1); }
            return;
        }
        if (obj instanceof java.util.Map<?, ?> m) {
            int n = 0;
            for (Object o : m.values()) { if (n++ > 32) break; collectUsers(o, out, seen, depth + 1); }
            return;
        }
        if (obj instanceof Object[] arr) {
            for (int i = 0; i < Math.min(arr.length, 32); i++) collectUsers(arr[i], out, seen, depth + 1);
            return;
        }
        try {
            for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                String cn = c.getName();
                if (!cn.startsWith("X.") && !cn.startsWith("com.instagram.")) break;
                for (Field f : ps.reso.instaeclipse.utils.core.SafeReflect.fields(c)) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || f.getType() == String.class) continue;
                    f.setAccessible(true);
                    Object v;
                    try { v = f.get(obj); } catch (Throwable e) { continue; }
                    collectUsers(v, out, seen, depth + 1);
                }
            }
        } catch (Throwable ignored) {}
    }

    /** True when one of the User's String fields is {@code id} (its user id). */
    private static boolean hasId(Object user, String id) {
        for (Class<?> c = user.getClass(); c != null && c != Object.class; c = c.getSuperclass())
            for (Field f : ps.reso.instaeclipse.utils.core.SafeReflect.fields(c)) {
                if (f.getType() != String.class || Modifier.isStatic(f.getModifiers())) continue;
                try { f.setAccessible(true); if (id.equals(f.get(user))) return true; } catch (Throwable ignored) {}
            }
        return false;
    }

    private static void collectStrings(Object obj, List<String> out, Set<Object> seen, int depth) {
        if (obj == null || depth > 2 || !seen.add(obj) || out.size() > 80) return;
        try {
            for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                String cn = c.getName();
                if (!cn.startsWith("X.") && !cn.startsWith("com.instagram.")) break;
                for (Field f : ps.reso.instaeclipse.utils.core.SafeReflect.fields(c)) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    f.setAccessible(true);
                    Object v;
                    try { v = f.get(obj); } catch (Throwable e) { continue; }
                    if (v instanceof CharSequence cs) out.add(cs.toString());
                    else if (v != null) collectStrings(v, out, seen, depth + 1);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void toast(Activity a, String msg) {
        IeToast.makeText(a, msg, Toast.LENGTH_SHORT).show();
    }
}
