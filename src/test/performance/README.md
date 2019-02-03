Demo image for Testing the Plugin's performance
===

This configuration is designed to experience the performance slowdowns on a large number
of roles. Based on [oleg-nenashev/demo-jenkins-config-as-code](https://github.com/oleg-nenashev/demo-jenkins-config-as-code).

### Usage

Run image:

```shell
docker run --rm --name ci-jenkins-io-dev -v maven-repo:/root/.m2 -e DEV_HOST=${CURRENT_HOST} -p 8080:8080 -p 50000:50000 onenashev/demo-jenkins-config-as-code
```

Jenkins will need to connect to the Docker host to run agents.
If you use Docker for Mac, use `-Dio.jenkins.dev.host` and additional `socat` image for forwarding.

```shell
docker run -d -v /var/run/docker.sock:/var/run/docker.sock -p 2376:2375 bobrik/socat TCP4-LISTEN:2375,fork,reuseaddr UNIX-CONNECT:/var/run/docker.sock
```

#### Developing Pipeline libraries

In the _Development_ folder there is a _PipelineLib_ folder, which allows local building and testing of the library.
This folder can be mapped to a local repository in order to develop the library without committing changes: 

```shell
docker run --rm --name ci-jenkins-io-dev -v maven-repo:/root/.m2 -v ${MY_PIPELINE_LIBRARY_DIR}:/var/jenkins_home/pipeline-library -v ${MY_OTHER_PIPELINE_LIBS_DIRS}:/var/jenkins_home/pipeline-libs -e DEV_HOST=${CURRENT_HOST} -p 8080:8080 -p 50000:50000  onenashev/demo-jenkins-config-as-code
```

Once started, you can just start editing the Pipeline library locally.
On every job start the changes will be reflected in the directory without committing anything.

##### Debugging Master

In order to debug the master, use the `-e DEBUG=true -p 5005:5005` when starting the container.
Jenkins will be suspended on the startup in such case.

If you open parent POM as a Maven project in your IDE, 
you will be also able to debug initialization Groovy scripts.

### Building images

#### Agents

Having a local agent build is a prerequisite for using the master
for high-speed builds with Maven repository caching.
For this purpose there is a custom Dockerfile in the `/agent` folder.

```shell
cd agent && docker build -t onenashev/demo-jenkins-maven-builder .
```

#### Master

Build image:

```shell
docker build -t onenashev/demo-jenkins-config-as-code .
```
