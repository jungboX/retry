var builder = DistributedApplication.CreateBuilder(args);
var repositoryRoot = Path.GetFullPath(Path.Combine(builder.AppHostDirectory, "..", ".."));

builder.AddAzureContainerAppEnvironment("aca-env");

var copilotGithubToken = builder.AddParameter("copilotGithubToken", secret: true);

var postgres = builder.AddAzurePostgresFlexibleServer("postgres")
    .WithPasswordAuthentication()
    .RunAsContainer(container => container.WithDataVolume());
var database = postgres.AddDatabase("retry");

// Dockerfile 빌드 컨텍스트는 저장소 루트다.
var mcp = builder.AddDockerfile("mcp-server", repositoryRoot, "services/mcp-server/Dockerfile")
    .WithHttpEndpoint(port: 8082, targetPort: 8082, name: "http")
    .WithHttpHealthCheck("/actuator/health");

var agent = builder.AddDockerfile("agent-service", repositoryRoot, "services/agent/Dockerfile")
    .WithHttpEndpoint(port: 8000, targetPort: 8000, name: "http")
    .WithEnvironment("MCP_SERVER_URL", mcp.GetEndpoint("http"))
    .WithEnvironment("COPILOT_GITHUB_TOKEN", copilotGithubToken)
    .WithHttpHealthCheck("/health")
    .WaitFor(mcp);

var api = builder.AddDockerfile("api-service", repositoryRoot, "services/api/Dockerfile")
    .WithHttpEndpoint(port: 8081, targetPort: 8081, name: "http")
    .WithEnvironment("AGENT_SERVICE_URL", agent.GetEndpoint("http"))
    .WithReference(database)
    .WithHttpHealthCheck("/actuator/health")
    .WaitFor(database)
    .WaitFor(agent);

var web = builder.AddViteApp("web", Path.Combine(repositoryRoot, "frontend"));

// 외부에 공개하는 진입점은 게이트웨이뿐이고, 프런트엔드 정적 파일을 서빙하며 /api를 API로 프록시한다.
#pragma warning disable ASPIREJAVASCRIPT001
builder.AddYarp("gateway")
    .WithConfiguration(yarp => yarp.AddRoute("/api/{**catch-all}", api.GetEndpoint("http")))
    .WithExternalHttpEndpoints()
    .PublishWithStaticFiles(web);
#pragma warning restore ASPIREJAVASCRIPT001

builder.Build().Run();
