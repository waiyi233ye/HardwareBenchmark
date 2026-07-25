package com.hwbench.gradlecompat;

import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Zip;

/**
 * Compatibility shim for ForgeGradle 2.3 which calls the removed
 * Jar/Zip.setClassifier/setBaseName/setVersion methods on Gradle 8+.
 *
 * Each static method takes the archive task as the first argument (matching the
 * invokevirtual stack layout so we only need to change the opcode to
 * invokestatic and redirect the methodref).
 */
public class JarCompat {

    // ---- Overloads for org.gradle.api.tasks.bundling.Jar ----

    public static void setClassifier(Jar jar, String classifier) {
        if (classifier != null && !classifier.isEmpty()) {
            jar.getArchiveClassifier().set(classifier);
        }
    }

    public static void setBaseName(Jar jar, String baseName) {
        jar.getArchiveBaseName().set(baseName != null ? baseName : "");
    }

    public static void setVersion(Jar jar, String version) {
        jar.getArchiveVersion().set(version != null ? version : "");
    }

    public static void setAppendix(Jar jar, String appendix) {
        jar.getArchiveAppendix().set(appendix != null ? appendix : "");
    }

    public static void setExtension(Jar jar, String extension) {
        jar.getArchiveExtension().set(extension != null ? extension : "jar");
    }

    public static void setArchiveName(Jar jar, String name) {
        jar.getArchiveFileName().set(name);
    }

    // ---- Overloads for org.gradle.api.tasks.bundling.Zip ----

    public static void setClassifier(Zip zip, String classifier) {
        if (classifier != null && !classifier.isEmpty()) {
            zip.getArchiveClassifier().set(classifier);
        }
    }

    public static void setBaseName(Zip zip, String baseName) {
        zip.getArchiveBaseName().set(baseName != null ? baseName : "");
    }

    public static void setVersion(Zip zip, String version) {
        zip.getArchiveVersion().set(version != null ? version : "");
    }

    public static void setAppendix(Zip zip, String appendix) {
        zip.getArchiveAppendix().set(appendix != null ? appendix : "");
    }

    public static void setExtension(Zip zip, String extension) {
        zip.getArchiveExtension().set(extension != null ? extension : "jar");
    }

    public static void setArchiveName(Zip zip, String name) {
        zip.getArchiveFileName().set(name);
    }

    // ---- Overloads for org.gradle.jvm.tasks.Jar (parent in older Gradle) ----

    public static void setClassifier(org.gradle.jvm.tasks.Jar jar, String classifier) {
        if (classifier != null && !classifier.isEmpty()) {
            jar.getArchiveClassifier().set(classifier);
        }
    }

    // ---- Overloads for JavaExec.setMain (removed in Gradle 8) ----

    public static org.gradle.api.tasks.JavaExec setMain(org.gradle.api.tasks.JavaExec task, String mainClass) {
        task.getMainClass().set(mainClass);
        return task;
    }
}
