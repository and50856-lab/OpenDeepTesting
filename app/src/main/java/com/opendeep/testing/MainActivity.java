package com.opendeep.testing;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

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
        subtitle.setText("Device diagnostics • Early development build");
        subtitle.setTextColor(0xFF9AA4B2);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(7), 0, dp(24));
        root.addView(subtitle);

        root.addView(section("DEVICE"));
        root.addView(info("Manufacturer", Build.MANUFACTURER));
        root.addView(info("Model", Build.MODEL));
        root.addView(info("Android", Build.VERSION.RELEASE +
                " (API " + Build.VERSION.SDK_INT + ")"));
        root.addView(info("Build", Build.DISPLAY));

        root.addView(section("BOOTLOADER DIAGNOSTICS"));

        TextView diagnostics = new TextView(this);
        diagnostics.setTextColor(0xFFE6EAF0);
        diagnostics.setTextSize(14);
        diagnostics.setPadding(dp(14), dp(14), dp(14), dp(14));
        diagnostics.setBackgroundColor(0xFF1B222C);
        root.addView(diagnostics);

        diagnostics.setText(readDiagnostics());

        TextView note = new TextView(this);
        note.setText(
                "IMPORTANT\n" +
                "This build only reads device information. " +
                "It does not unlock the bootloader, modify partitions, " +
                "or contact OPPO servers. A genuine unlock requires " +
                "an officially supported authorization path for the exact device."
        );

        note.setTextColor(0xFFFFC978);
        note.setTextSize(13);
        note.setPadding(0, dp(24), 0, 0);
        root.addView(note);

        setContentView(scroll);
    }

    private TextView section(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(0xFF70B7FF);
        view.setTextSize(12);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(16), 0, dp(8));
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

    private String readDiagnostics() {
        StringBuilder out = new StringBuilder();

        out.append("ro.boot.flash.locked: ")
                .append(getProp("ro.boot.flash.locked")).append('\n');

        out.append("ro.boot.vbmeta.device_state: ")
                .append(getProp("ro.boot.vbmeta.device_state")).append('\n');

        out.append("ro.boot.verifiedbootstate: ")
                .append(getProp("ro.boot.verifiedbootstate")).append('\n');

        out.append("ro.oem_unlock_supported: ")
                .append(getProp("ro.oem_unlock_supported")).append('\n');

        int allowed = -1;

        try {
            allowed = Settings.Global.getInt(
                    getContentResolver(),
                    "oem_unlock_allowed",
                    -1
            );
        } catch (Exception ignored) {
        }

        out.append("OEM unlock setting: ");

        out.append(
                allowed == 1 ? "enabled" :
                allowed == 0 ? "disabled" :
                "not exposed / unknown"
        );

        out.append("\n\nThese values are diagnostic only; ")
                .append("they do not prove that OPPO will authorize unlocking.");

        return out.toString();
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

    private int dp(int value) {
        return (int) (
                value * getResources().getDisplayMetrics().density + 0.5f
        );
    }
}
