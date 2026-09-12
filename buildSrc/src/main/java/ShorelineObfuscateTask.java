import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class ShorelineObfuscateTask extends DefaultTask {

    @Input
    public abstract ListProperty<String> getExcludePatterns();

    @Input
    @org.gradle.api.tasks.Optional
    public abstract org.gradle.api.provider.Property<Boolean> getRenameClasses();

    @Input
    @org.gradle.api.tasks.Optional
    public abstract org.gradle.api.provider.Property<Boolean> getRenameFields();

    @Classpath
    @org.gradle.api.tasks.Optional
    public abstract ConfigurableFileCollection getLibraryClasspath();

    @InputFile
    public abstract RegularFileProperty getInputJar();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @TaskAction
    public void obfuscate() throws Exception {
        File inputFile = getInputJar().get().getAsFile();
        File outputFile = getOutputJar().get().getAsFile();

        Map<String, String> hierarchy = new LinkedHashMap<>();
        Map<String, byte[]> classEntries = new LinkedHashMap<>();
        Map<String, byte[]> resourceEntries = new LinkedHashMap<>();
        Manifest manifest = null;

        try (JarFile jar = new JarFile(inputFile)) {
            manifest = jar.getManifest();
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.equals("META-INF/MANIFEST.MF")) continue;
                byte[] bytes;
                try (InputStream is = jar.getInputStream(entry)) {
                    bytes = is.readAllBytes();
                }
                if (name.endsWith(".class")) {
                    classEntries.put(name, bytes);
                    try {
                        ClassReader cr = new ClassReader(bytes);
                        String superName = cr.getSuperName();
                        if (superName != null) hierarchy.put(cr.getClassName(), superName);
                    } catch (Exception ignored) {}
                } else {
                    resourceEntries.put(name, bytes);
                }
            }
        }

        int libClasses = 0;
        for (File lib : getLibraryClasspath().getFiles()) {
            if (!lib.isFile() || !lib.getName().endsWith(".jar")) continue;
            try (JarFile jar = new JarFile(lib)) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.getName().endsWith(".class")) continue;
                    try (InputStream is = jar.getInputStream(entry)) {
                        ClassReader cr = new ClassReader(is);
                        String superName = cr.getSuperName();
                        if (superName != null) {
                            hierarchy.putIfAbsent(cr.getClassName(), superName);
                            libClasses++;
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }

        getLogger().lifecycle("[CxConfusus] Hierarchy: {} project, {} library classes",
                classEntries.size(), libClasses);

        List<Pattern> patterns = getExcludePatterns().get().stream()
                .map(Pattern::compile)
                .collect(Collectors.toList());

        List<URL> frameUrls = new ArrayList<>();
        frameUrls.add(inputFile.toURI().toURL());
        for (File lib : getLibraryClasspath().getFiles()) {
            if (lib.isFile() && lib.getName().endsWith(".jar")) {
                frameUrls.add(lib.toURI().toURL());
            }
        }
        ClassLoader frameParent = ClassLoader.getPlatformClassLoader();
        Map<String, byte[]> outputClasses = new LinkedHashMap<>();
        CxConfusus obfuscator;
        try (URLClassLoader frameLoader = new URLClassLoader(frameUrls.toArray(URL[]::new), frameParent)) {
            obfuscator = new CxConfusus(hierarchy, patterns, frameLoader);
            for (var entry : classEntries.entrySet()) {
                String entryName = entry.getKey();
                String className = entryName.substring(0, entryName.length() - 6);
                outputClasses.put(entryName, obfuscator.transformClass(className, entry.getValue()));
            }
        }

        Map<String, String> classRenameMap = Collections.emptyMap();
        Map<String, String> fieldRenameMap = Collections.emptyMap();
        boolean shouldRenameClasses = getRenameClasses().getOrElse(true);
        boolean shouldRenameFields = getRenameFields().getOrElse(true);
        if (shouldRenameClasses) {
            classRenameMap = obfuscator.buildClassRenameMap(outputClasses.keySet());
        }
        if (shouldRenameFields) {
            fieldRenameMap = obfuscator.buildFieldRenameMap(outputClasses);
        }
        if (!classRenameMap.isEmpty() || !fieldRenameMap.isEmpty()) {
            CxConfusus.HierarchyAwareRemapper remapper = new CxConfusus.HierarchyAwareRemapper(
                    classRenameMap, fieldRenameMap, hierarchy);
            Map<String, byte[]> renamedClasses = new LinkedHashMap<>();

            for (var entry : outputClasses.entrySet()) {
                String entryName = entry.getKey();
                String oldName = entryName.substring(0, entryName.length() - 6);
                String newName = classRenameMap.getOrDefault(oldName, oldName);

                ClassReader reader = new ClassReader(entry.getValue());
                ClassWriter writer = new ClassWriter(0);
                ClassRemapper cr = new ClassRemapper(writer, remapper);
                reader.accept(cr, ClassReader.EXPAND_FRAMES);

                renamedClasses.put(newName + ".class", writer.toByteArray());
            }
            outputClasses = renamedClasses;

            getLogger().lifecycle("[CxConfusus] Renamed {} classes, {} fields (jar-wide)",
                    obfuscator.getClassesRenamed(), obfuscator.getFieldsRenamed());
        }

        Path tempPath = outputFile.toPath().resolveSibling(outputFile.getName() + ".tmp");
        Manifest finalManifest = manifest;
        try (JarOutputStream jos = finalManifest != null
                ? new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(tempPath)), finalManifest)
                : new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(tempPath)))) {
            for (var e : outputClasses.entrySet()) {
                jos.putNextEntry(new JarEntry(e.getKey()));
                jos.write(e.getValue());
                jos.closeEntry();
            }
            for (var e : resourceEntries.entrySet()) {
                jos.putNextEntry(new JarEntry(e.getKey()));
                jos.write(e.getValue());
                jos.closeEntry();
            }
        }

        Files.move(tempPath, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        getLogger().lifecycle("[CxConfusus] {} transformed, {} excluded, {} skipped, {} verifier-passthrough",
                obfuscator.getClassesTransformed(),
                obfuscator.getClassesExcluded(),
                obfuscator.getClassesSkipped(),
                obfuscator.getClassesVerifierPassthrough());
        getLogger().lifecycle("[CxConfusus] {} strings, {} numbers, {} opaque, {} trashM, {} trashF, {} fields renamed, {} methods renamed, {} sigs stripped, {} proxies, {} flattened",
                obfuscator.getStringsEncrypted(),
                obfuscator.getNumbersObfuscated(),
                obfuscator.getOpaquePredicatesInserted(),
                obfuscator.getTrashMethodsAdded(),
                obfuscator.getTrashFieldsAdded(),
                obfuscator.getFieldsRenamed(),
                obfuscator.getMethodsRenamed(),
                obfuscator.getSignaturesStripped(),
                obfuscator.getProxiesCreated(),
                obfuscator.getMethodsFlattened());
    }
}
