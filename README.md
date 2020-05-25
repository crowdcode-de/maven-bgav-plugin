# Maven BGAV Plugin


## Overview

Maven plugin for adding ticket id to POM Version, if Git branch is feature, bugfix or hotfix


## Requirements

JDK 8+


## Installation

For local build you have to add the Maven BGAV Plugin to your project POM.
If you want to use with Jenkins, just add it your Jenkinsfile, tested on Jenkins with multi pipelines.

*Note:* Jenkins Git plugin checks out with the last commit id, make sure Jenkins is using this command: git checkout ${env.BRANCH_NAME}

### for local build

```java
  <build>
    <plugins>
      <plugin>
        <groupId>io.crowdcode</groupId>
        <artifactId>bgav-maven-plugin</artifactId>
        <version>0.2.1</version>
        <executions>
          <execution>
            <phase>compile</phase>
            <goals>
              <goal>bgav</goal>
            </goals>
          </execution>
        </executions>
        <configuration>
          <regex_ticket>(\p{Upper}{1,}-\d{1,})</regex_ticket>
          <gituser>gituser</gituser>
          <gitpassword>gitpassword</gitpassword>
          <namespace>your.groupid.namespace</namespace>
        </configuration>
      </plugin>
    </plugins>
  </build>
```

### for Jenkins

```java
pipeline {
    agent { label 'jenkins-jdk11' }
    stages {
        stage('checkout') {
            steps {
                echo 'Pulling...' + env.BRANCH_NAME
                checkout scm
            }
        }
        stage('Prepare') {
           steps {
                withCredentials(
                        [usernamePassword(credentialsId: 'crowdcodeBitbucket',
                                usernameVariable: 'gitUser',
                                passwordVariable: 'gitPwd'
                        )]) {
                sh "git status"
                sh "git checkout ${env.BRANCH_NAME}"
                mvn("-DfailOnAlteredPom=false -DbranchName=${env.BRANCH_NAME} -Dgituser=${gituser} -D=gitpassword=${gitPwd} io.crowdcode:bgav-maven-plugin:0.2.1:bgav")
                }
           }
        }
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
      mavenSettingsConfig: 'crowdcode-maven-settings',
      maven: 'maven-3.6.0') {
    sh "mvn -U -B -e ${param}"
  }
}
```


## Usage

mvn -Dgituser=xxxx -D=gitpassword=yyyy io.crowdcode:bgav-maven-plugin:bgav

## Parameters

- gituser, Git user
- gitpassword, Git password
- regex_ticket, RegEx for getting the ticket id
- regex_branch, RegEx for getting the branch
- (DEPRECATED) failOnMissingBranchId, flag for fail on Jenkins if missing branch id, default true, set for Jenkins build to false, -DfailOnMissingBranchId=false
- failOnAlteredPom, flag to fail the build if the pom has been modified, commited and pushed by the plugin
- branchName, for setting branch name in Jenkins
- namespace, a list of groupIds which shall be regarded when the plugin is walking through the dependencies. Normally this 
  should be the groupIds of your own modules, e.g. com.yourcompany


## Author

Andreas Ernst, andreas.ernst@crowdcode.io

Marcus Nörder-Tuitje, marcus.noerder-tuitje@crowdcode.io