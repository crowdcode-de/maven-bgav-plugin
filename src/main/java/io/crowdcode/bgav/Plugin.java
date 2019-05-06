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
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

/**
 *
 * @author andreas
 */
@Mojo(name = "bgav")
public class Plugin extends AbstractMojo {

    @Parameter(defaultValue = "${project}")
    private MavenProject mavenProject;
    @Parameter
    private String[] namespace;

    final Log log = getLog();

    private final String regexp = "(feature)/([A-Z0-9\\-])*-.*";
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

        // check for Git Repo
        Git git = getGitRepo(model);
        if (git == null) {
            return;
        }

        try {
            Status status = git.status().call();
            log.info("Git status: " + status);
            log.info("hasUncommittedChanges: " + status.hasUncommittedChanges());
            Set<String> changes = status.getModified();
            log.info("Git changes: " + changes);
            for (String change : changes) {
                log.info("Git changes: " + change);
                if (change.equals("pom.xml")) {
                    throw new MojoExecutionException("POM is not commited... please commit before building application.");
                }
            }
        } catch (GitAPIException | NoWorkTreeException ex) {
            getLog().error("Git error: " + ex);
            throw new MojoExecutionException("Git status failed: " + ex);
        }
        
        Repository repo = git.getRepository();
        try {
            String branch = repo.getBranch();
            log.info("Git branch: " + branch);
            // NCX-14 check for feature branch
            if (branch != null && branch.startsWith("feature")) {
                String pomVersion = getMatchFirst(model.getVersion(), REGEX_TICKET);
                String ticketID = getMatchFirst(branch, REGEX_TICKET);
                log.info("POM Version: " + pomVersion);
                log.info("ticketID: " + ticketID);
                if (pomVersion == null) {
                    // NCX-16 write new verion to POM
//                    writeChangedPOM(model, git, ticketID, pomfile);
                } else if (ticketID.equals(pomVersion)) {
                    // POM Version has TicketID
                    log.info("Git branch ticket ID matches POM ticket ID ... done.");
                } else {
                    // POM Version has TicketID
                    throw new MojoExecutionException("mismatch Git branch ticket ID and POM branch version ticket ID");
                }
            } else {
                log.info("no Git feature branch ... done.");
            }
        } catch (IOException | MojoExecutionException ex) {
            throw new MojoExecutionException("could not get branch: " + ex);
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
            getLog().error("ParseError: " + ex);
            throw new MojoExecutionException("could not read POM: " + ex);
        }
        return model;
    }

    /**
     * check for Git Repository
     *
     * @param Model
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

    Boolean checkBranch() {
        return true;
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
        try {
            CredentialsProvider cp = new UsernamePasswordCredentialsProvider("crowdcode", "lSir05xA3k-6");
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
//            log.info("Matcher: " + match);
        }
        return match;
    }
}
