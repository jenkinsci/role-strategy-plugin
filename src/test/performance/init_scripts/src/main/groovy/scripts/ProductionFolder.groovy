// Initializes the production jobs directory, which runs with deployed scripts and repos

import com.cloudbees.hudson.plugins.folder.Folder
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy
import com.synopsys.arc.jenkins.plugins.ownership.OwnershipDescription
import com.synopsys.arc.jenkins.plugins.ownership.jobs.JobOwnerHelper
import hudson.plugins.git.GitSCM
import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

println("=== Initialize the Production folder")
if (Jenkins.instance.getItem("Production") != null) {
    println("Production folder has been already initialized, skipping the step")
    return
}

// Create multiple projects
GitSCM source1 = new GitSCM("https://gist.github.com/AbhyudayaSharma/1bccbb2e760ca0706907f451347d5727.git")
for (int i = 0; i < 500; i++) {
    def myFolder = Jenkins.getInstance().createProject(Folder.class, "Foooooooolder" + i)
    WorkflowJob project4 = myFolder.createProject(WorkflowJob.class, "test" + i)
    project4.setDefinition(new CpsScmFlowDefinition(source1, "Jenkinsfile"))
    JobOwnerHelper.setOwnership(project4, new OwnershipDescription(false, "admin", Arrays.asList("user")))
}

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
