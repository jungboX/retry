using Aspire.Hosting;

var builder = DistributedApplication.CreateBuilder(args);

builder.AddNpmApp("frontend", "../../frontend", "dev");
builder.AddExecutable("mcp-server", "java", ["-jar", "../../services/mcp-server/target/mcp-server-0.1.0.jar"]);
builder.AddExecutable("agent-service", "python", ["-m", "retry_agent.main"]);
builder.AddExecutable("api-service", "java", ["-jar", "../../services/api/target/retry-api-0.1.0.jar"]);

builder.Build().Run();
