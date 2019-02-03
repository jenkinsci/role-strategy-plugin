// Initializes the production jobs directory, which runs with deployed scripts and repos

import com.cloudbees.hudson.plugins.folder.Folder
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy
import com.synopsys.arc.jenkins.plugins.ownership.OwnershipDescription
import com.synopsys.arc.jenkins.plugins.ownership.jobs.JobOwnerHelper
import hudson.plugins.git.GitSCM
import jenkins.model.Jenkins
import jenkins.plugins.git.GitSCMSource
import org.jenkinsci.plugins.ownership.model.folders.FolderOwnershipHelper
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.libs.FolderLibraries
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever

println("=== Initialize the Production folder")
if (Jenkins.instance.getItem("Production") != null) {
    println("Production folder has been already initialized, skipping the step")
    return
}

def folder = Jenkins.instance.createProject(Folder.class, "Production")

// Include https://github.com/jenkins-infra/pipeline-library
def pipelineLibrarySource = new GitSCMSource("pipeline-library", "https://github.com/jenkins-infra/pipeline-library.git", null, null, null, false)
LibraryConfiguration lc = new LibraryConfiguration("pipeline-library", new SCMSourceRetriever(pipelineLibrarySource))
lc.with {
    implicit = true
    defaultVersion = "master"
}
folder.addProperty(new FolderLibraries([lc]))
FolderOwnershipHelper.setOwnership(folder, new OwnershipDescription(true, "admin"))

// Add a sample project
WorkflowJob project1 = folder.createProject(WorkflowJob.class, "Ownership_Plugin_Agent")
project1.setDefinition(new CpsFlowDefinition("buildPlugin(platforms: ['linux'], repo: 'https://github.com/jenkinsci/ownership-plugin.git')", true))
JobOwnerHelper.setOwnership(project1, new OwnershipDescription(true, "admin", Arrays.asList("user")))

WorkflowJob project2 = folder.createProject(WorkflowJob.class, "Ownership_Plugin_Master")
project2.setDefinition(new CpsFlowDefinition("buildPlugin(platforms: ['master'], repo: 'https://github.com/jenkinsci/ownership-plugin.git')", true))
JobOwnerHelper.setOwnership(project2, new OwnershipDescription(true, "admin", Arrays.asList("user")))

// Sample project with a build flow from SCM
for (int i = 0; i < 500; i++) {
    def myFolder = Jenkins.getInstance().createProject(Folder.class, "Foooooolder" + i)
    WorkflowJob project4 = myFolder.createProject(WorkflowJob.class, "test" + i)
    GitSCM source1 = new GitSCM("https://gist.github.com/AbhyudayaSharma/1bccbb2e760ca0706907f451347d5727.git")
    project4.setDefinition(new CpsScmFlowDefinition(source1, "Jenkinsfile"))
    JobOwnerHelper.setOwnership(project4, new OwnershipDescription(false, "admin", Arrays.asList("user")))
}
WorkflowJob project3 = folder.createProject(WorkflowJob.class, "Remoting")
GitSCM source = new GitSCM("https://github.com/jenkinsci/remoting.git")
project3.setDefinition(new CpsScmFlowDefinition(source, "Jenkinsfile"))
JobOwnerHelper.setOwnership(project3, new OwnershipDescription(true, "admin", Arrays.asList("user")))

WorkflowJob project4 = folder.createProject(WorkflowJob.class, "test")
GitSCM source1 = new GitSCM("https://gist.github.com/AbhyudayaSharma/1bccbb2e760ca0706907f451347d5727.git")
project4.setDefinition(new CpsScmFlowDefinition(source1, "Jenkinsfile"))
JobOwnerHelper.setOwnership(project3, new OwnershipDescription(true, "admin", Arrays.asList("user15")))

RoleBasedAuthorizationStrategy strategy = RoleBasedAuthorizationStrategy.getInstance()
def rand = new Random()
for (int i = 0; i < 500; i++) {
    def roleName = "myRole" + i
    def userName = "user" + i
    if (rand.nextBoolean()) {
        strategy.doAddRole(RoleBasedAuthorizationStrategy.PROJECT, roleName,
                "hudson.model.Item.Discover,hudson.model.Item.Read,hudson.model.Item.Build",
                "true", "F(o+)+lder[" + rand.nextInt(10) + rand.nextInt(10) + "]{1,2}")
    } else {
        strategy.doAddRole(RoleBasedAuthorizationStrategy.PROJECT, roleName,
                "hudson.model.Item.Discover,hudson.model.Item.Build,hudson.model.Item.Cancel",
                "true", "(((?=Folder)(?=([a-zA-Z]+)*)(?=Folder)(Folder))+)+["
                + rand.nextInt(10) + rand.nextInt(10) + "]*")
    }
    strategy.doAssignRole(RoleBasedAuthorizationStrategy.PROJECT, roleName, userName)
}

// TODO: Add Multi-Branch project, which does not build with Windows
