package nhb.eclipse.ultimate.mcpserver.tools.maven;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.MavenUpdateRequest;
import org.eclipse.m2e.core.ui.internal.editing.PomEdits;
import org.eclipse.m2e.core.ui.internal.editing.PomHelper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

final class MavenDependencyEditSupport {

    private MavenDependencyEditSupport() {
    }

    static IFile pomFile(String projectName) throws Exception {
        IFile pom = MavenSupport.facade(projectName).getPom();
        if (pom == null || !pom.exists()) {
            throw new IllegalArgumentException("Maven project has no workspace pom.xml: " + projectName);
        }
        return pom;
    }

    static void apply(String projectName, PomEdits.Operation operation, String label, boolean refreshProject,
            boolean forceDependencyUpdate) throws Exception {
        IFile pom = pomFile(projectName);
        TextChange change = PomHelper.createChange(pom, operation, label, true);
        if (change == null) {
            throw new IllegalStateException("m2e could not create a POM edit for " + pom.getFullPath());
        }
        change.perform(new NullProgressMonitor());
        if (refreshProject) {
            refresh(pom.getProject(), forceDependencyUpdate);
        }
    }

    static void refresh(IProject project, boolean forceDependencyUpdate) throws Exception {
        NullProgressMonitor monitor = new NullProgressMonitor();
        if (forceDependencyUpdate) {
            MavenPlugin.getMavenProjectRegistry().refresh(new MavenUpdateRequest(project, false, true));
        }
        MavenPlugin.getProjectConfigurationManager().updateProjectConfiguration(project, monitor);
    }

    static Element dependencies(Document document, boolean dependencyManagement, boolean create) {
        Element project = document.getDocumentElement();
        if (!dependencyManagement) {
            return create ? PomEdits.getChild(project, new String[] { PomEdits.DEPENDENCIES })
                    : PomEdits.findChild(project, PomEdits.DEPENDENCIES);
        }

        if (create) {
            return PomEdits.getChild(project, new String[] { PomEdits.DEPENDENCY_MANAGEMENT, PomEdits.DEPENDENCIES });
        }
        Element management = PomEdits.findChild(project, PomEdits.DEPENDENCY_MANAGEMENT);
        return management == null ? null : PomEdits.findChild(management, PomEdits.DEPENDENCIES);
    }

    static Element findDependency(Element dependencies, String groupId, String artifactId, String type,
            String classifier) {
        if (dependencies == null) {
            return null;
        }
        String expectedType = normalizeType(type);
        String expectedClassifier = normalize(classifier);
        for (Element dependency : PomHelper.findDependencies(dependencies)) {
            if (!groupId.equals(childText(dependency, PomEdits.GROUP_ID))
                    || !artifactId.equals(childText(dependency, PomEdits.ARTIFACT_ID))) {
                continue;
            }
            if (expectedType.equals(normalizeType(childText(dependency, PomEdits.TYPE)))
                    && expectedClassifier.equals(normalize(childText(dependency, PomEdits.CLASSIFIER)))) {
                return dependency;
            }
        }
        return null;
    }

    static Element createDependency(Element dependencies, String groupId, String artifactId) {
        return PomHelper.createDependency(dependencies, groupId, artifactId, null);
    }

    static void setField(Element dependency, String name, String value) {
        if (value == null) {
            return;
        }
        Element child = PomEdits.findChild(dependency, name);
        if (value.isBlank()) {
            if (child != null) {
                PomEdits.removeChild(dependency, child);
            }
        } else if (child == null) {
            PomEdits.createElementWithText(dependency, name, value);
        } else {
            PomEdits.setText(child, value);
        }
    }

    static String childText(Element parent, String name) {
        Element child = PomEdits.findChild(parent, name);
        String value = child == null ? null : PomEdits.getTextValue(child);
        return normalize(value);
    }

    static String normalizeType(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? "jar" : normalized;
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    static void removeEmptyParents(Document document, boolean dependencyManagement, Element dependencies) {
        PomEdits.removeIfNoChildElement(dependencies);
        if (dependencyManagement) {
            Element management = PomEdits.findChild(document.getDocumentElement(), PomEdits.DEPENDENCY_MANAGEMENT);
            if (management != null) {
                PomEdits.removeIfNoChildElement(management);
            }
        }
    }
}
