package nhb.eclipse.ultimate.mcpserver.tools.ide;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;

/** Shared helpers for walking/reading files under a project or container. */
final class FileWalker {

    private static final java.util.Set<String> BINARY_EXTENSIONS = java.util.Set.of("class", "jar", "zip", "png",
            "jpg", "jpeg", "gif", "ico", "so", "dylib", "dll", "exe", "bin");

    private FileWalker() {
    }

    static List<IFile> allFiles(IResource root) throws Exception {
        List<IFile> files = new ArrayList<>();
        root.accept(new IResourceProxyVisitor() {
            @Override
            public boolean visit(IResourceProxy proxy) {
                if (proxy.getType() == IResource.FILE) {
                    files.add((IFile) proxy.requestResource());
                }
                return true;
            }
        }, IResource.NONE);
        return files;
    }

    static boolean isProbablyText(IFile file) {
        String ext = file.getFileExtension();
        return ext == null || !BINARY_EXTENSIONS.contains(ext.toLowerCase());
    }

    static List<String> readLines(IFile file) {
        List<String> lines = new ArrayList<>();
        try (InputStream in = file.getContents();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException | org.eclipse.core.runtime.CoreException e) {
            // Unreadable/binary file — treat as no lines.
        }
        return lines;
    }
}
