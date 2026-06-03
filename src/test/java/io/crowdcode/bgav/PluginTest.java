package io.crowdcode.bgav;

import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.jgit.api.Git;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.UUID;

import static org.junit.Assert.*;

public class PluginTest {

    private boolean suppressPush=false;
    private File baseDir = new File(".");

    private final String pomFile="pom.xml";

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
        MavenHandler mavenHandler = new MavenHandler(plugin.getLogs(), false, suppressPush, baseDir, null, null, null, null, pomFile);
        mavenHandler.getModel(new File(UUID.randomUUID().toString()));
    }

    @Test
    public void testGetModel2() throws MojoExecutionException {
        Plugin plugin = new Plugin();
        MavenHandler mavenHandler = new MavenHandler(plugin.getLogs(), false, suppressPush, baseDir, null, null, null, null, pomFile);
        mavenHandler.getModel(new File("pom.xml"));
    }

    @Test
    public void testGetGitRepo() throws MojoExecutionException {
        Plugin plugin = new Plugin();
        MavenHandler mavenHandler = new MavenHandler(plugin.getLogs(), false, suppressPush, baseDir, null, null, null, null, pomFile);
        Model model = mavenHandler.getModel(new File(pomFile));
        GitHandler gitHandler = new GitHandler(false, suppressPush, plugin.getLog(), pomFile, baseDir);
        Git git = gitHandler.getGitLocalRepo(model);
        Assert.assertNotNull(git);
    }

    @Test
    public void testCheckForAllowedBranch() {
        Plugin plugin = new Plugin();
        assertTrue(plugin.checkForAllowedBgavBranch("feature"));
        assertTrue(plugin.checkForAllowedBgavBranch("bugfix"));
        assertTrue(plugin.checkForAllowedBgavBranch("hotfix"));
        assertFalse(plugin.checkForAllowedBgavBranch("master"));
        assertFalse(plugin.checkForAllowedBgavBranch("develop"));
        assertFalse(plugin.checkForAllowedBgavBranch("release"));
    }

    @Test
    public void testCheckForAllowedReleaseBranch() {
        Plugin plugin = new Plugin();
        assertFalse(plugin.checkForAllowedNonBgavBranch("feature"));
        assertFalse(plugin.checkForAllowedNonBgavBranch("bugfix"));
        assertFalse(plugin.checkForAllowedNonBgavBranch("hotfix"));
        assertTrue(plugin.checkForAllowedNonBgavBranch("master"));
        assertTrue(plugin.checkForAllowedNonBgavBranch("develop"));
        assertTrue(plugin.checkForAllowedNonBgavBranch("release"));
    }

    @Test
    public void testSetPomVersion() {
        Plugin plugin = new Plugin();
        MavenHandler mavenHandler = new MavenHandler(plugin.getLog(), false, suppressPush, baseDir, null, null, null, null, pomFile);
        assertEquals(mavenHandler.determinePomVersion("1.0.1-SNAPSHOT", "NCX-11"), "1.0.1-NCX-11-SNAPSHOT");
        assertEquals(mavenHandler.determinePomVersion("1.0.1-SNAPSHOT", "NCX-7"), "1.0.1-NCX-7-SNAPSHOT");
        assertEquals(mavenHandler.determinePomVersion("1.0.1-SNAPSHOT", "HSMRT-50"), "1.0.1-HSMRT-50-SNAPSHOT");
        assertEquals(mavenHandler.determinePomVersion("1.0.1", "NCX-11"), "1.0.1-NCX-11");
        assertEquals(mavenHandler.determinePomVersion("1.0.1", "NCX-7"), "1.0.1-NCX-7");
    }

    @Test
    public void testExtractTicketIdFromLowercaseBranch() {
        Plugin plugin = new Plugin();
        // lowercase branch names should match and return uppercase ticket ID
        String ticketId = plugin.getMatchFirst("feature/ncxrs-200WP02-domain-model", "(?i)(\\p{Alpha}{1,}-\\d{1,})");
        assertNotNull("Ticket ID should be extracted from lowercase branch name", ticketId);
        assertEquals("NCXRS-200", ticketId.toUpperCase());
    }

    @Test
    public void testExtractTicketIdFromUppercaseBranch() {
        Plugin plugin = new Plugin();
        // uppercase branch names should still work
        String ticketId = plugin.getMatchFirst("feature/NCXRS-200WP02-domain-model", "(?i)(\\p{Alpha}{1,}-\\d{1,})");
        assertNotNull("Ticket ID should be extracted from uppercase branch name", ticketId);
        assertEquals("NCXRS-200", ticketId.toUpperCase());
    }

    @Test
    public void testRemovePomVerion() {
        Plugin plugin = new Plugin();
        MavenHandler mavenHandler = new MavenHandler(plugin.getLog(), false, suppressPush, baseDir, null, null, null, null, pomFile);
        assertEquals(mavenHandler.determineNonBgavPomVersion("1.0.1-NCX-11-SNAPSHOT"), "1.0.1-SNAPSHOT");
        assertEquals(mavenHandler.determineNonBgavPomVersion("1.0.1-HSMRT-50-SNAPSHOT"), "1.0.1-SNAPSHOT");
        assertEquals(mavenHandler.determineNonBgavPomVersion("1.0.1-NCX-11"), "1.0.1");
        assertEquals(mavenHandler.determineNonBgavPomVersion("1.0.1"), "1.0.1");
        assertEquals(mavenHandler.determineNonBgavPomVersion("1.0.1-SNAPSHOT"), "1.0.1-SNAPSHOT");
//        assertEquals(mavenHandler.setNonBgavPomVersion("1.0.1-RELEASE"), "1.0.1-RELEASE");
    }

    @Test
    public void testIsParentInNamespace_externalParent() {
        Plugin plugin = new Plugin();
        Model model = new Model();
        Parent parent = new Parent();
        parent.setGroupId("org.springframework.boot");
        parent.setArtifactId("spring-boot-starter-parent");
        parent.setVersion("3.1.0");
        model.setParent(parent);

        String[] namespace = {"example.com", "io.crowdcode"};
        assertFalse("Externer Parent (spring-boot-starter-parent) darf nicht als interner Namespace erkannt werden",
                plugin.isParentInNamespace(model, namespace));
    }

    @Test
    public void testIsParentInNamespace_internalParent() {
        Plugin plugin = new Plugin();
        Model model = new Model();
        Parent parent = new Parent();
        parent.setGroupId("example.com.example");
        parent.setArtifactId("example-parent");
        parent.setVersion("1.0.0-SNAPSHOT");
        model.setParent(parent);

        String[] namespace = {"example.com", "io.crowdcode"};
        assertTrue("Interner Parent muss als zum Namespace gehörig erkannt werden",
                plugin.isParentInNamespace(model, namespace));
    }

    @Test
    public void testIsParentInNamespace_noParent() {
        Plugin plugin = new Plugin();
        Model model = new Model();

        String[] namespace = {"example.com", "io.crowdcode"};
        assertFalse("Modell ohne Parent darf nicht als im Namespace erkannt werden",
                plugin.isParentInNamespace(model, namespace));
    }

    /*@Test
    public void testCheckBranch() throws MojoExecutionException {
        Plugin plugin = new Plugin();
        Model model = plugin.getModel(new File("pom.xml"));
        Git git = plugin.getGitLocalRepo(model);
        Repository repo = git.getRepository();
        String commitId = plugin.getCommitId(git);
        String branch = plugin.checkBranchName(repo, commitId);
        System.out.println("commitid: " + commitId);
        System.out.println("branch:   " + branch);
        assertTrue(commitId, true);
    }*/
}
