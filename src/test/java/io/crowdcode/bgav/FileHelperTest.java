package io.crowdcode.bgav;

import org.apache.maven.model.Dependency;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FileHelperTest {

    @Test
    public void testgetFilePathFromDependency() {
        Dependency dependency = setDependency("ncx-maven-bgav-plugin", "io.crowdcode", "0.2.2-SNAPSHOT");
        FileHelper fileHelper = new FileHelper( new Plugin().getLog());
        final String actual = System.getProperty("user.home") + "/.m2/repository/" +
                "io.crowdcode".replaceAll("[.]", "/") + "/" +
                "ncx-maven-bgav-plugin" + "/" + dependency.getVersion() + "/" +
                dependency.getArtifactId() + "-" + dependency.getVersion() + ".pom";
        final String expected = fileHelper.getPOMFilePathFromDependency(dependency).toString();
        assertEquals(expected.replace("\\","/"), actual.replace("\\","/"));
    }

    private Dependency setDependency(String artefactid, String groupid, String version) {
        Dependency dependency = new Dependency();
        dependency.setArtifactId("ncx-maven-bgav-plugin");
        dependency.setGroupId("io.crowdcode");
        dependency.setVersion("0.2.2-SNAPSHOT");
        return dependency;
    }
}
