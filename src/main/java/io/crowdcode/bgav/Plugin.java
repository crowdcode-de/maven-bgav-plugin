package io.crowdcode.bgav;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author andreas
 */
@Mojo(name = "bgav")
public class Plugin extends AbstractMojo {

    @Parameter(property = "gituser")
    private String gituser;

    @Parameter(property = "gitpassword")
    private String gitpassword;

    @Parameter(property = "regex_ticket")
    private String regex_ticket;

    @Parameter(property = "regex_branch")
    private String regex_branch;

    @Parameter(property = "failOnMissingBranchId")
    private boolean failOnMissingBranchId = true;

    @Parameter(property = "branchName")
    private String branchName;

    @Parameter(property = "namespace")
    private String[] namespace;

    final Log log = getLog();

    private final String regexp = "(feature)/([A-Z0-9\\-])*-.*";
    private final String REGEX_BRANCH = "(feature|bugfix|hotfix)";
    private final String REGEX_TICKET = "(\\p{Upper}{1,}-\\d{1,})";

    /**
     * TODO: document me
     *
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        File pomfile = new File("pom.xml");
        Model model = getModel(pomfile);
        log.info("Project " + model);
        log.info("failOnMissingBranchId: " + failOnMissingBranchId);
        log.info("branchName: " + branchName);
        if ( (gituser == null || gituser.isEmpty()) || (gitpassword == null || gitpassword.isEmpty())) {
            log.info("no Git credentials set");
        } else {
            log.info("Git credentials set");
        }

        // 1. check for SNAPSHOT -> if not: abort
        if (!checkForSnapshot(model)) {
            throw new MojoExecutionException("project is not a SNAPSHOT");
        }
        // (POM) {Version}-SNAPSHOT
        // (POM) {Version}-{TicketID}-SNAPSHOT
        // 2. check for branch: MUST NOT be develop or master or release
        // (GIT) {feature|bugfix|hotfix}/{branchname}
        //       {branchname} --> {TicketID}-{Description}
        //                         NCX-7-foobar-gabba-gabba-hey
        //                         ^^^^^--Ticket format
        // (GIT) must not be develop, master, release

        // check for Git Repo -> @todo: autocloseable
        Git git = getGitRepo(model);
        if (git == null) {
            return;
        }

        checkStatus(git);

        Repository repo = git.getRepository();
        String commitID = getCommitId(git);
        String branch = checkBranchName(repo, commitID);
        if (branch == null) {
            throw new MojoExecutionException("could not get Git branch");
        } else if (branch.startsWith("feature")) {
            // NCX-14 check for feature branch
            String pomTicketID, ticketID;
            if (regex_ticket == null || regex_ticket.isEmpty()) {
                log.info("RegEx for ticket ID is empty, use default one");
                pomTicketID = getMatchFirst(model.getVersion(), REGEX_TICKET);
                ticketID = getMatchFirst(branch, REGEX_TICKET);
            } else {
                log.info("use provided RegEx for ticket ID");
                pomTicketID = getMatchFirst(model.getVersion(), regex_ticket);
                ticketID = getMatchFirst(branch, regex_ticket);
            }
            log.info("POM Version: " + pomTicketID);
            log.info("ticketID: " + ticketID);
            if (pomTicketID == null) {
                // NCX-16 write new verion to POM
                writeChangedPOM(model, git, ticketID, pomfile);
                commitAndPush(git, ticketID);
                if (failOnMissingBranchId) {
                    // NCX-26
                    throw new MojoExecutionException("build failed due to missing branch id and failOnMissingBranchId parameter.");
                }
            } else if (ticketID.equals(pomTicketID)) {
                // POM Version has TicketID
                log.info("Git branch ticket ID matches POM ticket ID ... done.");
            } else {
                // POM Version has TicketID
                throw new MojoExecutionException("mismatch Git branch ticket ID and POM branch version ticket ID");
            }
        } else if (checkForAllowedBranch(branch)) {
            throw new MojoExecutionException("not allowed branch: " + branch);
        } else {
            log.info("no Git feature branch ... done.");
        }
        repo.close();
        git.close();
    }

    /**
     * get Maven project model from POM
     *
     * @param pomfile
     * @return
     * @throws MojoExecutionException
     */
    Model getModel(File pomfile) throws MojoExecutionException {
        Model model = null;
        FileReader reader = null;
        MavenXpp3Reader mavenreader = new MavenXpp3Reader();
        try {
            reader = new FileReader(pomfile);
            model = mavenreader.read(reader);
            model.setPomFile(pomfile);
        } catch (IOException | XmlPullParserException ex) {
            getLog().error("Error: " + ex);
            throw new MojoExecutionException("could not read POM: " + ex);
        }
        return model;
    }

    /**
     * check for Git status abort if POM has changed
     *
     * @param git
     * @throws MojoExecutionException
     */
    void checkStatus(Git git) throws MojoExecutionException {
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
            getLog().error("Git error: " + ex);
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
    Git getGitRepo(Model model) throws MojoExecutionException {
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
     * check Git for SNAPSHOT
     *
     * @param model
     * @return true/false
     */
    Boolean checkForSnapshot(Model model) {
        return model.getVersion().contains("SNAPSHOT");
    }

    /**
     * check Git for allowed branch
     *
     * @param branch
     * @return true/false
     */
    Boolean checkForAllowedBranch(String branch) {
        String check = "";
        if (regex_branch == null || regex_branch.isEmpty()) {
            log.info("RegEx for branch is empty, use default one");
            check = getMatchFirst(branch, REGEX_BRANCH);
            return check == null ? false : !check.isEmpty();
        } else {
            log.info("use provided RegEx for branch");
            check = getMatchFirst(branch, regex_branch);
            return check == null ? false : !check.isEmpty();
        }
    }

    /**
     * get commit id
     *
     * @param git
     * @return commit id
     * @throws MojoExecutionException
     */
    String getCommitId(Git git) throws MojoExecutionException {
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
     * check branch name equals commitId --> then running on Jenkins
     *
     * @param repo
     * @param commitId
     * @return branch
     */
    String checkBranchName(Repository repo, String commitId) throws MojoExecutionException {
        String branch = "";
        try {
            branch = repo.getBranch();
            if (branch == null) {
                throw new MojoExecutionException("cannot get branch");
            } else if (branch.equals(commitId)) {
                // running on Jenkins
                log.info("running on Jenkins...");
                if (branchName == null || branchName.isEmpty()) {
                    throw new MojoExecutionException("Maven parameter 'branchName' is not set");
                }
                branch = branchName;
            } else {
                if (branchName == null || branchName.isEmpty()) {
                    branch = repo.getBranch();
                } else {
                    branch = branchName;
                }
            }
            log.info("Git branch: " + branch);
        } catch (IOException | MojoExecutionException ex) {
            log.error("cannot get branch: " + ex);
            throw new MojoExecutionException("cannot get branch");
        }
        return branch;
    }

    /**
     * write changed POM
     *
     * @param model
     * @param git
     * @param ticketID
     * @param pomfile
     * @throws MojoExecutionException
     */
    void writeChangedPOM(Model model, Git git, String ticketID, File pomfile) throws MojoExecutionException {
        // NCX-15
        model.setVersion(model.getVersion() + "-" + ticketID + "-SNAPSHOT");
        try (final FileOutputStream fileOutputStream = new FileOutputStream(pomfile)) {
            new MavenXpp3Writer().write(fileOutputStream, model);
        } catch (IOException ex) {
            log.error("IOException: " + ex);
            throw new MojoExecutionException("could not write POM: " + ex);
        }
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
     * RegEx auf Branch
     *
     * @param search
     * @param pat
     * @return
     */
    String getMatchFirst(String search, String pat) {
        String match = null;
        Pattern pattern = Pattern.compile(pat);
        Matcher matcher = pattern.matcher(search);
        while (matcher.find()) {
            match = matcher.group(1);
            log.info("Matcher: " + match);
        }
        return match;
    }
}
