package com.opendeep.testing;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private static final int SAVE_TXT_REQUEST = 2101;
    private static final int SAVE_JSON_REQUEST = 2102;

    private LinearLayout root;
    private TextView statusView;
    private TextView reportPreview;

    private DiagnosticEngine.Result diagnosticResult;

    private boolean collecting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Удаляем старые снимки диагностики предыдущей версии.
        getSharedPreferences(
                "open_deep_testing_history",
                Context.MODE_PRIVATE
        ).edit().clear().apply();

        buildScreen();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(15, 18, 24));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(32));

        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("OPEN DEEP TESTING");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setTypeface(null, 1);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(
                "Device diagnostics • Version 2.0.0\n"
                        + android.os.Build.MANUFACTURER + " "
                        + android.os.Build.MODEL
        );
        subtitle.setTextColor(Color.rgb(155, 166, 183));
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(7), 0, dp(22));
        root.addView(subtitle);

        addSection("FULL DEVICE DIAGNOSTICS");

        addInfo(
                "One complete scan",
                "Collects current device information, system properties, "
                        + "partition layout, boot chain, GSI indicators "
                        + "and hardware information."
        );

        addButton("RUN FULL DEVICE DIAGNOSTICS", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runDiagnostics();
            }
        });

        statusView = new TextView(this);
        statusView.setText("STATUS: NOT RUN");
        statusView.setTextColor(Color.rgb(220, 225, 235));
        statusView.setTextSize(15);
        statusView.setTypeface(null, 1);
        statusView.setPadding(dp(14), dp(14), dp(14), dp(14));
        statusView.setBackgroundColor(Color.rgb(29, 36, 47));

        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        statusParams.topMargin = dp(12);
        root.addView(statusView, statusParams);

        addSection("REPORT CENTER");

        addButton("VIEW FULL REPORT", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showReport();
            }
        });

        addButton("COPY TXT REPORT", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!hasReport()) return;

                copyText(diagnosticResult.text);
                toast("TXT report copied");
            }
        });

        addButton("SHARE TXT REPORT", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!hasReport()) return;

                shareText(
                        "OpenDeepTesting full diagnostic report",
                        diagnosticResult.text
                );
            }
        });

        addButton("SAVE TXT REPORT", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!hasReport()) return;

                saveReport(SAVE_TXT_REQUEST);
            }
        });

        addButton("SAVE JSON REPORT", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!hasReport()) return;

                saveReport(SAVE_JSON_REQUEST);
            }
        });

        addSection("BOOTLOADER UNLOCK INVESTIGATION");

        addInfo(
                "Purpose",
                "Collect evidence about the bootloader lock, AVB, "
                        + "Verified Boot, OEM unlock flags and boot configuration."
        );

        addInfo(
                "Important",
                "The app can inspect Android-visible information. "
                        + "It cannot grant OPPO authorization, bypass "
                        + "Qualcomm Secure Boot or create an authenticated "
                        + "Firehose programmer."
        );

        addSection("PARTITION MAP 2.0");

        addInfo(
                "What is collected",
                "Kernel block devices, by-name links, Device Mapper devices, "
                        + "loop devices, mount points, filesystems and "
                        + "read/write indicators where accessible."
        );

        addSection("GSI COMPATIBILITY");

        addInfo(
                "What is collected",
                "Treble, VNDK, vendor API, CPU ABI, dynamic partition "
                        + "indicators, A/B properties and other visible "
                        + "system properties."
        );

        addSection("SAFETY");

        addInfo(
                "Read-only operation",
                "OpenDeepTesting does not unlock the bootloader, "
                        + "flash firmware, erase partitions, format storage "
                        + "or execute EDL commands."
        );

        addInfo(
                "Access limitations",
                "Some information may be hidden by Android permissions, "
                        + "SELinux or manufacturer restrictions. "
                        + "UNAVAILABLE does not automatically mean impossible."
        );

        reportPreview = new TextView(this);
        reportPreview.setText(
                "No diagnostic report yet.\n\n"
                        + "Press RUN FULL DEVICE DIAGNOSTICS to begin."
        );
        reportPreview.setTextColor(Color.rgb(180, 190, 205));
        reportPreview.setTextSize(13);
        reportPreview.setPadding(
                dp(14), dp(14), dp(14), dp(14)
        );
        reportPreview.setBackgroundColor(Color.rgb(24, 29, 38));

        LinearLayout.LayoutParams previewParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        previewParams.topMargin = dp(12);
        root.addView(reportPreview, previewParams);

        setContentView(scroll);
    }

    private void runDiagnostics() {
        if (collecting) return;

        collecting = true;
        diagnosticResult = null;

        statusView.setText("STATUS: COLLECTING...");
        statusView.setTextColor(Color.rgb(255, 198, 105));

        reportPreview.setText(
                "Collecting device information...\n"
                        + "Please wait."
        );

        Toast.makeText(
                this,
                "Full diagnostics started",
                Toast.LENGTH_SHORT
        ).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final DiagnosticEngine.Result result;

                try {
                    result = DiagnosticEngine.collect(
                            MainActivity.this
                    );
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            collecting = false;

                            statusView.setText("STATUS: FAILED");
                            statusView.setTextColor(
                                    Color.rgb(255, 110, 110)
                            );

                            reportPreview.setText(
                                    "Diagnostic collection failed:\n\n"
                                            + e.toString()
                            );

                            toast("Diagnostic collection failed");
                        }
                    });

                    return;
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        diagnosticResult = result;
                        collecting = false;

                        statusView.setText(
                                "STATUS: " + result.status
                        );

                        statusView.setTextColor(
                                Color.rgb(112, 214, 154)
                        );

                        reportPreview.setText(
                                "FULL DIAGNOSTICS COMPLETED\n\n"
                                        + "Report is ready.\n\n"
                                        + "TXT size: "
                                        + result.text.length()
                                        + " characters\n"
                                        + "JSON size: "
                                        + result.json.length()
                                        + " characters\n\n"
                                        + "Use the Report Center below "
                                        + "to view, copy, share or save it."
                        );

                        toast("Full diagnostics completed");
                    }
                });
            }
        }).start();
    }

    private boolean hasReport() {
        if (diagnosticResult == null) {
            toast("Run full diagnostics first");
            return false;
        }

        return true;
    }

    private void showReport() {
        if (!hasReport()) return;

        ScrollView scroll = new ScrollView(this);

        TextView text = new TextView(this);
        text.setText(diagnosticResult.text);
        text.setTextColor(Color.WHITE);
        text.setTextSize(12);
        text.setTextIsSelectable(true);
        text.setPadding(
                dp(14), dp(14), dp(14), dp(14)
        );

        scroll.addView(text);

        new android.app.AlertDialog.Builder(this)
                .setTitle("FULL DIAGNOSTIC REPORT")
                .setView(scroll)
                .setPositiveButton("CLOSE", null)
                .show();
    }

    private void saveReport(int requestCode) {
        String mime;
        String filename;

        if (requestCode == SAVE_JSON_REQUEST) {
            mime = "application/json";
            filename = "OpenDeepTesting-report.json";
        } else {
            mime = "text/plain";
            filename = "OpenDeepTesting-report.txt";
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, filename);

        try {
            startActivityForResult(intent, requestCode);
        } catch (Exception e) {
            toast("Cannot open file picker");
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode != SAVE_TXT_REQUEST
                && requestCode != SAVE_JSON_REQUEST) {
            return;
        }

        Uri uri = data.getData();

        if (uri == null) {
            toast("Invalid file location");
            return;
        }

        String content;

        if (requestCode == SAVE_JSON_REQUEST) {
            content = diagnosticResult.json;
        } else {
            content = diagnosticResult.text;
        }

        writeFile(uri, content);
    }

    private void writeFile(Uri uri, String content) {
        OutputStream stream = null;

        try {
            stream = getContentResolver().openOutputStream(uri);

            if (stream == null) {
                toast("Cannot open selected file");
                return;
            }

            stream.write(
                    content.getBytes(StandardCharsets.UTF_8)
            );

            stream.flush();

            toast("Report saved successfully");

        } catch (Exception e) {
            toast("Save failed: " + e.getMessage());

        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void shareText(String title, String content) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, title);
        intent.putExtra(Intent.EXTRA_TEXT, content);

        startActivity(
                Intent.createChooser(intent, "Share diagnostic report")
        );
    }

    private void copyText(String value) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(
                        Context.CLIPBOARD_SERVICE
                );

        if (clipboard != null) {
            clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                            "OpenDeepTesting report",
                            value
                    )
            );
        }
    }

    private void addSection(String title) {
        TextView view = new TextView(this);
        view.setText(title);
        view.setTextColor(Color.rgb(112, 183, 255));
        view.setTextSize(14);
        view.setTypeface(null, 1);
        view.setPadding(0, dp(23), 0, dp(9));

        root.addView(view);
    }

    private void addInfo(String title, String description) {
        TextView view = new TextView(this);

        view.setText(title + "\n" + description);
        view.setTextColor(Color.rgb(211, 218, 229));
        view.setTextSize(13);
        view.setPadding(
                dp(13), dp(12), dp(13), dp(12)
        );
        view.setBackgroundColor(Color.rgb(25, 31, 41));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = dp(7);
        root.addView(view, params);
    }

    private void addButton(
            String title,
            View.OnClickListener listener
    ) {
        Button button = new Button(this);

        button.setText(title);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.rgb(43, 91, 145)
                )
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.bottomMargin = dp(8);
        root.addView(button, params);

        button.setOnClickListener(listener);
    }

    private int dp(int value) {
        return (int) (
                value * getResources()
                        .getDisplayMetrics().density + 0.5f
        );
    }

    private void toast(String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }
}
