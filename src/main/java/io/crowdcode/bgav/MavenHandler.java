package io.crowdcode.bgav;

import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DeploymentRepository;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.xpp3.SettingsXpp3Reader;
import org.apache.maven.shared.utils.ReaderFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.jgit.api.Git;

import java.io.*;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
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
    public void checkforDependencies(Model model, String[] groupIds, String ticketId, String gituser, String gitpassword) throws MojoExecutionException, Exception {
        if (groupIds == null) {
            log.info("no group id(s) defined ... finished.");
            return;
        }
        log.info("checking dependencies for affected group id(s)...");
        DeploymentRepository deploymentRepository = model.getDistributionManagement().getSnapshotRepository();
        log.info("using deployment repository: " + deploymentRepository + " with URL: " + deploymentRepository.getUrl());
        List<Dependency> dependencyListmodel = model.getDependencies();
        for (Dependency dependency : dependencyListmodel) {
            for (String groupid : groupIds) {
                if (dependency.getGroupId().contains(groupid)) {
                    log.info("affected dependency found: " + dependency);
                    // @todo: check if branched version of dep exists
                    // ->> get POM from dependency --> Git --> SCM --> getDatas
                    Model dependencyModel = getSCMfromPOM(dependency);
                    
                    // get Git Project URI
                    String dependencyScmUrl = dependencyModel.getScm().getUrl();
//                    dependencyScmUrl = "https:" + dependencyScmUrl.substring(dependencyScmUrl.indexOf("//"), dependencyScmUrl.length());
                    log.info("Dependency SCM URL: " + dependencyScmUrl);
                    if (dependencyScmUrl == null || dependencyScmUrl.isEmpty()) {
                        throw new MojoExecutionException("not SCM URL for affected dependency found, please add SCM to " + dependency.getArtifactId() + " POM file");
                    }

                    // @todo: wenn kein repo vorhanden - abbrechen? Ja, da eins vorhanden sein muß
                    GitHandler gitHandler = new GitHandler(log, gituser, gitpassword);

                    // setup local temporary Directory for Git checkout
                    FileHelper fileHelper = new FileHelper(log);
                    File localDirectory = fileHelper.createTempGitCheckoutDirectory(log, dependency.getArtifactId());

                    // clone Repo
                    Git gitDependency = gitHandler.cloneGitRemoteRepo(dependencyScmUrl, localDirectory);
                    String[] branches = gitHandler.getBranchesFromDependency(gitDependency);
                    gitDependency.close();
                    // delete local Repository
                    fileHelper.deleteTempGitCheckoutDirectory(log, dependency.getArtifactId());

                    // check for affected groudids
                    Boolean checkForTicketId = false;
                    for (String branch : branches) {
                        log.info("branch: " + branch + " ticketID: " + ticketId);
                        if ( branch.contains(ticketId)) {
                            checkForTicketId = true;
                        }
                    }
                    if (!checkForTicketId) {
                        //@todo: change Version to correct version, throw an error
                        throw new MojoExecutionException("affected groupid contains not the nessesary ticket id in version");
                    }

                    /*log.info("read metadata from: " + deploymentRepository.getUrl() + dependency.getGroupId().replaceAll("[.]", "/") + "/" + dependency.getArtifactId() + "/maven-metadata.xml");
                    Server server = getServer(deploymentRepository.getId());
                    List<String> versionen = getVersions(server, deploymentRepository, dependency);
                    for (String version : versionen) {
                        if (version.equals(dependency.getVersion())) {
                            log.info("found matching version: " + version);
                        }
                        if (version.contains(ticketId)) {

                        }
                    }*/
//                    if (new GitHandler(log).) {
//
//                    }
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
    private Model getSCMfromPOM(Dependency dependency) throws MojoExecutionException {
        File pomfile = new FileHelper(log).getPOMFilePathFromDependency(dependency);
        log.info("POM File from " + dependency.getArtifactId() + ": " + pomfile);
        return getModel(pomfile);
    }

    private Server getServer(String serverId) throws MojoExecutionException {
        Settings settings = getSettings();
        for (Server server : settings.getServers()) {
            if (server.getId().equals(serverId)) {
                log.info("found repository server: " + server.getId() + ", use credentials from " + server.getUsername());
                return server;
            }
        }
        throw new MojoExecutionException("no matching repository servers where found for " + serverId);
    }

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

    private List<String> getVersions(Server server, DeploymentRepository deploymentRepository, Dependency dependency) {
        List<String> versionen = null;
        try {
//              URL url = new URL("http://repo1.maven.org/maven2/org/apache/maven/maven-repository-metadata/maven-metadata.xml");
            URL url = new URL(deploymentRepository.getUrl() + dependency.getGroupId().replaceAll("[.]", "/") + "/" + dependency.getArtifactId() + "/maven-metadata.xml");
            Metadata metadata = getMetadata(url, server);
            log.info("groupId: " + metadata.getGroupId());
            log.info("artifactId: " + metadata.getArtifactId());
            log.info("latest: " + metadata.getVersioning().getLatest());
            log.info("Versions: " + metadata.getVersioning().getVersions());
            versionen = metadata.getVersioning().getVersions();
            for (String version : metadata.getVersioning().getVersions()) {
                log.info("--> Version: " + version);
            }
        } catch (IOException e) {
            log.error("error getting maven-metadata.xml from " + server.getId() + " for " + dependency.getGroupId() + ":" +
                    dependency.getArtifactId() + ": " + e);
        }
        return versionen;
    }

    private Metadata getMetadata(URL url, Server server) {
        try {
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(server.getUsername(), server.getPassword().toCharArray());
                }
            });
            Metadata metadata;
            try (InputStream inputStream = url.openStream()) {
                MetadataXpp3Reader metadataXpp3Reader = new MetadataXpp3Reader();
                metadata = metadataXpp3Reader.read(inputStream);
            }
            return metadata;
        } catch (IOException | XmlPullParserException ex) {
            log.error("error getting metadata: " + ex);
        }
        return null;
    }
}
