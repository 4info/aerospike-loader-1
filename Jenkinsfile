#!groovy
node('jenkinsb-qa-slave-10') {
    try {
            stage("Cleanup Workspace"){
                deleteDir()
            }
            stage("Checkout Creative-Audit"){
                checkout scm
            }
           stage("Build Aerospike Loader"){
                sh 'java -version'
                sh 'mvn clean install -DskipTests'
            }

           stage("Archive Artifacts"){
                archiveArtifacts artifacts: '/home/jenkins/.m2/repository/com/aerospike/aerospike-load/2.3.6/aerospike-load-2.3.6-jar-with-dependencies.jar', fingerprint: true
                stash includes: 'target/creative-audit.jar,configuration.tar.gz', name: 'Build_Artifacts', useDefaultExcludes: false
                sh 'gsutil cp /home/jenkins/.m2/repository/com/aerospike/aerospike-load/2.3.6/aerospike-load-2.3.6-jar-with-dependencies.jar gs://rtb-qa-4info-jenkins/jenkins-04/aerospike-load/aerospike-load-2.3.6-jar-with-dependencies.jar' 
           }

        }
        catch (err) {
            emailext (
                to: 'ttran@cadent.tv',
                subject: "${env.JOB_NAME} (#${env.BUILD_NUMBER}) failed",
                body: "Build URL: ${env.BUILD_URL}\n\nChanges:${currentBuild.changeSets}",
                attachLog: true,
            )
            throw err
        }
}
