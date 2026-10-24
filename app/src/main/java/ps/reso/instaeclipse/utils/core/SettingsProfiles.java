package ps.reso.instaeclipse.utils.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Settings profiles: named sets of settings, one of them active. The active profile IS the current
 * settings (whatever the menus change goes straight into it), so only the other profiles keep
 * their values here; they are taken from the current settings when the active profile is left.
 *
 * <p>Stored as one JSON string setting ({@link #KEY}), synced like any other between Instagram and
 * the companion. A profile holds only the values that differ from the default: a key it doesn't
 * list is at its default (off), so features added later start off in every profile.
 *
 * <p>Works on plain setting maps (key -> Boolean / Integer / String, as stored in the prefs), so
 * Instagram and the companion run the same code. Pure Java (org.json only) so it can be unit tested.
 */
public final class SettingsProfiles {

    public static final String KEY = "settingsProfiles";

    /** Settings shared by every profile: the shortcuts that switch profiles (or a profile could
     *  lock you in), the profile list itself, secrets, and stored data / files rather than features. */
    public static final Set<String> GLOBAL_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            KEY, "profileSwitchVibrate", "profileSwitchShowName",
            "cleanUiToggleGesture", "ghostToggleGesture", "secretMenuDoubleTap",
            "appDisguise",
            "lockDirectPasscode", "lockDirectSalt", "lockedCollections",
            "spoofRecent", "spoofAvatar",
            "customFontPath", "customEmojiPath", "downloaderCustomPath", "downloaderCustomUri")));

    public static final class Profile {
        public final String id;
        public String name;
        JSONObject data;

        Profile(String id, String name, JSONObject data) {
            this.id = id;
            this.name = name;
            this.data = data != null ? data : new JSONObject();
        }
    }

    private String active;
    private int next = 1;
    private final List<Profile> list = new ArrayList<>();

    private SettingsProfiles() {}

    /** The stored profiles; empty (see {@link #isEmpty}) when {@code raw} is blank or unreadable. */
    public static SettingsProfiles parse(String raw) {
        SettingsProfiles p = new SettingsProfiles();
        if (raw == null || raw.trim().isEmpty()) return p;
        try {
            JSONObject root = new JSONObject(raw);
            p.next = Math.max(1, root.optInt("next", 1));
            JSONArray arr = root.optJSONArray("list");
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null || o.optString("id").isEmpty() || p.get(o.optString("id")) != null) continue;
                p.list.add(new Profile(o.optString("id"), o.optString("name", o.optString("id")), o.optJSONObject("data")));
            }
            p.active = root.optString("active", null);
            if (p.get(p.active) == null) p.active = p.list.isEmpty() ? null : p.list.get(0).id;
        } catch (JSONException e) {
            p.list.clear();
            p.active = null;
        }
        return p;
    }

    /** A single profile holding the current settings. */
    public static SettingsProfiles create(String firstName) {
        SettingsProfiles p = new SettingsProfiles();
        p.active = p.add(firstName).id;
        return p;
    }

    public String serialize() {
        try {
            JSONObject root = new JSONObject();
            root.put("v", 1);
            root.put("active", active);
            root.put("next", next);
            JSONArray arr = new JSONArray();
            for (Profile pr : list) {
                JSONObject o = new JSONObject();
                o.put("id", pr.id);
                o.put("name", pr.name);
                // The active profile's values are the current settings, not this copy.
                o.put("data", pr.id.equals(active) ? new JSONObject() : pr.data);
                arr.put(o);
            }
            root.put("list", arr);
            return root.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public List<Profile> list() {
        return Collections.unmodifiableList(list);
    }

    public Profile active() {
        return get(active);
    }

    public Profile get(String id) {
        if (id == null) return null;
        for (Profile pr : list) if (pr.id.equals(id)) return pr;
        return null;
    }

    /** The profile after the active one (wrapping around), or null with fewer than two. */
    public String nextId() {
        if (list.size() < 2) return null;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(active)) return list.get((i + 1) % list.size()).id;
        }
        return list.get(0).id;
    }

    /** {@code base}, or "base 2", "base 3"... if a profile already has that name. */
    public String uniqueName(String base) {
        String name = base;
        for (int n = 2; hasName(name); n++) name = base + " " + n;
        return name;
    }

    private boolean hasName(String name) {
        for (Profile pr : list) if (pr.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** True for a setting that belongs to a profile (a known setting, not a global one). */
    public static boolean isProfileKey(String key, Map<String, Object> defaults) {
        return defaults.containsKey(key) && !GLOBAL_KEYS.contains(key);
    }

    // ----- operations -----
    // Those that change the current settings return what to write: the changed settings plus this
    // store ({@link #KEY}). The others only change the store: write serialize() under KEY.

    /** Leaves the active profile (keeping its current values) for {@code id}. Empty if already active. */
    public Map<String, Object> activate(String id, Map<String, ?> live, Map<String, Object> defaults) {
        Profile target = get(id);
        Map<String, Object> out = new LinkedHashMap<>();
        if (target == null || id.equals(active)) return out;
        Profile current = active();
        if (current != null) current.data = snapshot(live, defaults);
        active = id;
        out.putAll(diff(values(target.data, defaults), live, defaults));
        out.put(KEY, serialize());
        return out;
    }

    /** A new profile with every setting off. */
    public Profile add(String name) {
        Profile pr = new Profile("p" + next++, name, new JSONObject());
        list.add(pr);
        return pr;
    }

    /** A copy of {@code id} (of the current settings, for the active profile). */
    public Profile duplicate(String id, String name, Map<String, ?> live, Map<String, Object> defaults) {
        Profile src = get(id);
        if (src == null) return null;
        JSONObject data = id.equals(active) ? snapshot(live, defaults) : copy(src.data);
        Profile pr = new Profile("p" + next++, name, data);
        list.add(list.indexOf(src) + 1, pr);
        return pr;
    }

    public boolean rename(String id, String name) {
        Profile pr = get(id);
        if (pr == null || name == null || name.trim().isEmpty()) return false;
        pr.name = name.trim();
        return true;
    }

    /** False for the active profile and for the last one left. */
    public boolean delete(String id) {
        Profile pr = get(id);
        if (pr == null || id.equals(active) || list.size() < 2) return false;
        return list.remove(pr);
    }

    /** Turns every setting of {@code id} off: for the active profile, the settings to write. */
    public Map<String, Object> reset(String id, Map<String, ?> live, Map<String, Object> defaults) {
        Profile pr = get(id);
        Map<String, Object> out = new LinkedHashMap<>();
        if (pr == null) return out;
        pr.data = new JSONObject();
        if (id.equals(active)) out.putAll(diff(values(pr.data, defaults), live, defaults));
        out.put(KEY, serialize());
        return out;
    }

    // ----- values -----

    /** The profile settings among {@code live} that differ from their default. */
    static JSONObject snapshot(Map<String, ?> live, Map<String, Object> defaults) {
        JSONObject data = new JSONObject();
        for (Map.Entry<String, Object> d : defaults.entrySet()) {
            String key = d.getKey();
            if (!isProfileKey(key, defaults)) continue;
            Object v = typed(live.get(key), d.getValue());
            if (v == null || v.equals(d.getValue())) continue;
            try {
                data.put(key, v);
            } catch (JSONException ignored) {}
        }
        return data;
    }

    /** Every profile setting: the stored value, or the default for one the profile doesn't list. */
    static Map<String, Object> values(JSONObject data, Map<String, Object> defaults) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> d : defaults.entrySet()) {
            if (!isProfileKey(d.getKey(), defaults)) continue;
            Object v = typed(data.opt(d.getKey()), d.getValue());
            out.put(d.getKey(), v != null ? v : d.getValue());
        }
        return out;
    }

    /** The entries of {@code target} that change what {@code live} holds (a missing key is at its default). */
    static Map<String, Object> diff(Map<String, Object> target, Map<String, ?> live, Map<String, Object> defaults) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> t : target.entrySet()) {
            Object current = live.get(t.getKey());
            if (current == null) current = defaults.get(t.getKey());
            if (!Objects.equals(typed(current, t.getValue()), t.getValue())) out.put(t.getKey(), t.getValue());
        }
        return out;
    }

    /** {@code v} as the type of {@code like} (Boolean / Integer / String), or null if it can't be. */
    static Object typed(Object v, Object like) {
        if (v == null || like == null) return null;
        if (like instanceof Boolean) return v instanceof Boolean ? v : null;
        if (like instanceof Integer) return v instanceof Number ? (Object) ((Number) v).intValue() : null;
        if (like instanceof String) return v instanceof String ? v : null;
        return null;
    }

    private static JSONObject copy(JSONObject o) {
        JSONObject out = new JSONObject();
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            String k = it.next();
            try {
                out.put(k, o.get(k));
            } catch (JSONException ignored) {}
        }
        return out;
    }
}
