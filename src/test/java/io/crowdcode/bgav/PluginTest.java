package io.crowdcode.bgav;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.model.Model;
import org.junit.Test;
import java.io.File;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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
        MavenHandler mavenHandler = new MavenHandler(plugin.getLogs());
        mavenHandler.getModel(new File(UUID.randomUUID().toString()));
    }

    @Test
    public void testGetModel2() throws MojoExecutionException {
        Plugin plugin = new Plugin();
        MavenHandler mavenHandler = new MavenHandler(plugin.getLogs());
        mavenHandler.getModel(new File("pom.xml"));
    }

    @Test
    public void testGetGitRepo() throws MojoExecutionException {
        Plugin plugin = new Plugin();
        MavenHandler mavenHandler = new MavenHandler(plugin.getLogs());
        Model model = mavenHandler.getModel(new File("pom.xml"));
        Git git = plugin.getGitRepo(model);
        Assert.assertNotNull(git);
    }

    @Test
    public void testCheckForSnapshot() {
        Plugin plugin = new Plugin();
        Model model = new Model();
        model.setVersion("1.0.1-SNAPSHOT");
        assertTrue(plugin.checkForSnapshot(model));
        model.setVersion("1.0.1");
        assertTrue(!plugin.checkForSnapshot(model));
    }

    @Test
    public void testCheckForAllowedBranch() {
        Plugin plugin = new Plugin();
        assertTrue(plugin.checkForAllowedBranch("feature"));
        assertTrue(plugin.checkForAllowedBranch("bugfix"));
        assertTrue(plugin.checkForAllowedBranch("hotfix"));
        assertFalse(plugin.checkForAllowedBranch("master"));
        assertFalse(plugin.checkForAllowedBranch("develop"));
        assertFalse(plugin.checkForAllowedBranch("release"));
    }

    @Test
    public void testSetPomVersion() {
        Plugin plugin = new Plugin();
        assertEquals(plugin.setPomVersion("1.0.1-SNAPSHOT", "NCX-11"), "1.0.1-NCX-11-SNAPSHOT");
        assertEquals(plugin.setPomVersion("1.0.1-SNAPSHOT", "NCX-7"), "1.0.1-NCX-7-SNAPSHOT");
        assertEquals(plugin.setPomVersion("1.0.1", "NCX-11"), "1.0.1-NCX-11-SNAPSHOT");
        assertEquals(plugin.setPomVersion("1.0.1", "NCX-7"), "1.0.1-NCX-7-SNAPSHOT");
    }

    /*@Test
    public void testCheckBranch() throws MojoExecutionException {
        Plugin plugin = new Plugin();
        Model model = plugin.getModel(new File("pom.xml"));
        Git git = plugin.getGitRepo(model);
        Repository repo = git.getRepository();
        String commitId = plugin.getCommitId(git);
        String branch = plugin.checkBranchName(repo, commitId);
        System.out.println("commitid: " + commitId);
        System.out.println("branch:   " + branch);
        assertTrue(commitId, true);
    }*/
}
