[![Travis build status](https://img.shields.io/travis/mike10004/gae-testing-support.svg)](https://travis-ci.org/mike10004/gae-testing-support)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.mike10004/gae-testing-support.svg)](https://repo1.maven.org/maven2/com/github/mike10004/gae-testing-support/)

gae-testing-support
===================

A library that makes it easier to do integration testing of a Google App
Engine app. Uses the Google Cloud SDK and App Engine Java SDK. Modeled after
the GCloud Maven Plugin's start and stop mojos. Distributed under the same
license as that project, as we use a lot of its code.

Maven
-----

        <dependency>
            <groupId>com.github.mike10004</groupId>
            <artifactId>gae-testing-support</artifactId>
            <version>0.3</version>
            <scope>test</scope>
        </dependency>

