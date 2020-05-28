$[/myProject/groovy/scripts/preamble.groovy.ignore]

GCPSecretManager plugin = new GCPSecretManager()
plugin.runStep('Expand Credentials', 'Expand Credentials', 'expandCredentials')