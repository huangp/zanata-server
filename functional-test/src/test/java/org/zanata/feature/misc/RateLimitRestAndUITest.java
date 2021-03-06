/*
 * Copyright 2014, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.zanata.feature.misc;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.zanata.feature.Feature;
import org.zanata.feature.testharness.TestPlan.DetailedTest;
import org.zanata.feature.testharness.ZanataTestCase;
import org.zanata.page.administration.AdministrationPage;
import org.zanata.page.administration.ServerConfigurationPage;
import org.zanata.util.Constants;
import org.zanata.util.PropertiesHolder;
import org.zanata.util.ZanataRestCaller;
import org.zanata.workflow.LoginWorkFlow;

import javax.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Patrick Huang <a
 *         href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
@Feature(
        summary = "The system can be set to rate consecutive REST access calls",
        tcmsTestPlanIds = 5315, tcmsTestCaseIds = 0)
@Category(DetailedTest.class)
@Slf4j
public class RateLimitRestAndUITest extends ZanataTestCase {

    private static final String TRANSLATOR = "translator";
    private static final String TRANSLATOR_API =
            PropertiesHolder.getProperty(Constants.zanataTranslatorKey
                    .value());
    private String maxConcurrentPathParam = "c/max.concurrent.req.per.apikey";
    private String maxActivePathParam = "c/max.active.req.per.apikey";

    @Test(timeout = ZanataTestCase.MAX_SHORT_TEST_DURATION)
    public void canConfigureRateLimitByWebUI() {
        ServerConfigurationPage serverConfigPage = new LoginWorkFlow()
                .signIn("admin", "admin")
                .goToAdministration()
                .goToServerConfigPage();

        assertThat(serverConfigPage.getMaxConcurrentRequestsPerApiKey())
                .isEqualTo("default is 6");
        assertThat(serverConfigPage.getMaxActiveRequestsPerApiKey())
                .isEqualTo("default is 2");

        AdministrationPage administrationPage =
                serverConfigPage.inputMaxConcurrent(5).inputMaxActive(3).save();

        // RHBZ1160651
        // assertThat(administrationPage.getNotificationMessage())
        // .isEqualTo("Configuration was successfully updated.");

        serverConfigPage = administrationPage.goToServerConfigPage();

        assertThat(serverConfigPage.getMaxActiveRequestsPerApiKey())
                .isEqualTo("3");
        assertThat(serverConfigPage.getMaxConcurrentRequestsPerApiKey())
                .isEqualTo("5");
    }

    @Test(timeout = ZanataTestCase.MAX_SHORT_TEST_DURATION)
    public void canCallServerConfigurationRestService() throws Exception {
        WebResource.Builder clientRequest =
                clientRequestAsAdmin("rest/configurations/"
                        + maxConcurrentPathParam);
        clientRequest.entity("1", MediaType.APPLICATION_JSON_TYPE);
        // can put
        clientRequest.put();

        // can get single configuration
        String rateLimitConfig =
                clientRequestAsAdmin(
                        "rest/configurations/" + maxConcurrentPathParam).get(
                        String.class);

        assertThat(rateLimitConfig)
                .contains("max.concurrent.req.per.apikey");
        assertThat(rateLimitConfig).contains("<value>1</value>");

        // can get all configurations
        String configurations =
                clientRequestAsAdmin("rest/configurations/").get(String.class);
        log.info("result {}", configurations);

        assertThat(configurations).isNotNull();
    }

    private static WebResource.Builder clientRequestAsAdmin(String path) {
        return Client
                .create()
                .resource(PropertiesHolder.getProperty(Constants.zanataInstance
                        .value()) + path)
                .header("X-Auth-User", "admin")
                .header("X-Auth-Token",
                        PropertiesHolder.getProperty(Constants.zanataApiKey
                                .value()))
                .header("Content-Type", "application/xml")
                .header("Accept", "application/xml");
    }

    @Test(timeout = ZanataTestCase.MAX_SHORT_TEST_DURATION)
    public void serverConfigurationRestServiceOnlyAvailableToAdmin()
            throws Exception {
        // all request should be rejected
        ClientResponse response =
                clientRequestAsTranslator("rest/configurations/").get(
                        ClientResponse.class);
        assertThat(response.getStatus()).isEqualTo(403);

        ClientResponse response1 =
                clientRequestAsTranslator(
                        "rest/configurations/c/email.admin.addr").get(
                        ClientResponse.class);
        assertThat(response1.getStatus()).isEqualTo(403);

        WebResource.Builder request =
                clientRequestAsTranslator(
                "rest/configurations/c/email.admin.addr");
        request.entity("admin@email.com", MediaType.APPLICATION_JSON_TYPE);
        try {
            request.put();
        } catch (UniformInterfaceException e) {
            assertThat(e.getResponse().getStatus()).isEqualTo(403);
        }
    }

    private static WebResource.Builder clientRequestAsTranslator(String path) {
        return Client.create().resource(
                PropertiesHolder.getProperty(Constants.zanataInstance
                        .value()) + path)
                .header("X-Auth-User", TRANSLATOR)
                .header("X-Auth-Token", TRANSLATOR_API)
                .header("Content-Type", "application/xml")
                .header("Accept", "application/xml");
    }

    @Test(timeout = ZanataTestCase.MAX_SHORT_TEST_DURATION)
    public void canOnlyDealWithKnownConfiguration() throws Exception {
        WebResource.Builder clientRequest =
                clientRequestAsAdmin("rest/configurations/c/abc");

        try {
            clientRequest.put();
        } catch (UniformInterfaceException e) {
            assertThat(e.getResponse().getStatus()).isEqualTo(400);
        }

        ClientResponse getResponse =
                clientRequestAsAdmin("rest/configurations/c/abc").get(
                        ClientResponse.class);
        assertThat(getResponse.getStatus()).isEqualTo(404);
    }

    @Test(timeout = ZanataTestCase.MAX_SHORT_TEST_DURATION)
    @Ignore("RHBZ1218458")
    public void canLimitConcurrentRestRequestsPerAPIKey() throws Exception {
        // translator creates the project/version
        final String projectSlug = "project";
        final String iterationSlug = "version";
        new ZanataRestCaller(TRANSLATOR, TRANSLATOR_API)
                .createProjectAndVersion(projectSlug, iterationSlug, "gettext");

        WebResource.Builder clientRequest =
                clientRequestAsAdmin("rest/configurations/"
                        + maxConcurrentPathParam);
        clientRequest.entity("2", MediaType.APPLICATION_JSON_TYPE);

        clientRequest.put();

        // prepare to fire multiple REST requests
        final AtomicInteger atomicInteger = new AtomicInteger(1);

        // requests from translator user
        final int translatorThreads = 3;
        Callable<Integer> translatorTask = new Callable<Integer>() {

            @Override
            public Integer call() {
                return invokeRestService(new ZanataRestCaller(TRANSLATOR,
                        TRANSLATOR_API), projectSlug, iterationSlug,
                        atomicInteger);
            }
        };
        List<Callable<Integer>> translatorTasks =
                Collections.nCopies(translatorThreads, translatorTask);

        // requests from admin user
        int adminThreads = 2;
        Callable<Integer> adminTask = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return invokeRestService(new ZanataRestCaller(), projectSlug,
                        iterationSlug, atomicInteger);
            }
        };

        List<Callable<Integer>> adminTasks =
                Collections.nCopies(adminThreads, adminTask);

        ExecutorService executorService =
                Executors.newFixedThreadPool(translatorThreads + adminThreads);

        List<Callable<Integer>> tasks =
                ImmutableList.<Callable<Integer>> builder()
                        .addAll(translatorTasks).addAll(adminTasks).build();

        List<Future<Integer>> futures = executorService.invokeAll(tasks);

        List<Integer> result = getResultStatusCodes(futures);

        // 1 request from translator should get 429 and fail
        log.info("result: {}", result);
        assertThat(result).contains(201, 201, 201, 201, 429);
    }

    @Test(timeout = 5000)
    public void exceptionWillReleaseSemaphore() throws Exception {
        // Given: max active is set to 1
        WebResource.Builder configRequest =
                clientRequestAsAdmin("rest/configurations/"
                        + maxActivePathParam);
        configRequest.entity("1", MediaType.APPLICATION_JSON_TYPE);
        configRequest.put();

        // When: multiple requests that will result in a mapped exception
        WebResource.Builder clientRequest =
                clientRequestAsAdmin(
                "rest/test/data/sample/dummy?exception=org.zanata.rest.NoSuchEntityException");
        clientRequest.get(ClientResponse.class);
        clientRequest.get(ClientResponse.class);
        clientRequest.get(ClientResponse.class);
        clientRequest.get(ClientResponse.class);

        // Then: request that result in exception should still release
        // semaphore. i.e. no permit leak
        assertThat(1).isEqualTo(1);
    }

    @Test(timeout = 5000)
    public void unmappedExceptionWillAlsoReleaseSemaphore() throws Exception {
        // Given: max active is set to 1
        WebResource.Builder configRequest =
                clientRequestAsAdmin("rest/configurations/"
                        + maxActivePathParam);
        configRequest.entity("1", MediaType.APPLICATION_JSON_TYPE);
        configRequest.put();

        // When: multiple requests that will result in an unmapped exception
        WebResource.Builder clientRequest =
                clientRequestAsAdmin(
                "rest/test/data/sample/dummy?exception=java.lang.RuntimeException");
        clientRequest.get(ClientResponse.class);
        clientRequest.get(ClientResponse.class);
        clientRequest.get(ClientResponse.class);
        clientRequest.get(ClientResponse.class);

        // Then: request that result in exception should still release
        // semaphore. i.e. no permit leak
        assertThat(1).isEqualTo(1);
    }

    private static Integer invokeRestService(ZanataRestCaller restCaller,
            String projectSlug, String iterationSlug,
            AtomicInteger atomicInteger) {
        try {
            int counter = atomicInteger.getAndIncrement();
            return restCaller.postSourceDocResource(projectSlug, iterationSlug,
                    ZanataRestCaller.buildSourceResource("doc" + counter,
                            ZanataRestCaller.buildTextFlow("res" + counter,
                                    "content" + counter)), false);
        } catch (UniformInterfaceException e) {
            log.info("rest call failed: {}", e.getMessage());
            return e.getResponse().getStatus();
        }
    }

    private static List<Integer> getResultStatusCodes(
            List<Future<Integer>> futures) {
        return Lists.transform(futures,
                input -> {
                    try {
                        return input.get();
                    } catch (Exception e) {
                        // by using filter we lose RESTeasy's exception
                        // translation
                        String message = e.getMessage().toLowerCase();
                        if (message
                                .matches(".+429.+too many concurrent request.+")) {
                            return 429;
                        }
                        throw Throwables.propagate(e);
                    }
                });
    }
}

