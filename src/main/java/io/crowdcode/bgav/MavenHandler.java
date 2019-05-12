package io.crowdcode.bgav;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 *
 * @author andreas
 */
public class MavenHandler {

    final Log log;

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
}
