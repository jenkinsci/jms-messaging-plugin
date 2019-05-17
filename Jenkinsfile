node('docker') {
    /* clean out the workspace just to be safe */
    deleteDir()

    /* Grab our source for this build */
    checkout scm

    String containerArgs = '-v /var/run/docker.sock:/var/run/docker.sock --shm-size 2g'
    stage('Test') {
        docker.image('jenkins/ath:acceptance-test-harness-1.65').inside(containerArgs) {
            sh '''
                eval $(./vnc.sh 2> /dev/null)
                mvn clean install -DskipTests
                mvn test -Dmaven.test.failure.ignore=true -DElasticTime.factor=2 -Djenkins.version=2.107.3 -DforkCount=1 -B
            '''
        }
    }

    stage('Archive') {
        junit 'target/surefire-reports/**/*.xml'
        archiveArtifacts artifacts: 'target/**/jms-messaging.hpi', fingerprint: true
        archiveArtifacts artifacts: 'target/diagnostics/**', allowEmptyArchive: true
    }
}
