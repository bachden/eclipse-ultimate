package nhb.eclipse.plugin.mcp.ultimate.tools.coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;

/** Read/write helpers shared by the text-editing coder tools. */
final class TextFiles {

    private TextFiles() {
    }

    static IFile file(String projectName, String filePath) {
        IProject project = CoderResources.project(projectName);
        IResource resource = CoderResources.resource(project, filePath);
        if (!(resource instanceof IFile)) {
            throw new IllegalArgumentException(filePath + " is not a file");
        }
        return (IFile) resource;
    }

    static String read(IFile file) throws Exception {
        try (InputStream in = file.getContents()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    static void write(IFile file, String content) throws Exception {
        file.setContents(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), true, true,
                new NullProgressMonitor());
    }
}
