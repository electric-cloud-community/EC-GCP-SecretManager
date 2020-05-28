import com.cloudbees.flowpdf.Log
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.gax.core.CredentialsProvider
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.gax.core.GoogleCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.secretmanager.v1.*

import groovy.json.JsonSlurper

class SecretManagerWrapper {
    String project
    SecretManagerServiceClient client
    private Log log

    SecretManagerWrapper(String key, Log log) {
        assert key: "No key is provided"
        List<String> scopes = SecretManagerServiceSettings.getDefaultServiceScopes()
        GoogleCredentials credentials = GoogleCredentials
            .fromStream(new ByteArrayInputStream(key.getBytes('UTF-8')))
            .createScoped(scopes)

        CredentialsProvider provider = FixedCredentialsProvider.create(credentials)
        SecretManagerServiceSettings settings = SecretManagerServiceSettings
            .newBuilder()
            .setCredentialsProvider(provider)
            .build()
        client = SecretManagerServiceClient.create(settings)
        Map keyContent = new JsonSlurper().parseText(key)
        project = keyContent.project_id
        assert project
        this.project = project
        this.log = log
    }

    SecretVersion latestSecretVersion(String secretName) {
        SecretName parent = SecretName.of(project, secretName)
        List<SecretVersion> versions = client.listSecretVersions(parent).iterateAll().asList()
        return versions?.sort()?.last()
    }

    String retriveSecretVersion(String secretName, String version) {
        SecretVersionName name = SecretVersionName.of(project, secretName, version)
        return client.accessSecretVersion(name).getPayload().getData().toStringUtf8()
    }

    String retrieveSecretVersion(Map data) {
        String name = data.name
        assert name : "No name is provided"
        if (data.version) {
            return retriveSecretVersion(name, data.version)
        }
        else {
            def version = latestSecretVersion(name)
            return client.accessSecretVersion(version.getName()).getPayload().getData().toStringUtf8()
        }
    }


}
