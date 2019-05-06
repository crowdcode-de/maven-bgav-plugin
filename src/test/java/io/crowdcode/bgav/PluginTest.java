package io.crowdcode.bgav;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.model.Model;
import org.junit.Test;
import java.io.File;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.junit.Assert;

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

    @Test
    public void testGetGitRepo() throws MojoExecutionException {
        Plugin plugin = new Plugin();
        Model model = plugin.getModel(new File("pom.xml"));
        Git git = plugin.getGitRepo(model);
        Assert.assertNotNull(git);
    }
}