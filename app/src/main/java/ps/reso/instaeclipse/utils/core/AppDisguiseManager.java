package ps.reso.instaeclipse.utils.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import ps.reso.instaeclipse.BuildConfig;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * App disguise: which launcher entry (activity-alias in the manifest) the companion app shows,
 * each with its own icon and name, like Lawnchair's private space disguises.
 */
public class AppDisguiseManager {

    public static final String DEFAULT = "default";
    public static final String CALCULATOR = "calculator";
    public static final String NOTES = "notes";
    public static final String FILE_MANAGER = "file_manager";
    public static final String CALENDAR = "calendar";
    public static final String WEATHER = "weather";

    /** The disguises, in picker order: setting key, launcher alias, name and icon. */
    public enum Option {
        DEFAULT_OPTION(DEFAULT, ".MainActivityDefault", R.string.app_name, R.mipmap.ic_launcher),
        CALCULATOR_OPTION(CALCULATOR, ".MainActivityCalculator", R.string.app_name_calculator, R.drawable.ic_disguise_calculator),
        NOTES_OPTION(NOTES, ".MainActivityNotes", R.string.app_name_notes, R.drawable.ic_disguise_notes),
        FILE_MANAGER_OPTION(FILE_MANAGER, ".MainActivityFileManager", R.string.app_name_file_manager, R.drawable.ic_disguise_files),
        CALENDAR_OPTION(CALENDAR, ".MainActivityCalendar", R.string.app_name_calendar, R.drawable.ic_disguise_calendar),
        WEATHER_OPTION(WEATHER, ".MainActivityWeather", R.string.app_name_weather, R.drawable.ic_disguise_weather);

        public final String key;
        final String alias;
        public final int labelRes;
        public final int iconRes;

        Option(String key, String alias, int labelRes, int iconRes) {
            this.key = key;
            this.alias = alias;
            this.labelRes = labelRes;
            this.iconRes = iconRes;
        }

        public static Option of(String key) {
            for (Option o : values()) if (o.key.equals(key)) return o;
            return DEFAULT_OPTION;
        }
    }

    /** Saves the disguise and switches the launcher entry (in the companion app; from Instagram's
     *  process the saved setting reaches the companion, which switches it, see PrefsSyncProvider). */
    public static void applyDisguise(Context context, String disguiseType) {
        if (disguiseType == null) disguiseType = DEFAULT;
        FeatureFlags.appDisguise = disguiseType;
        SettingsManager.saveAllFlags();
        if (BuildConfig.APPLICATION_ID.equals(context.getPackageName())) applyAliases(context, disguiseType);
    }

    /** Enables the chosen launcher alias and disables the others. Companion app process only:
     *  another app may not change this package's components. */
    public static void applyAliases(Context context, String disguiseType) {
        Option chosen = Option.of(disguiseType);
        PackageManager pm = context.getPackageManager();
        String pkg = BuildConfig.APPLICATION_ID;
        try {
            // Enable first: the app always keeps one launcher entry.
            setAliasState(pm, pkg, chosen.alias, true);
            for (Option o : Option.values()) if (o != chosen) setAliasState(pm, pkg, o.alias, false);
        } catch (SecurityException e) {
            ModuleLog.line("(InstaEclipse) App disguise: launcher entry not switched: " + e.getMessage());
        }
    }

    /**
     * Rows for the disguise picker: each option's icon beside its name, in a single-choice list.
     * {@code res} holds the module's drawables (the module's own Resources inside Instagram).
     */
    public static android.widget.ListAdapter pickerAdapter(Context context, android.content.res.Resources res, String[] labels) {
        float density = context.getResources().getDisplayMetrics().density;
        int size = Math.round(32 * density);
        int pad = Math.round(16 * density);
        Option[] options = Option.values();
        return new android.widget.ArrayAdapter<String>(context, android.R.layout.select_dialog_singlechoice,
                android.R.id.text1, labels) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View row = super.getView(position, convertView, parent);
                android.widget.TextView text = row.findViewById(android.R.id.text1);
                if (text == null) return row;
                android.graphics.drawable.Drawable icon = null;
                try {
                    icon = res.getDrawable(options[position].iconRes, null).mutate();
                    icon.setBounds(0, 0, size, size);
                } catch (Throwable ignored) {}
                text.setCompoundDrawablesRelative(icon, null, null, null);
                text.setCompoundDrawablePadding(pad);
                return row;
            }
        };
    }

    private static void setAliasState(PackageManager pm, String pkg, String aliasName, boolean enable) {
        ComponentName componentName = new ComponentName(pkg, pkg + aliasName);
        int state = enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (pm.getComponentEnabledSetting(componentName) == state) return;
        pm.setComponentEnabledSetting(componentName, state, PackageManager.DONT_KILL_APP);
    }
}
