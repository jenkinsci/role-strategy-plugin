.PHONY: build run debug

build:
	docker build -t abhyudaya/jenkins-role-strategy-slowdown-config .

run:
	docker run --rm --name ci-jenkins-io-dev -v maven-repo:/root/.m2 -p 8080:8080 -p 50000:50000 abhyudaya/jenkins-role-strategy-slowdown-config

debug:
	docker run --rm --name ci-jenkins-io-dev -e DEBUG=true -p 5005:5005 -v maven-repo:/root/.m2 -p 8080:8080 -p 50000:50000 abhyudaya/jenkins-role-strategy-slowdown-config
