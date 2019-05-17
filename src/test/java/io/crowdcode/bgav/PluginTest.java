package io.crowdcode.bgav;

import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.jgit.api.Git;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.UUID;

import static org.junit.Assert.*;

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
        GitHandler gitHandler = new GitHandler(plugin.getLog());
        Git git = gitHandler.getGitRepo(model);
        Assert.assertNotNull(git);
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
        MavenHandler mavenHandler = new MavenHandler(plugin.getLog());
        assertEquals(mavenHandler.setPomVersion("1.0.1-SNAPSHOT", "NCX-11"), "1.0.1-NCX-11-SNAPSHOT");
        assertEquals(mavenHandler.setPomVersion("1.0.1-SNAPSHOT", "NCX-7"), "1.0.1-NCX-7-SNAPSHOT");
        assertEquals(mavenHandler.setPomVersion("1.0.1", "NCX-11"), "1.0.1-NCX-11-SNAPSHOT");
        assertEquals(mavenHandler.setPomVersion("1.0.1", "NCX-7"), "1.0.1-NCX-7-SNAPSHOT");
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
