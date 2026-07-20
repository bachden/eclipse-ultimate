package nhb.eclipse.ultimate.mcpserver.ui;

import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.jface.preference.RadioGroupFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import nhb.eclipse.ultimate.mcpserver.Activator;
import nhb.eclipse.ultimate.mcpserver.McpServerPreferences;

/**
 * Workbench preference page for the MCP HTTP server's host/port/auth.
 * Changing any value while the server is running restarts it on save so the
 * new binding takes effect immediately. Also includes a helper that
 * generates ready-to-paste MCP client config for popular agents.
 */
public class McpPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    private static final McpConfigGenerator.Agent[] AGENTS = McpConfigGenerator.Agent.values();

    private Combo agentCombo;
    private StyledText configText;
    private Button authEnabledButton;
    private Text tokenText;
    private String currentToken;
    private IntegerFieldEditor portField;
    private Composite fieldEditorParent;

    public McpPreferencePage() {
        super(GRID);
        setTitle("Eclipse Ultimate MCP Server");
        PreferenceStore store = new PreferenceStore();
        store.setValue(McpServerPreferences.PREF_HOST, McpServerPreferences.getHost());
        store.setValue(McpServerPreferences.PREF_PORT, McpServerPreferences.getPort());
        store.setValue(McpServerPreferences.PREF_PORT_MODE, McpServerPreferences.getPortMode());
        setPreferenceStore(store);
        currentToken = McpServerPreferences.getAuthToken();
    }

    @Override
    public void init(IWorkbench workbench) {
        // Nothing to do; preference store is wired up in the constructor.
    }

    @Override
    protected void createFieldEditors() {
        setDescription("Configure the MCP HTTP server that exposes this Eclipse instance to external agents. "
                + "Changes apply immediately if the server is running.");
        fieldEditorParent = getFieldEditorParent();
        addField(new StringFieldEditor(McpServerPreferences.PREF_HOST, "Listener host:", fieldEditorParent));

        RadioGroupFieldEditor portModeEditor = new RadioGroupFieldEditor(McpServerPreferences.PREF_PORT_MODE,
                "Port assignment:", 1,
                new String[][] {
                        { "Random (recommended — lets multiple Eclipse workspaces run concurrently)",
                                McpServerPreferences.PORT_MODE_RANDOM },
                        { "Fixed (use the port below)", McpServerPreferences.PORT_MODE_FIXED } },
                fieldEditorParent, true);
        addField(portModeEditor);

        portField = new IntegerFieldEditor(McpServerPreferences.PREF_PORT, "Port:", fieldEditorParent);
        portField.setValidRange(1, 65535);
        addField(portField);
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        super.propertyChange(event);
        if (event.getProperty().equals(org.eclipse.jface.preference.FieldEditor.VALUE)
                && event.getSource() instanceof RadioGroupFieldEditor) {
            updatePortFieldEnablement(event.getNewValue());
        }
    }

    private void updatePortFieldEnablement() {
        updatePortFieldEnablement(getPreferenceStore().getString(McpServerPreferences.PREF_PORT_MODE));
    }

    private void updatePortFieldEnablement(Object portMode) {
        if (portField == null) {
            return;
        }
        boolean fixed = McpServerPreferences.PORT_MODE_FIXED.equals(portMode);
        portField.setEnabled(fixed, fieldEditorParent);
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite outer = new Composite(parent, SWT.NONE);
        outer.setLayout(new GridLayout(1, false));
        outer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Control fieldEditors = super.createContents(outer);
        fieldEditors.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        updatePortFieldEnablement();

        createAuthSection(outer);
        createConfigHelperSection(outer);
        return outer;
    }

    private void createAuthSection(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Authentication");
        group.setLayout(new GridLayout(3, false));
        group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        authEnabledButton = new Button(group, SWT.CHECK);
        authEnabledButton.setText("Require bearer token for requests");
        authEnabledButton.setSelection(McpServerPreferences.isAuthEnabled());
        authEnabledButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
        authEnabledButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateTokenEnablement();
                refreshConfigText();
            }
        });

        Label tokenLabel = new Label(group, SWT.NONE);
        tokenLabel.setText("Token:");

        tokenText = new Text(group, SWT.BORDER | SWT.READ_ONLY);
        tokenText.setText(currentToken);
        tokenText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button renewButton = new Button(group, SWT.PUSH);
        renewButton.setText("Renew");
        renewButton.setToolTipText("Generate a new token; the old token stops working immediately");
        renewButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                renewToken();
            }
        });

        updateTokenEnablement();
    }

    private void updateTokenEnablement() {
        boolean enabled = authEnabledButton.getSelection();
        tokenText.setEnabled(enabled);
    }

    private void renewToken() {
        currentToken = McpServerPreferences.generateToken();
        McpServerPreferences.setAuthToken(currentToken);
        tokenText.setText(currentToken);

        Activator activator = Activator.getDefault();
        if (activator != null && activator.isServerRunning()) {
            activator.restartServer();
        }
        refreshConfigText();
    }

    private void createConfigHelperSection(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Connect an agent");
        group.setLayout(new GridLayout(3, false));
        group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Label agentLabel = new Label(group, SWT.NONE);
        agentLabel.setText("Agent:");

        agentCombo = new Combo(group, SWT.READ_ONLY);
        for (McpConfigGenerator.Agent agent : AGENTS) {
            agentCombo.add(agent.label());
        }
        agentCombo.select(0);
        agentCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        agentCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshConfigText();
            }
        });

        Button copyButton = new Button(group, SWT.PUSH);
        copyButton.setText("Copy");
        copyButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                copyToClipboard();
            }
        });

        configText = new StyledText(group, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.READ_ONLY | SWT.V_SCROLL);
        GridData textData = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
        textData.heightHint = 100;
        configText.setLayoutData(textData);

        refreshConfigText();
    }

    private void refreshConfigText() {
        if (agentCombo == null || configText == null) {
            return;
        }
        McpConfigGenerator.Agent agent = AGENTS[agentCombo.getSelectionIndex()];
        Activator activator = Activator.getDefault();
        boolean running = activator != null && activator.isServerRunning();
        // While running, show the actual bound host/port (which may differ from the
        // configured values in random-port mode); otherwise fall back to configuration.
        String host = running ? activator.getBoundHost() : getPreferenceStore().getString(McpServerPreferences.PREF_HOST);
        int port = running ? activator.getBoundPort() : getPreferenceStore().getInt(McpServerPreferences.PREF_PORT);
        boolean authEnabled = authEnabledButton != null && authEnabledButton.getSelection();
        configText.setText(McpConfigGenerator.generate(agent, host, port, authEnabled, currentToken));
    }

    private void copyToClipboard() {
        if (configText == null) {
            return;
        }
        Clipboard clipboard = new Clipboard(configText.getDisplay());
        try {
            clipboard.setContents(new Object[] { configText.getText() }, new Transfer[] { TextTransfer.getInstance() });
        } finally {
            clipboard.dispose();
        }
    }

    @Override
    public boolean performOk() {
        boolean ok = super.performOk();
        if (ok) {
            String host = getPreferenceStore().getString(McpServerPreferences.PREF_HOST);
            int port = getPreferenceStore().getInt(McpServerPreferences.PREF_PORT);
            String portMode = getPreferenceStore().getString(McpServerPreferences.PREF_PORT_MODE);
            McpServerPreferences.setHost(host);
            McpServerPreferences.setPort(port);
            McpServerPreferences.setPortMode(portMode);
            McpServerPreferences.setAuthEnabled(authEnabledButton.getSelection());

            Activator activator = Activator.getDefault();
            if (activator != null && activator.isServerRunning()) {
                activator.restartServer();
            }
            refreshConfigText();
        }
        return ok;
    }
}
