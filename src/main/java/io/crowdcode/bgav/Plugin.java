package io.crowdcode.bgav;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Settings;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author andreas
 */
@Mojo(name = "bgav")
public class Plugin extends AbstractMojo {

    @Component
    private RepositorySystem repositorySystem;

    @Component
    private MavenProjectBuilder mavenProjectBuilder;

    @Parameter(property = "project.remoteArtifactRepositories")
    protected List<ArtifactRepository> remoteRepositories;

    @Parameter(property = "localRepository")
    protected ArtifactRepository localRepository;

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
     * flag for fail on Jenkins if missing branch id, deprecated since this flag is used for breaking the build
     * on altered pom.xml - @see failOnAlteredPom
     */
    @Deprecated
    @Parameter(property = "failOnMissingBranchId", alias = "fail_on_missing_branch_id")
    private boolean failOnMissingBranchId = false;

    /**
     * flag for fail on altered pom.xml
     */
    @Parameter(property = "failOnAlteredPom", alias = "fail_on_altered_pom")
    private boolean failOnAlteredPom = false;

    /**
     * setting branch id for Jenkinsfile
     */
    @Parameter(property = "branchName", alias = "branch_name")
    private String branchName;

    /**
     * setting for effected group ids walking through the dependencies
     */
    @Parameter(property = "namespace", required = true)
    private String[] namespace;


    /**
     * only debugging/testing purpose, suppress commit+push
     */
    @Parameter(property = "suppressCommit", defaultValue = "false")
    private boolean suppressCommit;

    /**
     * if later commits appear, the push can be suppressed
     */
    @Parameter(property = "suppressPush", defaultValue = "false")
    private boolean suppressPush;

    /**
     * pom.xml also can be a parameter
     */
    @Parameter(property = "pomFile", defaultValue = "pom.xml")
    private String pomFile;


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

    private final Map<String, Model> artifactMap = new HashMap<>();
    private File baseDir;

    /**
     * Maven plugin for adding ticket id to POM Version, if Git branch is
     * feature, bugfix or hotfix
     *
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        File pomfile = new File(pomFile);
        baseDir = pomfile.getAbsoluteFile().getParentFile();
        MavenHandler mavenHandler = new MavenHandler(log, suppressCommit, suppressPush, baseDir, repositorySystem, mavenProjectBuilder, remoteRepositories, localRepository, pomFile);
        Model model = mavenHandler.getModel(pomfile);

        log.info("Project " + model);
        log.info("failOnMissingBranchId: " + failOnMissingBranchId + " (DEPRECATED, please use failOnAlteredPom Parameter for future use)");
        log.info("failOnAlteredPom: " + failOnAlteredPom);
        log.info("branchName: " + branchName);
        log.info("getLocalRepository: " + settings.getLocalRepository());
        if ((gituser == null || gituser.isEmpty()) || (gitpassword == null || gitpassword.isEmpty())) {
            log.info("no Git credentials provided");
        } else {
            log.info("Git credentials provided");
        }

        // 1. check for SNAPSHOT -> if not: abort
        // 2019-08-09 makes no sense anymore, on BGAV -SNAPSHOT will be added, on non BGAV -SNAPSHOT will removed
//        if (!mavenHandler.checkForSnapshot(model)) {
//            throw new MojoExecutionException("project is not a SNAPSHOT");
//        }
        // (POM) {Version}-SNAPSHOT
        // (POM) {Version}-{TicketID}-SNAPSHOT
        // 2. check for branch: MUST NOT be develop or master or release
        // (GIT) {feature|bugfix|hotfix}/{branchname}
        //       {branchname} --> {TicketID}-{Description}
        //                         NCX-7-foobar-gabba-gabba-hey
        //                         ^^^^^--Ticket format
        // (GIT) must not be develop, master, release

        // check for Git Repo -> @todo: autocloseable
        GitHandler gitHandler = new GitHandler(log, gituser, gitpassword, suppressCommit, suppressPush, pomFile, baseDir);
        Git git = gitHandler.getGitLocalRepo(model);
        if (git == null) {
            return;
        }

        gitHandler.checkStatus(git);

        Repository repo = git.getRepository();
        String commitID = gitHandler.getCommitId(git);
        String branch = gitHandler.checkBranchName(repo, commitID, branchName);
        String pomTicketId, ticketId = null;

        boolean gottaPush = processPom(pomfile, mavenHandler, model, gitHandler, git, branch, false, "");
        if (gottaPush) {
            try {
                gitHandler.commitAndPush(git);
            } catch (GitAPIException e) {
                throw new MojoExecutionException("Git push failed! "+e.getMessage(),e);
            }
        }
        git.close();
    }

    private boolean processPom(File pomfile, MavenHandler mavenHandler, Model model, GitHandler gitHandler, Git git, String branch, boolean isSubmodel, String parentID) throws MojoExecutionException {
        String pomTicketId;
        String ticketId;
        log.info("Processing "+pomfile.getAbsolutePath());
        boolean gottaPush=false;
        if (branch == null) {
            throw new MojoExecutionException("could not get Git branch");
        } else {
            final String parentVersion = model.getParent() != null ? model.getParent().getVersion() : "";
            final String version = model.getVersion();
            final String nonNullVersion = version != null ? version : parentVersion ;

            final boolean parentMustBeRegarded= model.getParent() != null && model.getParent().getVersion() != null;
            final boolean versionMustBeRegarded= version != null;

            artifactMap.put(model.getId(), model);

            final List<String> modules = model.getModules();
            if (modules != null && !modules.isEmpty()) {
                for (String module:modules) {
                    File subPom = new File(pomfile.getAbsoluteFile().getParentFile().getAbsolutePath()+"/"+module+"/"+pomFile);
                    MavenHandler subHandler = new MavenHandler(log, suppressCommit, suppressPush, baseDir, repositorySystem, mavenProjectBuilder, remoteRepositories, localRepository, pomFile);
                    Model subModel = mavenHandler.getModel(subPom);
                    gottaPush |= processPom(subPom, subHandler,subModel, gitHandler, git, branch, true, model.getId());
                }
            }

            if (checkForAllowedBgavBranch(branch)) {
                log.debug("running BGAV branch");
                // NCX-14 check for feature branch
                log.debug("POM Version: " + nonNullVersion);
                final String regexTicket;

                if (regex_ticket == null || regex_ticket.isEmpty()) {
                    log.info("RegEx for ticket ID is empty, use default one: " + REGEX_TICKET);
                    regexTicket = REGEX_TICKET;
                } else {
                    log.debug("use provided RegEx for ticket ID: " + regex_ticket);
                    regexTicket = regex_ticket;
                }

                if (versionMustBeRegarded) {
                    pomTicketId = getMatchFirst(nonNullVersion, regexTicket);
                } else {
                    pomTicketId = getMatchFirst(parentVersion, regexTicket);;
                }
                ticketId = getMatchFirst(branch, regexTicket);

                log.debug("POM ticketId: " + pomTicketId);
                log.debug("ticketId: " + ticketId);
                if (versionMustBeRegarded) {
                    // NCX-16 write new verion to POM
                    if (new XMLHandler(log, suppressCommit, suppressPush, mavenHandler).setBgavOnVersion(pomfile, ticketId)) {
                        gitHandler.add(git, ticketId + " - BGAV - set correct branched version", pomfile);
                        gottaPush = true;
                        if (failOnMissingBranchId || failOnAlteredPom) {
                            // NCX-26
                            throw new MojoExecutionException("build failed due to missing branch id and failOnMissingBranchId parameter.");
                        } else {
                            log.debug("failOnMissingBranchId parameter is not set");
                        }
                    }
                } else if (ticketId.equals(pomTicketId)) {
                    // POM Version has TicketID
                    log.debug("Git branch ticket ID matches POM ticket ID");
                } else if (parentMustBeRegarded) {
                    log.debug("Version is fully inherited");
                } else {
                    // POM Version has TicketID
                    throw new MojoExecutionException("mismatch Git branch ticket ID and POM branch version ticket ID");
                }

                if (parentMustBeRegarded && artifactMap.containsKey(model.getParent().getId())) {
                    // HIER
                    if (new XMLHandler(log, suppressCommit, suppressPush, mavenHandler).setBgavOnParentVersion(pomfile, ticketId)) {
                        gitHandler.add(git, ticketId + " - BGAV - set correct branched version", pomfile);
                        gottaPush = true;
                    }
                }

                // NCX-36 check for affected GroupIds in dependencies
                try {
                    String artifacts = mavenHandler.checkforDependencies(pomfile, model, namespace, ticketId, gituser, gitpassword, settings.getLocalRepository());
                    if (!artifacts.isEmpty()) {
                        gitHandler.add(git, ticketId + " - BGAV - set correct branched version for " + (artifacts.endsWith(", ") ? artifacts.substring(0, artifacts.length() - 2) : artifacts),pomfile);
                        gottaPush=true;
                    }
                } catch (Exception ex) {
                    throw new MojoExecutionException("could not check for dependencies: " + ex);
                }
            } else if (checkForAllowedNonBgavBranch(branch)) {
                log.debug("running non BGAV branch");
                // remove BGAV from POM
                log.debug("POM Version: " + nonNullVersion);
                if (regex_ticket == null || regex_ticket.isEmpty()) {
                    log.debug("RegEx for ticket ID is empty, use default one: " + REGEX_TICKET);
                    ticketId = getMatchFirst(nonNullVersion, REGEX_TICKET);
                } else {
                    log.debug("use provided RegEx for ticket ID: " + regex_ticket);
                    ticketId = getMatchFirst(nonNullVersion, regex_ticket);
                }
                log.debug("branched nonNullVersion found: " + ticketId);
                String nonBgavVersion = mavenHandler.determineNonBgavPomVersion(nonNullVersion);
                if (versionMustBeRegarded && !nonBgavVersion.equals(version)) {
                    log.debug("none BGAV - set correct none branched version to: " + nonBgavVersion);
                    if (new XMLHandler(log, suppressCommit, suppressPush, mavenHandler).removeBgavFromVersion(pomfile, nonBgavVersion)) {
                        gitHandler.add(git, nonBgavVersion + " - none BGAV - set correct none branched version", pomfile);
                        gottaPush = true;
                    }
                    if (failOnMissingBranchId || failOnAlteredPom) {
                        throw new MojoExecutionException("build failed due to new none branched version, new version pushed and committed.");
                    }
                } else {
                    log.debug("no BGAV information inside POM Version.");
                }

                if (parentMustBeRegarded) {
                    if (artifactMap.containsKey(parentID)) {
                        if (new XMLHandler(log, suppressCommit, suppressPush, mavenHandler).removeBgavFromParentVersion(pomfile, nonBgavVersion)) {
                            gitHandler.add(git, nonBgavVersion + " - none BGAV - set correct none branched parent version", pomfile);
                            gottaPush = true;
                        }
                    }
                }

                // remove non BGAV versions from dependencies
                try {
                    String artifacts = mavenHandler.removeBgavFromPom(pomfile, model, namespace);
                    if (!artifacts.isEmpty()) {
                        log.debug("removed non BGAV versions from dependencies");
                        gitHandler.add(git, "removed BGAV from " + (artifacts.endsWith(", ") ? artifacts.substring(0, artifacts.length() - 2) : artifacts),pomfile);
                        gottaPush=true;
                    } else {
                        log.debug("non BGAV dependencies have to removed");
                    }
                } catch (MojoExecutionException ex) {
                    throw new MojoExecutionException("could not check for dependencies: " + ex);
                }
                // TODO: remove BGAV nonNullVersion from parent
            } else {
                log.warn("no Git known branch");
            }
        }

        Model alteredModel = mavenHandler.getModel(pomfile);
        artifactMap.put(model.getId(), alteredModel);

        return gottaPush;
    }

    /**
     * check Git for allowed branch
     *
     * @param branch
     * @return true/false
     */
    Boolean checkForAllowedBgavBranch(String branch) {
        String check = "";
        log.debug("check for BGAV branch");
        if (regex_bgav_branch == null || regex_bgav_branch.isEmpty()) {
            log.debug("RegEx for BGAV branch is empty, use default one: " + REGEX_BGAV_BRANCH);
            check = getMatchFirst(branch, REGEX_BGAV_BRANCH);
            return check != null && !check.isEmpty();
        } else {
            log.debug("use provided RegEx for BGAV branch: " + regex_bgav_branch);
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
        log.debug("check for non BGAV branch");
        if (regex_not_bgav_branch == null || regex_not_bgav_branch.isEmpty()) {
            log.debug("RegEx for non BGAV branch is empty, use default one: " + REGEX_NON_BGAV_BRANCH);
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
