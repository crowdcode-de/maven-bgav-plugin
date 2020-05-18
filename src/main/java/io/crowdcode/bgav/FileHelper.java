package io.crowdcode.bgav;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static io.crowdcode.bgav.PropertyHelper.isPlaceholder;
import static io.crowdcode.bgav.PropertyHelper.resolveProperty;

public class FileHelper {

    private final Log log;
    private final String TEMP_DIR = System.getProperty("java.io.tmpdir") + "/";
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
    public File getPOMFilePathFromDependency(Model model, Dependency dependency, String localRepositoryPath) {
        String nativeVersion = dependency.getVersion();
        final String version = isPlaceholder(nativeVersion) ? resolveProperty(model, nativeVersion) : nativeVersion;

        return new File(localRepositoryPath + "/" +
                dependency.getGroupId().replaceAll("[.]", "/") + "/" +
                dependency.getArtifactId() + "/" + version + "/" +
                dependency.getArtifactId() + "-" + version + ".pom");
    }

    /**
     * create local Directory for Git checkout
     *
     * @param artifact
     */
    public File createTempGitCheckoutDirectory(String artifact) throws IOException {
        log.info("create temp dir for checkout: " + TEMP_DIR + artifact);
        localDirectory = File.createTempFile(artifact, "git");
        localDirectory.delete();
        localDirectory.mkdir();
        return localDirectory;
    }

    /**
     * delete local Directory for Git checkout
     *
     * @param artifact
     */
    public void deleteTempGitCheckoutDirectory(File directory) {
        log.info("delete temp dir for checkout: " + directory);
        Path path = directory.toPath();
        try {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            directory.deleteOnExit();
        } catch (IOException ex) {
            log.error("could not delete local checkout direcotory: " + ex.getLocalizedMessage());
        }
    }
}
