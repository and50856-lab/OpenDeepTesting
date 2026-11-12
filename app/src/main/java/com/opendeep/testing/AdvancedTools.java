package com.opendeep.testing;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class AdvancedTools {

    private static final String PREFS = "open_deep_testing_history";
    private static final String HISTORY = "snapshots";

    private AdvancedTools() {
    }

    public static void show(final Activity activity) {
        String[] tools = {
                "OEM UNLOCK ANALYSIS",
                "PROPERTY INSPECTOR",
                "PARTITION ANALYZER",
                "DEVICE COMPATIBILITY",
                "DIAGNOSTIC HISTORY",
                "GSI INSPECTOR",
                "BOOTLOADER COMMAND GUIDE",
                "SYSTEM PROPERTY DATABASE",
                "PARTITION MAP",
                "HISTORY COMPARISON"
        };

        new AlertDialog.Builder(activity)
                .setTitle("ADVANCED TOOLS")
                .setItems(tools, (dialog, which) -> {
                    switch (which) {
                        case 0: showOemAnalysis(activity); break;
                        case 1: showPropertyInspector(activity); break;
                        case 2: showPartitionAnalyzer(activity); break;
                        case 3: showCompatibility(activity); break;
                        case 4: showHistoryMenu(activity); break;
                        case 5: showGsiInspector(activity); break;
                        case 6: showBootloaderGuide(activity); break;
                        case 7: showPropertyDatabase(activity); break;
                        case 8: showPartitionMap(activity); break;
                        case 9: compareHistory(activity); break;
                    }
                })
                .setNegativeButton("CLOSE", null)
                .show();
    }

    // OEM UNLOCK ANALYSIS

    private static void showOemAnalysis(Activity activity) {
        String locked = prop("ro.boot.flash.locked");
        String avb = prop("ro.boot.vbmeta.device_state");
        String verified = prop("ro.boot.verifiedbootstate");
        String supported = prop("ro.oem_unlock_supported");
        String allowed = prop("sys.oem_unlock_allowed");

        int globalAllowed = settingGlobal(activity, "oem_unlock_allowed");
        int secureAllowed = settingSecure(activity, "oem_unlock_allowed");
        int globalEnabled = settingGlobal(activity, "oem_unlock_enabled");
        int secureEnabled = settingSecure(activity, "oem_unlock_enabled");

        StringBuilder out = new StringBuilder();

        out.append("OEM UNLOCK ANALYSIS\n\n");
        out.append("Bootloader: ").append(value(locked)).append('\n');
        out.append("AVB state: ").append(value(avb)).append('\n');
        out.append("Verified Boot: ").append(value(verified)).append('\n');
        out.append("OEM unlock supported: ").append(value(supported)).append('\n');

        out.append("\nUNLOCK FLAGS\n");
        out.append("Runtime property: ").append(value(allowed)).append('\n');
        out.append("Global allowed: ").append(settingValue(globalAllowed)).append('\n');
        out.append("Secure allowed: ").append(settingValue(secureAllowed)).append('\n');
        out.append("Global enabled: ").append(settingValue(globalEnabled)).append('\n');
        out.append("Secure enabled: ").append(settingValue(secureEnabled)).append('\n');

        out.append("\nANALYSIS\n");

        if ("0".equals(locked)) {
            out.append("The device reports an unlocked bootloader.");
        } else if (isTrue(allowed)) {
            out.append("The runtime OEM unlock property is enabled. ");
            out.append("This is a positive indication, but Fastboot authorization is not confirmed.");
        } else if (isFalse(allowed)) {
            out.append("The runtime OEM unlock property is disabled.");
        } else {
            out.append("Insufficient information to determine unlock readiness.");
        }

        out.append("\n\nAndroid settings and properties do not prove that the manufacturer will authorize unlocking.");

        showText(activity, "OEM UNLOCK ANALYSIS", out.toString());
    }

    // PROPERTY INSPECTOR

    private static void showPropertyInspector(final Activity activity) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(activity, 18), dp(activity, 12),
                dp(activity, 18), dp(activity, 12));

        final EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Example: ro.boot.flash.locked");
        layout.addView(input);

        Button search = new Button(activity);
        search.setText("READ PROPERTY");
        layout.addView(search);

        final TextView result = new TextView(activity);
        result.setText("Enter a property name.");
        result.setTextIsSelectable(true);
        result.setPadding(0, dp(activity, 15), 0, dp(activity, 5));
        layout.addView(result);

        search.setOnClickListener(v -> {
            String key = input.getText().toString().trim();

            if (key.isEmpty() || !key.matches("[A-Za-z0-9._-]+")) {
                result.setText("Invalid property name.");
                return;
            }

            result.setText(key + "\n\n" + value(prop(key)));
        });

        new AlertDialog.Builder(activity)
                .setTitle("PROPERTY INSPECTOR")
                .setView(layout)
                .setPositiveButton("CLOSE", null)
                .show();
    }

    // PARTITION ANALYZER

    private static void showPartitionAnalyzer(Activity activity) {
        StringBuilder out = new StringBuilder();
        out.append("PARTITION ANALYZER\n\n");

        out.append("BLOCK DEVICES\n\n");
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new FileReader("/proc/partitions"));
            String line;
            int shown = 0;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                if (trimmed.isEmpty() || trimmed.startsWith("major")) {
                    continue;
                }

                String[] parts = trimmed.split("\\s+");
                if (parts.length < 4) continue;

                if (shown < 40) {
                    try {
                        long blocks = Long.parseLong(parts[2]);
                        out.append(parts[3])
                                .append(" — ")
                                .append(blocks / 1024)
                                .append(" MB\n");
                    } catch (Exception ignored) {
                        out.append(parts[3]).append('\n');
                    }
                    shown++;
                }
            }

            if (shown == 0) {
                out.append("No accessible block devices.\n");
            }

        } catch (Exception e) {
            out.append("Block device information is restricted.\n");
        } finally {
            close(reader);
        }

        out.append("\nREAD-ONLY: no partitions are modified.");

        showText(activity, "PARTITION ANALYZER", out.toString());
    }

    // DEVICE COMPATIBILITY

    private static void showCompatibility(Activity activity) {
        StringBuilder out = new StringBuilder();

        out.append("DEVICE COMPATIBILITY\n\n");
        out.append("Android SDK: ").append(prop("ro.build.version.sdk")).append('\n');
        out.append("CPU ABIs: ").append(prop("ro.product.cpu.abilist")).append('\n');
        out.append("Treble: ").append(prop("ro.treble.enabled")).append('\n');
        out.append("Dynamic partitions: ").append(prop("ro.boot.dynamic_partitions")).append('\n');
        out.append("A/B updates: ").append(prop("ro.build.ab_update")).append('\n');
        out.append("Virtual A/B: ").append(prop("ro.virtual_ab.enabled")).append('\n');
        out.append("VNDK: ").append(prop("ro.vndk.version")).append('\n');

        out.append("\nThis is a preliminary compatibility check. It does not guarantee that a custom ROM or GSI will boot.");

        showText(activity, "DEVICE COMPATIBILITY", out.toString());
    }

    // GSI INSPECTOR

    private static void showGsiInspector(Activity activity) {
        String treble = prop("ro.treble.enabled");
        String abi = prop("ro.product.cpu.abilist");
        String vndk = prop("ro.vndk.version");
        String vendorApi = prop("ro.vendor.api_level");
        String sdk = prop("ro.build.version.sdk");
        String dynamic = prop("ro.boot.dynamic_partitions");
        String ab = prop("ro.build.ab_update");
        String virtualAb = prop("ro.virtual_ab.enabled");
        String slot = prop("ro.boot.slot_suffix");
        String fbe = prop("ro.crypto.type");

        StringBuilder out = new StringBuilder();

        out.append("GSI INSPECTOR\n\n");
        out.append("Treble enabled: ").append(value(treble)).append('\n');
        out.append("CPU architecture: ").append(value(abi)).append('\n');
        out.append("Android SDK: ").append(value(sdk)).append('\n');
        out.append("Vendor API level: ").append(value(vendorApi)).append('\n');
        out.append("VNDK version: ").append(value(vndk)).append('\n');
        out.append("Dynamic partitions: ").append(value(dynamic)).append('\n');
        out.append("A/B updates: ").append(value(ab)).append('\n');
        out.append("Virtual A/B: ").append(value(virtualAb)).append('\n');
        out.append("Slot suffix: ").append(value(slot)).append('\n');
        out.append("Encryption type: ").append(value(fbe)).append('\n');

        out.append("\nPRELIMINARY RESULT\n");

        if (isTrue(treble) && abi.contains("arm64-v8a")) {
            out.append("Basic GSI prerequisites are partially present: Treble and ARM64 ABI.");
        } else if (isFalse(treble)) {
            out.append("Treble is reported as disabled.");
        } else {
            out.append("Not enough information to assess basic GSI prerequisites.");
        }

        out.append("\n\nIMPORTANT\n");
        out.append("This app cannot verify vendor compatibility, kernel compatibility, AVB acceptance or bootloader unlock authorization.");
        out.append(" Do not flash a GSI based only on this report.");

        showText(activity, "GSI INSPECTOR", out.toString());
    }

    // BOOTLOADER COMMAND GUIDE

    private static void showBootloaderGuide(Activity activity) {
        String text =
                "BOOTLOADER COMMAND GUIDE\n\n" +
                "These commands are intended for a computer with Android Platform Tools.\n\n" +

                "1. fastboot devices\n" +
                "Checks whether the computer detects a Fastboot device.\n\n" +

                "2. fastboot getvar product\n" +
                "Requests the product identifier.\n\n" +

                "3. fastboot getvar unlocked\n" +
                "Requests the bootloader unlock state, if supported.\n\n" +

                "4. fastboot getvar secure\n" +
                "Requests the secure bootloader flag, if supported.\n\n" +

                "5. fastboot getvar current-slot\n" +
                "Requests the active slot on devices that use slots.\n\n" +

                "6. fastboot getvar all\n" +
                "Requests all variables exposed by the bootloader.\n\n" +

                "7. fastboot flashing get_unlock_ability\n" +
                "Requests whether unlocking is currently permitted, if implemented.\n\n" +

                "A failed or unknown command does not automatically mean that unlocking is impossible.\n\n" +
                "This guide contains query commands only. It does not issue unlock, erase, flash or format commands.";

        showText(activity, "BOOTLOADER COMMAND GUIDE", text);
    }

    // SYSTEM PROPERTY DATABASE

    private static void showPropertyDatabase(Activity activity) {
        String[] names = {
                "ro.boot.flash.locked",
                "ro.boot.vbmeta.device_state",
                "ro.boot.verifiedbootstate",
                "ro.oem_unlock_supported",
                "sys.oem_unlock_allowed",
                "ro.treble.enabled",
                "ro.product.cpu.abilist",
                "ro.build.version.sdk",
                "ro.build.version.release",
                "ro.vndk.version",
                "ro.vendor.api_level",
                "ro.boot.dynamic_partitions",
                "ro.build.ab_update",
                "ro.virtual_ab.enabled",
                "ro.crypto.state",
                "ro.crypto.type",
                "ro.boot.slot_suffix",
                "ro.board.platform",
                "ro.boot.hardware"
        };

        String[] descriptions = {
                "Bootloader flash lock state. Commonly 1 means locked, 0 means unlocked.",
                "Android Verified Boot device state.",
                "Verified Boot state. Green commonly indicates a verified locked boot chain.",
                "Whether the OEM reports bootloader unlocking support.",
                "Runtime property related to OEM unlock permission.",
                "Whether Project Treble is enabled.",
                "Supported CPU application binary interfaces.",
                "Android SDK/API level.",
                "Android release version.",
                "Vendor Native Development Kit version.",
                "Vendor interface API level, if exposed.",
                "Whether dynamic partitions are reported by the boot configuration.",
                "Whether the build reports A/B update support.",
                "Whether Virtual A/B updates are reported.",
                "Encryption state.",
                "Encryption mode, such as file-based encryption.",
                "Boot slot suffix, if the device exposes one.",
                "Hardware board identifier.",
                "Boot hardware identifier."
        };

        new AlertDialog.Builder(activity)
                .setTitle("SYSTEM PROPERTY DATABASE")
                .setItems(names, (dialog, which) -> {
                    String result = names[which] + "\n\n";
                    result += descriptions[which] + "\n\n";
                    result += "Current value: " + value(prop(names[which]));
                    showText(activity, "PROPERTY DETAILS", result);
                })
                .setNegativeButton("CLOSE", null)
                .show();
    }

    // PARTITION MAP

    private static void showPartitionMap(Activity activity) {
        LinkedHashMap<String, String> mounts = new LinkedHashMap<>();
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new FileReader("/proc/mounts"));
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");

                if (parts.length < 4) continue;

                String source = parts[0];
                String mountPoint = parts[1];
                String filesystem = parts[2];
                String options = parts[3];

                if (!source.contains("/dev/block")) continue;

                if (!mounts.containsKey(mountPoint)) {
                    mounts.put(
                            mountPoint,
                            "Source: " + source +
                            "\nFilesystem: " + filesystem +
                            "\nOptions: " + options
                    );
                }
            }

        } catch (Exception ignored) {
        } finally {
            close(reader);
        }

        StringBuilder out = new StringBuilder();
        out.append("PARTITION MAP\n\n");

        if (mounts.isEmpty()) {
            out.append("No block-backed mount points are accessible.");
        } else {
            out.append("Unique mount points: ")
                    .append(mounts.size())
                    .append("\n\n");

            for (Map.Entry<String, String> entry : mounts.entrySet()) {
                out.append(entry.getKey())
                        .append('\n')
                        .append(entry.getValue())
                        .append("\n\n");
            }
        }

        out.append("Note: mount points are not the same as the complete physical partition table.");

        showText(activity, "PARTITION MAP", out.toString());
    }

    // DIAGNOSTIC HISTORY

    private static void showHistoryMenu(final Activity activity) {
        String[] options = {
                "SAVE CURRENT SNAPSHOT",
                "VIEW SAVED HISTORY",
                "CLEAR HISTORY"
        };

        new AlertDialog.Builder(activity)
                .setTitle("DIAGNOSTIC HISTORY")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) saveSnapshot(activity);
                    if (which == 1) viewHistory(activity);
                    if (which == 2) clearHistory(activity);
                })
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private static JSONArray getHistory(Activity activity) {
        try {
            SharedPreferences prefs =
                    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

            return new JSONArray(prefs.getString(HISTORY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void saveSnapshot(Activity activity) {
        JSONArray history = getHistory(activity);
        JSONObject snapshot = new JSONObject();

        try {
            snapshot.put("date", new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()
            ).format(new Date()));

            snapshot.put("manufacturer", android.os.Build.MANUFACTURER);
            snapshot.put("model", android.os.Build.MODEL);
            snapshot.put("android", android.os.Build.VERSION.RELEASE);
            snapshot.put("fingerprint", android.os.Build.FINGERPRINT);
            snapshot.put("flash_locked", prop("ro.boot.flash.locked"));
            snapshot.put("avb", prop("ro.boot.vbmeta.device_state"));
            snapshot.put("verified_boot", prop("ro.boot.verifiedbootstate"));
            snapshot.put("oem_unlock_allowed", prop("sys.oem_unlock_allowed"));
            snapshot.put("oem_unlock_supported", prop("ro.oem_unlock_supported"));
            snapshot.put("treble", prop("ro.treble.enabled"));
            snapshot.put("vndk", prop("ro.vndk.version"));
            snapshot.put("vendor_api", prop("ro.vendor.api_level"));

            history.put(snapshot);

            while (history.length() > 10) {
                history.remove(0);
            }

            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(HISTORY, history.toString())
                    .apply();

            Toast.makeText(activity, "Snapshot saved", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(activity, "Could not save snapshot", Toast.LENGTH_SHORT).show();
        }
    }

    private static void viewHistory(Activity activity) {
        JSONArray history = getHistory(activity);
        StringBuilder out = new StringBuilder();

        out.append("SAVED DIAGNOSTIC SNAPSHOTS\n\n");

        if (history.length() == 0) {
            out.append("No saved snapshots yet.");
        }

        for (int i = history.length() - 1; i >= 0; i--) {
            JSONObject item = history.optJSONObject(i);
            if (item == null) continue;

            out.append("SNAPSHOT ").append(i + 1).append('\n');
            out.append("Date: ").append(item.optString("date")).append('\n');
            out.append("Device: ")
                    .append(item.optString("manufacturer"))
                    .append(' ')
                    .append(item.optString("model")).append('\n');
            out.append("Android: ").append(item.optString("android")).append('\n');
            out.append("Flash locked: ").append(item.optString("flash_locked")).append('\n');
            out.append("AVB: ").append(item.optString("avb")).append('\n');
            out.append("Verified Boot: ").append(item.optString("verified_boot")).append('\n');
            out.append("OEM unlock allowed: ").append(item.optString("oem_unlock_allowed")).append("\n\n");
        }

        showText(activity, "DIAGNOSTIC HISTORY", out.toString());
    }

    // HISTORY COMPARISON

    private static void compareHistory(final Activity activity) {
        final JSONArray history = getHistory(activity);

        if (history.length() < 2) {
            showText(activity, "HISTORY COMPARISON",
                    "At least two saved snapshots are required.\n\nSave another snapshot after a system change, then compare them.");
            return;
        }

        final String[] labels = new String[history.length()];

        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            labels[i] = "Snapshot " + (i + 1) + " — " +
                    (item == null ? "unknown" : item.optString("date"));
        }

        new AlertDialog.Builder(activity)
                .setTitle("SELECT OLDER SNAPSHOT")
                .setItems(labels, (dialog, first) -> {
                    new AlertDialog.Builder(activity)
                            .setTitle("SELECT NEWER SNAPSHOT")
                            .setItems(labels, (dialog2, second) -> {
                                if (first == second) {
                                    Toast.makeText(activity,
                                            "Choose two different snapshots",
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                showComparison(activity, history, first, second);
                            })
                            .setNegativeButton("CANCEL", null)
                            .show();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private static void showComparison(
            Activity activity,
            JSONArray history,
            int first,
            int second
    ) {
        JSONObject a = history.optJSONObject(first);
        JSONObject b = history.optJSONObject(second);

        if (a == null || b == null) return;

        String[] keys = {
                "flash_locked",
                "avb",
                "verified_boot",
                "oem_unlock_allowed",
                "oem_unlock_supported",
                "treble",
                "vndk",
                "vendor_api",
                "android",
                "fingerprint"
        };

        String[] labels = {
                "Bootloader lock",
                "AVB state",
                "Verified Boot",
                "OEM unlock allowed",
                "OEM unlock supported",
                "Treble",
                "VNDK",
                "Vendor API",
                "Android",
                "Build fingerprint"
        };

        StringBuilder out = new StringBuilder();

        out.append("HISTORY COMPARISON\n\n");
        out.append("A: ").append(a.optString("date")).append('\n');
        out.append("B: ").append(b.optString("date")).append("\n\n");

        boolean changed = false;

        for (int i = 0; i < keys.length; i++) {
            String oldValue = a.optString(keys[i], "not recorded");
            String newValue = b.optString(keys[i], "not recorded");

            out.append(labels[i]).append('\n');
            out.append("A: ").append(oldValue).append('\n');
            out.append("B: ").append(newValue).append('\n');

            if (!oldValue.equals(newValue)) {
                out.append("CHANGE DETECTED\n");
                changed = true;
            } else {
                out.append("No change\n");
            }

            out.append('\n');
        }

        if (!changed) {
            out.append("No differences found in the recorded fields.");
        }

        showText(activity, "HISTORY COMPARISON", out.toString());
    }

    private static void clearHistory(final Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("CLEAR HISTORY?")
                .setMessage("All saved diagnostic snapshots will be deleted.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("CLEAR", (dialog, which) -> {
                    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .remove(HISTORY)
                            .apply();

                    Toast.makeText(activity, "History cleared",
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // HELPERS

    private static void showText(
            Activity activity,
            String title,
            String text
    ) {
        ScrollView scroll = new ScrollView(activity);

        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(14);
        view.setTextIsSelectable(true);
        view.setPadding(dp(activity, 18), dp(activity, 15),
                dp(activity, 18), dp(activity, 18));

        scroll.addView(view);

        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("CLOSE", null)
                .show();
    }

    private static String prop(String key) {
        BufferedReader reader = null;

        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{"getprop", key}
            );

            reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String result = reader.readLine();
            process.waitFor();

            return result == null || result.trim().isEmpty()
                    ? "unavailable"
                    : result.trim();

        } catch (Exception e) {
            return "unavailable";
        } finally {
            close(reader);
        }
    }

    private static int settingGlobal(Activity activity, String key) {
        try {
            return Settings.Global.getInt(
                    activity.getContentResolver(), key, -1);
        } catch (Exception e) {
            return -1;
        }
    }

    private static int settingSecure(Activity activity, String key) {
        try {
            return Settings.Secure.getInt(
                    activity.getContentResolver(), key, -1);
        } catch (Exception e) {
            return -1;
        }
    }

    private static String settingValue(int value) {
        if (value == 1) return "1 (enabled)";
        if (value == 0) return "0 (disabled)";
        return "not exposed";
    }

    private static String value(String text) {
        return text == null || text.trim().isEmpty()
                ? "unavailable"
                : text;
    }

    private static boolean isTrue(String text) {
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    private static boolean isFalse(String text) {
        return "0".equals(text) || "false".equalsIgnoreCase(text);
    }

    private static void close(BufferedReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources()
                .getDisplayMetrics().density + 0.5f);
    }
}
