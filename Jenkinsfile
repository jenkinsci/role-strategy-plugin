// Builds the plugin using https://github.com/jenkins-infra/pipeline-library
buildPlugin(useContainerAgent: true, configurations: [
  [ platform: "linux", jdk: "8" ],
  [ platform: "windows", jdk: "8" ],
  [ platform: "linux", jdk: "11" ],
  [ platform: 'linux', jdk: '17', jenkins: '2.342' ],
])

//TODO(oleg_nenashev): Disabled due to out-of-memory issues on ci.jenkins.io agents. To be recovered once fixed
//runBenchmarks('jmh-report.json')
