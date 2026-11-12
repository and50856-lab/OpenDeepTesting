package com.opendeep.testing;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DiagnosticEngine {

    private static final String VERSION = "2.1.0";
    private static final int MAX_PROPERTIES = 10000;
    private static final int MAX_BLOCK_NODES = 1500;
    private static final int MAX_DIRECTORY_DEPTH = 3;

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

        // Important properties are queried individually as well.
        // This prevents them from disappearing because of list truncation.
        String[] importantProperties = {
                "ro.treble.enabled",
                "ro.vndk.version",
                "ro.product.vndk.version",
                "ro.vendor.vndk.version",
                "ro.vendor.api_level",
                "ro.board.api_level",
                "ro.vendor.build.version.sdk",
                "ro.build.version.sdk",
                "ro.product.cpu.abilist",
                "ro.product.cpu.abilist64",
                "ro.product.cpu.abilist32",
                "ro.boot.dynamic_partitions",
                "ro.dynamic_partitions",
                "ro.build.ab_update",
                "ro.virtual_ab.enabled",
                "ro.boot.slot_suffix",
                "ro.build.system_root_image",
                "ro.boot.flash.locked",
                "ro.boot.vbmeta.device_state",
                "ro.boot.verifiedbootstate",
                "ro.boot.veritymode",
                "ro.boot.avb_version",
                "ro.oem_unlock_supported",
                "sys.oem_unlock_allowed",
                "ro.bootmode",
                "ro.boot.bootreason",
                "ro.boot.hardware",
                "ro.hardware",
                "ro.board.platform",
                "ro.soc.manufacturer",
                "ro.soc.model",
                "ro.hardware.chipname",
                "ro.bootloader",
                "ro.boot.bootdevice",
                "ro.crypto.state",
                "ro.crypto.type",
                "sys.usb.config",
                "sys.usb.state",
                "persist.sys.usb.config"
        };

        for (String key : importantProperties) {
            if (!props.containsKey(key)
                    || isUnavailable(props.get(key))) {
                String value = readSingleProperty(key);

                if (!isUnavailable(value)) {
                    props.put(key, value);
                }
            }
        }

        JSONObject root = new JSONObject();
        StringBuilder report = new StringBuilder();

        String date = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss Z",
                Locale.US
        ).format(new Date());

        put(root, "application", "OpenDeepTesting");
        put(root, "report_version", VERSION);
        put(root, "generated_at", date);
        put(root, "collection_mode", "READ_ONLY");

        section(report, "OPEN DEEP TESTING — FULL DEVICE DIAGNOSTICS");
        line(report, "Report version: " + VERSION);
        line(report, "Generated: " + date);
        line(report, "Collection mode: READ-ONLY");
        line(report, " ");

        JSONObject device = inspectDevice();
        put(root, "device", device);

        section(report, "1. DEVICE INFORMATION");
        addJsonLines(report, device);

        JSONObject system = inspectSystem(context, props);
        put(root, "system", system);

        section(report, "2. SYSTEM AND CPU");
        addJsonLines(report, system);

        section(report, "3. SYSTEM PROPERTY DATABASE");
        addPropertiesToReport(report, props);

        JSONObject boot = inspectBoot(props, context);
        put(root, "boot_chain", boot);

        section(report, "4. BOOT CHAIN / AVB / OEM UNLOCK");
        addJsonLines(report, boot);

        JSONObject partitions = inspectPartitions();
        put(root, "partition_map", partitions);

        section(report, "5. PARTITION MAP 3.0");
        addPartitionReport(report, partitions);

        JSONObject gsi = inspectGsi(props);
        put(root, "gsi_compatibility", gsi);

        section(report, "6. GSI / PROJECT TREBLE");
        addJsonLines(report, gsi);

        JSONObject soc = inspectSoc(props);
        put(root, "soc_diagnostics", soc);

        section(report, "7. QUALCOMM / SOC DIAGNOSTICS");
        addJsonLines(report, soc);

        JSONObject hardware = inspectHardware(context);
        put(root, "hardware", hardware);

        section(report, "8. HARDWARE DIAGNOSTICS");
        addJsonLines(report, hardware);

        section(report, "9. BOOTLOADER / FASTBOOT / EDL INVESTIGATION");
        addUnlockAnalysis(report, boot, soc);

        section(report, "10. ACCESS LIMITATIONS");
        line(report, "Android applications normally run without root privileges.");
        line(report, "A missing property or inaccessible file is reported as unavailable or restricted.");
        line(report, "A mount point is not necessarily a physical partition.");
        line(report, "Device Mapper and loop devices are classified separately.");
        line(report, "A symbolic link is not counted as an additional physical partition.");
        line(report, "Android properties are indicators, not cryptographic proof of bootloader state.");
        line(report, "An Android application cannot query actual Fastboot variables while Android is running.");
        line(report, "EDL availability and OEM authorization cannot be established from ordinary Android access alone.");
        line(report, "No OEM server is contacted.");
        line(report, "No unlock, erase, format, write or flash command is executed.");

        String status = calculateOverallStatus(
                boot,
                partitions,
                gsi,
                props
        );

        section(report, "11. DIAGNOSTIC RESULT");
        line(report, "Collection status: " + status);
        line(report, "All values in this report were collected during this run.");
        line(report, " ");

        return new Result(
                report.toString(),
                root.toString(),
                status
        );
    }

    // ------------------------------------------------------------
    // DEVICE INFORMATION
    // ------------------------------------------------------------

    private static JSONObject inspectDevice() {
        JSONObject out = new JSONObject();

        put(out, "manufacturer", Build.MANUFACTURER);
        put(out, "brand", Build.BRAND);
        put(out, "model", Build.MODEL);
        put(out, "device", Build.DEVICE);
        put(out, "product", Build.PRODUCT);
        put(out, "hardware", Build.HARDWARE);
        put(out, "board", Build.BOARD);
        put(out, "bootloader", Build.BOOTLOADER);
        put(out, "android_release", Build.VERSION.RELEASE);
        put(out, "sdk", Build.VERSION.SDK_INT);
        put(out, "build_display", Build.DISPLAY);
        put(out, "build_id", Build.ID);
        put(out, "fingerprint", Build.FINGERPRINT);

        if (Build.VERSION.SDK_INT >= 23) {
            put(out, "security_patch", Build.VERSION.SECURITY_PATCH);
        }

        put(out, "supported_abis", join(Build.SUPPORTED_ABIS));

        return out;
    }

    // ------------------------------------------------------------
    // SYSTEM INFORMATION
    // ------------------------------------------------------------

    private static JSONObject inspectSystem(
            Context context,
            Map<String, String> props
    ) {
        JSONObject out = new JSONObject();

        put(out, "supported_abis", join(Build.SUPPORTED_ABIS));
        put(out, "cpu_cores", Runtime.getRuntime().availableProcessors());
        put(out, "cpu_info", readTextFile("/proc/cpuinfo", 100));
        put(out, "ram", readRam(context));
        put(out, "storage", readStorage());

        put(out, "property_count", props.size());

        return out;
    }

    private static void addPropertiesToReport(
            StringBuilder report,
            Map<String, String> props
    ) {
        if (props.isEmpty()) {
            line(report, "UNAVAILABLE: getprop returned no readable properties.");
            return;
        }

        line(report, "Collected properties: " + props.size());

        for (Map.Entry<String, String> entry : props.entrySet()) {
            line(report, entry.getKey() + " = " + entry.getValue());
        }
    }

    // ------------------------------------------------------------
    // BOOT CHAIN
    // ------------------------------------------------------------

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
                "ro.boot.bootdevice",
                "ro.boot.dynamic_partitions",
                "ro.build.ab_update",
                "ro.virtual_ab.enabled",
                "ro.crypto.state",
                "ro.crypto.type",
                "sys.usb.config",
                "sys.usb.state",
                "persist.sys.usb.config"
        };

        for (String key : keys) {
            put(out, key, get(props, key));
        }

        put(out, "settings_global_oem_unlock_allowed",
                readSetting(context, true, "oem_unlock_allowed"));

        put(out, "settings_secure_oem_unlock_allowed",
                readSetting(context, false, "oem_unlock_allowed"));

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
                "Android-visible indicators do not prove OEM authorization or actual Fastboot unlock capability.");

        put(out, "fastboot_query_status",
                "NOT_QUERYABLE_FROM_NORMAL_ANDROID_APP");

        put(out, "edl_query_status",
                "NOT_CONFIRMED_FROM_NORMAL_ANDROID_APP");

        return out;
    }

    // ------------------------------------------------------------
    // PARTITION MAP 3.0
    // ------------------------------------------------------------

    private static JSONObject inspectPartitions() {
        JSONObject out = new JSONObject();

        JSONArray blockDevices = new JSONArray();
        JSONArray physicalDevices = new JSONArray();
        JSONArray partitionDevices = new JSONArray();
        JSONArray logicalDevices = new JSONArray();
        JSONArray loopDevices = new JSONArray();
        JSONArray byName = new JSONArray();
        JSONArray mapperLinks = new JSONArray();
        JSONArray mounts = new JSONArray();
        JSONArray directoryNodes = new JSONArray();

        File sysBlock = new File("/sys/class/block");
        File[] blocks = sysBlock.listFiles();

        if (blocks == null) {
            put(out, "sys_class_block_status", "RESTRICTED_OR_UNAVAILABLE");
        } else {
            put(out, "sys_class_block_status", "READABLE");

            for (File block : blocks) {
                if (blockDevices.length() >= MAX_BLOCK_NODES) {
                    put(out, "block_device_limit_reached", true);
                    break;
                }

                JSONObject item = inspectBlockDevice(block);

                blockDevices.put(item);

                String name = block.getName();

                if (name.startsWith("dm-")) {
                    logicalDevices.put(item);
                } else if (name.startsWith("loop")) {
                    loopDevices.put(item);
                } else if (new File(block, "partition").exists()) {
                    partitionDevices.put(item);
                } else {
                    physicalDevices.put(item);
                }
            }
        }

        put(out, "proc_partitions",
                readTextFile("/proc/partitions", 5000));

        scanByName("/dev/block/by-name", byName);
        scanByName("/dev/block/bootdevice/by-name", byName);
        scanPlatformByName(byName);

        scanMapperDirectory("/dev/block/mapper", mapperLinks);

        Set<String> visited = new LinkedHashSet<>();
        int[] count = new int[]{0};

        scanBlockDirectory(
                new File("/dev/block"),
                0,
                visited,
                directoryNodes,
                count
        );

        String mountStatus = collectMounts(mounts);

        put(out, "physical_block_devices", physicalDevices);
        put(out, "partition_devices", partitionDevices);
        put(out, "logical_device_mapper_devices", logicalDevices);
        put(out, "loop_devices", loopDevices);
        put(out, "all_sysfs_block_devices", blockDevices);
        put(out, "by_name_partition_links", byName);
        put(out, "device_mapper_links", mapperLinks);
        put(out, "dev_block_directory_nodes", directoryNodes);
        put(out, "mount_points", mounts);
        put(out, "mount_collection_status", mountStatus);

        put(out, "device_count", blockDevices.length());
        put(out, "physical_device_count", physicalDevices.length());
        put(out, "partition_device_count", partitionDevices.length());
        put(out, "logical_device_count", logicalDevices.length());
        put(out, "loop_device_count", loopDevices.length());
        put(out, "by_name_link_count", byName.length());
        put(out, "mount_count", mounts.length());

        put(out, "partition_map_notes",
                "Sysfs block devices, partition nodes, by-name links, Device Mapper devices and mount points are separate categories. Symbolic links are not counted as new physical devices. Access restrictions do not mean that a partition does not exist.");

        return out;
    }

    private static JSONObject inspectBlockDevice(File block) {
        JSONObject item = new JSONObject();

        String name = block.getName();

        String sectors = readSmallFile(new File(block, "size"));
        String logicalSize = readSmallFile(
                new File(block, "queue/logical_block_size")
        );

        long sectorCount = parseLong(sectors, -1);
        long sectorBytes = parseLong(logicalSize, 512);

        if (sectorBytes <= 0) sectorBytes = 512;

        long totalBytes = -1;

        if (sectorCount >= 0) {
            totalBytes = sectorCount * sectorBytes;
        }

        put(item, "name", name);
        put(item, "sysfs_path", block.getAbsolutePath());
        put(item, "canonical_path", canonical(block));
        put(item, "device_number", readSmallFile(new File(block, "dev")));
        put(item, "size_sectors_512", sectors);
        put(item, "logical_block_size_bytes", logicalSize);
        put(item, "size_bytes", totalBytes);
        put(item, "size_human", humanBytes(totalBytes));
        put(item, "read_only", readSmallFile(new File(block, "ro")));
        put(item, "removable", readSmallFile(new File(block, "removable")));
        put(item, "partition_number",
                readSmallFile(new File(block, "partition")));
        put(item, "uevent", readSmallFile(new File(block, "uevent")));
        put(item, "type", classifyBlock(name, block));
        put(item, "slaves", listNames(new File(block, "slaves")));
        put(item, "holders", listNames(new File(block, "holders")));

        File dmName = new File(block, "dm/name");
        File dmUuid = new File(block, "dm/uuid");

        if (dmName.exists()) {
            put(item, "dm_name", readSmallFile(dmName));
        }

        if (dmUuid.exists()) {
            put(item, "dm_uuid", readSmallFile(dmUuid));
        }

        return item;
    }

    private static String classifyBlock(String name, File block) {
        if (name.startsWith("dm-")) return "DEVICE_MAPPER";
        if (name.startsWith("loop")) return "LOOP";
        if (name.startsWith("ram")) return "RAM_BLOCK";
        if (name.startsWith("zram")) return "ZRAM";
        if (new File(block, "partition").exists()) return "PARTITION";
        if (name.startsWith("mmcblk")) return "MMC_OR_UFS";
        if (name.startsWith("sd")) return "SCSI";
        if (name.startsWith("nvme")) return "NVME";
        return "OTHER_BLOCK_DEVICE";
    }

    private static void scanByName(String path, JSONArray target) {
        File dir = new File(path);
        File[] entries = dir.listFiles();

        if (entries == null) return;

        for (File entry : entries) {
            JSONObject item = new JSONObject();

            put(item, "name", entry.getName());
            put(item, "directory", path);
            put(item, "path", entry.getAbsolutePath());
            put(item, "canonical_target", canonical(entry));
            put(item, "exists", entry.exists());
            put(item, "is_symbolic_link",
                    !canonical(entry).equals(entry.getAbsolutePath()));
            put(item, "readable", entry.canRead());
            put(item, "writable", entry.canWrite());

            target.put(item);
        }
    }

    private static void scanPlatformByName(JSONArray target) {
        File platform = new File("/dev/block/platform");
        File[] entries = platform.listFiles();

        if (entries == null) return;

        for (File entry : entries) {
            scanByName(
                    new File(entry, "by-name").getAbsolutePath(),
                    target
            );
        }
    }

    private static void scanMapperDirectory(
            String path,
            JSONArray target
    ) {
        File dir = new File(path);
        File[] entries = dir.listFiles();

        if (entries == null) return;

        for (File entry : entries) {
            JSONObject item = new JSONObject();

            put(item, "name", entry.getName());
            put(item, "path", entry.getAbsolutePath());
            put(item, "canonical_target", canonical(entry));
            put(item, "exists", entry.exists());
            put(item, "readable", entry.canRead());
            put(item, "writable", entry.canWrite());

            target.put(item);
        }
    }

    private static void scanBlockDirectory(
            File directory,
            int depth,
            Set<String> visited,
            JSONArray output,
            int[] count
    ) {
        if (depth > MAX_DIRECTORY_DEPTH
                || count[0] >= MAX_BLOCK_NODES) {
            return;
        }

        File[] entries = directory.listFiles();

        if (entries == null) return;

        for (File entry : entries) {
            if (count[0] >= MAX_BLOCK_NODES) return;

            String path = entry.getAbsolutePath();
            String canonical = canonical(entry);

            if (!visited.add(canonical)) continue;

            JSONObject item = new JSONObject();

            put(item, "name", entry.getName());
            put(item, "path", path);
            put(item, "canonical_path", canonical);
            put(item, "exists", entry.exists());
            put(item, "readable", entry.canRead());
            put(item, "writable", entry.canWrite());
            put(item, "is_directory", entry.isDirectory());

            output.put(item);
            count[0]++;

            if (entry.isDirectory()
                    && !canonical.equals(path)) {
                continue;
            }

            if (entry.isDirectory()) {
                scanBlockDirectory(
                        entry,
                        depth + 1,
                        visited,
                        output,
                        count
                );
            }
        }
    }

    private static String collectMounts(JSONArray output) {
        String mountInfo = readTextFile("/proc/self/mountinfo", 10000);

        if (isReadError(mountInfo)) {
            mountInfo = readTextFile("/proc/mounts", 10000);

            if (isReadError(mountInfo)) {
                return "RESTRICTED_OR_UNAVAILABLE";
            }

            parseProcMounts(mountInfo, output);
            return "PROC_MOUNTS";
        }

        parseMountInfo(mountInfo, output);
        return "PROC_SELF_MOUNTINFO";
    }

    private static void parseMountInfo(
            String content,
            JSONArray output
    ) {
        Set<String> seen = new LinkedHashSet<>();

        String[] lines = content.split("\n");

        for (String line : lines) {
            String[] parts = line.trim().split("\\s+");

            int separator = -1;

            for (int i = 0; i < parts.length; i++) {
                if ("-".equals(parts[i])) {
                    separator = i;
                    break;
                }
            }

            if (separator < 0 || parts.length <= separator + 2
                    || parts.length < 6) {
                continue;
            }

            String source = unescapeMount(parts[separator + 2]);
            String mountPoint = unescapeMount(parts[4]);
            String key = source + "|" + mountPoint;

            if (!seen.add(key)) continue;

            JSONObject item = new JSONObject();

            put(item, "mount_id", parts[0]);
            put(item, "parent_id", parts[1]);
            put(item, "major_minor", parts[2]);
            put(item, "root", unescapeMount(parts[3]));
            put(item, "mount_point", mountPoint);
            put(item, "mount_options", parts[5]);
            put(item, "filesystem", parts[separator + 1]);
            put(item, "source", source);

            if (parts.length > separator + 3) {
                put(item, "super_options", parts[separator + 3]);
            }

            put(item, "read_only",
                    parts[5].contains("ro") || source.startsWith("/dev/block/"));

            output.put(item);
        }
    }

    private static void parseProcMounts(
            String content,
            JSONArray output
    ) {
        Set<String> seen = new LinkedHashSet<>();

        String[] lines = content.split("\n");

        for (String line : lines) {
            String[] parts = line.trim().split("\\s+");

            if (parts.length < 4) continue;

            String source = unescapeMount(parts[0]);
            String mountPoint = unescapeMount(parts[1]);
            String key = source + "|" + mountPoint;

            if (!seen.add(key)) continue;

            JSONObject item = new JSONObject();

            put(item, "source", source);
            put(item, "mount_point", mountPoint);
            put(item, "filesystem", parts[2]);
            put(item, "mount_options", parts[3]);
            put(item, "read_only", parts[3].contains("ro"));

            output.put(item);
        }
    }

    private static String unescapeMount(String value) {
        return value.replace("\\040", " ")
                .replace("\\011", "\t")
                .replace("\\012", "\n")
                .replace("\\134", "\\");
    }

    private static void addPartitionReport(
            StringBuilder report,
            JSONObject partitions
    ) {
        line(report, "Sysfs status: "
                + partitions.optString("sys_class_block_status"));

        line(report, "Physical block devices: "
                + partitions.optInt("physical_device_count"));

        line(report, "Partition devices: "
                + partitions.optInt("partition_device_count"));

        line(report, "Device Mapper devices: "
                + partitions.optInt("logical_device_count"));

        line(report, "Loop devices: "
                + partitions.optInt("loop_device_count"));

        line(report, "by-name links: "
                + partitions.optInt("by_name_link_count"));

        line(report, "Mount points: "
                + partitions.optInt("mount_count"));

        addArrayReport(report, "PHYSICAL BLOCK DEVICES",
                partitions.optJSONArray("physical_block_devices"));

        addArrayReport(report, "PARTITION DEVICES",
                partitions.optJSONArray("partition_devices"));

        addArrayReport(report, "DEVICE MAPPER / LOGICAL DEVICES",
                partitions.optJSONArray("logical_device_mapper_devices"));

        addArrayReport(report, "LOOP DEVICES",
                partitions.optJSONArray("loop_devices"));

        addArrayReport(report, "BY-NAME PARTITION LINKS",
                partitions.optJSONArray("by_name_partition_links"));

        addArrayReport(report, "DEVICE MAPPER LINKS",
                partitions.optJSONArray("device_mapper_links"));

        addArrayReport(report, "MOUNT RELATIONSHIPS",
                partitions.optJSONArray("mount_points"));

        line(report, "Mount source: "
                + partitions.optString("mount_collection_status"));

        line(report, " ");
        line(report, "Raw /proc/partitions:");
        line(report, partitions.optString("proc_partitions", "UNAVAILABLE"));
    }

    private static void addArrayReport(
            StringBuilder report,
            String title,
            JSONArray array
    ) {
        line(report, " ");
        line(report, "--- " + title + " ---");

        if (array == null || array.length() == 0) {
            line(report, "No entries visible to this application.");
            return;
        }

        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);

            if (item == null) {
                line(report, array.optString(i));
            } else {
                line(report, item.toString());
            }
        }
    }

    // ------------------------------------------------------------
    // GSI / TREBLE / VNDK
    // ------------------------------------------------------------

    private static JSONObject inspectGsi(
            Map<String, String> props
    ) {
        JSONObject out = new JSONObject();

        String treble = get(props, "ro.treble.enabled");
        String vndk = firstAvailable(
                props,
                "ro.vndk.version",
                "ro.product.vndk.version",
                "ro.vendor.vndk.version"
        );

        String vendorApi = firstAvailable(
                props,
                "ro.vendor.api_level",
                "ro.board.api_level",
                "ro.vendor.build.version.sdk"
        );

        String dynamic = firstAvailable(
                props,
                "ro.boot.dynamic_partitions",
                "ro.dynamic_partitions"
        );

        String ab = get(props, "ro.build.ab_update");
        String virtualAb = get(props, "ro.virtual_ab.enabled");

        String sdk = String.valueOf(Build.VERSION.SDK_INT);

        if (Build.VERSION.SDK_INT <= 0) {
            sdk = get(props, "ro.build.version.sdk");
        }

        String abi = join(Build.SUPPORTED_ABIS);

        if (isUnavailable(abi)) {
            abi = get(props, "ro.product.cpu.abilist");
        }

        String systemAsRoot = get(
                props,
                "ro.build.system_root_image"
        );

        String vendorManifest = findFirstReadable(
                "/vendor/etc/vintf/manifest.xml",
                "/vendor/manifest.xml",
                "/system/etc/vintf/manifest.xml"
        );

        String vendorFstab = findFirstReadable(
                "/vendor/etc/fstab",
                "/vendor/etc/fstab.qcom",
                "/vendor/etc/fstab.bengal"
        );

        String trebleAssessment;

        if (isTrue(treble)) {
            trebleAssessment = "ENABLED_PROPERTY";
        } else if (isFalse(treble)) {
            trebleAssessment = "DISABLED_PROPERTY";
        } else if (!isUnavailable(vendorManifest)) {
            trebleAssessment = "LIKELY_ENABLED_INFERRED_FROM_VINTF";
        } else {
            trebleAssessment = "UNAVAILABLE";
        }

        String manifestText = "UNAVAILABLE";
        String targetLevel = "UNAVAILABLE";

        if (!isUnavailable(vendorManifest)) {
            manifestText = readTextFile(vendorManifest, 300);
            targetLevel = findXmlAttribute(
                    manifestText,
                    "target-level"
            );
        }

        boolean arm64 = abi.toLowerCase(Locale.US).contains("arm64");
        boolean trebleYes = isTrue(treble)
                || trebleAssessment.startsWith("LIKELY_ENABLED");

        String assessment;

        if (trebleYes && arm64 && !isUnavailable(vndk)) {
            assessment = "BASIC_PREREQUISITES_PRESENT";
        } else if (trebleYes && arm64) {
            assessment = "TREBLE_AND_ARM64_PRESENT_VNDK_UNKNOWN";
        } else if (trebleYes || arm64) {
            assessment = "PARTIAL_PREREQUISITES";
        } else {
            assessment = "INSUFFICIENT_DATA";
        }

        put(out, "treble_property", treble);
        put(out, "treble_assessment", trebleAssessment);
        put(out, "treble_enabled",
                trebleYes ? "true_or_likely" : treble);

        put(out, "vndk_version", vndk);
        put(out, "vendor_api_level", vendorApi);
        put(out, "vendor_manifest_path", vendorManifest);
        put(out, "vendor_manifest_target_level", targetLevel);
        put(out, "vendor_fstab_path", vendorFstab);

        put(out, "dynamic_partitions_property", dynamic);
        put(out, "ab_update", ab);
        put(out, "virtual_ab", virtualAb);
        put(out, "sdk", sdk);
        put(out, "cpu_abilist", abi);
        put(out, "system_as_root_property", systemAsRoot);

        put(out, "arm64_abi_present", arm64);
        put(out, "preliminary_assessment", assessment);

        put(out, "limitations",
                "This is a preliminary compatibility assessment. It does not verify VINTF compatibility, kernel compatibility, AVB policy, partition image compatibility or bootloader acceptance.");

        return out;
    }

    private static String findFirstReadable(String... paths) {
        for (String path : paths) {
            File file = new File(path);

            if (file.exists() && file.canRead()) {
                return path;
            }
        }

        return "UNAVAILABLE";
    }

    private static String findXmlAttribute(
            String xml,
            String attribute
    ) {
        if (isUnavailable(xml)) return "UNAVAILABLE";

        Pattern pattern = Pattern.compile(
                attribute + "\\s*=\\s*\"([^\"]+)\""
        );

        Matcher matcher = pattern.matcher(xml);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return "NOT_FOUND_IN_READABLE_MANIFEST";
    }

    // ------------------------------------------------------------
    // QUALCOMM / SOC
    // ------------------------------------------------------------

    private static JSONObject inspectSoc(
            Map<String, String> props
    ) {
        JSONObject out = new JSONObject();

        String platform = firstAvailable(
                props,
                "ro.board.platform",
                "ro.boot.hardware",
                "ro.hardware"
        );

        String hardware = firstAvailable(
                props,
                "ro.boot.hardware",
                "ro.hardware",
                "ro.boot.product.hardware.sku"
        );

        String manufacturer = get(props, "ro.soc.manufacturer");
        String model = get(props, "ro.soc.model");
        String chipName = get(props, "ro.hardware.chipname");

        String[] socPaths = {
                "/sys/devices/soc0",
                "/sys/devices/system/soc/soc0"
        };

        JSONObject sysSoc = new JSONObject();

        for (String base : socPaths) {
            File dir = new File(base);

            if (!dir.exists()) continue;

            put(sysSoc, "path", base);
            put(sysSoc, "family",
                    readSmallFile(new File(dir, "family")));
            put(sysSoc, "machine",
                    readSmallFile(new File(dir, "machine")));
            put(sysSoc, "soc_id",
                    readSmallFile(new File(dir, "soc_id")));
            put(sysSoc, "revision",
                    readSmallFile(new File(dir, "revision")));
            put(sysSoc, "serial_number",
                    "NOT_COLLECTED_FOR_PRIVACY");

            break;
        }

        boolean qualcomm = containsAny(
                platform + " " + hardware + " " + manufacturer,
                "qcom",
                "qualcomm",
                "bengal",
                "sm"
        );

        boolean bengal = containsAny(
                platform + " " + hardware + " " + chipName,
                "bengal"
        );

        put(out, "platform", platform);
        put(out, "hardware", hardware);
        put(out, "soc_manufacturer", manufacturer);
        put(out, "soc_model", model);
        put(out, "chip_name", chipName);
        put(out, "qualcomm_platform_indicators", qualcomm);
        put(out, "bengal_platform_indicator", bengal);
        put(out, "sysfs_soc_information", sysSoc);

        put(out, "bootloader_interface",
                "NOT_ACCESSIBLE_FROM_NORMAL_ANDROID_APP");

        put(out, "fastboot_mode",
                "CANNOT_BE_CONFIRMED_WHILE_ANDROID_IS_RUNNING");

        put(out, "edl_mode",
                "CANNOT_BE_CONFIRMED_FROM_NORMAL_ANDROID_SESSION");

        put(out, "firehose_authentication",
                "NOT_TESTED_AND_NOT_GENERATED");

        return out;
    }

    // ------------------------------------------------------------
    // HARDWARE
    // ------------------------------------------------------------

    private static JSONObject inspectHardware(Context context) {
        JSONObject out = new JSONObject();

        put(out, "cpu_cores", Runtime.getRuntime().availableProcessors());
        put(out, "cpu_info", readTextFile("/proc/cpuinfo", 100));
        put(out, "ram", readRam(context));
        put(out, "storage", readStorage());

        DisplayMetrics metrics =
                context.getResources().getDisplayMetrics();

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
                    new android.content.IntentFilter(
                            Intent.ACTION_BATTERY_CHANGED
                    )
            );

            if (intent != null) {
                int level = intent.getIntExtra("level", -1);
                int scale = intent.getIntExtra("scale", -1);
                int temperature = intent.getIntExtra("temperature", -1);

                put(battery, "level", level);
                put(battery, "scale", scale);
                put(battery, "percentage",
                        scale > 0 && level >= 0
                                ? (level * 100 / scale) : -1);
                put(battery, "temperature_tenths_c", temperature);
                put(battery, "temperature_c",
                        temperature >= 0 ? temperature / 10.0 : -1);
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
            put(battery, "status",
                    "RESTRICTED: " + e.getClass().getSimpleName());
        }

        put(out, "battery", battery);

        JSONArray sensors = new JSONArray();

        try {
            SensorManager manager = (SensorManager)
                    context.getSystemService(Context.SENSOR_SERVICE);

            if (manager != null) {
                for (Sensor sensor :
                        manager.getSensorList(Sensor.TYPE_ALL)) {

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
            CameraManager manager = (CameraManager)
                    context.getSystemService(Context.CAMERA_SERVICE);

            if (manager != null) {
                for (String id : manager.getCameraIdList()) {
                    cameras.put(id);
                }
            }
        } catch (Exception e) {
            cameras.put("RESTRICTED: "
                    + e.getClass().getSimpleName());
        }

        put(out, "camera_ids", cameras);

        PackageManager pm = context.getPackageManager();

        JSONObject graphics = new JSONObject();

        put(graphics, "vulkan_level_feature",
                pm.hasSystemFeature(
                        "android.hardware.vulkan.level"
                ));

        put(graphics, "vulkan_version_feature",
                pm.hasSystemFeature(
                        "android.hardware.vulkan.version"
                ));

        put(graphics, "opengl_es_version",
                pm.getSystemAvailableFeatures() == null
                        ? "UNAVAILABLE"
                        : getOpenGlVersion(pm));

        put(out, "graphics_features", graphics);

        return out;
    }

    private static String getOpenGlVersion(PackageManager pm) {
        try {
            for (android.content.pm.FeatureInfo feature :
                    pm.getSystemAvailableFeatures()) {

                if (feature != null
                        && feature.reqGlEsVersion != 0) {

                    int version = feature.reqGlEsVersion;

                    return ((version >> 16) & 0xffff)
                            + "." + (version & 0xffff);
                }
            }
        } catch (Exception ignored) {
        }

        return "UNAVAILABLE";
    }

    // ------------------------------------------------------------
    // BOOTLOADER INVESTIGATION
    // ------------------------------------------------------------

    private static void addUnlockAnalysis(
            StringBuilder report,
            JSONObject boot,
            JSONObject soc
    ) {
        line(report, "Bootloader indicators: "
                + boot.optString(
                        "bootloader_assessment",
                        "UNKNOWN"
                ));

        line(report, "Unlock assessment: "
                + boot.optString(
                        "unlock_assessment",
                        "UNKNOWN"
                ));

        line(report, "Fastboot query: "
                + boot.optString(
                        "fastboot_query_status",
                        "UNKNOWN"
                ));

        line(report, "EDL query: "
                + boot.optString(
                        "edl_query_status",
                        "UNKNOWN"
                ));

        line(report, "Qualcomm indicator: "
                + soc.optString(
                        "qualcomm_platform_indicators",
                        "UNKNOWN"
                ));

        line(report, "Bengal indicator: "
                + soc.optString(
                        "bengal_platform_indicator",
                        "UNKNOWN"
                ));

        line(report, " ");
        line(report, "Important distinctions:");
        line(report, "- Android properties are indicators, not proof of OEM unlock authorization.");
        line(report, "- Fastboot variables require the phone to be in actual Fastboot mode and a host-side query.");
        line(report, "- A bootloader interface cannot be interrogated by this ordinary Android application.");
        line(report, "- EDL availability does not imply that an authenticated programmer is available.");
        line(report, "- This application does not bypass Qualcomm Secure Boot.");
        line(report, "- No unlock, erase, flash, format or EDL command is executed.");
    }

    // ------------------------------------------------------------
    // OVERALL STATUS
    // ------------------------------------------------------------

    private static String calculateOverallStatus(
            JSONObject boot,
            JSONObject partitions,
            JSONObject gsi,
            Map<String, String> props
    ) {
        boolean hasDeviceInfo = Build.MODEL != null
                && !Build.MODEL.trim().isEmpty();

        boolean hasProperties = !props.isEmpty();

        boolean hasBlockInfo = "READABLE".equals(
                partitions.optString(
                        "sys_class_block_status"
                )
        );

        boolean hasGsiInfo = !isUnavailable(
                gsi.optString("sdk")
        ) && !isUnavailable(
                gsi.optString("cpu_abilist")
        );

        int successful = 0;

        if (hasDeviceInfo) successful++;
        if (hasProperties) successful++;
        if (hasBlockInfo) successful++;
        if (hasGsiInfo) successful++;

        if (successful == 4) {
            return "COLLECTED_WITH_CORE_DATA";
        }

        if (successful >= 2) {
            return "COLLECTED_WITH_LIMITATIONS";
        }

        return "PARTIAL_COLLECTION";
    }

    // ------------------------------------------------------------
    // SYSTEM PROPERTY READER
    // ------------------------------------------------------------

    private static LinkedHashMap<String, String> readAllProperties() {
        LinkedHashMap<String, String> result =
                new LinkedHashMap<>();

        BufferedReader reader = null;

        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{"getprop"}
            );

            reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;
            int count = 0;

            while ((line = reader.readLine()) != null
                    && count < MAX_PROPERTIES) {

                int start = line.indexOf('[');
                int middle = line.indexOf("]: [", start + 1);
                int end = line.lastIndexOf(']');

                if (start < 0 || middle < 0
                        || end <= middle + 4) {
                    continue;
                }

                String key = line.substring(
                        start + 1,
                        middle
                );

                String value = line.substring(
                        middle + 4,
                        end
                );

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

    private static String readSingleProperty(String key) {
        BufferedReader reader = null;

        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{"getprop", key}
            );

            reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String value = reader.readLine();

            process.waitFor();

            if (value == null || value.trim().isEmpty()) {
                return "UNAVAILABLE";
            }

            return value.trim();

        } catch (Exception e) {
            return "UNAVAILABLE";
        } finally {
            close(reader);
        }
    }

    // ------------------------------------------------------------
    // FILE / SYSTEM HELPERS
    // ------------------------------------------------------------

    private static String readRam(Context context) {
        try {
            ActivityManager manager = (ActivityManager)
                    context.getSystemService(
                            Context.ACTIVITY_SERVICE
                    );

            if (manager == null) return "UNAVAILABLE";

            ActivityManager.MemoryInfo info =
                    new ActivityManager.MemoryInfo();

            manager.getMemoryInfo(info);

            return "Total: "
                    + (info.totalMem / 1048576L)
                    + " MB; available: "
                    + (info.availMem / 1048576L)
                    + " MB; low memory: "
                    + info.lowMemory;

        } catch (Exception e) {
            return "UNAVAILABLE: "
                    + e.getClass().getSimpleName();
        }
    }

    private static String readStorage() {
        try {
            File path = Environment.getDataDirectory();
            StatFs stat = new StatFs(path.getAbsolutePath());

            long total = stat.getTotalBytes();
            long available = stat.getAvailableBytes();

            return "Data filesystem: "
                    + humanBytes(total)
                    + " total; "
                    + humanBytes(available)
                    + " available";

        } catch (Exception e) {
            return "UNAVAILABLE: "
                    + e.getClass().getSimpleName();
        }
    }

    private static String readTextFile(
            String path,
            int maxLines
    ) {
        return readTextFile(new File(path), maxLines);
    }

    private static String readTextFile(
            File file,
            int maxLines
    ) {
        BufferedReader reader = null;
        StringBuilder out = new StringBuilder();

        try {
            reader = new BufferedReader(
                    new FileReader(file)
            );

            String line;
            int count = 0;

            while ((line = reader.readLine()) != null
                    && count < maxLines) {

                if (out.length() > 0) {
                    out.append('\n');
                }

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

    private static String readSmallFile(File file) {
        return readTextFile(file, 20);
    }

    private static String listNames(File directory) {
        File[] files = directory.listFiles();

        if (files == null) {
            return directory.exists()
                    ? "RESTRICTED_OR_UNAVAILABLE"
                    : "EMPTY_OR_UNAVAILABLE";
        }

        StringBuilder result = new StringBuilder();

        for (File file : files) {
            if (result.length() > 0) {
                result.append(", ");
            }

            result.append(file.getName());
        }

        return result.length() == 0
                ? "EMPTY" : result.toString();
    }

    private static String readSetting(
            Context context,
            boolean global,
            String name
    ) {
        try {
            int value;

            if (global) {
                value = Settings.Global.getInt(
                        context.getContentResolver(),
                        name,
                        -1
                );
            } else {
                value = Settings.Secure.getInt(
                        context.getContentResolver(),
                        name,
                        -1
                );
            }

            return value == -1
                    ? "NOT_EXPOSED"
                    : String.valueOf(value);

        } catch (Exception e) {
            return "NOT_EXPOSED";
        }
    }

    // ------------------------------------------------------------
    // GENERAL HELPERS
    // ------------------------------------------------------------

    private static String firstAvailable(
            Map<String, String> props,
            String... keys
    ) {
        for (String key : keys) {
            String value = get(props, key);

            if (!isUnavailable(value)) {
                return value;
            }
        }

        return "UNAVAILABLE";
    }

    private static String get(
            Map<String, String> props,
            String key
    ) {
        String value = props.get(key);

        return value == null || value.trim().isEmpty()
                ? "UNAVAILABLE"
                : value;
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
                || "unknown".equalsIgnoreCase(value)
                || "null".equalsIgnoreCase(value);
    }

    private static boolean isReadError(String value) {
        return value == null
                || value.startsWith("RESTRICTED_OR_UNAVAILABLE")
                || "EMPTY".equals(value)
                || "UNAVAILABLE".equals(value);
    }

    private static boolean containsAny(
            String source,
            String... values
    ) {
        if (source == null) return false;

        String lower = source.toLowerCase(Locale.US);

        for (String value : values) {
            if (lower.contains(value.toLowerCase(Locale.US))) {
                return true;
            }
        }

        return false;
    }

    private static long parseLong(
            String value,
            long fallback
    ) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String humanBytes(long bytes) {
        if (bytes < 0) return "UNAVAILABLE";

        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};

        int index = 0;

        while (value >= 1024 && index < units.length - 1) {
            value /= 1024;
            index++;
        }

        return String.format(
                Locale.US,
                "%.2f %s",
                value,
                units[index]
        );
    }

    private static String canonical(File file) {
        try {
            return file.getCanonicalPath();
        } catch (Exception e) {
            return "UNRESOLVED";
        }
    }

    private static String join(String[] values) {
        if (values == null || values.length == 0) {
            return "UNAVAILABLE";
        }

        StringBuilder out = new StringBuilder();

        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            if (out.length() > 0) {
                out.append(", ");
            }

            out.append(value);
        }

        return out.length() == 0
                ? "UNAVAILABLE"
                : out.toString();
    }

    private static void section(
            StringBuilder out,
            String title
    ) {
        out.append("\n==================================================\n");
        out.append(title).append('\n');
        out.append("==================================================\n");
    }

    private static void line(
            StringBuilder out,
            String value
    ) {
        out.append(value == null
                ? "UNAVAILABLE"
                : value).append('\n');
    }

    private static void addJsonLines(
            StringBuilder out,
            JSONObject object
    ) {
        if (object == null) return;

        JSONArray names = object.names();

        if (names == null) {
            line(out, "No readable values.");
            return;
        }

        for (int i = 0; i < names.length(); i++) {
            String key = names.optString(i);
            Object value = object.opt(key);

            if (value instanceof JSONObject
                    || value instanceof JSONArray) {

                line(out, key + ":");
                line(out, value.toString());

            } else {
                line(out, key + ": " + String.valueOf(value));
            }
        }
    }

    private static void put(
            JSONObject object,
            String key,
            Object value
    ) {
        try {
            object.put(
                    key,
                    value == null
                            ? JSONObject.NULL
                            : value
            );
        } catch (Exception ignored) {
        }
    }

    private static void close(BufferedReader reader) {
        if (reader == null) return;

        try {
            reader.close();
        } catch (Exception ignored) {
        }
    }
}
