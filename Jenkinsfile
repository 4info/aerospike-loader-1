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
                env.JAVA_HOME="${tool 'Java_Jenkins_OpenJDK11'}"
                env.PATH="${env.JAVA_HOME}/bin:${env.PATH}"
                sh 'java -version'
                sh 'mvn clean install -DskipTests'
               sh 'cp /home/jenkins/.m2/repository/com/aerospike/aerospike-load/2.3.6/aerospike-load-2.3.6-jar-with-dependencies.jar .'
            }

           stage("Archive Artifacts"){
                archiveArtifacts artifacts: 'target/aerospike-load-2.3.6-jar-with-dependencies.jar', fingerprint: true
                stash includes: 'target/aerospike-load-2.3.6-jar-with-dependencies.jar', name: 'Build_Artifacts', useDefaultExcludes: false
                sh 'gsutil cp target/aerospike-load-2.3.6-jar-with-dependencies.jar gs://rtb-qa-4info-jenkins/jenkins-04/aerospike-load/aerospike-load-2.3.6-jar-with-dependencies.jar' 
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
