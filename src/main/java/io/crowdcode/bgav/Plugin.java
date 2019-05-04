package io.crowdcode.bgav;

import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.StatusCommand;
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

//  @Parameter(defaultValue = "${project}", required = true)
//  @Parameter(defaultValue = "${mavenProject}", required = true, readonly = true)
    @Parameter(defaultValue = "${project}", readonly = false)
    private MavenProject mavenProject;
    private Scm scm;
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

        log.info("Hello, world.");
        log.info(mavenProject.toString());
        log.info("getArtifactId " + mavenProject.getArtifactId());
        log.info("getVersion    " + mavenProject.getVersion());
        // Ticket-ID schon vorhanden
        mavenProject.setVersion(mavenProject.getVersion() + "-NCX-1-SNAPSHOT");
        log.info("getVersion    " + mavenProject.getVersion());

        File pomfile = new File("pom.xml");

        Model model = getModel(pomfile);

        log.info("model    " + model);
        log.info("model    " + model.getVersion());
        log.info("model    " + model.getProjectDirectory());
        // Frage hier oder als Klasse var?, wegen schnick schnack halt...
        Git git;
        String branch, ticketID, pomBranchVersion;
        try {
            git = Git.open(model.getProjectDirectory());
            log.info("git    " + git);
        } catch (RepositoryNotFoundException ex) {
            // kein Git Repo vorhanden -> done.
            return;
        } catch (IOException ex) {
            throw new MojoExecutionException("could not read Git Repo: " + ex);
        }
        StatusCommand statusCommand = git.status();
        try {
            log.info("git.status():      " + statusCommand.call());
        } catch (GitAPIException | NoWorkTreeException ex) {
            Logger.getLogger(Plugin.class.getName()).log(Level.SEVERE, null, ex);
            throw new MojoExecutionException("git status failed: " + ex);
        }
        ListBranchCommand listBranchCommand = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE);
        log.info("listBranchCommand: " + listBranchCommand);
        Repository repo = git.getRepository();
        try {
            branch = repo.getBranch();
            pomBranchVersion = getMatchFirst(model.getVersion(), REGEX_TICKET);
            log.info("git branch: " + branch);
            log.info("git branch version: " + pomBranchVersion);
            if (branch.startsWith("feature")) {
                ticketID = getMatchFirst(branch, REGEX_TICKET);
                log.info("ticketID: " + ticketID);
                if (pomBranchVersion != null && !ticketID.equals(pomBranchVersion)) {
                    throw new MojoExecutionException("mismatch Git Branch Ticket ID and POM Ticket ID");
                } else if (pomBranchVersion == null) {
                    // write new verion to POM
                    model.setVersion(model.getVersion() + "-" + ticketID + "-SNAPSHOT");
                    try (final FileOutputStream fileOutputStream = new FileOutputStream(pomfile)) {
                        new MavenXpp3Writer().write(fileOutputStream, model);
                    } catch (IOException ex) {
                        log.error("IOException: " + ex);
                        throw new MojoExecutionException("could not write POM: " + ex);
                    }
                    try {
                        CredentialsProvider cp = new UsernamePasswordCredentialsProvider( "crowdcode", "lSir05xA3k-6");
                        git.commit().setMessage(ticketID + " - BGAV - set correkt branched version").call();
                        git.add().addFilepattern("pom.xml").call();
//                        AddCommand add = git.add();
//                        add.addFilepattern("pom.xml").call();
                        git.push().setCredentialsProvider( cp ).call();
                    } catch (GitAPIException ex) {
                        throw new MojoExecutionException("Git commit/push failed: " + ex);
                    }
                } else if (ticketID.equals(pomBranchVersion)) {
                    log.info("Git Branch Ticket ID matches POM Ticket ID ... done.");
                }
            }
        } catch (Exception ex) {
            throw new MojoExecutionException("RegEx failed: " + ex);
        }
        git.close();
    }

    /**
     * get Maven Project Model from POM
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

    String getMatchFirst(String search, String pat) {
        String match = null;
        Pattern pattern = Pattern.compile(pat);
        Matcher matcher = pattern.matcher(search);
        while (matcher.find()) {
            match = matcher.group(1);
            getLog().info("Matcher: " + match);
//            getLog().info("Matcher: " + matcher.group(2));
        }
        return match;
    }
}
