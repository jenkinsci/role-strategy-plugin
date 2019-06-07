// Builds a module using https://github.com/jenkins-infra/pipeline-library
buildPlugin(jenkinsVersions: [null, '2.150.2'])

node('highmem') {
    // TODO: use Lockable Resources plugin
    stage('benchmark') {
        List<String> mvnOptions = ['test', '-Dbenchmark']
        infra.runMaven(mvnOptions)
        archiveArtifacts artifacts: 'jmh-report.json'
    }
}
