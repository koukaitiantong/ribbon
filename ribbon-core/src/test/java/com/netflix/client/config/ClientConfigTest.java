/*
*
* Copyright 2013 Netflix, Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
*/
package com.netflix.client.config;

import static org.junit.Assert.*;

import com.netflix.config.ConfigurationManager;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runners.MethodSorters;

import java.util.Properties;

/**
 * Test cases to verify the correctness of the Client Configuration settings
 * 
 * @author stonse
 * 
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ClientConfigTest {
    @Rule
    public TestName testName = new TestName();

    IClientConfigKey<Integer> INTEGER_PROPERTY;

    IClientConfigKey<Integer> DEFAULT_INTEGER_PROPERTY;

    @Before
    public void setUp() throws Exception {
        INTEGER_PROPERTY = new CommonClientConfigKey<Integer>(
                "niws.loadbalancer.%s." + testName.getMethodName(), 10) {};
        DEFAULT_INTEGER_PROPERTY = new CommonClientConfigKey<Integer>(
                "niws.loadbalancer.default." + testName.getMethodName(), 30) {};
    }

    @AfterClass
    public static void shutdown() throws Exception {
    }

    @Test
    public void testNiwsConfigViaProperties() throws Exception {
    	DefaultClientConfigImpl clientConfig = new DefaultClientConfigImpl();
    	DefaultClientConfigImpl override = new DefaultClientConfigImpl();
    	clientConfig.loadDefaultValues();
        Properties props = new Properties();
        
        final String restClientName = "testRestClient";
        
        props.setProperty("netflix.appinfo.stack","xbox");
        props.setProperty("netflix.environment","test");
        props.setProperty("appname", "movieservice");
        props.setProperty(restClientName + ".ribbon." + CommonClientConfigKey.AppName.key(), "movieservice");
        props.setProperty(restClientName + ".ribbon." + CommonClientConfigKey.DeploymentContextBasedVipAddresses.key(), "${appname}-${netflix.appinfo.stack}-${netflix.environment},movieservice--${netflix.environment}");
        props.setProperty(restClientName + ".ribbon." + CommonClientConfigKey.EnableZoneAffinity.key(), "false");

        ConfigurationManager.loadProperties(props);
        ConfigurationManager.getConfigInstance().setProperty("testRestClient.ribbon.customProperty", "abc");
        
        clientConfig.loadProperties(restClientName);
        clientConfig.set(CommonClientConfigKey.ConnectTimeout, 1000);
        override.set(CommonClientConfigKey.Port, 8000);
        override.set(CommonClientConfigKey.ConnectTimeout, 5000);
        clientConfig.applyOverride(override);
        
        Assert.assertEquals("movieservice", clientConfig.get(CommonClientConfigKey.AppName));
        Assert.assertEquals(false, clientConfig.get(CommonClientConfigKey.EnableZoneAffinity));
        Assert.assertEquals("movieservice-xbox-test,movieservice--test", clientConfig.resolveDeploymentContextbasedVipAddresses());
        Assert.assertEquals(5000, clientConfig.get(CommonClientConfigKey.ConnectTimeout).longValue());

        Assert.assertEquals(8000, clientConfig.get(CommonClientConfigKey.Port).longValue());
        System.out.println("AutoVipAddress:" + clientConfig.resolveDeploymentContextbasedVipAddresses());
        
        ConfigurationManager.getConfigInstance().setProperty("testRestClient.ribbon.EnableZoneAffinity", "true");
        assertEquals(true, clientConfig.get(CommonClientConfigKey.EnableZoneAffinity));
    }
    
    @Test
    public void testresolveDeploymentContextbasedVipAddresses() throws Exception {
        final String restClientName = "testRestClient2";

    	DefaultClientConfigImpl clientConfig = new DefaultClientConfigImpl();
    	clientConfig.loadDefaultValues();

        Properties props = new Properties();
        props.setProperty(restClientName + ".ribbon." + CommonClientConfigKey.AppName.key(), "movieservice");
        props.setProperty(restClientName + ".ribbon." + CommonClientConfigKey.DeploymentContextBasedVipAddresses.key(), "${<appname>}-${netflix.appinfo.stack}-${netflix.environment}:${<port>},${<appname>}--${netflix.environment}:${<port>}");
        props.setProperty(restClientName + ".ribbon." + CommonClientConfigKey.Port.key(), "7001");
        props.setProperty(restClientName + ".ribbon." + CommonClientConfigKey.EnableZoneAffinity.key(), "true");

        ConfigurationManager.loadProperties(props);

        clientConfig.loadProperties(restClientName);

        Assert.assertEquals("movieservice", clientConfig.get(CommonClientConfigKey.AppName));
        Assert.assertEquals(true, clientConfig.get(CommonClientConfigKey.EnableZoneAffinity));
        
        ConfigurationManager.getConfigInstance().setProperty("testRestClient2.ribbon.DeploymentContextBasedVipAddresses", "movieservice-xbox-test:7001");
        assertEquals("movieservice-xbox-test:7001", clientConfig.get(CommonClientConfigKey.DeploymentContextBasedVipAddresses));
        
        ConfigurationManager.getConfigInstance().clearProperty("testRestClient2.ribbon.EnableZoneAffinity");
        assertFalse(clientConfig.get(CommonClientConfigKey.EnableZoneAffinity));
    }

    @Test
    public void testFallback_noneSet() {
        DefaultClientConfigImpl clientConfig = new DefaultClientConfigImpl();
        Property<Integer> prop = clientConfig.getGlobalProperty(INTEGER_PROPERTY.format(testName.getMethodName()))
                .fallbackWith(clientConfig.getGlobalProperty(DEFAULT_INTEGER_PROPERTY));

        Assert.assertEquals(30, prop.get().intValue());
    }

    @Test
    public void testFallback_fallbackSet() {
        ConfigurationManager.getConfigInstance().setProperty(DEFAULT_INTEGER_PROPERTY.key(), "100");
        DefaultClientConfigImpl clientConfig = new DefaultClientConfigImpl();
        Property<Integer> prop = clientConfig.getGlobalProperty(INTEGER_PROPERTY.format(testName.getMethodName()))
                .fallbackWith(clientConfig.getGlobalProperty(DEFAULT_INTEGER_PROPERTY));

        Assert.assertEquals(100, prop.get().intValue());
    }

    @Test
    public void testFallback_primarySet() {

        ConfigurationManager.getConfigInstance().setProperty(INTEGER_PROPERTY.format(testName.getMethodName()).key(), "200");

        DefaultClientConfigImpl clientConfig = new DefaultClientConfigImpl();
        Property<Integer> prop = clientConfig.getGlobalProperty(INTEGER_PROPERTY.format(testName.getMethodName()))
                .fallbackWith(clientConfig.getGlobalProperty(DEFAULT_INTEGER_PROPERTY));

        Assert.assertEquals(200, prop.get().intValue());
    }

    static class CustomValueOf {
        private final String value;

        public static CustomValueOf valueOf(String value) {
            return new CustomValueOf(value);
        }

        public CustomValueOf(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static IClientConfigKey<CustomValueOf> CUSTOM_KEY = new CommonClientConfigKey<CustomValueOf>("CustomValueOf", new CustomValueOf("default")) {};

    @Test
    public void testValueOfWithDefault() {
        DefaultClientConfigImpl clientConfig = new DefaultClientConfigImpl();

        CustomValueOf prop = clientConfig.getOrDefault(CUSTOM_KEY);
        Assert.assertEquals("default", prop.getValue());
    }

    @Test
    public void testValueOf() {
        ConfigurationManager.getConfigInstance().setProperty("testValueOf.ribbon.CustomValueOf", "value");

        DefaultClientConfigImpl clientConfig = new DefaultClientConfigImpl();
        clientConfig.setClientName("testValueOf");

        Property<CustomValueOf> prop = clientConfig.getDynamicProperty(CUSTOM_KEY);
        Assert.assertEquals("value", prop.get().getValue());
    }
}

