package nhb.eclipse.ultimate.mcpserver.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/** Shows a JSON document (request or response body) as an expandable/collapsible tree. */
public class JsonTreeDialog extends TitleAreaDialog {

    /** One row in the tree: a JSON member/element name paired with its value node. */
    private record Node(String label, JsonElement value) {
    }

    private final String title;
    private final String rawJson;

    public JsonTreeDialog(Shell parentShell, String title, String rawJson) {
        super(parentShell);
        this.title = title;
        this.rawJson = rawJson;
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle(title);

        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayout(new GridLayout(1, false));
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        TreeViewer viewer = new TreeViewer(container, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        GridData treeData = new GridData(SWT.FILL, SWT.FILL, true, true);
        treeData.widthHint = 640;
        treeData.heightHint = 420;
        viewer.getTree().setLayoutData(treeData);

        Node root = parseRoot();
        viewer.setContentProvider(new JsonContentProvider());
        viewer.setLabelProvider(new JsonLabelProvider());
        viewer.setInput(root);
        viewer.expandToLevel(2);

        return area;
    }

    private Node parseRoot() {
        if (rawJson == null || rawJson.isBlank()) {
            return new Node("(empty)", JsonNull());
        }
        try {
            return new Node("$", JsonParser.parseString(rawJson));
        } catch (Exception e) {
            return new Node("(unparsable JSON: " + e.getMessage() + ")", new JsonPrimitive(rawJson));
        }
    }

    private static JsonElement JsonNull() {
        return com.google.gson.JsonNull.INSTANCE;
    }

    private static final class JsonContentProvider implements ITreeContentProvider {

        @Override
        public Object[] getElements(Object inputElement) {
            return new Object[] { inputElement };
        }

        @Override
        public Object[] getChildren(Object parentElement) {
            Node node = (Node) parentElement;
            JsonElement value = node.value();
            List<Node> children = new ArrayList<>();
            if (value != null && value.isJsonObject()) {
                JsonObject obj = value.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    children.add(new Node(entry.getKey(), entry.getValue()));
                }
            } else if (value != null && value.isJsonArray()) {
                JsonArray array = value.getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    children.add(new Node("[" + i + "]", array.get(i)));
                }
            }
            return children.toArray();
        }

        @Override
        public Object getParent(Object element) {
            return null;
        }

        @Override
        public boolean hasChildren(Object element) {
            JsonElement value = ((Node) element).value();
            return value != null && (value.isJsonObject() && value.getAsJsonObject().size() > 0
                    || value.isJsonArray() && value.getAsJsonArray().size() > 0);
        }
    }

    private static final class JsonLabelProvider extends LabelProvider {

        @Override
        public String getText(Object element) {
            Node node = (Node) element;
            JsonElement value = node.value();
            if (value == null || value.isJsonNull()) {
                return node.label() + ": null";
            }
            if (value.isJsonObject()) {
                int size = value.getAsJsonObject().size();
                return node.label() + (size == 0 ? ": {}" : " {" + size + "}");
            }
            if (value.isJsonArray()) {
                int size = value.getAsJsonArray().size();
                return node.label() + (size == 0 ? ": []" : " [" + size + "]");
            }
            return node.label() + ": " + value.getAsString();
        }
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
    }

    @Override
    protected org.eclipse.swt.graphics.Point getInitialSize() {
        return new org.eclipse.swt.graphics.Point(700, 520);
    }
}
