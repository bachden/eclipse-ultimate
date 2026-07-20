# Eclipse Ultimate MCP Server

Eclipse Ultimate MCP Server runs a local Model Context Protocol (MCP) Streamable HTTP server inside Eclipse. It lets an MCP-compatible coding agent inspect and operate the live Eclipse workspace through Eclipse's own APIs.

It is intended for workflows that need more than filesystem access:

- inspect the active Eclipse workspace and Java model;
- read, search, format and refactor Java code through JDT;
- run tests and inspect compiler diagnostics;
- manage Git repositories through EGit/JGit;
- create and control Eclipse launch/debug configurations;
- identify the file currently open in the Eclipse editor.

The server runs locally inside Eclipse. It does not start a separate project process and it does not upload source code by itself. An MCP client receives data requested through tool calls.

## Requirements

- Eclipse IDE with the bundles used by this plugin. Eclipse IDE for Enterprise Java and Web Developers is recommended.
- Java 21 or newer.
- A trusted Eclipse workspace.
- An MCP client that supports Streamable HTTP, such as Codex CLI/IDE, Claude Code CLI or Claude Desktop.
- A local port available for the server. Random port mode is recommended when multiple Eclipse workspaces run at once.

## Install From GitHub Pages

1. In Eclipse, open **Help > Install New Software...**.
2. Add this update site:

~~~text
https://bachden.github.io/eclipse-ultimate/
~~~

3. Select **Eclipse Ultimate MCP Server**.
4. Accept the license prompts and complete the installation.
5. Restart Eclipse.

The Pages root may display 404 in a normal web browser. This is expected: the URL is a p2 update site, not an HTML website. Eclipse reads p2.index, artifacts.xml.xz and content.xml.xz from it.

## First Start

After Eclipse starts, the plugin starts the MCP server automatically when the persisted server state is enabled.

Defaults:

- host: 127.0.0.1;
- authentication: enabled;
- port mode: random;
- server enabled across Eclipse restarts.

The actual host and port are shown in the Eclipse status bar. Click the status item to open the server menu.

Available commands are under **Eclipse Ultimate MCP**:

- **Start MCP Server**
- **Stop MCP Server**
- **Restart MCP Server**
- **MCP Server Settings...**
- **MCP Server Connections...**

A user-triggered Start or Restart persists the enabled state. A user-triggered Stop persists the disabled state. Changing host, port, port mode, auth or token from the preferences page restarts the running server without changing the user's Start/Stop choice.

## Configure The Server

Open **Window > Preferences > Eclipse Ultimate MCP Server**.

### Listener

- **Listener host** controls the bind address. Keep 127.0.0.1 for a local-only server.
- **Random** port mode lets multiple Eclipse workspaces run concurrently.
- **Fixed** port mode uses the configured port. The default configured port is 8733.

The MCP endpoint is:

~~~text
http://<bound-host>:<bound-port>/mcp
~~~

When the listener host is 0.0.0.0, generated client configuration uses 127.0.0.1 as the local connection address. This avoids telling a local client to connect to the wildcard address.

### Authentication

Bearer-token authentication is enabled by default. The token is generated and persisted in Eclipse instance preferences. Use **Renew** to generate a new token; the previous token stops working immediately.

Do not commit or share the generated token. If it is exposed, renew it and update the MCP client configuration.

Disabling authentication is possible, but exposes file, Git, launch and debug operations to any process that can reach the bound address. Use it only on a deliberately isolated machine or network.

## Generated Client Configuration

When the server starts or restarts, the plugin updates both files at the Eclipse workspace root:

- .mcp.json: the mcpServers.eclipse-ultimate entry;
- .codex/config.toml: the mcp_servers.eclipse-ultimate entry.

Only the Eclipse Ultimate entry is managed. Other MCP servers and unrelated settings are preserved.

Codex configuration uses the current config.toml schema:

~~~toml
[mcp_servers.eclipse-ultimate]
url = "http://127.0.0.1:8733/mcp"
http_headers = { Authorization = "Bearer <token>" }
~~~

The .mcp.json shape is:

~~~json
{
  "mcpServers": {
    "eclipse-ultimate": {
      "type": "http",
      "url": "http://127.0.0.1:8733/mcp",
      "headers": {
        "Authorization": "Bearer <token>"
      }
    }
  }
}
~~~

These files may contain a bearer token. They are ignored by this repository's .gitignore; keep them private.

The preference page also contains a **Connect an agent** helper. It generates snippets for Claude Code CLI, Claude Desktop, Codex CLI and a raw endpoint URL. In random-port mode it uses the actual bound port.

### Codex CLI / IDE

Codex reads project-scoped MCP configuration from .codex/config.toml in trusted projects. The generated configuration is:

~~~toml
[mcp_servers.eclipse-ultimate]
url = "http://127.0.0.1:<bound-port>/mcp"
http_headers = { Authorization = "Bearer <token>" }
~~~

Restart or reload the Codex session after the file changes if the client does not refresh MCP configuration automatically.

### Claude Code CLI

With authentication:

~~~bash
claude mcp add --transport http eclipse-ultimate \
  http://127.0.0.1:<bound-port>/mcp \
  --header "Authorization: Bearer <token>"
~~~

Without authentication:

~~~bash
claude mcp add --transport http eclipse-ultimate \
  http://127.0.0.1:<bound-port>/mcp
~~~

### Claude Desktop

Add an HTTP server entry to claude_desktop_config.json:

~~~json
{
  "mcpServers": {
    "eclipse-ultimate": {
      "type": "http",
      "url": "http://127.0.0.1:<bound-port>/mcp",
      "headers": {
        "Authorization": "Bearer <token>"
      }
    }
  }
}
~~~

Restart Claude Desktop after editing its configuration.

## MCP Protocol

The endpoint is a JSON-RPC 2.0 MCP endpoint at POST /mcp.

Supported methods:

- initialize
- notifications/initialized
- ping
- tools/list
- tools/call

A minimal health check with authentication:

~~~bash
curl --fail-with-body \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"ping"}' \
  http://127.0.0.1:<bound-port>/mcp
~~~

The server accepts JSON-RPC batches. Notifications receive 202 Accepted; unsupported HTTP methods receive 405 Method Not Allowed.

## Available Tool Groups

Exact tool names and JSON input schemas are advertised through tools/list.

### IDE and workspace

- list projects, inspect project details and browse project layouts;
- read project resources, resolve linked workspace files to absolute paths and find files;
- plain-text and regular-expression search;
- get Java source, method source, class outlines and references;
- inspect compilation errors and project dependencies;
- format Java code, organize imports, apply JDT quick fixes and run Clean Up profiles;
- identify and open the file currently active in Eclipse;
- show proposed changes in Eclipse Compare;
- discover test classes, run all tests or run a test class;
- read launch console output;
- check Eclipse updates.

### Coder and refactoring

- create directories and files;
- delete, rename or move resources;
- replace full files or exact strings;
- insert or delete lines;
- apply unified patches;
- organize imports, apply JDT quick fixes and run Clean Up profiles;
- rename Java types;
- move Java types between packages;
- rename packages and update references.

### Mylyn

When the corresponding Mylyn bundles are installed:

- list and inspect configured Mylyn Task Repositories;
- list Mylyn Builds servers such as Jenkins;
- read cached plans, recent builds and bounded console logs;
- optionally refresh a build server configuration through its Mylyn connector;
- redact passwords, tokens and credential properties from all tool output.

### Git

Git operations use Eclipse EGit repository mappings and JGit APIs:

- status, diff and log;
- add and commit;
- branch listing, creation, checkout and deletion;
- reset;
- stash, stash list and stash pop.

Git write operations are exposed to the MCP client. Review requested operations before allowing an agent to execute them.

### Runner and debug

- list, inspect, create, update and refresh launch configurations;
- launch or stop applications;
- list and remove breakpoints;
- toggle line breakpoints and set conditions;
- resume, step into, step over and step return;
- inspect stack traces;
- evaluate expressions in suspended frames.

Debug operations act on the live Eclipse debug session and can change application state.

## Security Notes

This plugin exposes high-impact local operations: file writes, Java refactoring, Git mutation, launch control and debugger control.

Recommended defaults:

1. Keep the listener on 127.0.0.1.
2. Keep bearer authentication enabled.
3. Use random ports for multiple workspaces.
4. Treat the token as a secret.
5. Use client-side approval policies for write, Git and debug tools.
6. Do not bind to 0.0.0.0 unless the network boundary and firewall are intentional.
7. Do not paste generated configs or tokens into public issues, logs or commits.

The server records a recent connection log for display in **MCP Server Connections...**. The log is for local diagnostics, not an audit trail.

## Build Locally

Build the complete plugin, feature and update site with Maven/Tycho:

~~~bash
mvn --batch-mode --update-snapshots clean verify
~~~

Tycho always resolves the portable p2 target at eclipse-ultimate-mcp-server/eclipse-ci.target. The build does not scan or depend on Eclipse, DBeaver or other products installed on the local machine, so the same command works on development machines and GitHub Actions.

The update site is assembled in _updatesite/target/repository/ and copied to dist/.

Do not commit target/ or dist/.

## GitHub Actions And Publishing

The repository has two workflows:

- Build and Publish Eclipse Update Site: runs on pushes to main, uses the repository's portable Tycho target and deploys dist/ to GitHub Pages.
- Validate Eclipse Update Site: runs on pull requests and uploads the generated update site as a build artifact.

Published update site:

~~~text
https://bachden.github.io/eclipse-ultimate/
~~~

A normal browser may show 404 at the root because p2 repositories do not require an HTML index. Use the URL in Eclipse's **Install New Software...** dialog.

## Development Workflow

1. Make source changes under eclipse-ultimate-mcp-server/src/.
2. Update plugin or feature metadata when dependencies or extension points change.
3. Run Eclipse diagnostics and format affected Java files.
4. Run mvn --batch-mode --update-snapshots clean verify; this is the same portable Tycho build used by CI.
5. Test the installed update site in a clean or disposable Eclipse workspace.
6. Commit source and metadata only; keep generated build output and local MCP credentials untracked.

## Troubleshooting

### MCP client cannot connect

Check:

- Eclipse is running and the plugin status item says MCP: running.
- The client URL uses the actual bound port, not only the configured port in random mode.
- The Authorization bearer token matches the current Eclipse token.
- The client is sending an HTTP POST to /mcp.
- The client was restarted or reloaded after configuration changed.

The generated .mcp.json and .codex/config.toml are the fastest source of the current URL and token.

### 401 Unauthorized

Authentication is enabled and the token is missing or stale. Copy the current generated configuration or renew the token and update the client.

### 404 at the GitHub Pages root

This is normal for a p2 update site. Use the URL in Eclipse's software installation dialog.

### Port conflict

Switch to Random port mode or choose another fixed port. Restart the MCP server after changing the preference.

### Server does not start after Eclipse restart

Check whether **Stop Server** was selected previously. The plugin persists the user's desired Start/Stop state. Use **Start Server** to enable auto-start again.

### Build cannot resolve Eclipse dependencies

Run the Tycho command above and inspect eclipse-ci.target if dependency resolution fails. The target uses p2 repositories rather than machine-local Eclipse installations; verify repository changes through GitHub Actions.

## License

No license has been declared yet. Add a license before distributing the project beyond your own use.
