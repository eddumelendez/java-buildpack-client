package dev.snowdrop.buildpack.phases;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import dev.snowdrop.buildpack.ContainerLogReader;


public class Creator implements LifecyclePhase{

    private static final Logger log = LoggerFactory.getLogger(Creator.class);

    final LifecyclePhaseFactory factory;

    public Creator( LifecyclePhaseFactory factory ){
        this.factory = factory;
    }

    @Override
    public ContainerStatus runPhase(dev.snowdrop.buildpack.Logger logger, boolean useTimestamps) {

        // configure our call to 'creator' which will do all the work.
        String[] args = { "/cnb/lifecycle/creator", 
                        "-uid", "" + factory.buildUserId, 
                        "-gid", "" + factory.buildGroupId, 
                        "-cache-dir", LifecyclePhaseFactory.BUILD_VOL_PATH,
                        "-app", LifecyclePhaseFactory.APP_VOL_PATH + "/content", 
                        "-layers", LifecyclePhaseFactory.OUTPUT_VOL_PATH, 
                        "-platform", LifecyclePhaseFactory.PLATFORM_VOL_PATH, 
                        "-run-image", factory.runImageName, 
                        "-launch-cache", LifecyclePhaseFactory.LAUNCH_VOL_PATH, 
                        "-daemon", // TODO: non daemon support.
                        "-log-level", factory.buildLogLevel, 
                        "-skip-restore", factory.finalImageName };

        // TODO: read metadata from builderImage to confirm lifecycle version/platform
        // version compatibility.

        // TODO: add labels for container for creator etc (as per spec)
    
        String id = factory.getContainerForPhase(args);
        log.info("- build container id " + id);

        // launch the container!
        log.info("- launching build container");
        factory.dockerClient.startContainerCmd(id).exec();

        log.info("- attaching log relay");
        // grab the logs to stdout.
        factory.dockerClient.logContainerCmd(id)
        .withFollowStream(true)
        .withStdOut(true)
        .withStdErr(true)
        .withTimestamps(useTimestamps)
        .exec(new ContainerLogReader(logger));

        // wait for the container to complete, and retrieve the exit code.
        int rc = factory.dockerClient.waitContainerCmd(id).exec(new WaitContainerResultCallback()).awaitStatusCode();
        log.info("Buildpack container complete, with exit code " + rc);    

        return ContainerStatus.of(rc,id);
    }
    
}
