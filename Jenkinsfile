// Builds the plugin using https://github.com/jenkins-infra/pipeline-library
buildPlugin(configurations: [
  [ platform: "linux", jdk: "8", jenkins: null ],
  [ platform: "windows", jdk: "8", jenkins: null ],
  [ platform: "linux", jdk: "11", jenkins: null, javaLevel: 8 ]
])

//TODO(oleg_nenashev): Disabled due to out-of-memory issues on ci.jenkins.io agents. To be recovered once fixed
//runBenchmarks('jmh-report.json')
