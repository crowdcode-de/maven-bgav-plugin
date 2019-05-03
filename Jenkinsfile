pipeline {
    agent { label 'jenkins-jdk11' }
    stages {
        stage('Build') {
           steps {  mvn("clean install -DskipTests=true") }
        }
        stage('Unit tests') {
           steps { mvn("test -P-checks,test-coverage -Dskip.unit.tests=false -Dskip.integration.tests=true") }
        }
        stage('Integration tests') {
           steps { mvn("verify -P-checks,test-coverage -Dskip.unit.tests=true -Dskip.integration.tests=false") }
        }
        stage('Deploy') {
           steps { mvn("deploy -P-checks -DskipTests=true ") }
        }
    }
}
def mvn(param) {
  withMaven(
      mavenOpts: '-Xmx1536m -Xms512m',
      maven: 'maven-3.6.0') {
    sh "mvn -U -B -e ${param}"
  }
}