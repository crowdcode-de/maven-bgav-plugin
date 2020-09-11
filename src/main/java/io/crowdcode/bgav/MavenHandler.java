package io.crowdcode.bgav;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.*;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.xpp3.SettingsXpp3Reader;
import org.apache.maven.shared.utils.ReaderFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.jgit.api.Git;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
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
    private final File baseDir;
    private final XMLHandler xmlHandler;
    private final RepositorySystem repositorySystem;
    private final MavenProjectBuilder mavenProjectBuilder;
    private final List<ArtifactRepository> remoteRepositories;
    private final ArtifactRepository localRepository;
    private static final Map<String, DistributionManagement> distributionMap = new HashMap<>();
    private static final Map<String, Scm> scmMap = new HashMap<>();

    private class CheckOutDependency{
        public final File checkoutDir;
        public final Boolean hasBranch;

        public CheckOutDependency(File checkoutDir, Boolean hasBranch) {
            this.checkoutDir = checkoutDir;
            this.hasBranch = hasBranch;
        }
    }

    public MavenHandler(Log log, boolean suppressCommit, boolean suppressPush, File baseDir, RepositorySystem repositorySystem, MavenProjectBuilder mavenProjectBuilder, List<ArtifactRepository> remoteRepositories, ArtifactRepository localRepository) {
        this.log = log;
        this.suppressCommit = suppressCommit;
        this.suppressPush = suppressPush;
        this.baseDir = baseDir;
        this.repositorySystem = repositorySystem;
        this.mavenProjectBuilder = mavenProjectBuilder;
        this.remoteRepositories = remoteRepositories;
        this.localRepository = localRepository;
        xmlHandler = new XMLHandler(log, suppressCommit, suppressPush, this);
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
            pomVersion = determineNonBgavPomVersion(pomVersion);
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
            newPomVersion = pomVersion + "-" + ticketID;
        }
        log.info("new POM Version: " + newPomVersion);
        return newPomVersion;
    }

    public String determineNonBgavPomVersion(String pomVersion) {
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
        final DistributionManagement distributionManagement = getDistributionManagement(pomfile, model);

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
                    if (nativeVersion != null && !nativeVersion.trim().isEmpty()) {
                        if (nativeVersion != null && !nativeVersion.trim().isEmpty())
                            log.info("affected dependency found: " + dependency + " with version " + nativeVersion);
                        // @todo: check if branched version of dep exists
                        // ->> get POM from dependency --> Git --> SCM --> getDatas
                        MavenProject dependentProject = null;
                        try {
                            dependentProject = getSCMfromPOM(model, dependency, localRepositoryPath);
                        } catch (MojoExecutionException e) {
                            log.warn("could not get POM file: " + e);
                            return artifact;
                        }
                        // --> get POM from SCM from project POM file
                        // get Git Project URI

                        final Scm scm = getScm(dependentProject.getModel());
                        if (scm != null) {
                            String dependencyScmUrl = scm.getUrl();
                            String artifactId = dependency.getArtifactId();
                            if (dependencyScmUrl == null || dependencyScmUrl.isEmpty()) {
                                if (!scm.getConnection().isEmpty()) {
                                    log.info("Dependency SCM entries found");
                                }
                                log.warn("no SCM URL for affected dependency found, please add <url></url> tag to " +
                                        artifactId + "/" + nativeVersion + " POM file - skipping");
                            } else {
                                log.info("Dependency SCM URL found: " + dependencyScmUrl);
                                final CheckOutDependency checkOutDependency = checkoutFromDependencyRepository(dependency, dependencyScmUrl, gituser, gitpassword, ticketId);
                                try {
                                    if (checkOutDependency.hasBranch) {
                                        //@todo: commit and push changes --> throw an error --> Jenkins build will start again, or trigger the build manual again
                                        if (!isPlaceholder(nativeVersion)) {
                                            final Model checkedOutModel = getModel(new File(checkOutDependency.checkoutDir + "/pom.xml"));
                                            log.info("want to change: " + nativeVersion + " -- " + ticketId);
                                            String newVersion = determinePomVersion(checkedOutModel.getVersion(), ticketId);
                                            if (nativeVersion.contains(ticketId)) {
                                                log.info("POM contains ticketId - do nothing");
                                            } else {
                                                dependency.setVersion(determinePomVersion(nativeVersion, ticketId));
                                                artifact += artifactId + ", ";
                                                log.info("changed dep: " + dependency);
                                                //                                log.info("POM FILE: " + model.getPomFile());
                                                xmlHandler.alterDependency(pomfile, artifactId, newVersion);
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
                                                xmlHandler.alterProperty(pomfile, unkey(nativeVersion), newVersion);
                                            }
                                        }
                                    }
                                } finally {
                                    new FileHelper(log).deleteTempGitCheckoutDirectory(checkOutDependency.checkoutDir);
                                }
                            }
                        } else {
                            log.info(dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion() + " has no SCM configured");
                        }
                    } else {
                        log.debug("Dependency "+dependency+" skipped due to no version.");
                    }
                }
            }
        }
        return artifact;
    }

    private DistributionManagement getDistributionManagement(File pomfile, Model model) throws MojoExecutionException {
        log.info("checking dependencies for affected group id(s)...");
        DistributionManagement distributionManagement = model.getDistributionManagement();
        if (distributionManagement == null && model.getParent() != null) {
            if (distributionMap.containsKey(model.getParent().getId())) {
                distributionManagement =  distributionMap.get(model.getParent().getId());
            } else {
                final File parentPomFile = new File(pomfile.getAbsoluteFile().getParentFile() + "/" + model.getParent().getRelativePath());
                if (parentPomFile.exists() && parentPomFile.isFile() && !parentPomFile.isDirectory()) {
                    Model parentModel = getModel(parentPomFile);
                    distributionManagement = getDistributionManagement(parentPomFile, parentModel);
                }
                if (distributionManagement == null) {
                    try {
                        final String groupId = model.getGroupId() != null ? model.getGroupId() : model.getParent().getGroupId();
                        final String version = model.getVersion() != null ? model.getVersion() : model.getParent().getVersion();

                        final MavenProject mavenProject = resolveProject(groupId, model.getArtifactId(), version);
                        distributionManagement = mavenProject.getDistributionManagement();
                    } catch (ProjectBuildingException e) {
                        throw new MojoExecutionException(e.getMessage(), e);
                    }
                }
            }
        }

        distributionMap.put(model.getId(), distributionManagement);

        return distributionManagement;
    }


    private Scm getScm(Model model) throws MojoExecutionException {
        log.info("checking dependencies for affected group id(s)...");
        Scm scm = model.getScm();
        if (scm == null && model.getParent() != null) {
            if (scmMap.containsKey(model.getParent().getId())) {
                scm =  scmMap.get(model.getParent().getId());
            } else {
                try {
                    final String groupId = model.getGroupId() != null ? model.getGroupId() : model.getParent().getGroupId();
                    final String version = model.getVersion() != null ? model.getVersion() : model.getParent().getVersion();

                    final MavenProject mavenProject = resolveProject(groupId, model.getArtifactId(), version);
                    scm = mavenProject.getScm();
                } catch (ProjectBuildingException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
        }

        scmMap.put(model.getId(), scm);

        return scm;
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

                    if (version != null && !version.trim().isEmpty()){
                        if (!isPlaceholder(version)) {
                            String ticketId = extractTicketId(version);
                            if (ticketId != null && !ticketId.isEmpty()) {
                                log.info("dependency contains ticketId - remove it: " + ticketId);
                                String newPomDepVersion = determineNonBgavPomVersion(version);
                                dependency.setVersion(newPomDepVersion);
                                artifact += dependency.getArtifactId() + ", ";
                                try {
                                    xmlHandler.alterDependency(pomfile, dependency.getArtifactId(), newPomDepVersion);
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
                                String newVersion = determineNonBgavPomVersion(property);
                                Object dummy = setProperty(model, version, newVersion);
                                artifact += dependency.getArtifactId() + ", ";
                                try {
                                    xmlHandler.alterProperty(pomfile, unkey(version), newVersion);
                                } catch (MojoExecutionException ex) {
                                    log.warn("could not write POM");
                                }
                            } else {
                                log.info("dependency has no BGAV version");
                            }
                        }
                    } else {
                        log.debug("dependency "+ dependency+ " skipped due to missing version.");
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
    private MavenProject getSCMfromPOM(Model model, Dependency dependency, String localRepositoryPath) throws MojoExecutionException {
        // File pomfile = new FileHelper(log).getPOMFilePathFromDependency(model, dependency, localRepositoryPath);
        log.info("Resolviong " + dependency.getArtifactId());
        try {
            return resolveProject(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
        } catch (ProjectBuildingException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
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
    private CheckOutDependency checkoutFromDependencyRepository(Dependency dependency, String dependencyScmUrl, String gituser, String gitpassword, String ticketId) throws MojoExecutionException, IOException {
        GitHandler gitHandler = new GitHandler(log, gituser, gitpassword, suppressCommit, suppressPush, baseDir);

        // setup local temporary Directory for Git checkout
        FileHelper fileHelper = new FileHelper(log);
        File localDirectory = fileHelper.createTempGitCheckoutDirectory(dependency.getArtifactId());

        log.info("cloning into "+localDirectory.getAbsolutePath());

        // clone Repo
        Git gitDependency = gitHandler.cloneGitRemoteRepo(dependencyScmUrl, localDirectory);
        String[] branches = gitHandler.getBranchesFromDependency(gitDependency);

        final Optional<String> first = Arrays.asList(branches).stream()
                .filter(x -> x.contains(ticketId))
                .findFirst();
        Boolean branchFound = first.isPresent();
        String branch = first.get();
        gitHandler.checkoutBranch(gitDependency, branch);
        gitDependency.close();
        // delete local Repository
        return new CheckOutDependency(localDirectory, branchFound);
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

    public MavenProject resolveProject(String groupId, String artifactId, String version) throws ProjectBuildingException {
        Artifact pomArtifact = repositorySystem.createProjectArtifact(groupId, artifactId, version);
        MavenProject project = mavenProjectBuilder.buildFromRepository(pomArtifact
                , remoteRepositories, localRepository);
        return project;
    }

}
