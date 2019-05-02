ncx-maven-bgav-plugin

usage in POM, look at raw code not webview:



  <build>
    <plugins>
      <plugin>
        <groupId>io.crowdcode</groupId>
        <artifactId>bgav-maven-plugin</artifactId>
        <version>1.0-SNAPSHOT</version>
        <executions>
          <execution>
            <phase>compile</phase>
            <goals>
              <goal>bgav</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
