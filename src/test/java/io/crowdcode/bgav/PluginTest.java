package io.crowdcode.bgav;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import java.io.File;
import java.util.UUID;

import static org.junit.Assert.assertTrue;

public class PluginTest {

    @org.junit.Before
    public void setUp() throws Exception {
    }

    @Test
    public void execute() {
        assertTrue(true);
    }

    @Test(expected = MojoExecutionException.class)
    public void testGetModel() throws MojoExecutionException {
        Plugin plugin = new Plugin();
        plugin.getModel(new File(UUID.randomUUID().toString()));
    }

    @Test
    public void testGetModel2() throws MojoExecutionException {
        Plugin plugin = new Plugin();
        plugin.getModel(new File("pom.xml"));
    }

}