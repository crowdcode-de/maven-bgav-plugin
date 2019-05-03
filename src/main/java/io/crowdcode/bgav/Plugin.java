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

  final Log log = getLog();

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
    model.setVersion(model.getVersion() + "-NCX-1-SNAPSHOT");
    try {
      new MavenXpp3Writer().write(new FileOutputStream(pomfile), model);
    } catch (IOException ex) {
      log.error("IOException: " + ex);
      throw new MojoExecutionException("could not write POM: " + ex);
    }
  }

  /**
   * TODO document me
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

}
