package ps.reso.instaeclipse.utils.backup;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import ps.reso.instaeclipse.mods.ads.ShareDomain;
import ps.reso.instaeclipse.utils.core.SettingsManager;
import ps.reso.instaeclipse.utils.core.SettingsProfiles;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;

/**
 * Settings backup file: every setting (FeatureFlags), the settings profiles included, as
 * {"version":2,"settings":{key:value}}. Works on setting maps (key -> Boolean / Integer / String,
 * as stored), so Instagram and the companion write and read the same file.
 */
public class SettingsBackupManager {

    private static final int VERSION = 2;

    /** Never in a backup: the passcode (a backup can be shared), and paths to files on this phone. */
    static final Set<String> EXCLUDED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "lockDirectPasscode", "lockDirectSalt",
            "customFontPath", "customEmojiPath", "downloaderCustomPath", "downloaderCustomUri")));

    /** Instagram's current settings as a backup file. */
    public static String toJson() throws JSONException {
        return toJson(SettingsManager.currentValues());
    }

    /** {@code values} (stored settings) as a backup file; a missing setting is written at its default. */
    public static String toJson(Map<String, ?> values) throws JSONException {
        JSONObject s = new JSONObject();
        for (Map.Entry<String, Object> d : FeatureFlags.defaults().entrySet()) {
            if (EXCLUDED.contains(d.getKey())) continue;
            Object v = typed(values.get(d.getKey()), d.getValue());
            s.put(d.getKey(), v != null ? v : d.getValue());
        }
        JSONObject root = new JSONObject();
        root.put("version", VERSION);
        root.put("settings", s);
        return root.toString(2);
    }

    /**
     * The settings in a backup file, checked and typed as stored. Reads this format, version 1
     * files and a flat {key:value} object; unknown keys and invalid values are skipped, so older
     * backups work on newer builds. Settings the file doesn't have are left as they are.
     */
    public static Map<String, Object> parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject s = root.has("settings") ? root.getJSONObject("settings") : root;
        // Version 1: the downloader choice was stored the other way round.
        if (s.has("downloaderUseOriginal") && !s.has("downloaderCustomQuality"))
            s.put("downloaderCustomQuality", !s.optBoolean("downloaderUseOriginal", true));
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> defaults = FeatureFlags.defaults();
        for (Iterator<String> it = s.keys(); it.hasNext(); ) {
            String key = it.next();
            Object like = defaults.get(key);
            if (like == null || EXCLUDED.contains(key)) continue;
            Object v = typed(s.get(key), like);
            if (v == null) continue;
            v = checked(key, v);
            if (v != null) out.put(key, v);
        }
        return out;
    }

    /** {@code v} as the stored type of a setting like {@code like}; doubles are stored as text. */
    private static Object typed(Object v, Object like) {
        if (v == null || like == null) return null;
        if (like instanceof Boolean) return v instanceof Boolean ? v : null;
        if (like instanceof Integer) return v instanceof Number ? (Object) ((Number) v).intValue() : null;
        if (like instanceof String) {
            if (v instanceof String) return v;
            return v instanceof Number ? String.valueOf(((Number) v).doubleValue()) : null;
        }
        return null;
    }

    /** The value to restore for {@code key}, clamped or normalised; null if it isn't valid. */
    private static Object checked(String key, Object v) {
        switch (key) {
            case "startupTab":
                return v.equals("") || Arrays.asList(ps.reso.instaeclipse.mods.ui.StartupTabHook.TABS).contains(v) ? v : null;
            case "customShareDomain":
                return v.equals("") ? v : ShareDomain.normalize((String) v);
            case "exactTimestampFormat":
                return v.equals("") || ps.reso.instaeclipse.utils.text.TimestampFormat.isValid((String) v) ? v : null;
            case "spoofDevice":
                return v.equals("") || Arrays.asList(ps.reso.instaeclipse.mods.misc.DeviceSpoofHook.DEVICES).contains(v) ? v : null;
            case "spoofAvatar":
                return v.equals("") || ps.reso.instaeclipse.utils.profile.SpoofAvatar.isValid((String) v) ? v : null;
            case "storyRingColor":
                return v.equals("") || ps.reso.instaeclipse.mods.ui.StoryTrayHook.parseColors((String) v) != null ? v : null;
            case "spoofLat":
            case "spoofLng":
                try {
                    return String.valueOf(Double.parseDouble((String) v));
                } catch (NumberFormatException e) {
                    return null;
                }
            case "storyTrayScale":
                return Math.max(30, Math.min(100, (Integer) v));
            case "feedCornerRadius":
                return Math.max(0, Math.min(48, (Integer) v));
            case "spoofFollowers":
            case "spoofFollowing":
            case "spoofPosts":
                return Math.max(-1, (Integer) v);
            case SettingsProfiles.KEY:
                return v.equals("") || !SettingsProfiles.parse((String) v).isEmpty() ? v : null;
            default:
                return v;
        }
    }
}
