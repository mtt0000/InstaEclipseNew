package ps.reso.instaeclipse.mods.ghost;

import android.app.Activity;
import android.content.Context;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.log.ModuleLog;
import ps.reso.instaeclipse.xposedcompat.XC_MethodHook;
import ps.reso.instaeclipse.xposedcompat.XposedBridge;

public class InboxFoldersHook {

    private static WeakReference<Object> lastVmGeneratorRef = new WeakReference<>(null);
    private static Method diffUpdateMethod = null;

    private static Object generalFolderSingleton = null;
    private static Object chatsFolderSingleton = null;

    public void install(DexKitBridge bridge, ClassLoader loader) {
        installFolderDescriptorHooks(bridge, loader);
        installVmGeneratorHooks(bridge, loader);
    }

    public static void refreshActiveInbox(Activity activity) {
        try {
            Object vmGen = lastVmGeneratorRef.get();
            if (vmGen != null && diffUpdateMethod != null) {
                diffUpdateMethod.invoke(null, vmGen);
            } else if (activity != null) {
                HideChatsHook.refreshAccount(ps.reso.instaeclipse.utils.ghost.CurrentConversation.accountId(activity));
            }
        } catch (Throwable t) {
            ModuleLog.line("(IE|InboxFolders) error refreshing inbox: " + t.getMessage());
        }
    }

    private void installFolderDescriptorHooks(DexKitBridge bridge, ClassLoader loader) {
        try {
            List<ClassData> descriptors = bridge.findClass(FindClass.create()
                    .matcher(ClassMatcher.create().usingStrings("primary", "general", "requests_hidden_words", "school_channels")));
            if (descriptors.isEmpty()) return;

            Class<?> folderDescriptorClass = descriptors.get(0).getInstance(loader);

            for (Method m : folderDescriptorClass.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class && m.getReturnType() == folderDescriptorClass) {
                    m.setAccessible(true);
                    generalFolderSingleton = m.invoke(null, "general");
                    chatsFolderSingleton = m.invoke(null, "chats");
                    break;
                }
            }

            for (Method m : folderDescriptorClass.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 2 && m.getReturnType() == boolean.class) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (FeatureFlags.emptyGeneralFolder && isGeneralDescriptor(param.thisObject)) {
                                param.setResult(false);
                            }
                        }
                    });
                }
            }
        } catch (Throwable t) {
            ModuleLog.line("(IE|InboxFolders) Error in folder descriptor hooks: " + t.getMessage());
        }
    }

    public static boolean isGeneralDescriptor(Object obj) {
        if (obj == null) return false;
        if (generalFolderSingleton != null && obj == generalFolderSingleton) return true;

        String name = obj.getClass().getName();
        if (name.endsWith(".0Gmm") || name.contains("C425100Gmm")) return true;

        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != String.class) continue;
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if ("general".equalsIgnoreCase((String) val)) return true;
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    public static boolean isStateInGeneralFolder(Object state) {
        if (state == null) return false;
        try {
            for (Field f : state.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                f.setAccessible(true);
                Object val = f.get(state);
                if (isGeneralDescriptor(val)) return true;
            }
        } catch (Throwable t) {}
        return false;
    }

    private void installVmGeneratorHooks(DexKitBridge bridge, ClassLoader loader) {
        try {
            List<MethodData> diffUpdates = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create().usingStrings("InboxViewModelGenerator.calculateAndApplyDiffUpdate")));

            if (diffUpdates.isEmpty()) return;

            Method diffUpdateMethodResolved = diffUpdates.get(0).getMethodInstance(loader);
            diffUpdateMethod = diffUpdateMethodResolved;
            Class<?> vmGenClass = diffUpdateMethodResolved.getParameterTypes()[0];

            Field vmStateField = null;
            for (Field f : vmGenClass.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (!f.getType().getName().startsWith("X.")) continue;

                int listCount = 0;
                for (Field sf : f.getType().getDeclaredFields()) {
                    if (sf.getType() == java.util.List.class) listCount++;
                }
                if (listCount >= 3) {
                    vmStateField = f;
                    break;
                }
            }

            Field holdersField = null;
            for (Field f : vmGenClass.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (!f.getType().getName().startsWith("X.")) continue;

                if (f.getType().getDeclaredFields().length >= 20) {
                    holdersField = f;
                    break;
                }
            }

            if (vmStateField == null || holdersField == null) return;
            vmStateField.setAccessible(true);
            holdersField.setAccessible(true);
            Class<?> stateClass = vmStateField.getType();
            Class<?> holdersClass = holdersField.getType();

            for (Method m : vmGenClass.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == vmGenClass && m.getReturnType() == void.class) {
                    final Field finalVmStateField = vmStateField;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Object vmGen = param.thisObject != null ? param.thisObject : (param.args != null && param.args.length > 0 ? param.args[0] : null);
                            if (vmGen != null) {
                                lastVmGeneratorRef = new WeakReference<>(vmGen);
                                Object state = finalVmStateField.get(vmGen);

                                if (FeatureFlags.emptyGeneralFolder && isStateInGeneralFolder(state)) {
                                    Map<Field, Object> backup = new HashMap<>();
                                    for (Field f : stateClass.getDeclaredFields()) {
                                        if (f.getType() == java.util.List.class && !Modifier.isStatic(f.getModifiers())) {
                                            f.setAccessible(true);
                                            backup.put(f, f.get(state));
                                            f.set(state, Collections.emptyList());
                                        }
                                    }
                                    backupMap.put(param, backup);
                                    stateMap.put(param, state);
                                }
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Object state = stateMap.remove(param);
                            Map<Field, Object> backup = backupMap.remove(param);
                            if (state != null && backup != null) {
                                for (Map.Entry<Field, Object> entry : backup.entrySet()) {
                                    entry.getKey().set(state, entry.getValue());
                                }
                            }
                        }

                        private final Map<MethodHookParam, Map<Field, Object>> backupMap = new HashMap<>();
                        private final Map<MethodHookParam, Object> stateMap = new HashMap<>();
                    });
                }
            }

            Field sectionEnumField = null;
            Method singleItemGenMethod = null;

            for (Field f : holdersClass.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || !f.getType().getName().startsWith("X.")) continue;

                Class<?> genType = f.getType();
                for (Class<?> c = genType; c != null && c != Object.class; c = c.getSuperclass()) {
                    if (sectionEnumField == null) {
                        for (Field sf : c.getDeclaredFields()) {
                            if (sf.getType().isEnum() && sf.getType().getName().startsWith("X.")) {
                                sectionEnumField = sf;
                                sectionEnumField.setAccessible(true);
                                break;
                            }
                        }
                    }
                    if (singleItemGenMethod == null) {
                        for (Method sm : c.getDeclaredMethods()) {
                            if (sm.getParameterCount() == 1 && sm.getParameterTypes()[0] == stateClass && sm.getReturnType() != void.class) {
                                singleItemGenMethod = sm;
                                singleItemGenMethod.setAccessible(true);
                                break;
                            }
                        }
                    }
                }
                if (sectionEnumField != null && singleItemGenMethod != null) break;
            }

            if (sectionEnumField != null && singleItemGenMethod != null) {
                sectionField = sectionEnumField;
                holdersFieldRef = holdersField;
                itemReturnType = singleItemGenMethod.getReturnType();
                // The section holders (one generator per inbox section, INBOX_HEADER among them):
                // remembered at construction, since on IG 385713116 the view-model generator
                // methods above never run, so they can't be read through it.
                XposedBridge.hookAllConstructors(holdersClass, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (param.thisObject != null) holdersRef = new WeakReference<>(param.thisObject);
                    }
                });
                XposedBridge.hookMethod(singleItemGenMethod, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!FeatureFlags.hideDirectFoldersBar) return;
                        try {
                            onSectionItem(param);
                        } catch (Throwable t) {
                            // Never leave the folder bar half-built: drop it, as the plain hide did.
                            String sec = sectionName(param.thisObject);
                            if ("FOLDER".equals(sec) || "FILTER_ROW".equals(sec)) param.setResult(null);
                            if (!probeLogged) { probeLogged = true; ModuleLog.line("(IE|InboxFolders) header swap failed: " + t); }
                        }
                    }
                });
                ModuleLog.line("(IE|InboxFolders) section generators hooked");
            }

        } catch (Throwable t) {
            ModuleLog.line("(IE|InboxFolders) Error in VM generator hooks: " + t.getMessage());
        }
    }

    // ── Hide folders bar: show Instagram's own "Messages / Requests" header instead ──
    // A personal account gets the INBOX_HEADER section ("Messages" + "Requests"); a professional
    // one gets the folder tabs (Primary / General / Requests: the FILTER_ROW section on IG
    // 385713116, FOLDER on older builds) and no header. With the bar hidden we build that header
    // item and put it where the bar was, so the inbox looks like a personal account's and Requests
    // stays one tap away (Instagram's binder handles the tap).

    private static Field sectionField;
    private static Field holdersFieldRef;
    private static Class<?> itemReturnType;
    private static volatile WeakReference<Object> holdersRef = new WeakReference<>(null);
    /** Instagram generates its own header section on this account (personal account): use that slot. */
    private static volatile boolean headerSectionSeen;
    /** The first bar section seen (FOLDER or FILTER_ROW, depending on the IG build): the header
     *  goes only there, so a build generating both never shows it twice. */
    private static volatile String barSlot;
    private static volatile Constructor<?> headerCtor;
    private static volatile boolean probeLogged, ctorLogged, dumpLogged;

    /** One-time log of the shapes involved, so a header that doesn't show can be fixed from a user log. */
    private static void dump(String barSection, Object headerGen, Object folderItem) {
        try {
            StringBuilder sb = new StringBuilder("(IE|InboxFolders) DUMP bar=").append(barSection).append(" slot=")
                    .append(itemReturnType != null ? itemReturnType.getName() : "?")
                    .append(" folderItem=").append(folderItem != null ? folderItem.getClass().getName() : "null");
            Object holders = holders();
            if (holders != null) {
                sb.append(" sections=[");
                for (Field f : holders.getClass().getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    f.setAccessible(true);
                    String n = sectionName(f.get(holders));
                    if (n != null) sb.append(n).append(' ');
                }
                sb.append(']');
            }
            ModuleLog.line(sb.toString());
            if (headerGen == null) { ModuleLog.line("(IE|InboxFolders) DUMP no INBOX_HEADER generator (holders " + (holders != null ? "found" : "missing") + ")"); return; }
            for (Class<?> c = headerGen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                StringBuilder g = new StringBuilder("(IE|InboxFolders) DUMP headerGen ").append(c.getName()).append(" fields:");
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    g.append(' ').append(f.getName()).append(':').append(f.getType().getName());
                }
                g.append(" methods:");
                for (Method m : c.getDeclaredMethods()) {
                    if (Modifier.isStatic(m.getModifiers())) continue;
                    g.append(' ').append(m.getName()).append(java.util.Arrays.toString(m.getParameterTypes()))
                            .append("->").append(m.getReturnType().getName());
                }
                ModuleLog.line(g.toString());
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || !f.getType().getName().startsWith("X.")) continue;
                    for (Constructor<?> k : f.getType().getDeclaredConstructors())
                        ModuleLog.line("(IE|InboxFolders) DUMP ctor " + f.getType().getName()
                                + java.util.Arrays.toString(k.getParameterTypes()));
                }
            }
        } catch (Throwable t) {
            ModuleLog.line("(IE|InboxFolders) DUMP failed: " + t);
        }
    }

    private static String sectionName(Object gen) {
        try {
            if (gen == null || sectionField == null || !sectionField.getDeclaringClass().isInstance(gen)) return null;
            Object e = sectionField.get(gen);
            return e instanceof Enum<?> en ? en.name() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void onSectionItem(XC_MethodHook.MethodHookParam param) throws Throwable {
        String sec = sectionName(param.thisObject);
        if (sec == null) return;
        Object state = param.args != null && param.args.length > 0 ? param.args[0] : null;
        switch (sec) {
            case "INBOX_HEADER": {
                headerSectionSeen = true;
                Object header = param.getResult();
                if (header == null) header = buildHeader(param.thisObject, state);
                if (header != null) param.setResult(header);
                break;
            }
            case "FOLDER":
            case "FILTER_ROW": {
                if (barSlot == null) barSlot = sec;
                Object header = null;
                if (sec.equals(barSlot) && !headerSectionSeen) {
                    Object headerGen = headerGenerator();
                    if (!dumpLogged) { dumpLogged = true; dump(sec, headerGen, param.getResult()); }
                    if (headerGen != null) header = buildHeader(headerGen, state);
                    // The slot must accept a header item, or Instagram's caller would crash on the cast.
                    if (header != null && itemReturnType != null && !itemReturnType.isInstance(header)) {
                        if (!probeLogged) { probeLogged = true; ModuleLog.line("(IE|InboxFolders) header " + header.getClass().getName() + " doesn't fit slot " + itemReturnType.getName()); }
                        header = null;
                    }
                }
                param.setResult(header); // null = just hide the bar
                break;
            }
            default:
        }
    }

    private static Object holders() throws Throwable {
        Object h = holdersRef.get();
        if (h != null) return h;
        Object vmGen = lastVmGeneratorRef.get();
        return vmGen != null && holdersFieldRef != null ? holdersFieldRef.get(vmGen) : null;
    }

    /** The INBOX_HEADER section generator, from the view model generator's section holders. */
    private static Object headerGenerator() throws Throwable {
        Object holders = holders();
        if (holders == null) return null;
        for (Field f : holders.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            f.setAccessible(true);
            Object gen = f.get(holders);
            if ("INBOX_HEADER".equals(sectionName(gen))) return gen;
        }
        return null;
    }

    /** The header item the INBOX_HEADER generator keeps (Instagram's own when it made one), else a new one. */
    private static Object buildHeader(Object headerGen, Object state) throws Throwable {
        Field itemField = null;
        for (Field f : headerGen.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || !f.getType().getName().startsWith("X.")) continue;
            if (headerConstructor(f.getType()) != null) { itemField = f; break; }
        }
        if (itemField == null) {
            if (!probeLogged) { probeLogged = true; ModuleLog.line("(IE|InboxFolders) no header item field with a usable constructor"); }
            return null;
        }
        itemField.setAccessible(true);
        Object existing = itemField.get(headerGen);
        if (existing != null) return existing;

        Constructor<?> ctor = headerConstructor(itemField.getType());
        Class<?>[] types = ctor.getParameterTypes();
        Object[] args = new Object[types.length];
        Context ctx = ps.reso.instaeclipse.xposedcompat.AndroidAppHelper.currentApplication();
        String[] titles = {headerTitle(ctx, true), headerTitle(ctx, false)};
        int str = 0;
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            if (t == String.class || t == CharSequence.class) args[i] = str < titles.length ? titles[str++] : "";
            else if (t == int.class || t == Integer.class) args[i] = 0;          // pending request count
            else if (t == long.class || t == Long.class) args[i] = 0L;
            else if (t == boolean.class || t == Boolean.class) args[i] = Boolean.TRUE;
            else if (t.isEnum()) args[i] = enumFor(t, state);
        }
        if (!ctorLogged) {
            ctorLogged = true;
            ModuleLog.line("(IE|InboxFolders) header item " + ctor.getDeclaringClass().getName()
                    + java.util.Arrays.toString(types));
        }
        Object header = ctor.newInstance(args);
        itemField.set(headerGen, header);
        return header;
    }

    /** A constructor taking two or more Strings and otherwise only enums / numbers / booleans. */
    private static Constructor<?> headerConstructor(Class<?> type) {
        Constructor<?> cached = headerCtor;
        if (cached != null && cached.getDeclaringClass() == type) return cached;
        for (Constructor<?> c : type.getDeclaredConstructors()) {
            int strings = 0;
            boolean ok = true;
            for (Class<?> p : c.getParameterTypes()) {
                if (p == String.class || p == CharSequence.class) strings++;
                else if (!(p.isEnum() || p == int.class || p == Integer.class || p == long.class || p == Long.class
                        || p == boolean.class || p == Boolean.class)) { ok = false; break; }
            }
            if (ok && strings >= 2) {
                c.setAccessible(true);
                headerCtor = c;
                return c;
            }
        }
        return null;
    }

    /** The state's own value of that enum when it has one, else the enum's first constant. */
    private static Object enumFor(Class<?> enumType, Object state) {
        if (state != null) {
            for (Field f : state.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != enumType) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(state);
                    if (v != null) return v;
                } catch (Throwable ignored) {}
            }
        }
        Object[] all = enumType.getEnumConstants();
        return all != null && all.length > 0 ? all[0] : null;
    }

    /** Instagram's own label when one of its string names exists, else the module's translation. */
    @android.annotation.SuppressLint("DiscouragedApi")
    static String headerTitle(Context ctx, boolean messages) {
        String[] names = messages
                ? new String[]{"direct_header_title", "direct_inbox_header_title", "direct_messages_title", "messages"}
                : new String[]{"direct_message_requests", "direct_requests_title", "direct_message_requests_title", "requests"};
        if (ctx != null) {
            for (String n : names) {
                try {
                    int id = ctx.getResources().getIdentifier(n, "string", ctx.getPackageName());
                    if (id == 0) continue;
                    String v = ctx.getString(id);
                    if (v != null && !v.trim().isEmpty() && !v.contains("%")) return v;
                } catch (Throwable ignored) {}
            }
            String mine = ps.reso.instaeclipse.utils.i18n.I18n.t(ctx,
                    messages ? ps.reso.instaeclipse.R.string.ig_direct_header_messages : ps.reso.instaeclipse.R.string.ig_direct_header_requests);
            if (!mine.isEmpty()) return mine;
        }
        return messages ? "Messages" : "Requests";
    }
}
