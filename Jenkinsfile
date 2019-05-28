// Builds a module using https://github.com/jenkins-infra/pipeline-library
buildPlugin(jenkinsVersions: [null, '2.150.2'])

// TODO(oleg_nenashev): Review which labels we need to set for performance tests on ci.jenkins.io, reenable tests afterwards 
// See https://issues.jenkins-ci.org/browse/JENKINS-57425
// node {
//    stage('benchmark') {
//        List<String> mvnOptions = ['test', '-Dbenchmark']
//        infra.runMaven(mvnOptions)
//    }
//}
