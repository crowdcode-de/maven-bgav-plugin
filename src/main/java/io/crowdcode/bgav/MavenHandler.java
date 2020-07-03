package io.crowdcode.bgav;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DeploymentRepository;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.xpp3.SettingsXpp3Reader;
import org.apache.maven.shared.utils.ReaderFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.jgit.api.Git;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.crowdcode.bgav.PropertyHelper.*;

/**
 * @author andreas
 */
public class MavenHandler {

    private final Log log;
    private final boolean suppressCommit;
    private final boolean suppressPush;

    public MavenHandler(boolean suppressCommit, boolean suppressPush) {
        this.suppressCommit = suppressCommit;
        this.suppressPush = suppressPush;
        log = null;
    }

    public MavenHandler(Log log, boolean suppressCommit, boolean suppressPush) {
        this.log = log;
        this.suppressCommit = suppressCommit;
        this.suppressPush = suppressPush;
    }

    /**
     * get Maven project model from POM
     *
     * @param pomfile
     * @return
     * @throws MojoExecutionException
     */
    public Model getModel(File pomfile) throws MojoExecutionException {
        Model model = null;
        FileReader reader = null;
        MavenXpp3Reader mavenreader = new MavenXpp3Reader();
        try {
            reader = new FileReader(pomfile);
            model = mavenreader.read(reader);
            model.setPomFile(pomfile);
        } catch (IOException | XmlPullParserException ex) {
            log.error("Error: " + ex);
            throw new MojoExecutionException("could not read POM: " + ex);
        }
        return model;
    }

    /**
     * set new POM Version
     *
     * @param pomVersion
     * @param ticketID
     * @return new POM Version
     */
    public String determinePomVersion(String pomVersion, String ticketID) {
        // check is dependency has wrong ticketId
        String ticketId = extractTicketId(pomVersion);
        log.info("found ticketId in dependency: " + ticketId);
        if (ticketId != null && !ticketId.isEmpty()) { 
            pomVersion = setNonBgavPomVersion(pomVersion);
            log.info("removed wrong ticketId from POM Version: " + pomVersion);
        }
        String newPomVersion = "";
        if (pomVersion.contains(ticketID)) {
            return pomVersion;
        }
        if (pomVersion.contains("-SNAPSHOT")) {
            newPomVersion = pomVersion.substring(0, pomVersion.indexOf("-SNAPSHOT"));
            newPomVersion += "-" + ticketID + "-SNAPSHOT";
        } else {
            newPomVersion = pomVersion + "-" + ticketID + "-SNAPSHOT";
        }
        log.info("new POM Version: " + newPomVersion);
        return newPomVersion;
    }
    
    public String setNonBgavPomVersion(String pomVersion) {
        String newPomVersion = "";
        log.info("non BGAV POM Version: " + pomVersion);
        if (pomVersion.contains("-SNAPSHOT")) {
            newPomVersion = pomVersion.substring(0, pomVersion.indexOf("-"));
            newPomVersion += "-SNAPSHOT";
        } else {
            if (pomVersion.indexOf("-") > 0) {
                newPomVersion = pomVersion.substring(0, pomVersion.indexOf("-"));
            } else {
                newPomVersion = pomVersion;
            }
        }
        log.info("new non BGAV POM Version: " + newPomVersion);
        return newPomVersion;
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
     * checkForDependencies for affected GroupIDs
     *
     * @param model
     * @param groupIds
     * @throws org.apache.maven.plugin.MojoExecutionException
     */
    public String checkforDependencies(File pomfile, Model model, String[] groupIds, String ticketId, String gituser, String gitpassword, String localRepositoryPath) throws MojoExecutionException, Exception {
        if (groupIds == null) {
            log.info("no group id(s) defined ... finished.");
            return "";
        }
        log.info("checking dependencies for affected group id(s)...");
        final DistributionManagement distributionManagement = model.getDistributionManagement();

        if (distributionManagement == null || distributionManagement.getSnapshotRepository() == null) {
            log.warn("============================== MISSING DISTRIBUTION MANAGEMENT! ==============================");
            log.warn("========= Distribution Management is not properly configured! Skipping dependencies! =========");
            log.warn("============================== MISSING DISTRIBUTION MANAGEMENT! ==============================");
            return "";
        }

        DeploymentRepository deploymentRepository = distributionManagement.getSnapshotRepository();
        log.info("using deployment repository: " + deploymentRepository + " with URL: " + deploymentRepository.getUrl());
        List<Dependency> dependencyListmodel = model.getDependencies();
        String artifact = "";
        for (Dependency dependency : dependencyListmodel) {
            for (String groupid : groupIds) {
                if (dependency.getGroupId().contains(groupid)) {
                    String nativeVersion = dependency.getVersion();
                    log.info("affected dependency found: " + dependency + " with " + nativeVersion);
                    // @todo: check if branched version of dep exists
                    // ->> get POM from dependency --> Git --> SCM --> getDatas
                    Model dependencyModel = null;
                    try {
                        dependencyModel = getSCMfromPOM(model, dependency, localRepositoryPath);
                    } catch (MojoExecutionException e) {
                        log.warn("could not get POM file: " + e);
                        return artifact;
                    }
                    // --> get POM from SCM from project POM file
                    // get Git Project URI

                    if (dependencyModel.getScm() != null) {
                        String dependencyScmUrl = dependencyModel.getScm().getUrl();
                        String artifactId = dependency.getArtifactId();
                        if (dependencyScmUrl == null || dependencyScmUrl.isEmpty()) {
                            if (!dependencyModel.getScm().getConnection().isEmpty()) {
                                log.info("Dependency SCM entries found");
                            }
                            log.warn("no SCM URL for affected dependency found, please add <url></url> tag to " +
                                    artifactId + "/" + nativeVersion + " POM file - skipping");
                        } else {
                            log.info("Dependency SCM URL found: " + dependencyScmUrl);
                            if (checkoutFromDependencyRepository(dependency, dependencyScmUrl, gituser, gitpassword, ticketId)) {
                                //@todo: commit and push changes --> throw an error --> Jenkins build will start again, or trigger the build manual again
                                if (!isPlaceholder(nativeVersion)) {
                                    log.info("want to change: " + nativeVersion + " -- " + ticketId);
                                    String newVersion = determinePomVersion(nativeVersion, ticketId);
                                    if (nativeVersion.contains(ticketId)) {
                                        log.info("POM contains ticketId - do nothing");
                                    } else {
                                        dependency.setVersion(determinePomVersion(nativeVersion, ticketId));
                                        artifact += artifactId + ", ";
                                        log.info("changed dep: " + dependency);
                                        //                                log.info("POM FILE: " + model.getPomFile());
                                        new XMLHandler(log, suppressCommit, suppressPush).alterDependency(pomfile, artifactId, newVersion);
                                    }
                                } else {
                                    String resolvedVersion = resolveProperty(model, nativeVersion);
                                    log.info("want to change placeholder: " + nativeVersion + " (" + resolvedVersion + ") -- " + ticketId);
                                    if (resolvedVersion.contains(ticketId)) {
                                        log.info("POM contains ticketId - do nothing");
                                    } else {
                                        String newVersion = determinePomVersion(resolvedVersion, ticketId);
                                        setProperty(model, nativeVersion, newVersion);
                                        artifact += artifactId + ", ";
                                        log.info("changed dep: " + dependency);
                                        //                                log.info("POM FILE: " + model.getPomFile());
                                        new XMLHandler(log, suppressCommit, suppressPush).alterProperty(pomfile, unkey(nativeVersion), newVersion);
                                    }
                                }
                            }
                        }
                    } else  {
                        log.info(dependency.getGroupId()+":"+dependency.getArtifactId()+":"+dependency.getVersion()+" has no SCM configured");
                    }
                }
            }
        }
        return artifact;
    }

    /**
     * remove BGAV from dependencies
     *
     * @param pomfile
     * @param model
     * @param groupIds
     * @return
     */
    public String removeBgavFromPom(File pomfile, Model model, String[] groupIds) {
        if (groupIds == null) {
            log.info("no group id(s) defined ... finished.");
            return "";
        }
        log.info("checking dependencies for affected group id(s)...");
        String artifact = "";
        List<Dependency> dependencyListmodel = model.getDependencies();
        for (Dependency dependency : dependencyListmodel) {
            for (String groupid : groupIds) {
                if (dependency.getGroupId().contains(groupid)) {
                    String version = dependency.getVersion();
                    log.info("affected dependency found: " + dependency + " with " + version);
                    // @todo: check if branched version of dep exists
                    // ->> get POM from dependency --> Git --> SCM --> getDatas

                    if (!isPlaceholder(version)) {
                        String ticketId = extractTicketId(version);
                        if (ticketId != null && !ticketId.isEmpty()) {
                            log.info("dependency contains ticketId - remove it: " + ticketId);
                            String newPomDepVersion = setNonBgavPomVersion(version);
                            dependency.setVersion(newPomDepVersion);
                            artifact += dependency.getArtifactId() + ", ";
                            try {
                                new XMLHandler(log, suppressCommit, suppressPush).alterDependency(pomfile, dependency.getArtifactId(), newPomDepVersion);
                            } catch (MojoExecutionException ex) {
                                log.warn("could not write POM");
                            }
                        } else {
                            log.info("dependency has no BGAV version");
                        }
                    } else {
                        String property = resolveProperty(model, version);
                        String ticketId = extractTicketId(property);
                        if (ticketId != null && !ticketId.isEmpty()) {
                            log.info("property " + version + " contains ticketId - remove it: " + ticketId);
                            String newVersion = setNonBgavPomVersion(property);
                            Object dummy = setProperty(model, version, newVersion);
                            artifact += dependency.getArtifactId() + ", ";
                            try {
                                new XMLHandler(log, suppressCommit, suppressPush).alterProperty(pomfile, unkey(version), newVersion);
                            } catch (MojoExecutionException ex) {
                                log.warn("could not write POM");
                            }
                        } else {
                            log.info("dependency has no BGAV version");
                        }
                    }
                }
            }
        }
        return artifact;
    }

    private String extractTicketId(String version) {
        return getMatchFirst(version, "(\\p{Upper}{1,}-\\d{1,})");
    }


    /**
     * get local POM File to get POM-Model from Dependency
     *
     * <p>Get the local filepath to the POM file of Dependency </p>
     *
     * @param dependency
     * @return Model
     * @throws MojoExecutionException
     */
    private Model getSCMfromPOM(Model model, Dependency dependency, String localRepositoryPath) throws MojoExecutionException {
        File pomfile = new FileHelper(log).getPOMFilePathFromDependency(model, dependency, localRepositoryPath);
        log.info("POM File from " + dependency.getArtifactId() + ": " + pomfile);
        return getModel(pomfile);
    }

    /**
     * check for affected branch
     *
     * @param dependency
     * @param dependencyScmUrl
     * @param gituser
     * @param gitpassword
     * @param ticketId
     * @return branchFound
     * @throws MojoExecutionException
     */
    private Boolean checkoutFromDependencyRepository(Dependency dependency, String dependencyScmUrl, String gituser, String gitpassword, String ticketId) throws MojoExecutionException, IOException {
        GitHandler gitHandler = new GitHandler(log, gituser, gitpassword, suppressCommit, suppressPush);

        // setup local temporary Directory for Git checkout
        FileHelper fileHelper = new FileHelper(log);
        File localDirectory = fileHelper.createTempGitCheckoutDirectory(dependency.getArtifactId());

        // clone Repo
        Git gitDependency = gitHandler.cloneGitRemoteRepo(dependencyScmUrl, localDirectory);
        String[] branches = gitHandler.getBranchesFromDependency(gitDependency);
        Boolean branchFound = check(branches, ticketId);
        gitDependency.close();
        // delete local Repository
        fileHelper.deleteTempGitCheckoutDirectory(localDirectory);
        return branchFound;
    }

    /**
     * check if branches matched ticketid
     *
     * @param branches
     * @param ticketId
     * @return checkForTicketId
     */
    private Boolean check(String[] branches, String ticketId) throws MojoExecutionException {
        // check for affected groudids
        Boolean checkForTicketId = false;
        for (String branch : branches) {
//            log.info("branch: " + branch + " ticketID: " + ticketId);
            if ( branch.contains(ticketId)) {
                checkForTicketId = true;
                log.info("this is our branched version: " + branch + " for ticketID: " + ticketId);
            } else {
                log.info("branch: " + branch + " - does not match our ticket " + ticketId);
            }
        }
        if (!checkForTicketId) {
//            log.info("there are no matches to our branched version - finished");
            throw new MojoExecutionException("there are no matches to our branched version");
        }
        return checkForTicketId;
    }

    /**
     * get Maven settings.xml
     *
     * @return settings
     */
    private Settings getSettings() {
        log.info("reading .m2/settings.xml for server credentials");
        Settings settings = null;
        try (Reader reader = ReaderFactory.newXmlReader(new File(System.getProperty("user.home") + "/.m2/settings.xml"))) {
            SettingsXpp3Reader modelReader = new SettingsXpp3Reader();
            settings = modelReader.read(reader);
        } catch (Exception ex) {
            log.error("could not read .m2/settings.xml: " + ex);
        }
        return settings;
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
}
