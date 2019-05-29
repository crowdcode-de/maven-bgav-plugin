package io.crowdcode.bgav;

import org.apache.maven.plugin.logging.Log;
import org.eclipse.jgit.api.Git;
import org.junit.Test;

public class GitHandlerTest {

    GitHandler gitHandler;
    Log log;

    @org.junit.Before
    public void setUp() throws Exception {
        Plugin plugin = new Plugin();
        log = plugin.getLog();
    }

    public void testGetBranchesFromDependency() {
//        Git git = new Git(GitHandler);
    }
}
