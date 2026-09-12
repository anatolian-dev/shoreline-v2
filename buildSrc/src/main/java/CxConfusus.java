import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public final class CxConfusus {

    private static final String DECRYPT_DESC = "(Ljava/lang/String;I)Ljava/lang/String;";
    private static final String SHORELINE_PREFIX = "net/shoreline/";
    private static final String WATERMARK_TEXT = "CxConfusus";

    private final Map<String, String> hierarchy;
    private final List<Pattern> excludePatterns;
    private final ClassLoader frameClassLoader;
    private final Random random = new Random();

    private int stringsEncrypted;
    private int numbersObfuscated;
    private int classesTransformed;
    private int classesSkipped;
    private int classesExcluded;
    private int opaquePredicatesInserted;
    private int trashMethodsAdded;
    private int trashFieldsAdded;
    private int fieldsRenamed;
    private int methodsRenamed;
    private int signaturesStripped;
    private int proxiesCreated;
    private int methodsFlattened;
    private int classesRenamed;
    private int classesVerifierPassthrough;


    public CxConfusus(Map<String, String> hierarchy, List<Pattern> excludePatterns) {
        this(hierarchy, excludePatterns, null);
    }

    public CxConfusus(Map<String, String> hierarchy, List<Pattern> excludePatterns, ClassLoader frameClassLoader) {
        this.hierarchy = hierarchy;
        this.excludePatterns = excludePatterns;
        this.frameClassLoader = frameClassLoader;
    }

    public byte[] transformClass(String className, byte[] originalBytes) {
        if (isExcluded(className)) {
            classesExcluded++;
            return originalBytes;
        }

        if (avoidsAsmStackMapRecompute(className)) {
            classesVerifierPassthrough++;
            return originalBytes;
        }

        ClassNode node = readNode(originalBytes);
        if ((node.access & Opcodes.ACC_INTERFACE) != 0) {
            classesExcluded++;
            return originalBytes;
        }

        boolean startupCritical = isStartupCriticalClass(className);
        boolean loaderCompat = isLoaderCompatibilityClass(className);

        stripDebug(node);
        addClassWatermark(node);
        extractConstantFields(node);
        obfuscateNumbers(node);
        obfuscateLongs(node);
        encryptStrings(node);
        if (!startupCritical && !loaderCompat) {
            addReferenceProxies(node);
        }

        renamePrivateMembers(node);
        stripSignatures(node);
        addTrashMethods(node);
        addTrashFields(node);
        shuffleMembers(node);

        try {
            HierarchyWriter writer = new HierarchyWriter(hierarchy, frameClassLoader);
            node.accept(writer);
            classesTransformed++;
            return writer.toByteArray();
        } catch (Throwable e) {
            classesSkipped++;
            return originalBytes;
        }
    }

    public int getStringsEncrypted() { return stringsEncrypted; }
    public int getNumbersObfuscated() { return numbersObfuscated; }
    public int getClassesTransformed() { return classesTransformed; }
    public int getClassesSkipped() { return classesSkipped; }
    public int getClassesExcluded() { return classesExcluded; }
    public int getOpaquePredicatesInserted() { return opaquePredicatesInserted; }
    public int getTrashMethodsAdded() { return trashMethodsAdded; }
    public int getFieldsRenamed() { return fieldsRenamed; }
    public int getMethodsRenamed() { return methodsRenamed; }
    public int getTrashFieldsAdded() { return trashFieldsAdded; }
    public int getSignaturesStripped() { return signaturesStripped; }
    public int getProxiesCreated() { return proxiesCreated; }
    public int getMethodsFlattened() { return methodsFlattened; }
    public int getClassesRenamed() { return classesRenamed; }
    public int getClassesVerifierPassthrough() { return classesVerifierPassthrough; }

    public Map<String, String> buildFieldRenameMap(Map<String, byte[]> classEntries) {
        Map<String, String> fieldRenameMap = new LinkedHashMap<>();
        Map<String, ClassNode> nodesByClass = new LinkedHashMap<>();
        Map<String, Map<String, Set<String>>> declaredNamesByDesc = new HashMap<>();
        Map<String, Map<String, Set<String>>> renamedNamesByDesc = new HashMap<>();

        for (var entry : classEntries.entrySet()) {
            String entryName = entry.getKey();
            if (!entryName.endsWith(".class")) continue;
            String className = entryName.substring(0, entryName.length() - 6);
            if (isExcluded(className)) continue;

            ClassNode node;
            try {
                node = readNode(entry.getValue());
            } catch (Exception e) {
                continue;
            }

            if ((node.access & Opcodes.ACC_INTERFACE) != 0) continue;
            nodesByClass.put(className, node);

            Map<String, Set<String>> perDesc = declaredNamesByDesc.computeIfAbsent(className, k -> new HashMap<>());
            for (FieldNode field : node.fields) {
                perDesc.computeIfAbsent(field.desc, k -> new LinkedHashSet<>()).add(field.name);
            }
        }

        List<String> sortedClasses = new ArrayList<>(nodesByClass.keySet());
        Map<String, Integer> depthCache = new HashMap<>();
        sortedClasses.sort(Comparator
                .<String>comparingInt(name -> inheritanceDepth(name, depthCache))
                .thenComparing(name -> name));

        for (String className : sortedClasses) {
            ClassNode node = nodesByClass.get(className);
            boolean isEnum = (node.access & Opcodes.ACC_ENUM) != 0;

            Set<String> usedNames = new HashSet<>();
            for (FieldNode f : node.fields) usedNames.add(f.name);

            int fieldCounter = 0;
            for (FieldNode field : node.fields) {
                String key = fieldKey(className, field.name, field.desc);

                if ((field.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0) {
                    fieldRenameMap.put(key, field.name);
                    continue;
                }

                if (isEnum && (field.access & Opcodes.ACC_ENUM) != 0) {
                    fieldRenameMap.put(key, field.name);
                    continue;
                }
                if (isEnum && "$VALUES".equals(field.name)) {
                    fieldRenameMap.put(key, field.name);
                    continue;
                }
                if ((field.access & Opcodes.ACC_SYNTHETIC) != 0) {
                    fieldRenameMap.put(key, field.name);
                    continue;
                }

                
                Set<String> blockedNames = new HashSet<>(usedNames);
                String parent = hierarchy.get(className);
                Set<String> visited = new HashSet<>();
                while (parent != null && visited.add(parent)) {
                    Map<String, Set<String>> declared = declaredNamesByDesc.get(parent);
                    if (declared != null) {
                        Set<String> names = declared.get(field.desc);
                        if (names != null) blockedNames.addAll(names);
                    }
                    Map<String, Set<String>> renamed = renamedNamesByDesc.get(parent);
                    if (renamed != null) {
                        Set<String> names = renamed.get(field.desc);
                        if (names != null) blockedNames.addAll(names);
                    }
                    parent = hierarchy.get(parent);
                }

                String newName = confusingName(400 + fieldCounter++);
                int attempts = 0;
                while (blockedNames.contains(newName) && attempts++ < 1000) {
                    newName = confusingName(400 + fieldCounter++);
                }
                usedNames.add(newName);

                fieldRenameMap.put(key, newName);
                renamedNamesByDesc
                        .computeIfAbsent(className, k -> new HashMap<>())
                        .computeIfAbsent(field.desc, k -> new LinkedHashSet<>())
                        .add(newName);
                fieldsRenamed++;
            }
        }
        return fieldRenameMap;
    }

    public Map<String, String> buildClassRenameMap(Set<String> classEntryNames) {
        Map<String, String> renameMap = new LinkedHashMap<>();

        Set<String> allClassNames = new LinkedHashSet<>();
        for (String entryName : classEntryNames) {
            if (entryName.endsWith(".class")) {
                allClassNames.add(entryName.substring(0, entryName.length() - 6));
            }
        }

        Map<String, List<String>> outerToInners = new LinkedHashMap<>();
        for (String name : allClassNames) {
            int dollarIdx = name.indexOf('$');
            if (dollarIdx >= 0) {
                String outerName = name.substring(0, dollarIdx);
                outerToInners.computeIfAbsent(outerName, k -> new ArrayList<>()).add(name);
            }
        }

        int classCounter = 0;
        Set<String> usedNames = new HashSet<>();

        for (String name : allClassNames) {
            if (name.contains("$")) continue;
            if (isExcluded(name)) continue;

            String newName = nextClassName(classCounter++, usedNames);
            renameMap.put(name, newName);
            usedNames.add(newName);
            classesRenamed++;

            List<String> inners = outerToInners.get(name);
            if (inners != null) {
                int innerCounter = 0;
                for (String inner : inners) {
                    String innerSuffix = confusingName(innerCounter++);
                    String newInnerName = newName + "$" + innerSuffix;
                    while (usedNames.contains(newInnerName)) {
                        innerSuffix = confusingName(++innerCounter);
                        newInnerName = newName + "$" + innerSuffix;
                    }
                    renameMap.put(inner, newInnerName);
                    usedNames.add(newInnerName);
                    classesRenamed++;
                }
            }
        }
        return renameMap;
    }

    private String nextClassName(int index, Set<String> used) {
        String candidate = SHORELINE_PREFIX + confusingName(index);
        while (used.contains(candidate)) {
            candidate = SHORELINE_PREFIX + confusingName(++index) + confusingName(random.nextInt(300));
        }
        return candidate;
    }

    private int inheritanceDepth(String className, Map<String, Integer> cache) {
        Integer cached = cache.get(className);
        if (cached != null) return cached;

        int depth = 0;
        String current = className;
        Set<String> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            current = hierarchy.get(current);
            if (current != null) depth++;
        }
        cache.put(className, depth);
        return depth;
    }

    private static String fieldKey(String owner, String name, String descriptor) {
        return owner + "." + name + ":" + descriptor;
    }

    private static boolean isLoaderCompatibilityClass(String className) {
        return className.startsWith("net/shoreline/eventbus/");
    }

    private static boolean isStartupCriticalClass(String className) {
        return className.equals("net/shoreline/client/ShorelineMod")
                || className.startsWith("net/shoreline/client/core/")
                || className.startsWith("net/shoreline/eventbus/");
    }

    private static boolean avoidsAsmStackMapRecompute(String className) {
        return "net/shoreline/client/api/file/ConfigContainerFile".equals(className)
                || "net/shoreline/client/api/preset/AbstractPreset".equals(className);
    }


    private boolean isExcluded(String className) {
        for (Pattern p : excludePatterns) {
            if (p.matcher(className).find()) return true;
        }
        return false;
    }

    private static ClassNode readNode(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        return node;
    }

    private void stripDebug(ClassNode node) {
        node.sourceFile = null;
        node.sourceDebug = null;
        for (MethodNode method : node.methods) {
            method.localVariables = null;
            if (method.instructions == null) continue;
            List<AbstractInsnNode> lineNodes = new ArrayList<>();
            for (var it = method.instructions.iterator(); it.hasNext(); ) {
                AbstractInsnNode insn = it.next();
                if (insn instanceof LineNumberNode) lineNodes.add(insn);
            }
            for (AbstractInsnNode ln : lineNodes) method.instructions.remove(ln);
        }
    }

    private void addClassWatermark(ClassNode node) {
        Set<String> existingNames = new HashSet<>();
        for (FieldNode field : node.fields) existingNames.add(field.name);

        String fieldName = "$" + WATERMARK_TEXT;
        int suffix = 0;
        while (existingNames.contains(fieldName)) {
            fieldName = "$" + WATERMARK_TEXT + (++suffix);
        }

        node.fields.add(new FieldNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                fieldName,
                "Ljava/lang/String;",
                null,
                WATERMARK_TEXT
        ));
    }

    private static boolean methodHasHandlers(MethodNode m) {
        return m.tryCatchBlocks != null && !m.tryCatchBlocks.isEmpty();
    }

    private void obfuscateNumbers(ClassNode node) {
        for (MethodNode method : node.methods) {
            if (method.name.equals("<clinit>")) continue;
            if (method.instructions == null) continue;
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
            if (methodHasHandlers(method)) continue;

            List<AbstractInsnNode> targets = new ArrayList<>();
            for (var it = method.instructions.iterator(); it.hasNext(); ) {
                AbstractInsnNode insn = it.next();
                Integer val = intValue(insn);
                if (val != null && val != 0 && val != 1 && val != -1) targets.add(insn);
            }

            for (AbstractInsnNode insn : targets) {
                int value = intValue(insn);
                InsnList replacement = new InsnList();
                int variant = random.nextInt(3);
                switch (variant) {
                    case 0 -> {
                        
                        int key = 1 + random.nextInt(255);
                        replacement.add(pushInt(value ^ key));
                        replacement.add(pushInt(key));
                        replacement.add(new InsnNode(Opcodes.IXOR));
                    }
                    case 1 -> {
                        
                        int key = 1 + random.nextInt(255);
                        int noise = 10 + random.nextInt(1000);
                        replacement.add(pushInt((value + noise) ^ key));
                        replacement.add(pushInt(key));
                        replacement.add(new InsnNode(Opcodes.IXOR));
                        replacement.add(pushInt(noise));
                        replacement.add(new InsnNode(Opcodes.ISUB));
                    }
                    case 2 -> {
                        
                        int key = random.nextInt(256) | 1;
                        int encoded = ~value ^ key;
                        replacement.add(pushInt(encoded));
                        replacement.add(pushInt(key));
                        replacement.add(new InsnNode(Opcodes.IXOR));
                        replacement.add(new InsnNode(Opcodes.ICONST_M1));
                        replacement.add(new InsnNode(Opcodes.IXOR));
                    }
                }
                method.instructions.insertBefore(insn, replacement);
                method.instructions.remove(insn);
                numbersObfuscated++;
            }
        }
    }

    private void obfuscateLongs(ClassNode node) {
        for (MethodNode method : node.methods) {
            if (method.name.equals("<clinit>")) continue;
            if (method.instructions == null) continue;
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
            if (methodHasHandlers(method)) continue;

            List<LdcInsnNode> targets = new ArrayList<>();
            for (var it = method.instructions.iterator(); it.hasNext(); ) {
                AbstractInsnNode insn = it.next();
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Long val && val != 0L && val != 1L) {
                    targets.add(ldc);
                }
            }

            for (LdcInsnNode ldc : targets) {
                long value = (Long) ldc.cst;
                long key = random.nextLong() | 1L;
                InsnList replacement = new InsnList();
                replacement.add(new LdcInsnNode(value ^ key));
                replacement.add(new LdcInsnNode(key));
                replacement.add(new InsnNode(Opcodes.LXOR));
                method.instructions.insertBefore(ldc, replacement);
                method.instructions.remove(ldc);
                numbersObfuscated++;
            }
        }
    }

    private void addTrashMethods(ClassNode node) {
        int count = 6 + random.nextInt(5);
        Set<String> existingNames = new HashSet<>();
        for (MethodNode m : node.methods) existingNames.add(m.name + m.desc);

        for (int i = 0; i < count; i++) {
            String name = generateTrashName(existingNames);
            String desc = TRASH_DESCS[random.nextInt(TRASH_DESCS.length)];
            if (existingNames.contains(name + desc)) continue;
            existingNames.add(name + desc);

            MethodNode trash = new MethodNode(
                    Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE,
                    name, desc, null, null);

            InsnList il = new InsnList();
            int bodyType = random.nextInt(4);
            switch (bodyType) {
                case 0 -> {
                    
                    il.add(new LdcInsnNode(randomGarbage()));
                    il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                            "java/lang/String", "length", "()I", false));
                    il.add(pushInt(random.nextInt(100)));
                    il.add(new InsnNode(Opcodes.IADD));
                    il.add(new InsnNode(Opcodes.POP));
                }
                case 1 -> {
                    
                    LabelNode loopStart = new LabelNode();
                    LabelNode loopEnd = new LabelNode();
                    il.add(new InsnNode(Opcodes.ICONST_0));
                    il.add(new VarInsnNode(Opcodes.ISTORE, 0));
                    il.add(loopStart);
                    il.add(new VarInsnNode(Opcodes.ILOAD, 0));
                    il.add(pushInt(5 + random.nextInt(20)));
                    il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, loopEnd));
                    il.add(new IincInsnNode(0, 1));
                    il.add(new JumpInsnNode(Opcodes.GOTO, loopStart));
                    il.add(loopEnd);
                }
                case 2 -> {
                    
                    il.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "java/lang/System", "currentTimeMillis", "()J", false));
                    il.add(new InsnNode(Opcodes.LCONST_0));
                    il.add(new InsnNode(Opcodes.LCMP));
                    LabelNode skip = new LabelNode();
                    il.add(new JumpInsnNode(Opcodes.IFGE, skip));
                    il.add(new LdcInsnNode(randomGarbage()));
                    il.add(new InsnNode(Opcodes.POP));
                    il.add(skip);
                }
                default -> {
                    
                    int ops = 3 + random.nextInt(8);
                    for (int j = 0; j < ops; j++) {
                        il.add(pushInt(random.nextInt(Short.MAX_VALUE)));
                        if (j > 0) {
                            il.add(new InsnNode(ARITH_OPS[random.nextInt(ARITH_OPS.length)]));
                        } else {
                            il.add(new InsnNode(Opcodes.POP));
                            il.add(pushInt(random.nextInt(Short.MAX_VALUE)));
                        }
                    }
                    il.add(new InsnNode(Opcodes.POP));
                }
            }
            appendDefaultReturn(il, desc);

            trash.instructions = il;
            trash.maxStack = 6;
            trash.maxLocals = 4;
            node.methods.add(trash);
            trashMethodsAdded++;
        }
    }

    private String randomGarbage() {
        
        StringBuilder sb = new StringBuilder();
        int len = 4 + random.nextInt(12);
        for (int i = 0; i < len; i++) sb.append((char) (32 + random.nextInt(94)));
        return sb.toString();
    }

    private static final String[] TRASH_DESCS = {
            "()V", "()I", "(I)V", "(I)I", "(II)I"
    };

    private static final int[] ARITH_OPS = {
            Opcodes.IADD, Opcodes.ISUB, Opcodes.IMUL, Opcodes.IXOR, Opcodes.IOR, Opcodes.IAND
    };

    private String generateTrashName(Set<String> existing) {
        for (int i = 0; i < 50; i++) {
            String name = confusingName(50 + random.nextInt(100));
            boolean collision = false;
            for (String d : TRASH_DESCS) {
                if (existing.contains(name + d)) { collision = true; break; }
            }
            if (!collision) return name;
        }
        return confusingName(200 + random.nextInt(100));
    }

    private static void appendDefaultReturn(InsnList il, String desc) {
        char ret = desc.charAt(desc.indexOf(')') + 1);
        switch (ret) {
            case 'V' -> il.add(new InsnNode(Opcodes.RETURN));
            case 'I', 'Z', 'B', 'S', 'C' -> { il.add(new InsnNode(Opcodes.ICONST_0)); il.add(new InsnNode(Opcodes.IRETURN)); }
            case 'J' -> { il.add(new InsnNode(Opcodes.LCONST_0)); il.add(new InsnNode(Opcodes.LRETURN)); }
            case 'F' -> { il.add(new InsnNode(Opcodes.FCONST_0)); il.add(new InsnNode(Opcodes.FRETURN)); }
            case 'D' -> { il.add(new InsnNode(Opcodes.DCONST_0)); il.add(new InsnNode(Opcodes.DRETURN)); }
            default -> { il.add(new InsnNode(Opcodes.ACONST_NULL)); il.add(new InsnNode(Opcodes.ARETURN)); }
        }
    }

    private void encryptStrings(ClassNode node) {
        String decryptName = pickDecryptName(node);
        int variant = random.nextInt(3); 
        boolean any = false;

        for (MethodNode method : node.methods) {
            if (method.instructions == null) continue;
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
            if (methodHasHandlers(method)) continue;

            List<LdcInsnNode> targets = new ArrayList<>();
            for (var it = method.instructions.iterator(); it.hasNext(); ) {
                AbstractInsnNode insn = it.next();
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s && !s.isEmpty()) {
                    targets.add(ldc);
                }
            }

            for (LdcInsnNode ldc : targets) {
                String original = (String) ldc.cst;
                int key = 1 + random.nextInt(255);
                InsnList replacement = new InsnList();
                replacement.add(new LdcInsnNode(encryptString(original, key, variant)));
                replacement.add(pushInt(key));
                replacement.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC, node.name, decryptName, DECRYPT_DESC, false));
                method.instructions.insertBefore(ldc, replacement);
                method.instructions.remove(ldc);
                stringsEncrypted++;
                any = true;
            }
        }

        if (any) emitDecryptMethod(node, decryptName, variant);
    }

    private static String encryptString(String plain, int key, int variant) {
        char[] c = plain.toCharArray();
        for (int i = 0; i < c.length; i++) {
            switch (variant) {
                case 0 -> c[i] = (char) (c[i] ^ ((key + i * 7) & 0xFF));
                case 1 -> c[i] = (char) (c[i] ^ ((key ^ (i * 13 + 37)) & 0xFF));
                case 2 -> c[i] = (char) (c[i] ^ ((key + i * i * 3 + i * 11) & 0xFF));
            }
        }
        return new String(c);
    }


    private void extractConstantFields(ClassNode node) {
        boolean isEnum = (node.access & Opcodes.ACC_ENUM) != 0;
        if (isEnum) return;

        List<FieldNode> toExtract = new ArrayList<>();
        for (FieldNode field : node.fields) {
            if (field.value instanceof String) {
                toExtract.add(field);
            }
        }
        if (toExtract.isEmpty()) return;

        MethodNode clinit = null;
        for (MethodNode m : node.methods) {
            if (m.name.equals("<clinit>")) { clinit = m; break; }
        }
        if (clinit == null) {
            clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            clinit.instructions = new InsnList();
            clinit.instructions.add(new InsnNode(Opcodes.RETURN));
            node.methods.add(clinit);
        }

        AbstractInsnNode insertPoint = clinit.instructions.getFirst();

        for (FieldNode field : toExtract) {
            String value = (String) field.value;
            field.value = null;

            InsnList init = new InsnList();
            init.add(new LdcInsnNode(value));
            init.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, field.name, field.desc));
            clinit.instructions.insertBefore(insertPoint, init);
        }
    }

    private void renamePrivateMembers(ClassNode node) {
        
        
        
        if (node.nestHostClass != null || (node.nestMembers != null && !node.nestMembers.isEmpty())) return;

        Map<String, String> methodMap = new HashMap<>();
        int methodCounter = 0;
        for (MethodNode method : node.methods) {
            if ((method.access & Opcodes.ACC_PRIVATE) == 0) continue;
            if (method.name.equals("<init>") || method.name.equals("<clinit>")) continue;
            if (method.desc.equals(DECRYPT_DESC)) continue;
            if (method.name.startsWith("lambda$")) continue;
            String newName = shortName(methodCounter++);
            methodMap.put(method.name + method.desc, newName);
            method.name = newName;
            methodsRenamed++;
        }

        for (MethodNode method : node.methods) {
            if (method.instructions == null) continue;
            for (var it = method.instructions.iterator(); it.hasNext(); ) {
                AbstractInsnNode insn = it.next();
                if (insn instanceof MethodInsnNode min && min.owner.equals(node.name)) {
                    String mapped = methodMap.get(min.name + min.desc);
                    if (mapped != null) min.name = mapped;
                }
                if (insn instanceof InvokeDynamicInsnNode indy && indy.bsmArgs != null) {
                    for (int i = 0; i < indy.bsmArgs.length; i++) {
                        if (indy.bsmArgs[i] instanceof Handle h && h.getOwner().equals(node.name)) {
                            String mapped = methodMap.get(h.getName() + h.getDesc());
                            if (mapped != null) {
                                indy.bsmArgs[i] = new Handle(h.getTag(), h.getOwner(), mapped, h.getDesc(), h.isInterface());
                            }
                        }
                    }
                }
            }
        }
    }

    private void stripSignatures(ClassNode node) {
        
        
        

        
        node.invisibleAnnotations = null;
        node.invisibleTypeAnnotations = null;
        for (FieldNode field : node.fields) {
            field.invisibleAnnotations = null;
            field.invisibleTypeAnnotations = null;
        }
        for (MethodNode method : node.methods) {
            method.invisibleAnnotations = null;
            method.invisibleTypeAnnotations = null;
            method.invisibleLocalVariableAnnotations = null;
            method.invisibleParameterAnnotations = null;
        }
        
        if (node.innerClasses != null) {
            node.innerClasses.clear();
        }
    }

    private void addTrashFields(ClassNode node) {
        int count = 8 + random.nextInt(5);
        Set<String> existingNames = new HashSet<>();
        for (FieldNode f : node.fields) existingNames.add(f.name);

        String[] trashDescs = {"I", "J", "Z", "Ljava/lang/String;", "[B", "D", "F"};
        for (int i = 0; i < count; i++) {
            String name = generateTrashFieldName(existingNames);
            if (name == null) continue;
            existingNames.add(name);
            String desc = trashDescs[random.nextInt(trashDescs.length)];
            int access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
            node.fields.add(new FieldNode(access, name, desc, null, null));
            trashFieldsAdded++;
        }
    }

    private String generateTrashFieldName(Set<String> existing) {
        for (int attempt = 0; attempt < 50; attempt++) {
            String name = confusingName(150 + random.nextInt(100));
            if (!existing.contains(name)) return name;
        }
        return null;
    }

    private void shuffleMembers(ClassNode node) {
        
        
        if (node.methods.size() > 1) Collections.shuffle(node.methods, random);
    }

    
    
    private void addReferenceProxies(ClassNode node) {
        
        Map<String, String> proxyNameMap = new LinkedHashMap<>();
        Map<String, int[]> proxyOpcodes = new LinkedHashMap<>();
        Map<String, String[]> proxyTargets = new LinkedHashMap<>();
        int proxyCounter = 0;

        for (MethodNode method : node.methods) {
            if (method.instructions == null) continue;
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
            if (methodHasHandlers(method)) continue;

            for (var it = method.instructions.iterator(); it.hasNext(); ) {
                AbstractInsnNode insn = it.next();
                if (!(insn instanceof MethodInsnNode min)) continue;
                if (min.getOpcode() != Opcodes.INVOKESTATIC) continue;
                if (min.owner.equals(node.name)) continue;
                if (min.owner.startsWith("[")) continue;
                
                
                if (isAncestor(min.owner, node.name)) continue;

                String proxyKey = min.getOpcode() + ":" + min.owner + "." + min.name + min.desc;
                if (!proxyNameMap.containsKey(proxyKey)) {
                    String proxyName = confusingName(250 + proxyCounter++);
                    proxyNameMap.put(proxyKey, proxyName);
                    proxyOpcodes.put(proxyKey, new int[]{min.getOpcode()});
                    proxyTargets.put(proxyKey, new String[]{min.owner, min.name, min.desc,
                            min.itf ? "1" : "0"});
                }
            }
        }

        if (proxyNameMap.isEmpty()) return;

        
        for (MethodNode method : node.methods) {
            if (method.instructions == null) continue;
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
            if (methodHasHandlers(method)) continue;

            List<AbstractInsnNode> toProcess = new ArrayList<>();
            for (var it = method.instructions.iterator(); it.hasNext(); ) {
                AbstractInsnNode insn = it.next();
                if (insn instanceof MethodInsnNode) toProcess.add(insn);
            }

            for (AbstractInsnNode insn : toProcess) {
                MethodInsnNode min = (MethodInsnNode) insn;
                if (min.getOpcode() != Opcodes.INVOKESTATIC) continue;
                if (min.owner.equals(node.name)) continue;
                if (min.owner.startsWith("[")) continue;
                if (isAncestor(min.owner, node.name)) continue;

                String proxyKey = min.getOpcode() + ":" + min.owner + "." + min.name + min.desc;
                String proxyName = proxyNameMap.get(proxyKey);
                if (proxyName == null) continue;

                String proxyDesc = min.desc;

                MethodInsnNode proxyCall = new MethodInsnNode(
                        Opcodes.INVOKESTATIC, node.name, proxyName, proxyDesc, false);
                method.instructions.insertBefore(min, proxyCall);
                method.instructions.remove(min);
            }
        }

        
        Set<String> existingMethods = new HashSet<>();
        for (MethodNode m : node.methods) existingMethods.add(m.name + m.desc);

        for (Map.Entry<String, String> entry : proxyNameMap.entrySet()) {
            String key = entry.getKey();
            String proxyName = entry.getValue();
            String[] target = proxyTargets.get(key);
            int opcode = proxyOpcodes.get(key)[0];

            String targetOwner = target[0];
            String targetName = target[1];
            String targetDesc = target[2];
            boolean targetItf = "1".equals(target[3]);

            String proxyDesc = targetDesc;

            if (existingMethods.contains(proxyName + proxyDesc)) continue;
            existingMethods.add(proxyName + proxyDesc);

            MethodNode proxy = new MethodNode(
                    Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                    proxyName, proxyDesc, null, null);

            InsnList body = new InsnList();
            Type[] argTypes = Type.getArgumentTypes(proxyDesc);
            int slot = 0;
            for (Type t : argTypes) {
                body.add(new VarInsnNode(t.getOpcode(Opcodes.ILOAD), slot));
                slot += t.getSize();
            }
            body.add(new MethodInsnNode(opcode, targetOwner, targetName, targetDesc, targetItf));
            Type retType = Type.getReturnType(proxyDesc);
            body.add(new InsnNode(retType.getOpcode(Opcodes.IRETURN)));

            proxy.instructions = body;
            proxy.maxStack = Math.max(slot + 2, 4);
            proxy.maxLocals = slot + 1;
            node.methods.add(proxy);
            proxiesCreated++;
        }
    }

    private boolean isAncestor(String candidate, String className) {
        String current = className;
        Set<String> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            if (current.equals(candidate)) return true;
            current = hierarchy.get(current);
        }
        return false;
    }

    
    
    
    private void flattenControlFlow(ClassNode node) {
        for (MethodNode method : new ArrayList<>(node.methods)) {
            if (method.name.equals("<init>") || method.name.equals("<clinit>")) continue;
            if (method.instructions == null || method.instructions.size() < 16) continue;
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
            
            if (method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) continue;

            
            boolean hasSwitch = false;
            boolean hasUnsupportedFlow = false;
            for (var it = method.instructions.iterator(); it.hasNext(); ) {
                AbstractInsnNode insn = it.next();
                if (insn instanceof TableSwitchInsnNode || insn instanceof LookupSwitchInsnNode) {
                    hasSwitch = true;
                    break;
                }
                int op = insn.getOpcode();
                if (insn instanceof InvokeDynamicInsnNode
                        || op == Opcodes.MONITORENTER
                        || op == Opcodes.MONITOREXIT) {
                    hasUnsupportedFlow = true;
                    break;
                }
            }
            if (hasSwitch || hasUnsupportedFlow) continue;

            
            InsnList savedInsns = method.instructions;
            int savedMaxLocals = method.maxLocals;
            int savedMaxStack = method.maxStack;

            try {
                Analyzer<BasicValue> analyzer = new Analyzer<>(new BasicInterpreter());
                Frame<BasicValue>[] frames = analyzer.analyze(node.name, method);
                flattenMethodImpl(node, method, frames);
            } catch (Throwable t) {
                
                method.instructions = savedInsns;
                method.maxLocals = savedMaxLocals;
                method.maxStack = savedMaxStack;
            }
        }
    }

    private void flattenMethodImpl(ClassNode owner, MethodNode method, Frame<BasicValue>[] frames) throws Exception {
        AbstractInsnNode[] insns = method.instructions.toArray();
        int insnCount = insns.length;
        if (insnCount == 0) return;

        
        Set<LabelNode> jumpTargets = new HashSet<>();
        for (AbstractInsnNode insn : insns) {
            if (insn instanceof JumpInsnNode jin) jumpTargets.add(jin.label);
        }

        
        Map<LabelNode, Integer> labelIndex = new HashMap<>();
        for (int i = 0; i < insnCount; i++) {
            if (insns[i] instanceof LabelNode ln) labelIndex.put(ln, i);
        }

        
        for (LabelNode target : jumpTargets) {
            Integer idx = labelIndex.get(target);
            if (idx == null || frames[idx] == null || frames[idx].getStackSize() != 0) return;
        }

        
        List<Integer> boundaries = new ArrayList<>();
        boundaries.add(0);

        for (int i = 1; i < insnCount; i++) {
            if (frames[i] == null) continue;
            if (frames[i].getStackSize() != 0) continue;

            if (insns[i] instanceof LabelNode ln && jumpTargets.contains(ln)) {
                boundaries.add(i);
                continue;
            }

            int prev = i - 1;
            while (prev >= 0 && insns[prev].getOpcode() == -1) prev--;
            if (prev >= 0) {
                int op = insns[prev].getOpcode();
                if (op == Opcodes.GOTO ||
                    (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) ||
                    op == Opcodes.ATHROW) {
                    boundaries.add(i);
                }
            }
        }

        TreeSet<Integer> unique = new TreeSet<>(boundaries);
        boundaries = new ArrayList<>(unique);

        int blockCount = boundaries.size();
        if (blockCount < 3) return;

        
        int[][] blockRanges = new int[blockCount][2];
        for (int i = 0; i < blockCount; i++) {
            blockRanges[i][0] = boundaries.get(i);
            blockRanges[i][1] = (i + 1 < blockCount) ? boundaries.get(i + 1) : insnCount;
        }

        
        Set<Integer> stateSet = new LinkedHashSet<>();
        while (stateSet.size() < blockCount) stateSet.add(100 + random.nextInt(9000));
        List<Integer> stateList = new ArrayList<>(stateSet);
        Collections.shuffle(stateList, random);
        int[] states = new int[blockCount];
        for (int i = 0; i < blockCount; i++) states[i] = stateList.get(i);

        
        int[] insnToBlock = new int[insnCount];
        Arrays.fill(insnToBlock, -1);
        for (int b = 0; b < blockCount; b++) {
            for (int i = blockRanges[b][0]; i < blockRanges[b][1]; i++) {
                insnToBlock[i] = b;
            }
        }

        
        Map<LabelNode, Integer> labelToState = new HashMap<>();
        for (Map.Entry<LabelNode, Integer> entry : labelIndex.entrySet()) {
            int idx = entry.getValue();
            if (idx >= 0 && idx < insnCount && insnToBlock[idx] >= 0) {
                labelToState.put(entry.getKey(), states[insnToBlock[idx]]);
            }
        }

        
        Map<LabelNode, LabelNode> cloneMap = new HashMap<>();
        for (int i = 0; i < insnCount; i++) {
            if (insns[i] instanceof LabelNode ln) {
                cloneMap.put(ln, new LabelNode());
            }
        }

        
        int stateSlot = method.maxLocals;
        LabelNode loopTop = new LabelNode();
        LabelNode defaultLabel = new LabelNode();

        
        
        
        int paramSlots = Type.getArgumentsAndReturnSizes(method.desc) >> 2;
        if ((method.access & Opcodes.ACC_STATIC) != 0) paramSlots--;

        Map<Integer, Integer> localTypes = new LinkedHashMap<>();
        for (AbstractInsnNode insn : insns) {
            if (insn instanceof VarInsnNode vin && vin.var >= paramSlots && vin.var != stateSlot) {
                int op = vin.getOpcode();
                switch (op) {
                    case Opcodes.ILOAD, Opcodes.ISTORE -> localTypes.putIfAbsent(vin.var, Opcodes.ISTORE);
                    case Opcodes.LLOAD, Opcodes.LSTORE -> localTypes.putIfAbsent(vin.var, Opcodes.LSTORE);
                    case Opcodes.FLOAD, Opcodes.FSTORE -> localTypes.putIfAbsent(vin.var, Opcodes.FSTORE);
                    case Opcodes.DLOAD, Opcodes.DSTORE -> localTypes.putIfAbsent(vin.var, Opcodes.DSTORE);
                    case Opcodes.ALOAD, Opcodes.ASTORE -> localTypes.putIfAbsent(vin.var, Opcodes.ASTORE);
                }
            } else if (insn instanceof IincInsnNode iinc && iinc.var >= paramSlots && iinc.var != stateSlot) {
                localTypes.putIfAbsent(iinc.var, Opcodes.ISTORE);
            }
        }

        InsnList newInsns = new InsnList();

        for (Map.Entry<Integer, Integer> entry : localTypes.entrySet()) {
            int slot = entry.getKey();
            switch (entry.getValue()) {
                case Opcodes.ISTORE -> { newInsns.add(new InsnNode(Opcodes.ICONST_0)); newInsns.add(new VarInsnNode(Opcodes.ISTORE, slot)); }
                case Opcodes.LSTORE -> { newInsns.add(new InsnNode(Opcodes.LCONST_0)); newInsns.add(new VarInsnNode(Opcodes.LSTORE, slot)); }
                case Opcodes.FSTORE -> { newInsns.add(new InsnNode(Opcodes.FCONST_0)); newInsns.add(new VarInsnNode(Opcodes.FSTORE, slot)); }
                case Opcodes.DSTORE -> { newInsns.add(new InsnNode(Opcodes.DCONST_0)); newInsns.add(new VarInsnNode(Opcodes.DSTORE, slot)); }
                case Opcodes.ASTORE -> { newInsns.add(new InsnNode(Opcodes.ACONST_NULL)); newInsns.add(new VarInsnNode(Opcodes.ASTORE, slot)); }
            }
        }

        newInsns.add(pushInt(states[0]));
        newInsns.add(new VarInsnNode(Opcodes.ISTORE, stateSlot));

        newInsns.add(loopTop);
        newInsns.add(new VarInsnNode(Opcodes.ILOAD, stateSlot));

        LabelNode[] caseLabels = new LabelNode[blockCount];
        for (int i = 0; i < blockCount; i++) caseLabels[i] = new LabelNode();

        Integer[] sortOrder = new Integer[blockCount];
        for (int i = 0; i < blockCount; i++) sortOrder[i] = i;
        Arrays.sort(sortOrder, Comparator.comparingInt(a -> states[a]));

        int[] sortedKeys = new int[blockCount];
        LabelNode[] sortedLabels = new LabelNode[blockCount];
        for (int i = 0; i < blockCount; i++) {
            sortedKeys[i] = states[sortOrder[i]];
            sortedLabels[i] = caseLabels[sortOrder[i]];
        }
        newInsns.add(new LookupSwitchInsnNode(defaultLabel, sortedKeys, sortedLabels));


        List<Integer> blockOrder = new ArrayList<>();
        for (int i = 0; i < blockCount; i++) blockOrder.add(i);
        Collections.shuffle(blockOrder, random);

        for (int blockIdx : blockOrder) {
            int start = blockRanges[blockIdx][0];
            int end = blockRanges[blockIdx][1];

            newInsns.add(caseLabels[blockIdx]);

            int lastReal = end - 1;
            while (lastReal >= start && insns[lastReal].getOpcode() == -1) lastReal--;

            List<Object[]> deferredHandlers = new ArrayList<>();

            for (int i = start; i < end; i++) {
                AbstractInsnNode insn = insns[i];

                if (insn instanceof JumpInsnNode jin) {
                    Integer targetIdx = labelIndex.get(jin.label);
                    int targetBlock = (targetIdx != null && targetIdx >= 0 && targetIdx < insnCount)
                            ? insnToBlock[targetIdx] : -1;

                    if (jin.getOpcode() == Opcodes.GOTO) {
                        if (targetBlock >= 0 && targetBlock != blockIdx) {
                            newInsns.add(pushInt(states[targetBlock]));
                            newInsns.add(new VarInsnNode(Opcodes.ISTORE, stateSlot));
                            newInsns.add(new JumpInsnNode(Opcodes.GOTO, loopTop));
                            continue;
                        }

                        newInsns.add(insn.clone(cloneMap));
                    } else {

                        if (targetBlock >= 0 && targetBlock != blockIdx) {
                            LabelNode trueHandler = new LabelNode();
                            newInsns.add(new JumpInsnNode(jin.getOpcode(), trueHandler));
                            deferredHandlers.add(new Object[]{trueHandler, states[targetBlock]});
                            continue;
                        }

                        newInsns.add(insn.clone(cloneMap));
                    }
                } else {

                    newInsns.add(insn.clone(cloneMap));
                }
            }


            if (lastReal >= start) {
                int op = insns[lastReal].getOpcode();
                boolean terminal = op == Opcodes.GOTO ||
                        (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) ||
                        op == Opcodes.ATHROW;
                if (!terminal && blockIdx + 1 < blockCount) {
                    newInsns.add(pushInt(states[blockIdx + 1]));
                    newInsns.add(new VarInsnNode(Opcodes.ISTORE, stateSlot));
                    newInsns.add(new JumpInsnNode(Opcodes.GOTO, loopTop));
                }
            }


            for (Object[] handler : deferredHandlers) {
                LabelNode trueLabel = (LabelNode) handler[0];
                int targetState = (Integer) handler[1];
                newInsns.add(trueLabel);
                newInsns.add(pushInt(targetState));
                newInsns.add(new VarInsnNode(Opcodes.ISTORE, stateSlot));
                newInsns.add(new JumpInsnNode(Opcodes.GOTO, loopTop));
            }
        }


        newInsns.add(defaultLabel);
        newInsns.add(new TypeInsnNode(Opcodes.NEW, "java/lang/RuntimeException"));
        newInsns.add(new InsnNode(Opcodes.DUP));
        newInsns.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/RuntimeException", "<init>", "()V", false));
        newInsns.add(new InsnNode(Opcodes.ATHROW));


        int newMaxLocals = stateSlot + 1;
        int newMaxStack = Math.max(method.maxStack + 4, 8);

        InsnList origInsns = method.instructions;
        int origMaxLocals = method.maxLocals;
        int origMaxStack = method.maxStack;

        method.instructions = newInsns;
        method.maxLocals = newMaxLocals;
        method.maxStack = newMaxStack;

        try {

            HierarchyWriter testWriter = new HierarchyWriter(hierarchy, frameClassLoader);
            ClassNode temp = new ClassNode();
            temp.version = owner.version;
            temp.access = Opcodes.ACC_PUBLIC;
            temp.name = owner.name;
            temp.superName = owner.superName != null ? owner.superName : "java/lang/Object";
            temp.methods.add(method);
            temp.accept(testWriter);
            testWriter.toByteArray();
            methodsFlattened++;
        } catch (Throwable t) {

            method.instructions = origInsns;
            method.maxLocals = origMaxLocals;
            method.maxStack = origMaxStack;
            throw new Exception("Frame computation failed for flattened method", t);
        }
    }

    private static String shortName(int index) {
        return confusingName(index);
    }




    private static final Map<Character, char[]> HOMOGLYPHS = createHomoglyphMap();

    private static Map<Character, char[]> createHomoglyphMap() {
        Map<Character, char[]> map = new HashMap<>();
        map.put('C', new char[]{'C', '\u0421', '\u03F9', '\uFF23'});
        map.put('x', new char[]{'x', '\u0445', '\uFF58'});
        map.put('o', new char[]{'o', '\u03BF', '\u043E', '\uFF4F'});
        map.put('n', new char[]{'n', '\u0578', '\uFF4E'});
        map.put('f', new char[]{'f', '\uFF46'});
        map.put('u', new char[]{'u', '\u057D', '\u03C5', '\uFF55'});
        map.put('s', new char[]{'s', '\u0455', '\uFF53'});
        return Collections.unmodifiableMap(map);
    }

    private static final String[][] CONFUSE_ALPHABETS = {

        {"CxCx", "xCxC", "CxxC", "CCxx", "xxCC", "CxCC", "xCCx", "Cx0x", "x0Cx", "CxCx0"},

        {"O0", "0O", "OO", "O0O", "0O0", "OOO", "O00", "00O", "0OO", "O0O0"},

        {"\u0430", "\u043e", "\u0435", "\u0430\u043e", "\u043e\u0430", "\u0435\u0430",
         "\u0430\u0435", "\u043e\u0435", "\u0435\u043e", "\u0430\u043e\u0435"},
    };

    private static String confusingName(int index) {
        int alphabetCount = CONFUSE_ALPHABETS.length;
        int setSize = CONFUSE_ALPHABETS[0].length;
        int alphabet = index % alphabetCount;
        int nameIdx = index / alphabetCount;
        String[] set = CONFUSE_ALPHABETS[alphabet];
        String name;
        if (nameIdx < setSize) {
            name = watermarkName(set[nameIdx]);
        } else {
            StringBuilder sb = new StringBuilder();
            int remaining = nameIdx;
            int segment = 0;
            do {
                int pick = remaining % setSize;
                remaining /= setSize;
                String[] segSet = CONFUSE_ALPHABETS[(alphabet + segment) % alphabetCount];
                sb.append(segSet[pick]);
                segment++;
            } while (remaining > 0);
            name = watermarkName(sb.toString());
        }
        return applyHomoglyphs(name);
    }

    private static String applyHomoglyphs(String name) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        char[] chars = name.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char[] replacements = HOMOGLYPHS.get(chars[i]);
            if (replacements != null) {
                chars[i] = replacements[rng.nextInt(replacements.length)];
            }
        }
        return new String(chars);
    }

    private static String watermarkName(String base) {
        if (base.contains(WATERMARK_TEXT)) return base;
        return base + WATERMARK_TEXT;
    }

    private static String pickDecryptName(ClassNode node) {
        Set<String> taken = new HashSet<>();
        for (MethodNode m : node.methods) {
            if (m.desc.equals(DECRYPT_DESC)) taken.add(m.name);
        }

        String[] candidates = {
            "CxCx", "CxCxCx", "CxCx0", "Cx0Cx0", "0CxCx0",
            "CxCxO", "O0O", "0O0", "\u043e\u0435", "CxCx1"
        };
        for (String c : candidates) {
            String candidate = watermarkName(c);
            if (!taken.contains(candidate)) return candidate;
        }
        int n = 0;
        while (taken.contains(watermarkName("CxCx" + n))) n++;
        return watermarkName("CxCx" + n);
    }

    private static void emitDecryptMethod(ClassNode node, String name, int variant) {
        MethodNode m = new MethodNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                name, DECRYPT_DESC, null, null);

        LabelNode loopBody = new LabelNode();
        LabelNode loopCheck = new LabelNode();
        InsnList il = new InsnList();

        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "toCharArray", "()[C", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 2));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 3));
        il.add(new JumpInsnNode(Opcodes.GOTO, loopCheck));

        il.add(loopBody);
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new InsnNode(Opcodes.DUP2));
        il.add(new InsnNode(Opcodes.CALOAD));

        switch (variant) {
            case 0 -> {

                il.add(new VarInsnNode(Opcodes.ILOAD, 1));
                il.add(new VarInsnNode(Opcodes.ILOAD, 3));
                il.add(new IntInsnNode(Opcodes.BIPUSH, 7));
                il.add(new InsnNode(Opcodes.IMUL));
                il.add(new InsnNode(Opcodes.IADD));
            }
            case 1 -> {

                il.add(new VarInsnNode(Opcodes.ILOAD, 1));
                il.add(new VarInsnNode(Opcodes.ILOAD, 3));
                il.add(new IntInsnNode(Opcodes.BIPUSH, 13));
                il.add(new InsnNode(Opcodes.IMUL));
                il.add(new IntInsnNode(Opcodes.BIPUSH, 37));
                il.add(new InsnNode(Opcodes.IADD));
                il.add(new InsnNode(Opcodes.IXOR));
            }
            case 2 -> {

                il.add(new VarInsnNode(Opcodes.ILOAD, 1));
                il.add(new VarInsnNode(Opcodes.ILOAD, 3));
                il.add(new VarInsnNode(Opcodes.ILOAD, 3));
                il.add(new InsnNode(Opcodes.IMUL));
                il.add(new InsnNode(Opcodes.ICONST_3));
                il.add(new InsnNode(Opcodes.IMUL));
                il.add(new InsnNode(Opcodes.IADD));
                il.add(new VarInsnNode(Opcodes.ILOAD, 3));
                il.add(new IntInsnNode(Opcodes.BIPUSH, 11));
                il.add(new InsnNode(Opcodes.IMUL));
                il.add(new InsnNode(Opcodes.IADD));
            }
        }
        il.add(new IntInsnNode(Opcodes.SIPUSH, 255));
        il.add(new InsnNode(Opcodes.IAND));
        il.add(new InsnNode(Opcodes.IXOR));
        il.add(new InsnNode(Opcodes.I2C));
        il.add(new InsnNode(Opcodes.CASTORE));
        il.add(new IincInsnNode(3, 1));

        il.add(loopCheck);
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new InsnNode(Opcodes.ARRAYLENGTH));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPLT, loopBody));

        il.add(new TypeInsnNode(Opcodes.NEW, "java/lang/String"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/String", "<init>", "([C)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));

        m.instructions = il;
        m.maxStack = 8;
        m.maxLocals = 4;
        node.methods.add(m);
    }

    private static Integer intValue(AbstractInsnNode insn) {
        if (insn instanceof InsnNode) {
            int op = insn.getOpcode();
            if (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5)
                return op - Opcodes.ICONST_0;
        } else if (insn instanceof IntInsnNode ii) {
            if (ii.getOpcode() == Opcodes.BIPUSH || ii.getOpcode() == Opcodes.SIPUSH)
                return ii.operand;
        } else if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Integer) {
            return (Integer) ldc.cst;
        }
        return null;
    }

    private static AbstractInsnNode pushInt(int v) {
        if (v >= -1 && v <= 5) return new InsnNode(Opcodes.ICONST_0 + v);
        if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) return new IntInsnNode(Opcodes.BIPUSH, v);
        if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE) return new IntInsnNode(Opcodes.SIPUSH, v);
        return new LdcInsnNode(v);
    }

    static final class HierarchyAwareRemapper extends org.objectweb.asm.commons.Remapper {
        private final Map<String, String> classMap;
        private final Map<String, String> fieldMap;
        private final Map<String, String> hierarchy;

        HierarchyAwareRemapper(Map<String, String> classMap, Map<String, String> fieldMap, Map<String, String> hierarchy) {
            this.classMap = classMap;
            this.fieldMap = fieldMap;
            this.hierarchy = hierarchy;
        }

        @Override
        public String map(String internalName) {
            return classMap.getOrDefault(internalName, internalName);
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            Set<String> visited = new HashSet<>();
            String current = owner;
            while (current != null && visited.add(current)) {
                String mapped = fieldMap.get(fieldKey(current, name, descriptor));
                if (mapped != null) return mapped;
                current = hierarchy.get(current);
            }
            return name;
        }

        private static String fieldKey(String owner, String name, String descriptor) {
            return owner + "." + name + ":" + descriptor;
        }
    }

    static final class HierarchyWriter extends ClassWriter {
        private final Map<String, String> hierarchy;
        private final ClassLoader frameClassLoader;

        HierarchyWriter(Map<String, String> hierarchy, ClassLoader frameClassLoader) {
            super(COMPUTE_FRAMES);
            this.hierarchy = hierarchy;
            this.frameClassLoader = frameClassLoader;
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            if ("java/lang/Object".equals(type1) || "java/lang/Object".equals(type2)) {
                return "java/lang/Object";
            }
            if (frameClassLoader != null && type1.charAt(0) != '[' && type2.charAt(0) != '[') {
                try {
                    Class<?> c1 = Class.forName(type1.replace('/', '.'), false, frameClassLoader);
                    Class<?> c2 = Class.forName(type2.replace('/', '.'), false, frameClassLoader);
                    if (c1.isAssignableFrom(c2)) {
                        return type1;
                    }
                    if (c2.isAssignableFrom(c1)) {
                        return type2;
                    }
                    if (c1.isInterface() || c2.isInterface()) {
                        return "java/lang/Object";
                    }
                    while (c1 != null && !c1.isAssignableFrom(c2)) {
                        c1 = c1.getSuperclass();
                    }
                    if (c1 == null) {
                        return "java/lang/Object";
                    }
                    return c1.getName().replace('.', '/');
                } catch (ClassNotFoundException | LinkageError ignored) {
                }
            }

            Set<String> ancestors = new LinkedHashSet<>();
            String cur = type1;
            Set<String> visited = new HashSet<>();
            while (cur != null && !"java/lang/Object".equals(cur) && visited.add(cur)) {
                ancestors.add(cur);
                cur = resolve(cur);
            }
            ancestors.add("java/lang/Object");

            cur = type2;
            visited.clear();
            while (cur != null && visited.add(cur)) {
                if (ancestors.contains(cur)) return cur;
                if ("java/lang/Object".equals(cur)) return cur;
                cur = resolve(cur);
            }
            return "java/lang/Object";
        }

        private String resolve(String type) {
            String parent = hierarchy.get(type);
            if (parent != null) return parent;
            ClassLoader cl = frameClassLoader != null ? frameClassLoader : getClass().getClassLoader();
            try {
                Class<?> c = Class.forName(type.replace('/', '.'), false, cl);
                Class<?> s = c.getSuperclass();
                return s != null ? s.getName().replace('.', '/') : null;
            } catch (Exception e) {
                return null;
            }
        }
    }
}
