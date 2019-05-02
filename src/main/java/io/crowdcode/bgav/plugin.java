package io.crowdcode.bgav;

import org.apache.maven.model.Scm;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author andreas
 */
@Mojo( name = "bgav")
public class plugin extends AbstractMojo {

  @Parameter(defaultValue = "${project}", required = true)
//  @Parameter(defaultValue = "${project}", required = true, readonly = true)
  private MavenProject project;
  private Scm scm;
  
  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    getLog().info( "Hello, world.");
    getLog().info( project.toString());
    getLog().info( "getArtifactId " + project.getArtifactId());
    getLog().info( "getVersion    " + project.getVersion());
    project.setVersion(project.getVersion() + "-NCX-");
    getLog().info( "getVersion    " + project.getVersion());
//    scm = project.getScm();
//    if (scm == null) {
//      throw new MojoExecutionException("no SCM defined...");
//    }
//    getPluginContext()
  }
  
}
