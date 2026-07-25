package com.hwbench.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Linux运行库自动补全模块
 * 检测缺失的系统工具库并自动安装
 */
public class LibraryManager {

    private final List<String> requiredLibraries;
    private final boolean autoInstall;
    private final String packageManagerPref;

    public LibraryManager(List<String> requiredLibraries, boolean autoInstall, String packageManagerPref) {
        this.requiredLibraries = requiredLibraries;
        this.autoInstall = autoInstall;
        this.packageManagerPref = packageManagerPref;
    }

    /**
     * 检查并补全所有缺失的库
     * @return 检查和安装结果报告
     */
    public String checkAndInstall() {
        StringBuilder report = new StringBuilder();
        report.append("═══════════════════════════════════════════════════\n");
        report.append("          Linux运行库检查与补全报告\n");
        report.append("═══════════════════════════════════════════════════\n\n");

        // 检测操作系统
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("linux")) {
            report.append("当前系统不是Linux (").append(osName).append(")，跳过库检查。\n");
            return report.toString();
        }

        // 检测是否为root用户
        boolean isRoot = "root".equals(System.getProperty("user.name"));
        if (!isRoot) {
            report.append("⚠ 当前非root用户，自动安装可能需要sudo权限\n\n");
        }

        // 检测包管理器
        String pkgManager = detectPackageManager();
        if (pkgManager == null) {
            report.append("✗ 未检测到支持的包管理器\n");
            report.append("支持的包管理器: apt, yum, dnf, pacman, zypper\n\n");
            return report.toString();
        }
        report.append("检测到包管理器: ").append(pkgManager).append("\n\n");

        // 检查JNA本地库
        report.append("【Java本地库检查】\n");
        String jnaPath = checkJNALibrary();
        report.append(jnaPath).append("\n");

        // 检查每个必需的库
        report.append("【系统工具库检查】\n");
        List<String> missingLibs = new ArrayList<>();
        for (String lib : requiredLibraries) {
            boolean installed = checkLibraryInstalled(lib, pkgManager);
            if (installed) {
                report.append(String.format("  ✓ %s - 已安装\n", lib));
            } else {
                report.append(String.format("  ✗ %s - 缺失\n", lib));
                missingLibs.add(lib);
            }
        }
        report.append("\n");

        // 自动安装缺失的库
        if (!missingLibs.isEmpty()) {
            if (autoInstall) {
                report.append("【自动安装缺失库】\n");
                for (String lib : missingLibs) {
                    String installResult = installLibrary(lib, pkgManager, isRoot);
                    report.append(installResult).append("\n");
                }
            } else {
                report.append("【手动安装命令】\n");
                for (String lib : missingLibs) {
                    report.append(getInstallCommand(lib, pkgManager, isRoot)).append("\n");
                }
                report.append("\n");
            }
        } else {
            report.append("✓ 所有必需库已安装\n\n");
        }

        // 检查JVM环境
        report.append("【JVM环境检查】\n");
        report.append(String.format("  Java版本: %s\n", System.getProperty("java.version")));
        report.append(String.format("  JVM: %s\n", System.getProperty("java.vm.name")));
        report.append(String.format("  JVM内存: 最大%s\n",
                formatBytes(Runtime.getRuntime().maxMemory())));
        report.append(String.format("  工作目录: %s\n",
                new File(".").getAbsolutePath()));
        report.append("\n");

        return report.toString();
    }

    /**
     * 检测系统包管理器
     */
    private String detectPackageManager() {
        if (!packageManagerPref.equals("auto")) {
            if (commandExists(packageManagerPref)) {
                return packageManagerPref;
            }
        }

        String[] managers = {"apt-get", "apt", "dnf", "yum", "pacman", "zypper"};
        for (String mgr : managers) {
            if (commandExists(mgr)) {
                return mgr;
            }
        }
        return null;
    }

    /**
     * 检查命令是否存在
     */
    private boolean commandExists(String command) {
        try {
            Process process = new ProcessBuilder("which", command).redirectErrorStream(true).start();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查库是否已安装
     */
    private boolean checkLibraryInstalled(String lib, String pkgManager) {
        // 尝试用which检查
        try {
            Process process = new ProcessBuilder("which", lib).redirectErrorStream(true).start();
            process.waitFor();
            if (process.exitValue() == 0) return true;
        } catch (Exception ignored) {}

        // 尝试用包管理器检查
        try {
            Process process;
            switch (pkgManager) {
                case "apt":
                case "apt-get":
                    process = new ProcessBuilder("dpkg", "-l", lib).redirectErrorStream(true).start();
                    break;
                case "dnf":
                case "yum":
                    process = new ProcessBuilder("rpm", "-q", lib).redirectErrorStream(true).start();
                    break;
                case "pacman":
                    process = new ProcessBuilder("pacman", "-Q", lib).redirectErrorStream(true).start();
                    break;
                default:
                    return false;
            }
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 安装库
     */
    private String installLibrary(String lib, String pkgManager, boolean isRoot) {
        String command = getInstallCommand(lib, pkgManager, isRoot);
        try {
            String[] cmd;
            if (!isRoot) {
                cmd = new String[]{"sudo", "sh", "-c", command};
            } else {
                cmd = new String[]{"sh", "-c", command};
            }

            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                return String.format("  ✓ %s 安装成功", lib);
            } else {
                // 截取最后几行错误信息
                String[] lines = output.toString().trim().split("\n");
                String error = lines.length > 0 ? lines[lines.length - 1] : "未知错误";
                return String.format("  ✗ %s 安装失败: %s", lib, error);
            }
        } catch (Exception e) {
            return String.format("  ✗ %s 安装异常: %s", lib, e.getMessage());
        }
    }

    /**
     * 获取安装命令
     */
    private String getInstallCommand(String lib, String pkgManager, boolean isRoot) {
        String prefix = isRoot ? "" : "sudo ";
        switch (pkgManager) {
            case "apt":
            case "apt-get":
                return prefix + pkgManager + " install -y " + getAptPackageName(lib);
            case "dnf":
                return prefix + "dnf install -y " + lib;
            case "yum":
                return prefix + "yum install -y " + lib;
            case "pacman":
                return prefix + "pacman -S --noconfirm " + lib;
            case "zypper":
                return prefix + "zypper install -y " + lib;
            default:
                return "# 请手动安装: " + lib;
        }
    }

    /**
     * 获取apt包名映射
     */
    private String getAptPackageName(String lib) {
        switch (lib) {
            case "lm-sensors": return "lm-sensors";
            case "pciutils": return "pciutils";
            case "lshw": return "lshw";
            case "smartmontools": return "smartmontools";
            default: return lib;
        }
    }

    /**
     * 检查JNA本地库
     */
    private String checkJNALibrary() {
        String jnaPath = System.getProperty("jna.library.path");
        String javaLibPath = System.getProperty("java.library.path");

        boolean jnaAvailable = false;
        try {
            // 尝试加载JNA
            Class.forName("com.sun.jna.Native");
            jnaAvailable = true;
        } catch (ClassNotFoundException e) {
            jnaAvailable = false;
        }

        StringBuilder sb = new StringBuilder();
        if (jnaAvailable) {
            sb.append("  ✓ JNA (Java Native Access) 已加载\n");
        } else {
            sb.append("  ✗ JNA 加载失败 - 硬件检测可能受限\n");
        }
        if (jnaPath != null) {
            sb.append("  JNA库路径: ").append(jnaPath).append("\n");
        }
        sb.append("  Java库路径: ").append(javaLibPath != null ? javaLibPath : "默认").append("\n");

        return sb.toString();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024 * 1024) return bytes / 1024 + " KB";
        if (bytes < 1024L * 1024 * 1024) return bytes / (1024 * 1024) + " MB";
        return bytes / (1024L * 1024 * 1024) + " GB";
    }
}
