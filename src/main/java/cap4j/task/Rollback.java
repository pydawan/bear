package cap4j.task;

import cap4j.core.GlobalContext;
import cap4j.core.SessionContext;
import cap4j.plugins.Plugin;
import cap4j.session.Result;

/**
 * User: chaschev
 * Date: 9/24/13
 */
public class Rollback extends Plugin{
    public Rollback(GlobalContext global) {
        super(global);
    }

    @Override
    public void init() {

    }

    @Override
    public Task getSetup() {
        return null;
    }

    public final Task<TaskResult> pointToPreviousRelease = new Task<TaskResult>() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            requirePreviousRelease($);

            return new TaskResult(
                system.script()
                    .line().sudo().addRaw("rm -r %s", $.var(cap.currentPath)).build()
                    .line().sudo().addRaw("ln -s %s %s", $.var(cap.getPreviousReleasePath), $.var(cap.currentPath)).build()
                    .run()
            );
        }
    }.desc("[internal] Points the current symlink at the previous release.\n" +
        "      This is called by the rollback sequence, and should rarely (if\n" +
        "      ever) need to be called directly.");

    public final Task<TaskResult> cleanup = new Task<TaskResult>() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            return new TaskResult(
                system.run(
                    system.line().sudo().addRaw("if [ `readlink #{%s}` != #{%s} ]; then #{try_sudo} rm -rf #{%s}; fi",
                        $.var(cap.currentPath), $.var(cap.releasePath), $.var(cap.releasePath) ))
            );
        }
    };

    public final Task<TaskResult> code = new Task<TaskResult>() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            return new TaskResult(Result.and(
                runner.run(pointToPreviousRelease),
                runner.run(cleanup)));
        }
    };

    public final Task<TaskResult> $default = new Task<TaskResult>() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            return new TaskResult(Result.and(
                runner.run(pointToPreviousRelease),
                runner.run(global.tasks.restartApp),
                runner.run(cleanup)));
        }
    };


    private void requirePreviousRelease(SessionContext $) {
        if($.var(cap.getPreviousReleasePath) != null) {
            throw new RuntimeException("could not rollback the code because there is no prior release");
        }
    }
}