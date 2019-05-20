Demo image for Testing the Plugin's performance
===

This configuration is designed to experience the performance slowdowns on a large number
of roles. Based on [oleg-nenashev/demo-jenkins-config-as-code](https://github.com/oleg-nenashev/demo-jenkins-config-as-code).

### Usage

Run image:

```shell
docker run --rm --name ci-jenkins-io-dev -v maven-repo:/root/.m2 -p 8080:8080 -p 50000:50000 abhyudaya/jenkins-role-strategy-slowdown-config
```

Jenkins will need to connect to the Docker host to run agents.
If you use Docker for Mac, use `-Dio.jenkins.dev.host` and additional `socat` image for forwarding.

```shell
docker run -d -v /var/run/docker.sock:/var/run/docker.sock -p 2376:2375 bobrik/socat TCP4-LISTEN:2375,fork,reuseaddr UNIX-CONNECT:/var/run/docker.sock
```

##### Debugging Master

In order to debug the master, use the `-e DEBUG=true -p 5005:5005` when starting the container.
Jenkins will be suspended on the startup in such case.

If you open parent POM as a Maven project in your IDE, 
you will be also able to debug initialization Groovy scripts.

### Building image

```shell
docker build -t abhyudaya/jenkins-role-strategy-slowdown-config .
```
