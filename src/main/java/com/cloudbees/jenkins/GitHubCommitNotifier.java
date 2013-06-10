package com.cloudbees.jenkins;

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.util.BuildData;
import hudson.scm.SCM;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

import static hudson.model.Result.*;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class GitHubCommitNotifier extends Notifier {


    @DataBoundConstructor
    public GitHubCommitNotifier() {
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        BuildData buildData = build.getAction(BuildData.class);
        String sha1 = ObjectId.toString(buildData.getLastBuiltRevision().getSha1());

        GitHubTrigger trigger = build.getProject().getTrigger(GitHubPushTrigger.class);
        for (GitHubRepositoryName gitHubRepositoryName : trigger.getGitHubRepositories()) {
            for (GHRepository repository : gitHubRepositoryName.resolve()) {
                GHCommitState state;
                final String verb;

                Result result = build.getResult();
                if (result.isBetterOrEqualTo(SUCCESS)) {
                    state = GHCommitState.SUCCESS;
                    verb = "succeeded";
                } else if (result.isBetterOrEqualTo(UNSTABLE)) {
                    state = GHCommitState.FAILURE;
                    verb = "found unstable";
                } else {
                    state = GHCommitState.ERROR;
                    verb = "failed";
                }

                // We do not use `build.getDurationString()` because it appends 'and counting' (build is still running)
                final String timeSpanString = Util.getTimeSpanString(System.currentTimeMillis() - build.getTimeInMillis());
                final String msg = String.format("Build #%d %s in %s", build.getNumber(), verb, timeSpanString);
                listener.getLogger().println("Setting commit status on GitHub for " + repository.getUrl() + "/commit/" + sha1);
                repository.createCommitStatus(sha1, state, build.getAbsoluteUrl(), msg);
            }
        }
        return true;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return "Set build status on GitHub commit";
        }
    }

}
