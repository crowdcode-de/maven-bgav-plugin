package io.crowdcode.bgav;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 *
 * @author andreas
 */
public class GitHandler {

    /**
     * user for Git
     */
    @Parameter(property = "gituser")
    private String gituser;

    /**
     * password for Git user
     */
    @Parameter(property = "gitpassword")
    private String gitpassword;

    final Log log;

    public GitHandler() {
        log = null;
    }

    public GitHandler(Log log) {
        this.log = log;
    }

    /**
     * check for Git status abort if POM has changed
     *
     * @param git
     * @throws MojoExecutionException
     */
    public void checkStatus(Git git) throws MojoExecutionException {
        try {
            Status status = git.status().call();
//            log.info("Git status: " + status);
            log.info("hasUncommittedChanges: " + status.hasUncommittedChanges());
            Set<String> changes = status.getModified();
            log.info("Git changes: " + changes);
            /*for (String change : changes) {
                log.info("Git changes: " + change);
                if (change.equals("pom.xml")) {
                    throw new MojoExecutionException("POM is not commited... please commit before building application.");
                }
            }*/
            if (changes.contains("pom.xml")) {
                throw new MojoExecutionException("POM is not commited... please commit before building application.");
            }
        } catch (GitAPIException | NoWorkTreeException ex) {
            log.error("Git error: " + ex);
            throw new MojoExecutionException("Git status failed: " + ex);
        }
    }

    /**
     * check for Git Repository
     *
     * @param model
     * @return Git
     * @throws MojoExecutionException
     */
    public Git getGitRepo(Model model) throws MojoExecutionException {
        Git git = null;
        try {
            git = Git.open(model.getProjectDirectory());
            log.info(git.toString());
        } catch (RepositoryNotFoundException ex) {
            // no Git Repo -> done.
            log.info("there is no Git repo ... done.");
            return git;
        } catch (IOException ex) {
            throw new MojoExecutionException("could not read Git repo: " + ex);
        }
        return git;
    }

    /**
     * get commit id
     *
     * @param git
     * @return commit id
     * @throws MojoExecutionException
     */
    public String getCommitId(Git git) throws MojoExecutionException {
        String commitId;
        try {
            List<Ref> refs = git.branchList().setContains("HEAD").setListMode(ListBranchCommand.ListMode.ALL).call();
            Ref ref = refs.get(0);
            ObjectId objectId = ref.getObjectId();
            commitId = objectId == null ? "" : objectId.getName();
            log.info("commit id: " + commitId);
        } catch (GitAPIException e) {
            log.error("cannot get commit id: " + e);
            throw new MojoExecutionException("cannot get commit id");
        }
        return commitId;
    }

    /**
     * Git POM commit and push
     *
     * @param git
     * @param ticketID
     * @throws MojoExecutionException
     */
    void commitAndPush(Git git, String ticketID) throws MojoExecutionException {
        try {
            CredentialsProvider cp = new UsernamePasswordCredentialsProvider(gituser, gitpassword);
            git.add().addFilepattern("pom.xml").call();
            git.commit().setMessage(ticketID + " - BGAV - set correkt branched version").call();
            git.push().setCredentialsProvider(cp).call();
        } catch (GitAPIException ex) {
            log.error("GitAPIException: " + ex);
            throw new MojoExecutionException("Git commit/push failed: " + ex);
        }
    }

    /**
     * write changed POM
     *
     * @param model
     * @param git
     * @param ticketID
     * @param pomfile
     * @throws MojoExecutionException
     * @deprecated due to the error of removing packaging information from POM
     */
    public void writeChangedPOM(Model model, Git git, String ticketID, File pomfile) throws MojoExecutionException {
        // NCX-15
        model.setVersion(setPomVersion(model.getVersion(), ticketID));
        try (final FileOutputStream fileOutputStream = new FileOutputStream(pomfile)) {
            new MavenXpp3Writer().write(fileOutputStream, model);
        } catch (IOException ex) {
            log.error("IOException: " + ex);
            throw new MojoExecutionException("could not write POM: " + ex);
        }
    }

    /**
     * set new POM Version
     *
     * @param pomVersion
     * @param ticketID
     * @return new POM Version
     */
    public String setPomVersion(String pomVersion, String ticketID) {
        String newPomVersion = "";
        if (pomVersion.contains("-SNAPSHOT")) {
            newPomVersion = pomVersion.substring(0, pomVersion.indexOf("-SNAPSHOT"));
            newPomVersion += "-" + ticketID + "-SNAPSHOT";
        } else {
            newPomVersion = pomVersion + "-" + ticketID + "-SNAPSHOT";
        }
        log.info("new POM Version: " + newPomVersion);
        return newPomVersion;
    }

    public void setGituser(String gituser) {
        this.gituser = gituser;
    }

    public void setGitpassword(String gitpassword) {
        this.gitpassword = gitpassword;
    }
}
