package com.github.mike10004.gaetesting;

import org.junit.Test;

import static org.junit.Assert.*;

public class GCloudAsyncRunnerFactoryTest {

    @Test
    public void forMavenProject_factory() {
        GCloudAsyncRunnerFactory.forMavenProject().factory(); // ok as long as no exception
    }

    @Test
    public void DevServerRule_build() {
        DevServerRule.factoryBuilder().rule(); // ok as long as no exception
    }

}