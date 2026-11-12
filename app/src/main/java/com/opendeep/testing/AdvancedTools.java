package com.opendeep.testing;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.InputType;
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
import java.util.Locale;

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
                "DIAGNOSTIC HISTORY"
        };

        new AlertDialog.Builder(activity)
                .setTitle("ADVANCED TOOLS")
                .setItems(tools, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showOemAnalysis(activity);
                            break;
                        case 1:
                            showPropertyInspector(activity);
                            break;
                        case 2:
                            showPartitionAnalyzer(activity);
                            break;
                        case 3:
                            showCompatibility(activity);
                            break;
                        case 4:
                            showHistoryMenu(activity);
                            break;
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
        String systemAllowed = prop("sys.oem_unlock_allowed");

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

        out.append("sys.oem_unlock_allowed: ")
                .append(value(systemAllowed)).append('\n');

        out.append("Global oem_unlock_allowed: ")
                .append(settingValue(globalAllowed)).append('\n');

        out.append("Secure oem_unlock_allowed: ")
                .append(settingValue(secureAllowed)).append('\n');

        out.append("Global oem_unlock_enabled: ")
                .append(settingValue(globalEnabled)).append('\n');

        out.append("Secure oem_unlock_enabled: ")
                .append(settingValue(secureEnabled)).append('\n');

        out.append("\nANALYSIS\n");

        if ("0".equals(locked)) {
            out.append("The device reports an unlocked bootloader.");
        } else if (isTrue(systemAllowed)) {
            out.append("The runtime OEM unlock property is enabled. ");
            out.append("This is a positive indication, but does not confirm Fastboot authorization.");
        } else if (isFalse(systemAllowed)) {
            out.append("The runtime OEM unlock property is disabled.");
        } else {
            out.append("Insufficient information to determine unlock readiness.");
        }

        out.append("\n\nIMPORTANT\n");
        out.append("Android settings and system properties are only indicators. ");
        out.append("They do not prove that the manufacturer will authorize unlocking.");

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
        result.setText(
                "Enter a property name.\n\n" +
                "Examples:\n" +
                "ro.boot.flash.locked\n" +
                "ro.boot.vbmeta.device_state\n" +
                "ro.treble.enabled\n" +
                "ro.build.version.release\n" +
                "ro.product.cpu.abilist"
        );

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
        out.append("VISIBLE BLOCK DEVICES\n\n");

        BufferedReader reader = null;

        try {
            reader = new BufferedReader(
                    new FileReader("/proc/partitions")
            );

            String line;
            int count = 0;
            int shown = 0;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                if (trimmed.isEmpty()
                        || trimmed.startsWith("major")) {
                    continue;
                }

                String[] parts = trimmed.split("\\s+");

                if (parts.length < 4) continue;

                count++;

                if (shown < 35) {
                    try {
                        long blocks = Long.parseLong(parts[2]);
                        long mb = blocks / 1024;

                        out.append(parts[3])
                                .append(" — ")
                                .append(mb)
                                .append(" MB\n");

                    } catch (Exception ignored) {
                        out.append(parts[3]).append('\n');
                    }

                    shown++;
                }
            }

            out.append("\nDetected entries: ").append(count);

            if (count > shown) {
                out.append("\nAdditional entries omitted.");
            }

        } catch (Exception e) {
            out.append("Block device information is restricted.");
        } finally {
            close(reader);
        }

        out.append("\n\nMOUNT SUMMARY\n");

        reader = null;

        try {
            reader = new BufferedReader(
                    new FileReader("/proc/mounts")
            );

            String line;
            int count = 0;
            int shown = 0;

            while ((line = reader.readLine()) != null) {
                if (line.contains("/dev/block")) {
                    count++;

                    if (shown < 15) {
                        String[] parts = line.split("\\s+");

                        if (parts.length >= 4) {
                            out.append(parts[1])
                                    .append(" — ")
                                    .append(parts[2])
                                    .append(" — ")
                                    .append(parts[3])
                                    .append('\n');
                        }

                        shown++;
                    }
                }
            }

            out.append("\nBlock-backed mounts: ").append(count);

        } catch (Exception e) {
            out.append("Mount information unavailable.");
        } finally {
            close(reader);
        }

        out.append("\n\nREAD-ONLY: no partitions are modified.");

        showText(activity, "PARTITION ANALYZER", out.toString());
    }

    // DEVICE COMPATIBILITY

    private static void showCompatibility(Activity activity) {
        String abi = prop("ro.product.cpu.abilist");
        String treble = prop("ro.treble.enabled");
        String dynamic = prop("ro.boot.dynamic_partitions");
        String ab = prop("ro.build.ab_update");
        String virtualAb = prop("ro.virtual_ab.enabled");
        String vndk = prop("ro.vndk.version");
        String sdk = prop("ro.build.version.sdk");

        StringBuilder out = new StringBuilder();

        out.append("DEVICE COMPATIBILITY\n\n");

        out.append("Android SDK: ").append(value(sdk)).append('\n');
        out.append("CPU ABIs: ").append(value(abi)).append('\n');
        out.append("Treble: ").append(value(treble)).append('\n');
        out.append("Dynamic partitions: ").append(value(dynamic)).append('\n');
        out.append("A/B updates: ").append(value(ab)).append('\n');
        out.append("Virtual A/B: ").append(value(virtualAb)).append('\n');
        out.append("VNDK version: ").append(value(vndk)).append('\n');

        out.append("\nGSI COMPATIBILITY\n");

        if (isTrue(treble)) {
            out.append("Project Treble is reported as enabled.\n");
            out.append("The device may be a candidate for GSI testing.");
        } else if (isFalse(treble)) {
            out.append("Project Treble is reported as disabled.");
        } else {
            out.append("Treble status could not be determined.");
        }

        out.append("\n\n64-BIT SUPPORT\n");

        if (abi.contains("arm64-v8a")) {
            out.append("ARM64 ABI is available.");
        } else {
            out.append("ARM64 ABI was not confirmed.");
        }

        out.append("\n\nLIMITATIONS\n");
        out.append("This is a preliminary compatibility check, not a GSI installation guarantee. ");
        out.append("Vendor compatibility, AVB, partition layout and bootloader authorization must also be checked.");

        showText(activity, "DEVICE COMPATIBILITY", out.toString());
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
                    if (which == 0) {
                        saveSnapshot(activity);
                    } else if (which == 1) {
                        viewHistory(activity);
                    } else if (which == 2) {
                        clearHistory(activity);
                    }
                })
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private static void saveSnapshot(Activity activity) {
        SharedPreferences prefs =
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        JSONArray history;

        try {
            history = new JSONArray(
                    prefs.getString(HISTORY, "[]")
            );
        } catch (Exception e) {
            history = new JSONArray();
        }

        JSONObject snapshot = new JSONObject();

        try {
            snapshot.put("date",
                    new SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss",
                            Locale.getDefault()
                    ).format(new Date()));

            snapshot.put("manufacturer", android.os.Build.MANUFACTURER);
            snapshot.put("model", android.os.Build.MODEL);
            snapshot.put("android", android.os.Build.VERSION.RELEASE);
            snapshot.put("fingerprint", android.os.Build.FINGERPRINT);

            snapshot.put("flash_locked",
                    prop("ro.boot.flash.locked"));

            snapshot.put("avb",
                    prop("ro.boot.vbmeta.device_state"));

            snapshot.put("verified_boot",
                    prop("ro.boot.verifiedbootstate"));

            snapshot.put("oem_unlock_allowed",
                    prop("sys.oem_unlock_allowed"));

            snapshot.put("oem_unlock_supported",
                    prop("ro.oem_unlock_supported"));

            history.put(snapshot);

            while (history.length() > 10) {
                history.remove(0);
            }

            prefs.edit()
                    .putString(HISTORY, history.toString())
                    .apply();

            Toast.makeText(
                    activity,
                    "Snapshot saved",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {
            Toast.makeText(
                    activity,
                    "Could not save snapshot",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private static void viewHistory(Activity activity) {
        SharedPreferences prefs =
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        JSONArray history;

        try {
            history = new JSONArray(
                    prefs.getString(HISTORY, "[]")
            );
        } catch (Exception e) {
            history = new JSONArray();
        }

        StringBuilder out = new StringBuilder();

        out.append("SAVED DIAGNOSTIC SNAPSHOTS\n\n");

        if (history.length() == 0) {
            out.append("No saved snapshots yet.");
        }

        for (int i = history.length() - 1; i >= 0; i--) {
            JSONObject item = history.optJSONObject(i);

            if (item == null) continue;

            out.append("SNAPSHOT ")
                    .append(i + 1)
                    .append('\n');

            out.append("Date: ")
                    .append(item.optString("date", "unknown"))
                    .append('\n');

            out.append("Device: ")
                    .append(item.optString("manufacturer"))
                    .append(' ')
                    .append(item.optString("model"))
                    .append('\n');

            out.append("Android: ")
                    .append(item.optString("android"))
                    .append('\n');

            out.append("Flash locked: ")
                    .append(item.optString("flash_locked"))
                    .append('\n');

            out.append("AVB: ")
                    .append(item.optString("avb"))
                    .append('\n');

            out.append("Verified Boot: ")
                    .append(item.optString("verified_boot"))
                    .append('\n');

            out.append("OEM unlock allowed: ")
                    .append(item.optString("oem_unlock_allowed"))
                    .append("\n\n");
        }

        showText(activity, "DIAGNOSTIC HISTORY", out.toString());
    }

    private static void clearHistory(final Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("CLEAR HISTORY?")
                .setMessage("All saved diagnostic snapshots will be deleted.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("CLEAR", (dialog, which) -> {
                    activity.getSharedPreferences(
                            PREFS,
                            Context.MODE_PRIVATE
                    ).edit().remove(HISTORY).apply();

                    Toast.makeText(
                            activity,
                            "History cleared",
                            Toast.LENGTH_SHORT
                    ).show();
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
        view.setPadding(
                dp(activity, 18),
                dp(activity, 15),
                dp(activity, 18),
                dp(activity, 18)
        );

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
                    activity.getContentResolver(),
                    key,
                    -1
            );
        } catch (Exception e) {
            return -1;
        }
    }

    private static int settingSecure(Activity activity, String key) {
        try {
            return Settings.Secure.getInt(
                    activity.getContentResolver(),
                    key,
                    -1
            );
        } catch (Exception e) {
            return -1;
        }
    }

    private static String settingValue(int value) {
        if (value == 1) return "1";
        if (value == 0) return "0";
        return "not exposed";
    }

    private static String value(String text) {
        return text == null || text.trim().isEmpty()
                ? "unavailable"
                : text;
    }

    private static boolean isTrue(String value) {
        return "1".equals(value)
                || "true".equalsIgnoreCase(value);
    }

    private static boolean isFalse(String value) {
        return "0".equals(value)
                || "false".equalsIgnoreCase(value);
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
        return (int) (
                value * context.getResources()
                        .getDisplayMetrics().density + 0.5f
        );
    }
}
