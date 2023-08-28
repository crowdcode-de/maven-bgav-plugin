package io.crowdcode.bgav;

import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.jgit.api.Git;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


public class Modify
{
    @Parameter(property = "bgav.modify.file", required = true)
    private String file;

    @Parameter(property = "bgav.modify.outputFile", required = false)
    private String outputFile;

    @Parameter(property = "bgav.modify.formatter", defaultValue = "conventional", required = false)
    private String formatter;

    @Parameter(required = false)
    private List<ModifyMojoModification> modifications;

    public void setFile(String file) {
        this.file = file;
    }

    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    public void setFormatter(String formatter) {
        this.formatter = formatter;
    }

    public void setModifications(List<ModifyMojoModification> modifications) {
        this.modifications = modifications;
    }

    public String getFile() {
        return file;
    }

    public void process(Log log, String version, GitHandler gitHandler, Git git) throws MojoExecutionException {

        FileSystem fs = FileSystems.getDefault();
        Path inputJson = fs.getPath(file);
        Path outputJson = outputFile == null ? inputJson : fs.getPath(outputFile);

        Configuration configuration = Configuration.builder()
                .jsonProvider(new JacksonJsonNodeJsonProvider())
                .mappingProvider(new JacksonMappingProvider())
                .build();

        DocumentContext json;
        try (InputStream in = Files.newInputStream(inputJson))
        {
            json = JsonPath.using(configuration).parse(in, "UTF-8");
        }
        catch (IOException e)
        {
            log.error("Unable to read input json file");
            throw new MojoExecutionException("Unable to read file '" + file + "'", e);
        }

        int count = 0;

        for (ModifyMojoModification modification : modifications)
        {
            String expression = modification.getExpression();
            String value = modification.getValue();
            if ("NewPomVersion".equals(value)){
                value=version;
            }
            json.set(expression, value);
            log.info(expression + "=" + value);
            count++;
        }

        try (OutputStream out = Files.newOutputStream(outputJson))
        {
            PrettyPrinter prettyPrinter;
            if ("conventional".equals(formatter)) {
                prettyPrinter = new ConventionalPrettyPrinter();
            } else if ("jackson".equals(formatter)) {
                prettyPrinter = new DefaultPrettyPrinter();
            } else {
                log.error("Invalid JSON formatter specified");
                throw new MojoExecutionException("Unknown formatter '" + formatter + "'");
            }
            ObjectWriter writer = new ObjectMapper().writer(prettyPrinter);
            writer.writeValue(out, json.json());
        }
        catch (IOException e)
        {
            log.error("Unable to write output json file");
            throw new MojoExecutionException("Unable write file '" + outputJson + "'", e);
        }

        if (count == 0)
        {
            log.error(count + " modifications written to json file " + outputJson);
            throw new MojoExecutionException("No properties were defined for setting");
        }
        log.info(count + " modifications written to json file " + outputJson);
        if( ! gitHandler.checkFileChanged(git,outputJson.toString())){
            return;
        }
        gitHandler.add(git,count + " modifications written to json file by bgav-plugin" ,outputJson.toFile(),outputJson.toString());
    }
}
