using Aspire.Hosting;

var builder = DistributedApplication.CreateBuilder(args);
var repositoryRoot = Path.GetFullPath(Path.Combine(Directory.GetCurrentDirectory(), "..", ".."));
var mcpDirectory = Path.Combine(repositoryRoot, "services", "mcp-server");
var agentDirectory = Path.Combine(repositoryRoot, "services", "agent");
var apiDirectory = Path.Combine(repositoryRoot, "services", "api");
var postgres = builder.AddPostgres("postgres").WithDataVolume();
var database = postgres.AddDatabase("retry");

var mcp = builder.AddExecutable(
        "mcp-server",
        "mvn",
        ["spring-boot:run"],
        mcpDirectory)
    .WithHttpEndpoint(port: 8082, targetPort: 8082, name: "http");

var agent = builder.AddExecutable(
        "agent-service",
        "python",
        ["-m", "uvicorn", "retry_agent.main:app", "--host", "0.0.0.0", "--port", "8000"],
        agentDirectory)
    .WithEnvironment("PYTHONPATH", Path.Combine(agentDirectory, "src"))
    .WithEnvironment("MCP_SERVER_URL", mcp.GetEndpoint("http"))
    .WithHttpEndpoint(port: 8000, targetPort: 8000, name: "http")
    .WaitFor(mcp);

var api = builder.AddExecutable(
        "api-service",
        "mvn",
        ["spring-boot:run"],
        apiDirectory)
    .WithEnvironment("AGENT_SERVICE_URL", agent.GetEndpoint("http"))
    .WithReference(database)
    .WaitFor(database)
    .WithHttpEndpoint(port: 8081, targetPort: 8081, name: "http")
    .WaitFor(agent);

builder.AddNpmApp("frontend", Path.Combine(repositoryRoot, "frontend"), "dev")
    .WithEnvironment("VITE_API_BASE_URL", "")
    .WaitFor(api);

builder.Build().Run();
