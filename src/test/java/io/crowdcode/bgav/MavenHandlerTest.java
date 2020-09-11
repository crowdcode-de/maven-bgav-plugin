package io.crowdcode.bgav;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class MavenHandlerTest {

    @org.junit.Before
    public void setUp() throws Exception {
    }

    @Test
    public void testCheckForSnapshot() {
        Plugin plugin = new Plugin();
        MavenHandler mavenHandler = new MavenHandler(plugin.getLog(), false, false, new File("."), null,null,null, null);
        assertEquals(mavenHandler.determinePomVersion("1.1.1-SNAPSHOT", "NCX-41"), "1.1.1-NCX-41-SNAPSHOT");
        assertEquals(mavenHandler.determinePomVersion("1.2.1-SNAPSHOT", "NCX-7"), "1.2.1-NCX-7-SNAPSHOT");
        assertEquals(mavenHandler.determinePomVersion("1.1.2-SNAPSHOT", "NCX-416"), "1.1.2-NCX-416-SNAPSHOT");
        assertEquals(mavenHandler.determinePomVersion("0.2.2-SNAPSHOT", "NCX-416"), "0.2.2-NCX-416-SNAPSHOT");
    }
}
