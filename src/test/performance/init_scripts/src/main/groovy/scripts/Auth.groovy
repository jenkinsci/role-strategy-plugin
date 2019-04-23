import hudson.model.User
import jenkins.model.Jenkins
import jenkins.security.QueueItemAuthenticatorConfiguration
import org.jenkinsci.plugins.authorizeproject.GlobalQueueItemAuthenticator
import org.jenkinsci.plugins.authorizeproject.strategy.TriggeringUsersAuthorizationStrategy

boolean createAdmin = Boolean.getBoolean("io.jenkins.dev.security.createAdmin")

println("=== Configuring users")
def securityRealm = Jenkins.instance.getSecurityRealm()
for (int i = 0; i < 300; i++) {
    String userName = "user" + i;
    User user = securityRealm.createAccount(userName, "user")
    user.setFullName(userName.toUpperCase(Locale.US))
}

if (createAdmin) {
    User admin = securityRealm.createAccount("admin", "admin")
    admin.setFullName("Admin")
}

println("=== Configure Authorize Project")
GlobalQueueItemAuthenticator auth = new GlobalQueueItemAuthenticator(
        new TriggeringUsersAuthorizationStrategy()
)
QueueItemAuthenticatorConfiguration.get().authenticators.add(auth)
