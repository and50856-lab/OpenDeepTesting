package com.opendeep.testing;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StatFs;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int REPORT_REQUEST = 1001;

    private LinearLayout root;
    private final Map<String, String> props = new LinkedHashMap<>();
    private String reportText = "";
    private String readiness = "UNKNOWN";
    private String readinessExplanation = "";

    private final String[] propertyKeys = {
            "ro.boot.flash.locked",
            "ro.boot.vbmeta.device_state",
            "ro.boot.verifiedbootstate",
            "ro.oem_unlock_supported",
            "sys.oem_unlock_allowed",
            "ro.bootmode",
            "ro.boot.bootreason",
            "ro.boot.slot_suffix",
            "ro.boot.hardware",
            "ro.board.platform",
            "ro.bootloader",
            "ro.build.type",
            "ro.build.tags",
            "ro.crypto.state",
            "ro.crypto.type",
            "init.svc.adbd",
            "sys.usb.config",
            "persist.sys.usb.config",
            "service.adb.tcp.port",
            "ro.product.cpu.abi",
            "ro.product.cpu.abilist"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
    }

    private void buildScreen() {
        props.clear();

        for (String key : propertyKeys) {
            props.put(key, getProp(key));
        }

        calculateReadiness();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(30));
        root.setBackgroundColor(0xFF101318);

        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("OPEN DEEP TESTING");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(25);
        title.setTypeface(null, 1);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Device diagnostics • Version 1.0.0");
        subtitle.setTextColor(0xFF9AA4B2);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(6), 0, dp(18));
        root.addView(subtitle);

        addButton("REFRESH DIAGNOSTICS", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buildScreen();
                Toast.makeText(
                        MainActivity.this,
                        "Diagnostics refreshed",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        addButton("ADVANCED TOOLS", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AdvancedTools.show(MainActivity.this);
            }
        });

        addSection("DEVICE INFORMATION");

        addInfo("Manufacturer", Build.MANUFACTURER);
        addInfo("Brand", Build.BRAND);
        addInfo("Model", Build.MODEL);
        addInfo("Device codename", Build.DEVICE);
        addInfo("Product", Build.PRODUCT);
        addInfo("Hardware", Build.HARDWARE);
        addInfo("Board", Build.BOARD);
        addInfo("Bootloader", unknown(Build.BOOTLOADER));
        addInfo("Android", Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")");
        addInfo("Build display", Build.DISPLAY);
        addInfo("Build ID", Build.ID);
        addInfo("Build fingerprint", Build.FINGERPRINT);

        if (Build.VERSION.SDK_INT >= 23) {
            addInfo("Security patch", Build.VERSION.SECURITY_PATCH);
        }

        addInfo("Build type", props.get("ro.build.type"));
        addInfo("Build tags", props.get("ro.build.tags"));

        addSection("PROCESSOR & MEMORY");

        addInfo("Supported ABIs", join(Build.SUPPORTED_ABIS));
        addInfo("Available CPU cores",
                String.valueOf(Runtime.getRuntime().availableProcessors()));
        addInfo("CPU information", readCpuInfo());
        addInfo("Total RAM", getRamInfo());
        addInfo("App data storage", getStorageInfo());

        addSection("BOOT MODE");

        addInfo("Current Android state",
                "The application is currently running inside Android");

        addInfo("ro.bootmode", props.get("ro.bootmode"));
        addInfo("Boot reason", props.get("ro.boot.bootreason"));
        addInfo("Slot suffix", props.get("ro.boot.slot_suffix"));
        addInfo("Boot hardware", props.get("ro.boot.hardware"));
        addInfo("Board platform", props.get("ro.board.platform"));

        addSection("BOOTLOADER DIAGNOSTICS");

        addInfo("ro.boot.flash.locked",
                props.get("ro.boot.flash.locked"));

        addInfo("AVB device state",
                props.get("ro.boot.vbmeta.device_state"));

        addInfo("Verified Boot state",
                props.get("ro.boot.verifiedbootstate"));

        addInfo("OEM unlock supported",
                props.get("ro.oem_unlock_supported"));

        addInfo("sys.oem_unlock_allowed",
                props.get("sys.oem_unlock_allowed"));

        addInfo("OEM unlock setting",
                getOemUnlockSetting());

        addInfo("Encryption state",
                props.get("ro.crypto.state"));

        addInfo("Encryption type",
                props.get("ro.crypto.type"));

        addSection("UNLOCK READINESS");

        TextView status = new TextView(this);
        status.setText("STATUS: " + readiness);
        status.setTextSize(20);
        status.setTypeface(null, 1);
        status.setTextColor(getReadinessColor());
        status.setPadding(dp(14), dp(15), dp(14), dp(10));
        status.setBackgroundColor(0xFF1B222C);
        root.addView(status);

        TextView explanation = new TextView(this);
        explanation.setText(readinessExplanation);
        explanation.setTextColor(0xFFD4DAE3);
        explanation.setTextSize(14);
        explanation.setPadding(dp(14), dp(10), dp(14), dp(14));
        explanation.setBackgroundColor(0xFF1B222C);
        root.addView(explanation);

        addSection("ADB DIAGNOSTICS");

        addInfo("ADB daemon state", props.get("init.svc.adbd"));
        addInfo("USB configuration", props.get("sys.usb.config"));
        addInfo("Persistent USB configuration",
                props.get("persist.sys.usb.config"));
        addInfo("ADB TCP port", props.get("service.adb.tcp.port"));

        addInfo("ADB connection",
                "The phone cannot reliably determine whether a computer is connected.");

        addInfo("Computer check",
                "Connect the phone with USB debugging enabled. Run: adb devices");

        addButton("COPY ADB COMMAND", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copy("adb devices");
            }
        });

        addSection("FASTBOOT");

        addInfo("Fastboot detection",
                "Cannot be checked from inside the running Android system.");

        addInfo("How to check",
                "Boot the phone into Fastboot mode, connect it to a computer, then run fastboot devices.");

        addButton("COPY FASTBOOT COMMAND", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copy("fastboot devices");
            }
        });

        addSection("PARTITIONS");

        addInfo("Partition layout", readPartitions());
        addInfo("Mount information", readMounts());

        addSection("DIAGNOSTIC REPORT");

        addInfo("Report contents",
                "Device information, system properties, bootloader diagnostics, unlock readiness, ADB/Fastboot information and accessible partition data.");

        addButton("EXPORT FULL TXT REPORT", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createReport();
            }
        });

        addButton("COPY FULL REPORT", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copy(reportText);
                Toast.makeText(
                        MainActivity.this,
                        "Report copied",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        addSection("IMPORTANT");

        addInfo("Safety",
                "OpenDeepTesting performs read-only diagnostics. It does not unlock the bootloader, bypass OEM authorization, write partitions, flash firmware or contact OPPO servers.");

        addInfo("Limitations",
                "Some properties and partition information may be hidden by Android permissions, SELinux or the device manufacturer. UNKNOWN does not mean impossible.");

        reportText = buildReport();

        setContentView(scroll);
    }

    private void calculateReadiness() {
        String locked = props.get("ro.boot.flash.locked");
        String supported = props.get("ro.oem_unlock_supported");
        String systemAllowed = props.get("sys.oem_unlock_allowed");
        int globalAllowed = readGlobalUnlockSetting();

        if ("0".equals(locked)) {
            readiness = "UNLOCKED";
            readinessExplanation =
                    "The flash lock property reports 0. The device appears to have an unlocked bootloader.";
            return;
        }

        boolean supportYes = isTrue(supported);
        boolean supportNo = isFalse(supported);

        boolean allowedYes =
                isTrue(systemAllowed) || globalAllowed == 1;

        boolean allowedNo =
                isFalse(systemAllowed) || globalAllowed == 0;

        if (supportYes && allowedYes) {
            readiness = "POSSIBLE";
            readinessExplanation =
                    "The available properties indicate OEM unlock support and an enabled unlock setting. This does not guarantee that the bootloader can actually be unlocked.";
        } else if (supportNo || allowedNo) {
            readiness = "RESTRICTED";
            readinessExplanation =
                    "At least one available property indicates that OEM unlocking is unsupported or currently disabled. This may be a setting restriction, not necessarily a permanent manufacturer lock.";
        } else {
            readiness = "UNKNOWN";
            readinessExplanation =
                    "There is not enough accessible information to determine unlock readiness. The manufacturer may hide the relevant properties or require server-side authorization.";
        }
    }

    private int getReadinessColor() {
        if ("POSSIBLE".equals(readiness)) return 0xFF70D69A;
        if ("RESTRICTED".equals(readiness)) return 0xFFFFC078;
        if ("UNLOCKED".equals(readiness)) return 0xFF70B7FF;
        return 0xFFE6EAF0;
    }

    private int readGlobalUnlockSetting() {
        try {
            return Settings.Global.getInt(
                    getContentResolver(),
                    "oem_unlock_allowed",
                    -1
            );
        } catch (Exception e) {
            return -1;
        }
    }

    private String getOemUnlockSetting() {
        int value = readGlobalUnlockSetting();

        if (value == 1) return "enabled (1)";
        if (value == 0) return "disabled (0)";
        return "not exposed / unknown";
    }

    private boolean isTrue(String value) {
        return "1".equals(value)
                || "true".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value);
    }

    private boolean isFalse(String value) {
        return "0".equals(value)
                || "false".equalsIgnoreCase(value)
                || "no".equalsIgnoreCase(value);
    }

    private String getProp(String key) {
        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{"getprop", key}
            );

            BufferedReader reader = new BufferedReader(
                    new java.io.InputStreamReader(
                            process.getInputStream()
                    )
            );

            String value = reader.readLine();
            reader.close();
            process.waitFor();

            if (value == null || value.trim().isEmpty()) {
                return "unavailable";
            }

            return value.trim();

        } catch (Exception e) {
            return "unavailable";
        }
    }

    private String readCpuInfo() {
        StringBuilder result = new StringBuilder();
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(
                    new FileReader("/proc/cpuinfo")
            );

            String line;
            int count = 0;

            while ((line = reader.readLine()) != null) {
                String lower = line.toLowerCase(Locale.US);

                if (lower.startsWith("hardware")
                        || lower.startsWith("model name")
                        || lower.startsWith("processor")
                        || lower.startsWith("cpu architecture")) {

                    if (result.length() > 0) result.append('\n');

                    result.append(line.trim());
                    count++;

                    if (count >= 8) break;
                }
            }

        } catch (Exception e) {
            return "unavailable";
        } finally {
            close(reader);
        }

        return result.length() == 0
                ? "unavailable"
                : result.toString();
    }

    private String getRamInfo() {
        try {
            ActivityManager manager =
                    (ActivityManager) getSystemService(
                            Context.ACTIVITY_SERVICE
                    );

            ActivityManager.MemoryInfo info =
                    new ActivityManager.MemoryInfo();

            manager.getMemoryInfo(info);

            long total = info.totalMem / (1024 * 1024);
            long available = info.availMem / (1024 * 1024);

            return total + " MB total, "
                    + available + " MB available";

        } catch (Exception e) {
            return "unavailable";
        }
    }

    private String getStorageInfo() {
        try {
            File path = getFilesDir();
            StatFs stat = new StatFs(path.getAbsolutePath());

            long total = stat.getTotalBytes() / (1024 * 1024);
            long free = stat.getAvailableBytes() / (1024 * 1024);

            return total + " MB total, "
                    + free + " MB available (app data filesystem)";

        } catch (Exception e) {
            return "unavailable";
        }
    }

    private String readPartitions() {
        BufferedReader reader = null;
        StringBuilder result = new StringBuilder();

        try {
            reader = new BufferedReader(
                    new FileReader("/proc/partitions")
            );

            String line;
            int count = 0;

            while ((line = reader.readLine()) != null) {
                if (count == 0) {
                    result.append(line).append('\n');
                    count++;
                    continue;
                }

                result.append(line).append('\n');
                count++;

                if (count >= 45) {
                    result.append("... output truncated");
                    break;
                }
            }

        } catch (Exception e) {
            return "Partition information is restricted or unavailable.";
        } finally {
            close(reader);
        }

        return result.length() == 0
                ? "unavailable"
                : result.toString();
    }

    private String readMounts() {
        BufferedReader reader = null;
        StringBuilder result = new StringBuilder();

        try {
            reader = new BufferedReader(
                    new FileReader("/proc/mounts")
            );

            String line;
            int count = 0;

            while ((line = reader.readLine()) != null) {
                if (line.contains("/dev/block")
                        || line.contains("/data")
                        || line.contains("/system")
                        || line.contains("/vendor")
                        || line.contains("/product")) {

                    if (result.length() > 0) result.append('\n');

                    result.append(line);
                    count++;

                    if (count >= 20) {
                        result.append("\n... output truncated");
                        break;
                    }
                }
            }

        } catch (Exception e) {
            return "Mount information is restricted or unavailable.";
        } finally {
            close(reader);
        }

        return result.length() == 0
                ? "unavailable"
                : result.toString();
    }

    private String buildReport() {
        StringBuilder out = new StringBuilder();

        out.append("OPEN DEEP TESTING\n");
        out.append("Full diagnostic report\n");
        out.append("Version: 1.0.0\n");

        out.append("Date: ")
                .append(new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        Locale.getDefault()
                ).format(new Date()))
                .append("\n\n");

        out.append("[DEVICE INFORMATION]\n");
        out.append("Manufacturer: ").append(Build.MANUFACTURER).append('\n');
        out.append("Brand: ").append(Build.BRAND).append('\n');
        out.append("Model: ").append(Build.MODEL).append('\n');
        out.append("Device: ").append(Build.DEVICE).append('\n');
        out.append("Product: ").append(Build.PRODUCT).append('\n');
        out.append("Hardware: ").append(Build.HARDWARE).append('\n');
        out.append("Board: ").append(Build.BOARD).append('\n');
        out.append("Bootloader: ").append(unknown(Build.BOOTLOADER)).append('\n');

        out.append("Android: ")
                .append(Build.VERSION.RELEASE)
                .append(" (API ")
                .append(Build.VERSION.SDK_INT)
                .append(")\n");

        out.append("Build display: ").append(Build.DISPLAY).append('\n');
        out.append("Build ID: ").append(Build.ID).append('\n');
        out.append("Fingerprint: ").append(Build.FINGERPRINT).append('\n');

        if (Build.VERSION.SDK_INT >= 23) {
            out.append("Security patch: ")
                    .append(Build.VERSION.SECURITY_PATCH).append('\n');
        }

        out.append("\n[PROCESSOR & MEMORY]\n");
        out.append("ABIs: ").append(join(Build.SUPPORTED_ABIS)).append('\n');
        out.append("CPU cores: ")
                .append(Runtime.getRuntime().availableProcessors()).append('\n');
        out.append("CPU information: ").append(readCpuInfo()).append('\n');
        out.append("RAM: ").append(getRamInfo()).append('\n');
        out.append("Storage: ").append(getStorageInfo()).append('\n');

        out.append("\n[BOOT MODE]\n");
        appendProperty(out, "ro.bootmode");
        appendProperty(out, "ro.boot.bootreason");
        appendProperty(out, "ro.boot.slot_suffix");
        appendProperty(out, "ro.boot.hardware");
        appendProperty(out, "ro.board.platform");

        out.append("\n[BOOTLOADER DIAGNOSTICS]\n");

        for (String key : propertyKeys) {
            if (key.startsWith("ro.boot.")
                    || key.startsWith("ro.oem.")
                    || key.startsWith("sys.oem.")) {
                appendProperty(out, key);
            }
        }

        out.append("OEM unlock setting: ")
                .append(getOemUnlockSetting()).append('\n');

        out.append("\n[UNLOCK READINESS]\n");
        out.append("Status: ").append(readiness).append('\n');
        out.append("Explanation: ").append(readinessExplanation).append('\n');

        out.append("\n[ADB]\n");
        appendProperty(out, "init.svc.adbd");
        appendProperty(out, "sys.usb.config");
        appendProperty(out, "persist.sys.usb.config");
        appendProperty(out, "service.adb.tcp.port");
        out.append("Host connection: cannot be confirmed by this app\n");
        out.append("PC command: adb devices\n");

        out.append("\n[FASTBOOT]\n");
        out.append("Host connection: cannot be confirmed by this app\n");
        out.append("PC command: fastboot devices\n");

        out.append("\n[PARTITIONS]\n");
        out.append(readPartitions()).append('\n');

        out.append("\n[MOUNTS]\n");
        out.append(readMounts()).append('\n');

        out.append("\n[SAFETY]\n");
        out.append("Read-only diagnostics. No partitions were modified.\n");
        out.append("This report does not prove that OEM will authorize unlocking.\n");

        return out.toString();
    }

    private void appendProperty(StringBuilder out, String key) {
        out.append(key)
                .append(": ")
                .append(props.containsKey(key)
                        ? props.get(key)
                        : getProp(key))
                .append('\n');
    }

    private void createReport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");

        intent.putExtra(
                Intent.EXTRA_TITLE,
                "OpenDeepTesting-1.0.0-report.txt"
        );

        startActivityForResult(intent, REPORT_REQUEST);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REPORT_REQUEST
                && resultCode == RESULT_OK
                && data != null
                && data.getData() != null) {
            writeReport(data.getData());
        }
    }

    private void writeReport(Uri uri) {
        try {
            OutputStream output =
                    getContentResolver().openOutputStream(uri);

            if (output == null) {
                throw new Exception("Cannot open output stream");
            }

            output.write(reportText.getBytes(StandardCharsets.UTF_8));
            output.close();

            Toast.makeText(
                    this,
                    "Report saved successfully",
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Report export failed: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void copy(String value) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        clipboard.setPrimaryClip(
                ClipData.newPlainText("OpenDeepTesting", value)
        );

        Toast.makeText(
                this,
                "Copied to clipboard",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void addSection(String name) {
        TextView view = new TextView(this);
        view.setText(name);
        view.setTextColor(0xFF70B7FF);
        view.setTextSize(13);
        view.setTypeface(null, 1);
        view.setPadding(0, dp(22), 0, dp(10));
        root.addView(view);
    }

    private void addInfo(String label, String value) {
        TextView view = new TextView(this);

        view.setText(label + "\n" + unknown(value));
        view.setTextColor(0xFFE6EAF0);
        view.setTextSize(14);
        view.setPadding(dp(14), dp(12), dp(14), dp(12));
        view.setBackgroundColor(0xFF1B222C);

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copy(label + ": " + unknown(value));
            }
        });

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = dp(7);
        view.setLayoutParams(params);

        root.addView(view);
    }

    private void addButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(listener);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = dp(8);
        button.setLayoutParams(params);

        root.addView(button);
    }

    private String join(String[] values) {
        if (values == null || values.length == 0) {
            return "unavailable";
        }

        StringBuilder result = new StringBuilder();

        for (String value : values) {
            if (result.length() > 0) result.append(", ");
            result.append(value);
        }

        return result.toString();
    }

    private String unknown(String value) {
        return value == null || value.trim().isEmpty()
                ? "unknown"
                : value;
    }

    private void close(BufferedReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception ignored) {
            }
        }
    }

    private int dp(int value) {
        return (int) (
                value * getResources()
                        .getDisplayMetrics()
                        .density + 0.5f
        );
    }
}
