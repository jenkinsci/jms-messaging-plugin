node('docker') {
    /* clean out the workspace just to be safe */
    deleteDir()

    /* Grab our source for this build */
    checkout scm

    String containerArgs = '-v /var/run/docker.sock:/var/run/docker.sock --shm-size 2g'
    stage('Test') {
        docker.image('jenkins/ath:acceptance-test-harness-1.69').inside(containerArgs) {
            sh """
                mvn -B clean install -DskipTests
                set-java.sh 8
                eval \$(vnc.sh)
                java -version

                run.sh firefox 2.176.1 -Dmaven.test.failure.ignore=true -DforkCount=1 -B
            """
        }
    }

    stage('Archive') {
        junit 'target/surefire-reports/**/*.xml'
        archiveArtifacts artifacts: 'target/**/jms-messaging.hpi', fingerprint: true
        archiveArtifacts artifacts: 'target/diagnostics/**', allowEmptyArchive: true
    }
}
