node('docker') {
    /* clean out the workspace just to be safe */
    deleteDir()

    /* Grab our source for this build */
    checkout scm

    String containerArgs = '-v /var/run/docker.sock:/var/run/docker.sock --shm-size 2g'
    stage('Build') {
        def uid = sh(script: 'id -u', returnStdout: true).trim()
        def gid = sh(script: 'id -g', returnStdout: true).trim()

        def buildArgs = "--build-arg=uid=${uid} --build-arg=gid=${gid} src/test/resources/ath-container"
        retry(3) {
            docker.build('jenkins/ath', buildArgs)
        }
    }

    stage('Test') {
        docker.image('jenkins/ath').inside(containerArgs) {
            sh '''
                eval $(./vnc.sh 2> /dev/null)
                mvn -B clean install -DskipTests
                mvn -B test -Dmaven.test.failure.ignore=true -DElasticTime.factor=2 -Djenkins.version=2.107.3 -DforkCount=1 -B
            '''
        }
    }

    stage('Archive') {
        junit 'target/surefire-reports/**/*.xml'
        archiveArtifacts artifacts: 'target/**/jms-messaging.hpi', fingerprint: true
        archiveArtifacts artifacts: 'target/diagnostics/**', allowEmptyArchive: true
    }
}
