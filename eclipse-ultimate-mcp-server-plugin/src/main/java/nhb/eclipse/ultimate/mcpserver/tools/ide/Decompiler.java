package nhb.eclipse.ultimate.mcpserver.tools.ide;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.JavaModelException;

import io.github.nbauma109.decompiler.JavaDecompilerPlugin;
import io.github.nbauma109.decompiler.editor.DecompilerSourceMapper;
import io.github.nbauma109.decompiler.editor.DecompilerType;

/**
 * Thin bridge to the Enhanced Class Decompiler (ECD++) plugin's programmatic API. Kept in its own
 * class so the optional compile-time dependency on {@code io.github.nbauma109.decompiler} stays
 * isolated; callers must first confirm the bundle is installed via {@code Platform.getBundle}.
 */
final class Decompiler {

    /**
     * Descriptions written from the engines' documented behavior (ECD++/transformer-api docs and
     * Vineflower's decompiler output-comparison notes), so callers can pick an engine appropriate
     * for the bytecode's age and language features instead of always taking the default.
     */
    private static final Map<String, String> DESCRIPTIONS = descriptions();

    private static final String DEFAULT_DESCRIPTION = "Third-party decompiler engine bundled with ECD++; "
            + "no specific guidance recorded for it, use it as a fallback if the recommended engine for this "
            + "bytecode's era doesn't produce clean output.";

    private Decompiler() {
    }

    static String decompilerType() {
        return DecompilerType.getDefault();
    }

    /** Every decompiler engine ECD++ currently exposes, in a stable order. */
    static Set<String> availableTypes() {
        return new TreeSet<>(DecompilerType.getDecompilerTypes());
    }

    /** Guidance on when an engine is the right pick, or a generic fallback for unrecognised engines. */
    static String describe(String engineName) {
        return DESCRIPTIONS.getOrDefault(engineName, DEFAULT_DESCRIPTION);
    }

    @SuppressWarnings("deprecation")
    static char[] decompile(IClassFile classFile) throws JavaModelException {
        DecompilerSourceMapper sourceMapper = JavaDecompilerPlugin.getDefault().getSourceMapper(decompilerType());
        return sourceMapper.findSource(classFile.getType());
    }

    private static Map<String, String> descriptions() {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("Vineflower", "Actively-maintained fork of Fernflower; the strongest general-purpose choice for "
                + "modern bytecode. Correctly reconstructs Java 14+ switch expressions, Java 16+ pattern "
                + "matching/local enums, Java 21 record patterns, sealed classes, and try-with-resources with "
                + "finally blocks. Recommended default for anything compiled with a recent JDK (17+).");
        d.put("CFR", "Mature, actively-maintained engine with solid support for modern language features "
                + "(switch expressions, try-with-resources, lambdas) and particularly strong handling of complex "
                + "generics and type inference. A good second opinion when Vineflower's output looks off, and a "
                + "safe choice across both older (Java 5-8) and newer bytecode.");
        d.put("Procyon", "Handles local enums, switch expressions and lambdas well, but has no support for the "
                + "Java 9+ module system and does not track newer language features (records, pattern matching, "
                + "sealed classes). Best suited to class files compiled with Java 8 or earlier.");
        d.put("Fernflower", "JetBrains' original engine (bundled in IntelliJ IDEA). Reliable baseline for "
                + "pre-Java-14 bytecode, but struggles with switch expressions (mis-decompiles the implicit "
                + "yield as a plain assignment), pattern matching, and some shift-operation bytecode. Prefer "
                + "Vineflower, its actively-maintained successor, unless matching IntelliJ's exact output matters.");
        d.put("JD-Core", "Analytical, control-flow-graph-based engine (like Fernflower) powering modern JD-GUI "
                + "(1.4.3+). Reasonable general-purpose choice for Java 5-11 bytecode; not tuned for the newest "
                + "language features (records, pattern matching, sealed classes) — prefer Vineflower or CFR for "
                + "those.");
        d.put("JD-Core v1", d.get("JD-Core"));
        d.put("JD-Core v0", "Legacy bytecode-pattern-matching engine (in the style of the old JAD decompiler), "
                + "used by JD-GUI up to 1.4.2. Only worth trying for very old (pre-Java-5) or unusually simple "
                + "class files, or as a last resort when every other engine fails on a given class — its output "
                + "is generally less faithful than the analytical engines (Fernflower/Vineflower/JD-Core v1).");
        d.put("JADX", "Purpose-built for Android/Dalvik bytecode (.dex, APKs), not regular JVM .class files. "
                + "Only appropriate when the type being viewed actually originates from Android bytecode; for "
                + "ordinary JVM class files, prefer Vineflower or CFR instead.");
        return d;
    }
}
