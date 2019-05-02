package io.crowdcode.bgav;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

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

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    getLog().info("Hello, world.");
    getLog().info(mavenProject.toString());
    getLog().info("getArtifactId " + mavenProject.getArtifactId());
    getLog().info("getVersion    " + mavenProject.getVersion());
    // Ticket-ID schon vorhanden
    mavenProject.setVersion(mavenProject.getVersion() + "-NCX-1-SNAPSHOT");
    getLog().info("getVersion    " + mavenProject.getVersion());

    File pomfile = new File("pom.xml");
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
    getLog().info("model    " + model);
    getLog().info("model    " + model.getVersion());
    model.setVersion(model.getVersion() + "-NCX-1-SNAPSHOT");
    try {
      new MavenXpp3Writer().write(new FileOutputStream(pomfile), model);
    } catch (IOException ex) {
      getLog().error("IOException: " + ex);
      throw new MojoExecutionException("could not write POM: " + ex);
    }
  }

}
