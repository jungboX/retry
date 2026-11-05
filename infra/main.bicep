targetScope = 'resourceGroup'

@description('앱 이름 접두사')
param appName string = 'retry-demo'

@description('Azure 지역')
param location string = resourceGroup().location

resource staticWebApp 'Microsoft.Web/staticSites@2023-01-01' = {
  name: '${appName}-web'
  location: location
  sku: {
    name: 'Free'
    tier: 'Free'
  }
  properties: {
    repositoryUrl: 'https://example.invalid/retry'
    branch: 'main'
    buildProperties: {
      appLocation: 'frontend'
      outputLocation: 'dist'
    }
  }
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

resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2023-10-01-preview' = {
  name: '${appName}-pg'
  location: location
  sku: {
    name: 'Standard_B2s'
    tier: 'Burstable'
  }
  properties: {
    administratorLogin: 'retryadmin'
    administratorLoginPassword: 'P@ssw0rd1234!'
    version: '16'
    storage: {
      storageSizeGB: 32
    }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
  }
}
