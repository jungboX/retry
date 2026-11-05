targetScope = 'resourceGroup'

@description('리소스 이름 접두사')
param appName string = 'cheongdo-work-demo'

@description('Azure 지역')
param location string = resourceGroup().location

@description('PostgreSQL 관리자 계정')
param databaseUsername string = 'retryadmin'

@secure()
@description('PostgreSQL 관리자 비밀번호')
param databasePassword string

@description('Azure Container Apps에 배포할 업무 API 이미지')
param apiImage string

@description('Azure Container Apps에 배포할 Agent 이미지')
param agentImage string

@description('Azure Container Apps에 배포할 MCP 이미지')
param mcpImage string

@secure()
@description('Agent Service가 Copilot SDK 인증에 사용할 서버 전용 토큰')
param copilotGithubToken string

resource postgresServer 'Microsoft.DBforPostgreSQL/flexibleServers@2022-12-01' = {
  name: '${appName}-db'
  location: location
  sku: {
    name: 'Standard_B1ms'
    tier: 'Burstable'
  }
  properties: {
    administratorLogin: databaseUsername
    administratorLoginPassword: databasePassword
    version: '16'
    storage: {
      storageSizeGB: 32
    }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
    network: {
      publicNetworkAccess: 'Enabled'
    }
  }
}

resource postgresDatabase 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2022-12-01' = {
  parent: postgresServer
  name: 'retry'
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}

resource postgresFirewall 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2022-12-01' = {
  parent: postgresServer
  name: 'AllowAzureServices'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

resource staticWebApp 'Microsoft.Web/staticSites@2023-01-01' = {
  name: '${appName}-web'
  location: location
  sku: {
    name: 'Free'
    tier: 'Free'
  }
  properties: {}
}

resource containerAppsEnvironment 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: '${appName}-env'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'none'
    }
  }
}

resource mcpApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: '${appName}-mcp'
  location: location
  properties: {
    managedEnvironmentId: containerAppsEnvironment.id
    configuration: {
      ingress: {
        external: false
        targetPort: 8082
        transport: 'auto'
      }
    }
    template: {
      containers: [
        {
          name: 'mcp-server'
          image: mcpImage
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: 1
      }
    }
  }
}

resource agentApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: '${appName}-agent'
  location: location
  properties: {
    managedEnvironmentId: containerAppsEnvironment.id
    configuration: {
      secrets: [
        {
          name: 'copilot-github-token'
          value: copilotGithubToken
        }
      ]
      ingress: {
        external: false
        targetPort: 8000
        transport: 'auto'
      }
    }
    template: {
      containers: [
        {
          name: 'agent-service'
          image: agentImage
          env: [
            {
              name: 'MCP_SERVER_URL'
              value: 'http://${mcpApp.properties.configuration.ingress.fqdn}'
            }
            {
              name: 'COPILOT_GITHUB_TOKEN'
              secretRef: 'copilot-github-token'
            }
          ]
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: 1
      }
    }
  }
}

resource apiApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: '${appName}-api'
  location: location
  properties: {
    managedEnvironmentId: containerAppsEnvironment.id
    configuration: {
      secrets: [
        {
          name: 'database-password'
          value: databasePassword
        }
      ]
      ingress: {
        external: true
        targetPort: 8081
        transport: 'auto'
      }
    }
    template: {
      containers: [
        {
          name: 'api-service'
          image: apiImage
          env: [
            {
              name: 'AGENT_SERVICE_URL'
              value: 'http://${agentApp.properties.configuration.ingress.fqdn}'
            }
            {
              name: 'CORS_ALLOWED_ORIGINS'
              value: 'https://${staticWebApp.properties.defaultHostname}'
            }
            {
              name: 'SPRING_DATASOURCE_URL'
              value: 'jdbc:postgresql://${postgresServer.properties.fullyQualifiedDomainName}:5432/retry?sslmode=require'
            }
            {
              name: 'SPRING_DATASOURCE_USERNAME'
              value: databaseUsername
            }
            {
              name: 'SPRING_DATASOURCE_PASSWORD'
              secretRef: 'database-password'
            }
          ]
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: 1
      }
    }
  }
  dependsOn: [
    postgresDatabase
    postgresFirewall
  ]
}

output webUrl string = 'https://${staticWebApp.properties.defaultHostname}'
output apiUrl string = 'https://${apiApp.properties.configuration.ingress.fqdn}'
output mcpInternalUrl string = 'http://${mcpApp.properties.configuration.ingress.fqdn}/mcp'
