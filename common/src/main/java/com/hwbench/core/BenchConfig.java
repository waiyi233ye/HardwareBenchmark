package com.hwbench.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HardwareBenchmark 统一配置类
 *
 * <p>从 {@code <服务端根>/config/hwbench.json} 读取配置，配置文件缺失时自动生成默认配置。
 * 所有跑分模块（CPU/内存/磁盘/报告）的参数都集中在此类中，通过 {@link #load(File)} 单例获取。</p>
 *
 * <p>JSON 解析采用内置的简易解析器，不依赖 gson 等外部库，兼容 Java 8。
 * 任何字段缺失或类型错误时使用默认值；整体解析失败时打印警告并使用默认值。</p>
 */
public class BenchConfig {

    // ===== CPU 配置 =====
    public final int cpuDonutFrames;
    public final int cpuComputeIterations;
    public final int cpuMatrixSize;
    public final boolean cpuShowAnimation;
    public final int cpuPrimeRange;
    public final int cpuFloatIterations;
    public final int cpuTimeoutSeconds;

    // ===== 内存配置 =====
    public final int memArraySizeMB;
    public final int memIterations;
    public final int memRandomAccessCount;
    public final int memTimeoutSeconds;

    // ===== 磁盘配置 =====
    public final int diskFileSizeMB;
    public final int diskBlockSizeKB;
    public final int diskRandomIOCount;
    public final int diskTimeoutSeconds;

    // ===== 报告配置 =====
    public final boolean reportSaveToFile;
    public final String reportOutputDir;
    public final boolean reportVerboseConsole;
    public final boolean reportWriteToServerLogs;

    /** 单例 */
    private static BenchConfig instance;

    /** 默认配置文件内容（与字段默认值保持一致） */
    private static final String DEFAULT_CONFIG_JSON =
            "{\n" +
            "  \"cpu\": {\n" +
            "    \"donut-frames\": 300,\n" +
            "    \"compute-iterations\": 5,\n" +
            "    \"matrix-size\": 512,\n" +
            "    \"show-donut-animation\": true,\n" +
            "    \"prime-range\": 10000000,\n" +
            "    \"float-iterations\": 50000000,\n" +
            "    \"timeout-seconds\": 60\n" +
            "  },\n" +
            "  \"memory\": {\n" +
            "    \"array-size-mb\": 64,\n" +
            "    \"iterations\": 3,\n" +
            "    \"random-access-count\": 1000000,\n" +
            "    \"timeout-seconds\": 60\n" +
            "  },\n" +
            "  \"disk\": {\n" +
            "    \"file-size-mb\": 512,\n" +
            "    \"block-size-kb\": 64,\n" +
            "    \"random-io-count\": 5000,\n" +
            "    \"timeout-seconds\": 120\n" +
            "  },\n" +
            "  \"report\": {\n" +
            "    \"save-to-file\": true,\n" +
            "    \"output-dir\": \"hwbench-reports\",\n" +
            "    \"verbose-console\": true,\n" +
            "    \"write-to-server-logs\": true\n" +
            "  }\n" +
            "}\n";

    /** 默认值常量，配置缺失或解析失败时回退使用 */
    private static final int DEF_CPU_DONUT_FRAMES = 300;
    private static final int DEF_CPU_COMPUTE_ITERATIONS = 5;
    private static final int DEF_CPU_MATRIX_SIZE = 512;
    private static final boolean DEF_CPU_SHOW_ANIMATION = true;
    private static final int DEF_CPU_PRIME_RANGE = 10_000_000;
    private static final int DEF_CPU_FLOAT_ITERATIONS = 50_000_000;
    private static final int DEF_CPU_TIMEOUT_SECONDS = 60;

    private static final int DEF_MEM_ARRAY_SIZE_MB = 64;
    private static final int DEF_MEM_ITERATIONS = 3;
    private static final int DEF_MEM_RANDOM_ACCESS_COUNT = 1_000_000;
    private static final int DEF_MEM_TIMEOUT_SECONDS = 60;

    private static final int DEF_DISK_FILE_SIZE_MB = 512;
    private static final int DEF_DISK_BLOCK_SIZE_KB = 64;
    private static final int DEF_DISK_RANDOM_IO_COUNT = 5000;
    private static final int DEF_DISK_TIMEOUT_SECONDS = 120;

    private static final boolean DEF_REPORT_SAVE_TO_FILE = true;
    private static final String DEF_REPORT_OUTPUT_DIR = "hwbench-reports";
    private static final boolean DEF_REPORT_VERBOSE_CONSOLE = true;
    private static final boolean DEF_REPORT_WRITE_TO_SERVER_LOGS = true;

    /**
     * 私有构造：所有字段由 {@link #loadConfig(File)} 解析后传入。
     */
    private BenchConfig(int cpuDonutFrames, int cpuComputeIterations, int cpuMatrixSize,
                        boolean cpuShowAnimation, int cpuPrimeRange, int cpuFloatIterations,
                        int cpuTimeoutSeconds,
                        int memArraySizeMB, int memIterations, int memRandomAccessCount,
                        int memTimeoutSeconds,
                        int diskFileSizeMB, int diskBlockSizeKB, int diskRandomIOCount,
                        int diskTimeoutSeconds,
                        boolean reportSaveToFile, String reportOutputDir,
                        boolean reportVerboseConsole, boolean reportWriteToServerLogs) {
        this.cpuDonutFrames = cpuDonutFrames;
        this.cpuComputeIterations = cpuComputeIterations;
        this.cpuMatrixSize = cpuMatrixSize;
        this.cpuShowAnimation = cpuShowAnimation;
        this.cpuPrimeRange = cpuPrimeRange;
        this.cpuFloatIterations = cpuFloatIterations;
        this.cpuTimeoutSeconds = cpuTimeoutSeconds;

        this.memArraySizeMB = memArraySizeMB;
        this.memIterations = memIterations;
        this.memRandomAccessCount = memRandomAccessCount;
        this.memTimeoutSeconds = memTimeoutSeconds;

        this.diskFileSizeMB = diskFileSizeMB;
        this.diskBlockSizeKB = diskBlockSizeKB;
        this.diskRandomIOCount = diskRandomIOCount;
        this.diskTimeoutSeconds = diskTimeoutSeconds;

        this.reportSaveToFile = reportSaveToFile;
        this.reportOutputDir = reportOutputDir;
        this.reportVerboseConsole = reportVerboseConsole;
        this.reportWriteToServerLogs = reportWriteToServerLogs;
    }

    /**
     * 加载单例配置（仅首次调用时实际读盘，后续直接返回缓存实例）。
     *
     * @param serverRoot 服务端根目录（{@code config/hwbench.json} 的父目录）
     * @return 已加载的配置实例
     */
    public static synchronized BenchConfig load(File serverRoot) {
        if (instance == null) {
            instance = loadConfig(serverRoot);
        }
        return instance;
    }

    /**
     * 强制重新读取配置文件并刷新单例。
     *
     * @param serverRoot 服务端根目录
     * @return 重新加载后的配置实例
     */
    public static synchronized BenchConfig reload(File serverRoot) {
        instance = loadConfig(serverRoot);
        return instance;
    }

    /**
     * 实际读取并解析配置文件的核心逻辑。
     * <ul>
     *   <li>文件不存在时写出默认配置后返回默认实例</li>
     *   <li>解析失败时打印警告并返回默认实例</li>
     *   <li>单个字段缺失/类型错误时该字段回退到默认值</li>
     * </ul>
     */
    private static BenchConfig loadConfig(File serverRoot) {
        // 1. 计算配置文件路径：serverRoot/config/hwbench.json
        File configDir = new File(serverRoot, "config");
        File configFile = new File(configDir, "hwbench.json");
        Path configPath = configFile.toPath();

        // 2. 文件不存在则生成默认配置
        if (!Files.exists(configPath)) {
            try {
                Files.createDirectories(configDir.toPath());
                Files.write(configPath, DEFAULT_CONFIG_JSON.getBytes(StandardCharsets.UTF_8));
                System.out.println("[HWBench] 未找到配置文件，已在 " + configFile.getAbsolutePath()
                        + " 生成默认配置");
            } catch (IOException e) {
                System.err.println("[HWBench] 生成默认配置文件失败: " + e.getMessage());
            }
            // 无论写入是否成功，都返回默认实例
            return buildDefault();
        }

        // 3. 读取并解析
        Map<String, Object> root;
        try {
            byte[] bytes = Files.readAllBytes(configPath);
            String content = new String(bytes, StandardCharsets.UTF_8);
            root = parseJsonObject(content);
        } catch (Exception e) {
            System.err.println("[HWBench] 配置文件解析失败，使用默认值: " + e.getMessage());
            return buildDefault();
        }

        if (root == null) {
            System.err.println("[HWBench] 配置文件解析失败，使用默认值: 配置内容不是有效的 JSON 对象");
            return buildDefault();
        }

        // 4. 逐段提取（任何字段缺失/类型错误均回退默认）
        Map<String, Object> cpu = getObject(root, "cpu");
        Map<String, Object> mem = getObject(root, "memory");
        Map<String, Object> disk = getObject(root, "disk");
        Map<String, Object> report = getObject(root, "report");

        return new BenchConfig(
                getInt(cpu, "donut-frames", DEF_CPU_DONUT_FRAMES),
                getInt(cpu, "compute-iterations", DEF_CPU_COMPUTE_ITERATIONS),
                getInt(cpu, "matrix-size", DEF_CPU_MATRIX_SIZE),
                getBoolean(cpu, "show-donut-animation", DEF_CPU_SHOW_ANIMATION),
                getInt(cpu, "prime-range", DEF_CPU_PRIME_RANGE),
                getInt(cpu, "float-iterations", DEF_CPU_FLOAT_ITERATIONS),
                getInt(cpu, "timeout-seconds", DEF_CPU_TIMEOUT_SECONDS),

                getInt(mem, "array-size-mb", DEF_MEM_ARRAY_SIZE_MB),
                getInt(mem, "iterations", DEF_MEM_ITERATIONS),
                getInt(mem, "random-access-count", DEF_MEM_RANDOM_ACCESS_COUNT),
                getInt(mem, "timeout-seconds", DEF_MEM_TIMEOUT_SECONDS),

                getInt(disk, "file-size-mb", DEF_DISK_FILE_SIZE_MB),
                getInt(disk, "block-size-kb", DEF_DISK_BLOCK_SIZE_KB),
                getInt(disk, "random-io-count", DEF_DISK_RANDOM_IO_COUNT),
                getInt(disk, "timeout-seconds", DEF_DISK_TIMEOUT_SECONDS),

                getBoolean(report, "save-to-file", DEF_REPORT_SAVE_TO_FILE),
                getString(report, "output-dir", DEF_REPORT_OUTPUT_DIR),
                getBoolean(report, "verbose-console", DEF_REPORT_VERBOSE_CONSOLE),
                getBoolean(report, "write-to-server-logs", DEF_REPORT_WRITE_TO_SERVER_LOGS)
        );
    }

    /** 构造一个全部使用默认值的实例 */
    private static BenchConfig buildDefault() {
        return new BenchConfig(
                DEF_CPU_DONUT_FRAMES, DEF_CPU_COMPUTE_ITERATIONS, DEF_CPU_MATRIX_SIZE,
                DEF_CPU_SHOW_ANIMATION, DEF_CPU_PRIME_RANGE, DEF_CPU_FLOAT_ITERATIONS,
                DEF_CPU_TIMEOUT_SECONDS,
                DEF_MEM_ARRAY_SIZE_MB, DEF_MEM_ITERATIONS, DEF_MEM_RANDOM_ACCESS_COUNT,
                DEF_MEM_TIMEOUT_SECONDS,
                DEF_DISK_FILE_SIZE_MB, DEF_DISK_BLOCK_SIZE_KB, DEF_DISK_RANDOM_IO_COUNT,
                DEF_DISK_TIMEOUT_SECONDS,
                DEF_REPORT_SAVE_TO_FILE, DEF_REPORT_OUTPUT_DIR,
                DEF_REPORT_VERBOSE_CONSOLE, DEF_REPORT_WRITE_TO_SERVER_LOGS
        );
    }

    // ====================== 类型安全提取工具 ======================

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getObject(Map<String, Object> map, String key) {
        if (map == null) return new LinkedHashMap<String, Object>();
        Object v = map.get(key);
        if (v instanceof Map) return (Map<String, Object>) v;
        return new LinkedHashMap<String, Object>();
    }

    private static int getInt(Map<String, Object> map, String key, int def) {
        if (map == null) return def;
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return def;
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        if (map == null) return def;
        Object v = map.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        return def;
    }

    private static String getString(Map<String, Object> map, String key, String def) {
        if (map == null) return def;
        Object v = map.get(key);
        if (v instanceof String) return (String) v;
        return def;
    }

    // ====================== 内置 JSON 解析器 ======================

    /**
     * 解析 JSON 文本，返回根对象（仅支持对象作为根节点，符合配置文件结构）。
     * 不依赖 gson/jackson，兼容 Java 8。
     */
    private static Map<String, Object> parseJsonObject(String text) {
        SimpleJsonParser parser = new SimpleJsonParser(text);
        Object result = parser.parse();
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            return map;
        }
        return null;
    }

    /**
     * 简易递归下降 JSON 解析器
     * 支持：对象、数组、字符串（含基本转义）、数字（int/double）、布尔、null。
     */
    private static final class SimpleJsonParser {
        private final String json;
        private int pos;

        SimpleJsonParser(String json) {
            this.json = json;
            this.pos = 0;
        }

        Object parse() {
            skipWhitespace();
            return parseValue();
        }

        private Object parseValue() {
            skipWhitespace();
            if (pos >= json.length()) return null;
            char c = json.charAt(pos);
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                case 't':
                case 'f': return parseBoolean();
                case 'n': return parseNull();
                default: return parseNumber();
            }
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            pos++; // 跳过 '{'
            skipWhitespace();
            if (peek() == '}') { pos++; return map; }

            while (pos < json.length()) {
                skipWhitespace();
                if (peek() != '"') {
                    // 容错：跳到下一个引号或结束
                    if (peek() == '}') { pos++; break; }
                    pos++;
                    continue;
                }
                String key = parseString();
                skipWhitespace();
                if (peek() == ':') pos++;
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char c = peek();
                if (c == ',') { pos++; continue; }
                if (c == '}') { pos++; break; }
                break;
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<Object>();
            pos++; // 跳过 '['
            skipWhitespace();
            if (peek() == ']') { pos++; return list; }

            while (pos < json.length()) {
                Object value = parseValue();
                list.add(value);
                skipWhitespace();
                char c = peek();
                if (c == ',') { pos++; continue; }
                if (c == ']') { pos++; break; }
                break;
            }
            return list;
        }

        private String parseString() {
            StringBuilder sb = new StringBuilder();
            pos++; // 跳过开头的 '"'
            while (pos < json.length()) {
                char c = json.charAt(pos++);
                if (c == '"') break;
                if (c == '\\' && pos < json.length()) {
                    char next = json.charAt(pos++);
                    switch (next) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            if (pos + 4 <= json.length()) {
                                String hex = json.substring(pos, pos + 4);
                                try {
                                    sb.append((char) Integer.parseInt(hex, 16));
                                } catch (NumberFormatException e) {
                                    sb.append(hex);
                                }
                                pos += 4;
                            }
                            break;
                        default: sb.append(next);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Object parseNumber() {
            int start = pos;
            if (peek() == '-' || peek() == '+') pos++;
            boolean isFloat = false;
            while (pos < json.length()) {
                char c = json.charAt(pos);
                if ((c >= '0' && c <= '9')) { pos++; continue; }
                if (c == '.' || c == 'e' || c == 'E') { isFloat = true; pos++; continue; }
                if ((c == '+' || c == '-') && pos > start) { pos++; continue; }
                break;
            }
            String num = json.substring(start, pos);
            if (num.isEmpty()) return Integer.valueOf(0);
            try {
                if (isFloat) return Double.parseDouble(num);
                return Integer.parseInt(num);
            } catch (NumberFormatException e) {
                try { return Double.parseDouble(num); }
                catch (NumberFormatException e2) { return Integer.valueOf(0); }
            }
        }

        private Boolean parseBoolean() {
            if (json.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (json.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            return null;
        }

        private Object parseNull() {
            if (json.startsWith("null", pos)) { pos += 4; return null; }
            return null;
        }

        private char peek() {
            return pos < json.length() ? json.charAt(pos) : '\0';
        }

        private void skipWhitespace() {
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
        }
    }
}
