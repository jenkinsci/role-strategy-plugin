// Builds a module using https://github.com/jenkins-infra/pipeline-library
buildPlugin(jenkinsVersions: [null, '2.150.2'])
node {
    stage('benchmark') {
        List<String> mvnOptions = ['test', '-Dbenchmark']
        infra.runMaven(mvnOptions)
    }
}
