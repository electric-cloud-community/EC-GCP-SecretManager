import com.cloudbees.flowpdf.*
import groovy.json.JsonSlurper

/**
 * GCPSecretManager
 */
class GCPSecretManager extends FlowPlugin {

    @Override
    Map<String, Object> pluginInfo() {
        return [
            pluginName         : '@PLUGIN_KEY@',
            pluginVersion      : '@PLUGIN_VERSION@',
            configFields       : ['config'],
            configLocations    : ['ec_plugin_cfgs'],
            defaultConfigValues: [:]
        ]
    }

    @Lazy
    SecretManagerWrapper wrapper = {
        Context context = getContext()
        String key = context.getConfigValues().getRequiredCredential("credential")?.secretValue
        assert key: "No key found in the credential"
        SecretManagerWrapper wrapper = new SecretManagerWrapper(key, log)
        return wrapper
    }()


    /**
     * expandCredentials - Expand Credentials/Expand Credentials
     * Add your code into this method and it will be called when the step runs
     * @param config (required: true)
     * @param sourceFile (required: )
     * @param source (required: )
     * @param targetFile (required: )

     */
    def expandCredentials(StepParameters p, StepResult sr) {
        String tmpl
        if (p.asMap.source) {
            tmpl = p.asMap.source
        }
        else if (p.asMap.sourceFile) {
            File sourceFile = new File(p.asMap.sourceFile)
            if (!sourceFile.exists()) {
                throw new RuntimeException("The fild $sourceFile.absolutePath does not exist")
            }
            tmpl = sourceFile.text
        }
        else {
            throw new RuntimeException("Either source or sourceFile must be provided")
        }

        assert tmpl : "The template is empty"

        Closure parseData
        JsonSlurper slurper = new JsonSlurper()
        parseData = { String d ->
            Map data
            try {
                data = slurper.parseText(d)
            } catch (Throwable e) {
                if (d =~ /=/) {
                    def parts = d.split(/,\s*/)
                    data = parts.collectEntries {
                        def (key, value) = it.split(/\s*=\s*/, 2)
                        [key, value]
                    }
                } else if (d =~ /\s+/) {
                    throw new RuntimeException("Failed to parse secret data $d: unrecognizable format of data. Please consult procedure documentation.")
                } else {
                    data = [name: d]
                }
            }
            return data
        }

        int count = 0
        tmpl = tmpl.replaceAll(/\(\(GCP-SECRET:\s*(.+?)\)\)/) { _, data ->
            Map secretData = parseData(data)
            log.info "Got secret data $data"
            String value = wrapper.retrieveSecretVersion(secretData)
            count ++
            String placeholder = value.size() < 20 ? '*' * value.size() : '*********....'
            log.info "Retrieved secret value ${placeholder}"
            return value
        }

        String fileName = p.asMap.targetFile
        assert fileName : "No file name is provided"
        File file = new File(fileName)
        if (file.exists() && p.asMap.overwriteTarget != 'true') {
            throw new RuntimeException("The file $file exists and overwrite is not set")
        }
        file.write(tmpl)
        log.info "Saved fetched values into file $file.absolutePath"
        sr.setJobStepSummary("Saved fetched values into file $file.absolutePath, fetched ${count} secrets")
    }


    // === step ends ===

}