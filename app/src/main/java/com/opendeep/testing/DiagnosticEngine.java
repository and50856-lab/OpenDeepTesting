package com.opendeep.testing;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.util.DisplayMetrics;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class DiagnosticEngine {

    private DiagnosticEngine() {
    }

    public static final class Result {
        public final String text;
        public final String json;
        public final String status;

        Result(String text, String json, String status) {
            this.text = text;
            this.json = json;
            this.status = status;
        }
    }

    public static Result collect(Context context) {
        LinkedHashMap<String, String> props = readAllProperties();

        JSONObject root = new JSONObject();
        StringBuilder report = new StringBuilder();

        String date = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss Z",
                Locale.US
        ).format(new Date());

        put(root, "application", "OpenDeepTesting");
        put(root, "report_version", "2.0.0");
        put(root, "generated_at", date);

        section(report, "OPEN DEEP TESTING — FULL DEVICE DIAGNOSTICS");
        line(report, "Report version: 2.0.0");
        line(report, "Generated: " + date);
        line(report, "Collection mode: read-only");
        line(report, " ");

        JSONObject device = new JSONObject();
        put(device, "manufacturer", Build.MANUFACTURER);
        put(device, "brand", Build.BRAND);
        put(device, "model", Build.MODEL);
        put(device, "device", Build.DEVICE);
        put(device, "product", Build.PRODUCT);
        put(device, "hardware", Build.HARDWARE);
        put(device, "board", Build.BOARD);
        put(device, "bootloader", Build.BOOTLOADER);
        put(device, "android_release", Build.VERSION.RELEASE);
        put(device, "sdk", Build.VERSION.SDK_INT);
        put(device, "build_display", Build.DISPLAY);
        put(device, "build_id", Build.ID);
        put(device, "fingerprint", Build.FINGERPRINT);

        if (Build.VERSION.SDK_INT >= 23) {
            put(device, "security_patch", Build.VERSION.SECURITY_PATCH);
        }

        put(root, "device", device);

        section(report, "1. DEVICE INFORMATION");
        addJsonLines(report, device);

        JSONObject system = new JSONObject();
        put(system, "supported_abis", join(Build.SUPPORTED_ABIS));
        put(system, "cpu_cores", Runtime.getRuntime().availableProcessors());
        put(system, "cpu_info", readCpuInfo());
        put(system, "ram", readRam(context));
        put(system, "storage", readStorage(context));
        put(system, "system_properties", new JSONObject(props));

        put(root, "system", system);

        section(report, "2. SYSTEM AND CPU");
        addJsonLines(report, system);

        section(report, "3. SYSTEM PROPERTY DATABASE");
        if (props.isEmpty()) {
            line(report, "UNAVAILABLE: getprop returned no readable properties.");
        } else {
            for (Map.Entry<String, String> entry : props.entrySet()) {
                line(report, entry.getKey() + " = " + entry.getValue());
            }
        }

        JSONObject boot = inspectBoot(props, context);
        put(root, "boot_chain", boot);

        section(report, "4. BOOT CHAIN / AVB / OEM UNLOCK");
        addJsonLines(report, boot);

        JSONObject partitions = inspectPartitions();
        put(root, "partition_map", partitions);

        section(report, "5. PARTITION MAP 2.0");
        addJsonLines(report, partitions);

        JSONObject gsi = inspectGsi(props);
        put(root, "gsi_compatibility", gsi);

        section(report, "6. GSI / PROJECT TREBLE");
        addJsonLines(report, gsi);

        JSONObject hardware = inspectHardware(context);
        put(root, "hardware", hardware);

        section(report, "7. HARDWARE DIAGNOSTICS");
        addJsonLines(report, hardware);

        section(report, "8. BOOTLOADER UNLOCK INVESTIGATION");
        addUnlockAnalysis(report, boot, props);

        section(report, "9. ACCESS LIMITATIONS");
        line(report, "Android applications normally run without root privileges.");
        line(report, "A missing property or inaccessible file is reported as unavailable/restricted.");
        line(report, "Mount points are not physical partitions. APEX, loop and dm devices are classified separately.");
        line(report, "Properties are indicators reported by Android, not cryptographic proof of bootloader state.");
        line(report, "The application does not contact OEM servers or execute Fastboot/EDL commands.");
        line(report, "No partition is modified, erased, formatted or flashed.");

        String status = calculateOverallStatus(boot, partitions, gsi);

        section(report, "10. DIAGNOSTIC RESULT");
        line(report, "Collection status: " + status);
        line(report, "This report contains current readings from this run only.");
        line(report, " ");

        String jsonText = root.toString();

        return new Result(report.toString(), jsonText, status);
    }

    private static JSONObject inspectBoot(
            Map<String, String> props,
            Context context
    ) {
        JSONObject out = new JSONObject();

        String[] keys = {
                "ro.boot.flash.locked",
                "ro.boot.vbmeta.device_state",
                "ro.boot.verifiedbootstate",
                "ro.boot.veritymode",
                "ro.boot.avb_version",
                "ro.oem_unlock_supported",
                "sys.oem_unlock_allowed",
                "ro.bootmode",
                "ro.boot.bootreason",
                "ro.boot.slot_suffix",
                "ro.boot.hardware",
                "ro.board.platform",
                "ro.bootloader",
                "ro.boot.dynamic_partitions",
                "ro.build.ab_update",
                "ro.virtual_ab.enabled",
                "ro.crypto.state",
                "ro.crypto.type"
        };

        for (String key : keys) {
            put(out, key, props.containsKey(key)
                    ? props.get(key) : "UNAVAILABLE");
        }

        put(out, "settings_global_oem_unlock_allowed",
                readSetting(context, Settings.Global.class,
                        "oem_unlock_allowed"));

        put(out, "settings_secure_oem_unlock_allowed",
                readSetting(context, Settings.Secure.class,
                        "oem_unlock_allowed"));

        String locked = get(props, "ro.boot.flash.locked");
        String avb = get(props, "ro.boot.vbmeta.device_state");
        String verified = get(props, "ro.boot.verifiedbootstate");
        String supported = get(props, "ro.oem_unlock_supported");
        String allowed = get(props, "sys.oem_unlock_allowed");

        String state;

        if ("0".equals(locked)) {
            state = "UNLOCKED_REPORTED";
        } else if ("1".equals(locked)
                || "locked".equalsIgnoreCase(avb)
                || "green".equalsIgnoreCase(verified)) {
            state = "LOCKED_INDICATORS_PRESENT";
        } else {
            state = "UNKNOWN";
        }

        put(out, "bootloader_assessment", state);

        boolean supportYes = isTrue(supported);
        boolean supportNo = isFalse(supported);
        boolean allowedYes = isTrue(allowed);
        boolean allowedNo = isFalse(allowed);

        String unlock;

        if ("0".equals(locked)) {
            unlock = "ALREADY_REPORTED_UNLOCKED";
        } else if (supportNo || allowedNo) {
            unlock = "RESTRICTED_INDICATORS";
        } else if (supportYes && allowedYes) {
            unlock = "POSSIBLE_BUT_NOT_CONFIRMED";
        } else {
            unlock = "INSUFFICIENT_DATA";
        }

        put(out, "unlock_assessment", unlock);
        put(out, "unlock_assessment_note",
                "This is a preliminary interpretation of Android-visible indicators. It is not proof of OEM authorization or actual Fastboot unlock capability.");

        return out;
    }

    private static JSONObject inspectGsi(Map<String, String> props) {
        JSONObject out = new JSONObject();

        String treble = get(props, "ro.treble.enabled");
        String vndk = get(props, "ro.vndk.version");
        String vendorApi = get(props, "ro.vendor.api_level");
        String dynamic = get(props, "ro.boot.dynamic_partitions");
        String ab = get(props, "ro.build.ab_update");
        String virtualAb = get(props, "ro.virtual_ab.enabled");
        String sdk = get(props, "ro.build.version.sdk");
        String abi = get(props, "ro.product.cpu.abilist");
        String systemAsRoot = get(props, "ro.build.system_root_image");

        put(out, "treble_enabled", treble);
        put(out, "vndk_version", vndk);
        put(out, "vendor_api_level", vendorApi);
        put(out, "dynamic_partitions_property", dynamic);
        put(out, "ab_update", ab);
        put(out, "virtual_ab", virtualAb);
        put(out, "sdk", sdk);
        put(out, "cpu_abilist", abi);
        put(out, "system_as_root_property", systemAsRoot);

        boolean trebleYes = isTrue(treble);
        boolean arm64 = abi.contains("arm64");

        String assessment;

        if (trebleYes && arm64 && !isUnavailable(vndk)) {
            assessment = "BASIC_PREREQUISITES_PRESENT";
        } else if (trebleYes || arm64) {
            assessment = "PARTIAL_PREREQUISITES";
        } else {
            assessment = "INSUFFICIENT_OR_NEGATIVE_INDICATORS";
        }

        put(out, "preliminary_assessment", assessment);
        put(out, "limitations",
                "GSI compatibility cannot be confirmed from properties alone. Vendor interface, VINTF manifests, kernel, AVB, partition layout and bootloader policy must also be evaluated.");

        return out;
    }

    private static JSONObject inspectPartitions() {
        JSONObject out = new JSONObject();
        JSONArray physical = new JSONArray();
        JSONArray byName = new JSONArray();
        JSONArray deviceMapper = new JSONArray();
        JSONArray mounts = new JSONArray();
        JSONArray loop = new JSONArray();

        String procPartitions = readTextFile("/proc/partitions", 300);
        put(out, "proc_partitions", procPartitions);

        File blockRoot = new File("/sys/class/block");
        File[] blocks = blockRoot.listFiles();

        if (blocks == null) {
            put(out, "sys_class_block_status", "RESTRICTED_OR_UNAVAILABLE");
        } else {
            put(out, "sys_class_block_status", "READABLE");

            for (File block : blocks) {
                String name = block.getName();
                JSONObject item = new JSONObject();

                put(item, "name", name);
                put(item, "size_sectors",
                        readTextFile(new File(block, "size").getPath(), 2));
                put(item, "read_only",
                        readTextFile(new File(block, "ro").getPath(), 2));
                put(item, "device_number",
                        readTextFile(new File(block, "dev").getPath(), 2));
                put(item, "removable",
                        readTextFile(new File(block, "removable").getPath(), 2));
                put(item, "type", classifyBlock(name));

                if (name.startsWith("dm-")) {
                    deviceMapper.put(item);
                } else if (name.startsWith("loop")) {
                    loop.put(item);
                } else {
                    physical.put(item);
                }
            }
        }

        scanDirectory("/dev/block/by-name", byName);
        scanDirectory("/dev/block/bootdevice/by-name", byName);
        scanDirectory("/dev/block/mapper", deviceMapper);

        String mountText = readTextFile("/proc/mounts", 500);
        String[] lines = mountText.split("\n");

        for (String line : lines) {
            if (line.trim().isEmpty()
                    || line.startsWith("UNAVAILABLE")
                    || line.startsWith("RESTRICTED")) {
                continue;
            }

            String[] parts = line.trim().split("\\s+");

            if (parts.length < 4) continue;

            JSONObject item = new JSONObject();
            put(item, "source", parts[0]);
            put(item, "mount_point", parts[1]);
            put(item, "filesystem", parts[2]);
            put(item, "options", parts[3]);

            mounts.put(item);
        }

        put(out, "physical_block_devices", physical);
        put(out, "by_name_partition_links", byName);
        put(out, "device_mapper_devices", deviceMapper);
        put(out, "loop_devices", loop);
        put(out, "mount_points", mounts);

        put(out, "classification_note",
                "The /sys/class/block list represents kernel block devices. by-name entries may be symbolic links. Mount entries are filesystems, not a physical partition count.");

        return out;
    }

    private static void scanDirectory(String path, JSONArray target) {
        File dir = new File(path);
        File[] files = dir.listFiles();

        if (files == null) return;

        for (File file : files) {
            JSONObject item = new JSONObject();

            put(item, "name", file.getName());
            put(item, "path", file.getAbsolutePath());
            put(item, "canonical_path", canonical(file));
            put(item, "exists", file.exists());
            put(item, "readable", file.canRead());
            put(item, "writable", file.canWrite());

            target.put(item);
        }
    }

    private static JSONObject inspectHardware(Context context) {
        JSONObject out = new JSONObject();

        put(out, "cpu_cores", Runtime.getRuntime().availableProcessors());
        put(out, "cpu_info", readCpuInfo());
        put(out, "ram", readRam(context));
        put(out, "storage", readStorage(context));

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();

        JSONObject display = new JSONObject();
        put(display, "width_pixels", metrics.widthPixels);
        put(display, "height_pixels", metrics.heightPixels);
        put(display, "density", metrics.density);
        put(display, "density_dpi", metrics.densityDpi);
        put(out, "display", display);

        JSONObject battery = new JSONObject();

        try {
            Intent intent = context.registerReceiver(
                    null,
                    new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            );

            if (intent != null) {
                put(battery, "level", intent.getIntExtra("level", -1));
                put(battery, "scale", intent.getIntExtra("scale", -1));
                put(battery, "temperature_tenths_c",
                        intent.getIntExtra("temperature", -1));
                put(battery, "voltage_mv",
                        intent.getIntExtra("voltage", -1));
                put(battery, "plugged",
                        intent.getIntExtra("plugged", -1));
                put(battery, "health",
                        intent.getIntExtra("health", -1));
                put(battery, "status",
                        intent.getIntExtra("status", -1));
            } else {
                put(battery, "status", "UNAVAILABLE");
            }
        } catch (Exception e) {
            put(battery, "status", "RESTRICTED: " + e.getClass().getSimpleName());
        }

        put(out, "battery", battery);

        JSONArray sensors = new JSONArray();

        try {
            SensorManager manager = (SensorManager)
                    context.getSystemService(Context.SENSOR_SERVICE);

            if (manager != null) {
                for (Sensor sensor : manager.getSensorList(Sensor.TYPE_ALL)) {
                    JSONObject item = new JSONObject();
                    put(item, "name", sensor.getName());
                    put(item, "vendor", sensor.getVendor());
                    put(item, "type", sensor.getType());
                    put(item, "version", sensor.getVersion());
                    put(item, "power_ma", sensor.getPower());
                    sensors.put(item);
                }
            }
        } catch (Exception e) {
            JSONObject item = new JSONObject();
            put(item, "error", e.toString());
            sensors.put(item);
        }

        put(out, "sensors", sensors);

        JSONArray cameras = new JSONArray();

        try {
            if (Build.VERSION.SDK_INT >= 21) {
                CameraManager manager = (CameraManager)
                        context.getSystemService(Context.CAMERA_SERVICE);

                if (manager != null) {
                    for (String id : manager.getCameraIdList()) {
                        cameras.put(id);
                    }
                }
            } else {
                cameras.put("Camera2 API unavailable on this Android version");
            }
        } catch (Exception e) {
            cameras.put("RESTRICTED: " + e.getClass().getSimpleName());
        }

        put(out, "camera_ids", cameras);

        PackageManager pm = context.getPackageManager();
        put(out, "vulkan_feature_reported",
                Build.VERSION.SDK_INT >= 24
                        && pm.hasSystemFeature("android.hardware.vulkan.level"));

        return out;
    }

    private static void addUnlockAnalysis(
            StringBuilder report,
            JSONObject boot,
            Map<String, String> props
    ) {
        line(report, "Bootloader indicators: "
                + boot.optString("bootloader_assessment", "UNKNOWN"));

        line(report, "Unlock assessment: "
                + boot.optString("unlock_assessment", "UNKNOWN"));

        line(report, "ro.boot.flash.locked = "
                + get(props, "ro.boot.flash.locked"));

        line(report, "ro.boot.vbmeta.device_state = "
                + get(props, "ro.boot.vbmeta.device_state"));

        line(report, "ro.boot.verifiedbootstate = "
                + get(props, "ro.boot.verifiedbootstate"));

        line(report, "ro.oem_unlock_supported = "
                + get(props, "ro.oem_unlock_supported"));

        line(report, "sys.oem_unlock_allowed = "
                + get(props, "sys.oem_unlock_allowed"));

        line(report, " ");
        line(report, "Important distinction:");
        line(report, "- Android properties are indicators, not proof of OEM unlock authorization.");
        line(report, "- Fastboot variables must be queried from a computer while the phone is actually in Fastboot mode.");
        line(report, "- EDL access and a Qualcomm programmer do not automatically grant permission to unlock.");
        line(report, "- An authenticated Firehose programmer or OEM server authorization cannot be generated by this app.");
        line(report, "- This app does not issue unlock, flashing, erase, format or EDL commands.");
    }

    private static String calculateOverallStatus(
            JSONObject boot,
            JSONObject partitions,
            JSONObject gsi
    ) {
        boolean props = partitions.optString(
                "sys_class_block_status", "UNKNOWN"
        ).equals("READABLE");

        boolean gsiPresent = gsi.optString(
                "preliminary_assessment", ""
        ).equals("BASIC_PREREQUISITES_PRESENT");

        if (props && gsiPresent) return "COLLECTED_WITH_CORE_DATA";
        if (props) return "COLLECTED_WITH_LIMITATIONS";
        return "PARTIAL_COLLECTION";
    }

    private static LinkedHashMap<String, String> readAllProperties() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        BufferedReader reader = null;

        try {
            Process process = Runtime.getRuntime().exec("getprop");

            reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;
            int count = 0;

            while ((line = reader.readLine()) != null && count < 600) {
                int start = line.indexOf('[');
                int middle = line.indexOf("]: [", start + 1);
                int end = line.lastIndexOf(']');

                if (start < 0 || middle < 0 || end <= middle + 4) {
                    continue;
                }

                String key = line.substring(start + 1, middle);
                String value = line.substring(middle + 4, end);

                result.put(key, value);
                count++;
            }

            process.waitFor();

        } catch (Exception ignored) {
        } finally {
            close(reader);
        }

        return result;
    }

    private static String readCpuInfo() {
        return readTextFile("/proc/cpuinfo", 35);
    }

    private static String readRam(Context context) {
        try {
            ActivityManager manager = (ActivityManager)
                    context.getSystemService(Context.ACTIVITY_SERVICE);

            ActivityManager.MemoryInfo info =
                    new ActivityManager.MemoryInfo();

            manager.getMemoryInfo(info);

            return "Total: " + (info.totalMem / 1048576L)
                    + " MB; available: " + (info.availMem / 1048576L)
                    + " MB; low memory: " + info.lowMemory;
        } catch (Exception e) {
            return "UNAVAILABLE: " + e.getClass().getSimpleName();
        }
    }

    private static String readStorage(Context context) {
        try {
            File path = Environment.getDataDirectory();
            StatFs stat = new StatFs(path.getAbsolutePath());

            long total = stat.getTotalBytes() / 1073741824L;
            long free = stat.getAvailableBytes() / 1073741824L;

            return "Data filesystem: " + total + " GB total, "
                    + free + " GB available";
        } catch (Exception e) {
            return "UNAVAILABLE: " + e.getClass().getSimpleName();
        }
    }

    private static String readTextFile(String path, int maxLines) {
        BufferedReader reader = null;
        StringBuilder out = new StringBuilder();

        try {
            reader = new BufferedReader(new FileReader(path));
            String line;
            int count = 0;

            while ((line = reader.readLine()) != null
                    && count < maxLines) {
                if (out.length() > 0) out.append('\n');
                out.append(line);
                count++;
            }

            if (count == 0) return "EMPTY";

            return out.toString();

        } catch (Exception e) {
            return "RESTRICTED_OR_UNAVAILABLE: "
                    + e.getClass().getSimpleName();
        } finally {
            close(reader);
        }
    }

    private static String readSetting(
            Context context,
            Class<?> table,
            String name
    ) {
        try {
            if (table == Settings.Global.class) {
                return String.valueOf(Settings.Global.getInt(
                        context.getContentResolver(), name, -1));
            }

            if (table == Settings.Secure.class) {
                return String.valueOf(Settings.Secure.getInt(
                        context.getContentResolver(), name, -1));
            }
        } catch (Exception ignored) {
        }

        return "NOT_EXPOSED";
    }

    private static String classifyBlock(String name) {
        if (name.startsWith("dm-")) return "DEVICE_MAPPER_LOGICAL";
        if (name.startsWith("loop")) return "LOOP_DEVICE";
        if (name.startsWith("ram")) return "RAM_BLOCK_DEVICE";
        if (name.startsWith("mmcblk")) return "MMC_OR_UFS_BLOCK_DEVICE";
        if (name.startsWith("sd")) return "SCSI_BLOCK_DEVICE";
        if (name.startsWith("nvme")) return "NVME_BLOCK_DEVICE";
        return "OTHER_BLOCK_DEVICE";
    }

    private static String canonical(File file) {
        try {
            return file.getCanonicalPath();
        } catch (Exception e) {
            return "UNRESOLVED";
        }
    }

    private static String get(Map<String, String> props, String key) {
        String value = props.get(key);
        return value == null || value.trim().isEmpty()
                ? "UNAVAILABLE" : value;
    }

    private static boolean isTrue(String value) {
        return "1".equals(value)
                || "true".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value);
    }

    private static boolean isFalse(String value) {
        return "0".equals(value)
                || "false".equalsIgnoreCase(value)
                || "no".equalsIgnoreCase(value);
    }

    private static boolean isUnavailable(String value) {
        return value == null
                || value.trim().isEmpty()
                || "UNAVAILABLE".equalsIgnoreCase(value)
                || "unknown".equalsIgnoreCase(value);
    }

    private static String join(String[] values) {
        if (values == null || values.length == 0) return "UNAVAILABLE";

        StringBuilder out = new StringBuilder();

        for (String value : values) {
            if (out.length() > 0) out.append(", ");
            out.append(value);
        }

        return out.toString();
    }

    private static void section(StringBuilder out, String title) {
        out.append("\n==================================================\n");
        out.append(title).append('\n');
        out.append("==================================================\n");
    }

    private static void line(StringBuilder out, String value) {
        out.append(value == null ? "UNAVAILABLE" : value).append('\n');
    }

    private static void addJsonLines(StringBuilder out, JSONObject object) {
        if (object == null) return;

        JSONArray names = object.names();

        if (names == null) {
            line(out, "No readable values.");
            return;
        }

        for (int i = 0; i < names.length(); i++) {
            String key = names.optString(i);
            Object value = object.opt(key);

            if (value instanceof JSONObject || value instanceof JSONArray) {
                line(out, key + ":");
                line(out, value.toString());
            } else {
                line(out, key + ": " + String.valueOf(value));
            }
        }
    }

    private static void put(JSONObject object, String key, Object value) {
        try {
            object.put(key, value == null ? JSONObject.NULL : value);
        } catch (Exception ignored) {
        }
    }

    private static void close(BufferedReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception ignored) {
            }
        }
    }
}
