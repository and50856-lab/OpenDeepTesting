package com.opendeep.testing;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private static final int CREATE_REPORT_REQUEST = 1001;
    private String reportText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(24));
        root.setBackgroundColor(0xFF101318);

        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("OPEN DEEP TESTING");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Device diagnostics • Version 0.2.0");
        subtitle.setTextColor(0xFF9AA4B2);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(7), 0, dp(20));
        root.addView(subtitle);

        // DEVICE
        root.addView(section("DEVICE"));

        root.addView(info("Manufacturer", Build.MANUFACTURER));
        root.addView(info("Model", Build.MODEL));
        root.addView(info("Device codename", Build.DEVICE));
        root.addView(info("Product", Build.PRODUCT));
        root.addView(info("Hardware", Build.HARDWARE));
        root.addView(info("Bootloader", emptyAsUnknown(Build.BOOTLOADER)));

        root.addView(info("Android",
                Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"));

        root.addView(info("Build display", Build.DISPLAY));
        root.addView(info("Build fingerprint", Build.FINGERPRINT));

        if (Build.VERSION.SDK_INT >= 23) {
            root.addView(info("Security patch", Build.VERSION.SECURITY_PATCH));
        }

        // BOOT MODE
        root.addView(section("BOOT MODE"));

        root.addView(info("Current state",
                "Application is running inside Android"));

        root.addView(info("ro.bootmode", getProp("ro.bootmode")));
        root.addView(info("ro.boot.bootreason", getProp("ro.boot.bootreason")));
        root.addView(info("ro.boot.slot_suffix", getProp("ro.boot.slot_suffix")));
        root.addView(info("ro.boot.hardware", getProp("ro.boot.hardware")));
        root.addView(info("ro.board.platform", getProp("ro.board.platform")));

        // BOOTLOADER
        root.addView(section("BOOTLOADER DIAGNOSTICS"));

        String diagnostics = readBootloaderDiagnostics();

        root.addView(info("Read-only bootloader properties", diagnostics));

        // FASTBOOT
        root.addView(section("FASTBOOT"));

        root.addView(info("Fastboot connection",
                "Cannot be detected from this phone app.\n" +
                "Connect the phone to a computer in Fastboot mode " +
                "and execute: fastboot devices"));

        // REPORT
        root.addView(section("REPORT"));

        TextView reportHint = new TextView(this);
        reportHint.setText(
                "Export collected diagnostic information as a TXT file."
        );
        reportHint.setTextColor(0xFFB8C1CE);
        reportHint.setTextSize(13);
        reportHint.setPadding(0, 0, 0, dp(12));
        root.addView(reportHint);

        Button exportButton = new Button(this);
        exportButton.setText("EXPORT DIAGNOSTIC REPORT");

        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openReportCreator();
            }
        });

        root.addView(exportButton);

        // SAFETY
        TextView note = new TextView(this);

        note.setText(
                "IMPORTANT\n" +
                "This application performs read-only diagnostics. " +
                "It does not unlock the bootloader, bypass OEM authorization, " +
                "modify partitions, or contact OPPO servers. " +
                "Diagnostic properties do not prove that OPPO will authorize unlocking."
        );

        note.setTextColor(0xFFFFC978);
        note.setTextSize(13);
        note.setPadding(0, dp(22), 0, 0);

        root.addView(note);

        reportText = buildReport(diagnostics);

        setContentView(scroll);
    }

    private String readBootloaderDiagnostics() {

        StringBuilder out = new StringBuilder();

        out.append("ro.boot.flash.locked: ")
                .append(getProp("ro.boot.flash.locked"))
                .append('\n');

        out.append("ro.boot.vbmeta.device_state: ")
                .append(getProp("ro.boot.vbmeta.device_state"))
                .append('\n');

        out.append("ro.boot.verifiedbootstate: ")
                .append(getProp("ro.boot.verifiedbootstate"))
                .append('\n');

        out.append("ro.oem_unlock_supported: ")
                .append(getProp("ro.oem_unlock_supported"))
                .append('\n');

        int allowed = -1;

        try {
            allowed = Settings.Global.getInt(
                    getContentResolver(),
                    "oem_unlock_allowed",
                    -1
            );
        } catch (Exception ignored) {
        }

        String setting =
                allowed == 1 ? "enabled" :
                allowed == 0 ? "disabled" :
                "not exposed / unknown";

        out.append("OEM unlock setting: ").append(setting);

        return out.toString();
    }

    private String buildReport(String diagnostics) {

        StringBuilder out = new StringBuilder();

        out.append("OPEN DEEP TESTING - DIAGNOSTIC REPORT\n");
        out.append("App version: 0.2.0\n\n");

        out.append("[DEVICE]\n");

        out.append("Manufacturer: ").append(Build.MANUFACTURER).append('\n');
        out.append("Model: ").append(Build.MODEL).append('\n');
        out.append("Device: ").append(Build.DEVICE).append('\n');
        out.append("Product: ").append(Build.PRODUCT).append('\n');
        out.append("Hardware: ").append(Build.HARDWARE).append('\n');

        out.append("Bootloader: ")
                .append(emptyAsUnknown(Build.BOOTLOADER))
                .append('\n');

        out.append("Android: ")
                .append(Build.VERSION.RELEASE)
                .append(" (API ")
                .append(Build.VERSION.SDK_INT)
                .append(")\n");

        out.append("Build display: ").append(Build.DISPLAY).append('\n');
        out.append("Build fingerprint: ").append(Build.FINGERPRINT).append('\n');

        if (Build.VERSION.SDK_INT >= 23) {
            out.append("Security patch: ")
                    .append(Build.VERSION.SECURITY_PATCH)
                    .append('\n');
        }

        out.append("\n[BOOT MODE]\n");

        out.append("Current state: application is running inside Android\n");

        out.append("ro.bootmode: ")
                .append(getProp("ro.bootmode"))
                .append('\n');

        out.append("ro.boot.bootreason: ")
                .append(getProp("ro.boot.bootreason"))
                .append('\n');

        out.append("ro.boot.slot_suffix: ")
                .append(getProp("ro.boot.slot_suffix"))
                .append('\n');

        out.append("ro.boot.hardware: ")
                .append(getProp("ro.boot.hardware"))
                .append('\n');

        out.append("ro.board.platform: ")
                .append(getProp("ro.board.platform"))
                .append('\n');

        out.append("\n[BOOTLOADER DIAGNOSTICS]\n");
        out.append(diagnostics).append('\n');

        out.append("\n[FASTBOOT]\n");

        out.append(
                "Not detectable from this phone app. " +
                "On a computer in Fastboot mode, run: fastboot devices\n"
        );

        out.append("\n[SAFETY]\n");

        out.append(
                "Read-only diagnostics. No partitions were modified. " +
                "This report does not establish OEM unlock authorization.\n"
        );

        return out.toString();
    }

    private void openReportCreator() {

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);

        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");

        intent.putExtra(
                Intent.EXTRA_TITLE,
                "OpenDeepTesting-report.txt"
        );

        startActivityForResult(intent, CREATE_REPORT_REQUEST);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CREATE_REPORT_REQUEST
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
                return;
            }

            output.write(
                    reportText.getBytes(StandardCharsets.UTF_8)
            );

            output.close();

        } catch (Exception ignored) {
        }
    }

    private String getProp(String key) {

        try {

            Process process = Runtime.getRuntime().exec(
                    new String[]{"getprop", key}
            );

            java.io.BufferedReader reader =
                    new java.io.BufferedReader(
                            new java.io.InputStreamReader(
                                    process.getInputStream()
                            )
                    );

            String value = reader.readLine();

            reader.close();
            process.waitFor();

            return value == null || value.isEmpty()
                    ? "unavailable"
                    : value;

        } catch (Exception e) {

            return "unavailable";
        }
    }

    private String emptyAsUnknown(String value) {

        return value == null || value.trim().isEmpty()
                ? "unknown"
                : value;
    }

    private TextView section(String value) {

        TextView view = new TextView(this);

        view.setText(value);
        view.setTextColor(0xFF70B7FF);
        view.setTextSize(12);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(17), 0, dp(8));

        return view;
    }

    private TextView info(String label, String value) {

        TextView view = new TextView(this);

        view.setText(label + "\n" + value);
        view.setTextColor(0xFFE6EAF0);
        view.setTextSize(14);
        view.setPadding(dp(14), dp(11), dp(14), dp(11));
        view.setBackgroundColor(0xFF1B222C);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = dp(7);

        view.setLayoutParams(params);

        return view;
    }

    private int dp(int value) {

        return (int) (
                value * getResources()
                        .getDisplayMetrics()
                        .density + 0.5f
        );
    }
}
