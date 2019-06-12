package io.crowdcode.bgav;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DeploymentRepository;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.xpp3.SettingsXpp3Reader;
import org.apache.maven.shared.utils.ReaderFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.jgit.api.Git;

import java.io.*;
import java.util.List;

/**
 * @author andreas
 */
public class MavenHandler {

    private final Log log;

    public MavenHandler() {
        log = null;
    }

    public MavenHandler(Log log) {
        this.log = log;
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
    public void checkforDependencies(File pomfile, Model model, String[] groupIds, String ticketId, String gituser, String gitpassword, String localRepositoryPath) throws MojoExecutionException, Exception {
        if (groupIds == null) {
            log.info("no group id(s) defined ... finished.");
            return;
        }
        log.info("checking dependencies for affected group id(s)...");
        DeploymentRepository deploymentRepository = model.getDistributionManagement().getSnapshotRepository();
        log.info("using deployment repository: " + deploymentRepository + " with URL: " + deploymentRepository.getUrl());
        List<Dependency> dependencyListmodel = model.getDependencies();
//        boolean dependencyHasToModified = false;
        for (Dependency dependency : dependencyListmodel) {
            for (String groupid : groupIds) {
                if (dependency.getGroupId().contains(groupid)) {
                    log.info("affected dependency found: " + dependency + " with " + dependency.getVersion());
                    // @todo: check if branched version of dep exists
                    // ->> get POM from dependency --> Git --> SCM --> getDatas
                    Model dependencyModel = getSCMfromPOM(dependency, localRepositoryPath);
                    
                    // get Git Project URI
                    String dependencyScmUrl = dependencyModel.getScm().getUrl();
                    if (dependencyScmUrl == null || dependencyScmUrl.isEmpty()) {
                        if (!dependencyModel.getScm().getConnection().isEmpty()) {
                            log.info("Dependency SCM entries found");
                        }
                        log.warn("no SCM URL for affected dependency found, please add <url></url> tag to " +
                                dependency.getArtifactId() + "/" + dependency.getVersion() + " POM file - skipping");
                    } else {
                        log.info("Dependency SCM URL found: " + dependencyScmUrl);
                        if ( checkoutFromDependencyRepository(dependency, dependencyScmUrl, gituser, gitpassword, ticketId)) {
                            //@todo: commit and push changes --> throw an error --> Jenkins build will start again, or trigger the build manual again
                            dependency.setVersion(setPomVersion(dependency.getVersion(), ticketId));
                            log.info("changed dep: " + dependency);
                            log.info("POM FILE: " + model.getPomFile());
                            new XMLHandler(log).writeChangedPomWithChangedDependency(pomfile, dependency.getArtifactId(), ticketId);
                        }
                    }
                }
            }
        }
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
    private Model getSCMfromPOM(Dependency dependency, String localRepositoryPath) throws MojoExecutionException {
        File pomfile = new FileHelper(log).getPOMFilePathFromDependency(dependency, localRepositoryPath);
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
    private Boolean checkoutFromDependencyRepository(Dependency dependency, String dependencyScmUrl, String gituser, String gitpassword, String ticketId) throws MojoExecutionException {
        GitHandler gitHandler = new GitHandler(log, gituser, gitpassword);

        // setup local temporary Directory for Git checkout
        FileHelper fileHelper = new FileHelper(log);
        File localDirectory = fileHelper.createTempGitCheckoutDirectory(dependency.getArtifactId());

        // clone Repo
        Git gitDependency = gitHandler.cloneGitRemoteRepo(dependencyScmUrl, localDirectory);
        String[] branches = gitHandler.getBranchesFromDependency(gitDependency);
        Boolean branchFound = check(branches, ticketId);
        gitDependency.close();
        // delete local Repository
        fileHelper.deleteTempGitCheckoutDirectory(dependency.getArtifactId());
        return branchFound;
    }

    /**
     * check if branches matched ticketid
     *
     * @param branches
     * @param ticketId
     * @return checkForTicketId
     */
    private Boolean check(String[] branches, String ticketId) {
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
        if (checkForTicketId) {
            log.info("changed branched version to: ");
        } else {
            log.info("there are no matches to our branched version - finished");
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
}
