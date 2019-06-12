package io.crowdcode.bgav;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class FileHelper {

    private final Log log;
    private final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    private File localDirectory;

    public FileHelper() {
        log = null;
    }

    public FileHelper(Log log) {
        this.log = log;
    }

    public File getLocalDirectory() {
        return localDirectory;
    }

    /**
     * get POM File from Dependency
     *
     * @param dependency
     * @return POM File
     */
    public File getPOMFilePathFromDependency(Dependency dependency, String localRepositoryPath) {
        return new File(localRepositoryPath + "/" +
                dependency.getGroupId().replaceAll("[.]", "/") + "/" +
                dependency.getArtifactId() + "/" + dependency.getVersion() + "/" +
                dependency.getArtifactId() + "-" + dependency.getVersion() + ".pom");
    }

    /**
     * create local Directory for Git checkout
     *
     * @param artefact
     */
    public File createTempGitCheckoutDirectory(String artefact) {
        log.info("create temp dir for checkout: " + TEMP_DIR + artefact);
        localDirectory = new File(TEMP_DIR + artefact);
        localDirectory.mkdir();
        return localDirectory;
    }

    /**
     * delete local Directory for Git checkout
     *
     * @param artefact
     */
    public void deleteTempGitCheckoutDirectory(String artefact) {
        log.info("delete temp dir for checkout: " + TEMP_DIR + artefact);
        new File(TEMP_DIR + artefact + "/.git").delete();
        new File(TEMP_DIR + artefact).delete();
        Path path = new File(TEMP_DIR + artefact).toPath();
        try {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
