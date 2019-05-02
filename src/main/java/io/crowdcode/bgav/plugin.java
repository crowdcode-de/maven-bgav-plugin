package io.crowdcode.bgav;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 *
 * @author andreas
 */
@Mojo( name = "bgav")
public class plugin extends AbstractMojo {

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    getLog().info( "Hello, world.");
//    getPluginContext()
  }
  
}
