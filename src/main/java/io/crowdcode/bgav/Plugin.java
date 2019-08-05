package io.crowdcode.bgav;

import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Settings;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author andreas
 */
@Mojo(name = "bgav")
public class Plugin extends AbstractMojo {

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

    /**
     * RegEx for getting the ticket id
     */
    @Parameter(property = "regex_ticket")
    private String regex_ticket;

    /**
     * RegEx for setting BGAV branch
     */
    @Parameter(property = "regex_bgav_branch")
    private String regex_bgav_branch;

    /**
     * RegEx for setting non BGAV branch
     */
    @Parameter(property = "regex_non_bgav_branch")
    private String regex_not_bgav_branch;

    /**
     * flag for fail on Jenkins if missing branch id
     */
    @Parameter(property = "failOnMissingBranchId")
    private boolean failOnMissingBranchId = true;

    /**
     * setting branch id for Jenkinsfile
     */
    @Parameter(property = "branchName")
    private String branchName;

    /**
     * setting for effected group ids walking through the dependencies
     */
    @Parameter(property = "namespace")
    private String[] namespace;

    final Log log = getLog();

    private final String regexp = "(feature)/([A-Z0-9\\-])*-.*";

    /**
     * default RegEx for BGAV
     */
    private final String REGEX_BGAV_BRANCH = "(feature|bugfix|hotfix)";

    /**
     * default RegEx for non BGAV branch
     */
    private final String REGEX_NON_BGAV_BRANCH = "(develop|master|release)";

    /**
     * default RegEx for ticket id
     */
    private final String REGEX_TICKET = "(\\p{Upper}{1,}-\\d{1,})";

    @Parameter( defaultValue = "${settings}", readonly = true )
    private Settings settings;

    /**
     * Maven plugin for adding ticket id to POM Version, if Git branch is
     * feature, bugfix or hotfix
     *
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        File pomfile = new File("pom.xml");
        MavenHandler mavenHandler = new MavenHandler(log);
        Model model = mavenHandler.getModel(pomfile);
        log.info("Project " + model);
        log.info("failOnMissingBranchId: " + failOnMissingBranchId);
        log.info("branchName: " + branchName);
        log.info("getLocalRepository: " + settings.getLocalRepository());
        if ((gituser == null || gituser.isEmpty()) || (gitpassword == null || gitpassword.isEmpty())) {
            log.info("no Git credentials provided");
        } else {
            log.info("Git credentials provided");
        }

        // 1. check for SNAPSHOT -> if not: abort
        if (!mavenHandler.checkForSnapshot(model)) {
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
        GitHandler gitHandler = new GitHandler(log, gituser, gitpassword);
        Git git = gitHandler.getGitLocalRepo(model);
        if (git == null) {
            return;
        }

        gitHandler.checkStatus(git);

        Repository repo = git.getRepository();
        String commitID = gitHandler.getCommitId(git);
        String branch = gitHandler.checkBranchName(repo, commitID, branchName);
        String pomTicketId, ticketId = null;
        if (branch == null) {
            throw new MojoExecutionException("could not get Git branch");
        } else if (checkForAllowedBgavBranch(branch)) {
            log.info("running BGAV branch");
            // NCX-14 check for feature branch
            log.info("POM Version: " + model.getVersion());
            if (regex_ticket == null || regex_ticket.isEmpty()) {
                log.info("RegEx for ticket ID is empty, use default one: " + REGEX_TICKET);
                pomTicketId = getMatchFirst(model.getVersion(), REGEX_TICKET);
                ticketId = getMatchFirst(branch, REGEX_TICKET);
            } else {
                log.info("use provided RegEx for ticket ID: " + regex_ticket);
                pomTicketId = getMatchFirst(model.getVersion(), regex_ticket);
                ticketId = getMatchFirst(branch, regex_ticket);
            }
            log.info("POM ticketId: " + pomTicketId);
            log.info("ticketId: " + ticketId);
            if (pomTicketId == null) {
                // NCX-16 write new verion to POM
                new XMLHandler(log).writeChangedPomWithXPath(pomfile, ticketId);
                gitHandler.commitAndPush(git, ticketId + " - BGAV - set correct branched version");
                if (failOnMissingBranchId) {
                    // NCX-26
                    throw new MojoExecutionException("build failed due to missing branch id and failOnMissingBranchId parameter.");
                }
            } else if (ticketId.equals(pomTicketId)) {
                // POM Version has TicketID
                log.info("Git branch ticket ID matches POM ticket ID");
            } else {
                // POM Version has TicketID
                throw new MojoExecutionException("mismatch Git branch ticket ID and POM branch version ticket ID");
            }
            // NCX-36 check for affected GroupIds in dependencies
            try {
                if (mavenHandler.checkforDependencies(pomfile, model, namespace, ticketId, gituser, gitpassword, settings.getLocalRepository())) {
                    gitHandler.commitAndPush(git, ticketId + " - BGAV - set correct branched version for " + mavenHandler.getArtefacts());
                }
            } catch (Exception ex) {
                throw new MojoExecutionException("could not check for dependencies: " + ex);
            }
        } else if (checkForAllowedNonBgavBranch(branch)) {
            log.info("running non BGAV branch");
            // remove BGAV from POM
            log.info("POM Version: " + model.getVersion()); 
            if (regex_ticket == null || regex_ticket.isEmpty()) {
                log.info("RegEx for ticket ID is empty, use default one: " + REGEX_TICKET);
                ticketId = getMatchFirst(model.getVersion(), REGEX_TICKET);
            } else {
                log.info("use provided RegEx for ticket ID: " + regex_ticket);
                ticketId = getMatchFirst(model.getVersion(), regex_ticket);
            }
            log.info("branched version found: " + ticketId);
            String nonBgavVersion = mavenHandler.setNonBgavPomVersion(model.getVersion());
            if (!nonBgavVersion.equals(model.getVersion())) {
                log.info("none BGAV - set correct none branched version to: " + nonBgavVersion);
                new XMLHandler(log).writeChangedPomWithXPath(pomfile, nonBgavVersion);
//                gitHandler.commitAndPush(git, nonBgavVersion + " - none BGAV - set correct none branched version");
                log.info("none BGAV - set correct none branched version to: " + nonBgavVersion);
//                throw new MojoExecutionException("build failed due to new none branched version, new version pushed and committed.");
            } else {
                log.info("no BGAV information inside POM Version.");
            }
            // remove dependencies
            /*try {
                if (mavenHandler.removeBgavFromPom(pomfile, model, namespace)) {
                    log.info("removed somethings from BGAV ....");
                    gitHandler.commitAndPush(git, "removed BGAV from " + mavenHandler.getArtefacts());
                } else {
                    log.info("none BGAV to have to removed");
                }
            } catch (MojoExecutionException ex) {
                throw new MojoExecutionException("could not check for dependencies: " + ex);
            }*/
        } else {
            log.info("no Git known branch");
            git.close();
            return;
        }
        git.close();
    }

    /**
     * check Git for allowed branch
     *
     * @param branch
     * @return true/false
     */
    Boolean checkForAllowedBgavBranch(String branch) {
        String check = "";
        log.info("check for BGAV branch");
        if (regex_bgav_branch == null || regex_bgav_branch.isEmpty()) {
            log.info("RegEx for BGAV branch is empty, use default one: " + REGEX_BGAV_BRANCH);
            check = getMatchFirst(branch, REGEX_BGAV_BRANCH);
            return check != null && !check.isEmpty();
        } else {
            log.info("use provided RegEx for BGAV branch: " + regex_bgav_branch);
            check = getMatchFirst(branch, regex_bgav_branch);
            return check != null && !check.isEmpty();
        }
    }

    /**
     * check Git for allowed deploy branch
     *
     * @param branch
     * @return true/false
     */
    Boolean checkForAllowedNonBgavBranch(String branch) {
        String check = "";
        log.info("check for non BGAV branch");
        if (regex_not_bgav_branch == null || regex_not_bgav_branch.isEmpty()) {
            log.info("RegEx for non BGAV branch is empty, use default one: " + REGEX_NON_BGAV_BRANCH);
            check = getMatchFirst(branch, REGEX_NON_BGAV_BRANCH);
            return check != null && !check.isEmpty();
        } else {
            log.info("use provided RegEx for non BGAV branch: " + regex_not_bgav_branch);
            check = getMatchFirst(branch, regex_not_bgav_branch);
            return check != null && !check.isEmpty();
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
//            log.info("Matcher: " + match);
        }
        return match;
    }

    public Log getLogs() {
        return log;
    }
}
