node {
   def mvnHome
   stage('Preparation') { // for display purposes
      // Get some code from a GitHub repository
      git 'https://github.com/sdevineau/role-strategy-plugin.git'
      // Get the Maven tool.
      // ** NOTE: This 'M3' Maven tool must be configured
      // **       in the global configuration.           
      mvnHome = tool 'M3'
   }
   stage('Build') {
      // Run the maven build
      if (isUnix()) {
        sh "'${mvnHome}/bin/mvn' -DskipTests=true -Dmaven.test.failure.ignore clean package"
      } else {
         bat(/"${mvnHome}\bin\mvn" -DskipTests=true -Dmaven.test.failure.ignore clean package/)
      }
   }
   
    parallel "unit-tests": {
        stage('Results') {
        if (isUnix()) {
            sh "'${mvnHome}/bin/mvn' test -Dmaven.test.failure.ignore"
          } else {
             bat(/"${mvnHome}\bin\mvn" test -Dmaven.test.failure.ignore/)
          }
        junit '**/target/surefire-reports/TEST-*.xml'
        archive 'target/*.jar'
       }
    }, "integration-tests": {
        withEnv(['jenkins_ist_url=http://localhost:8082', 'jenkins_ist_filepath=/Users/simondevineau/Workspace/Jenkins-ist']) {
           stage('Deploy in  IST') {
                  if (isUnix()) {
                     sh "cp target/*.hpi ${jenkins_ist_filepath}/workspace/plugins"
                     sh "curl -X POST ${jenkins_ist_url}/safeRestart --user admin:a242649cb654484a8809f772a0bcdf3b"
                  } else {
                     bat("echo 'I am lazy'")
                  } 
              }
        }
    },
    failFast: false
  
}
