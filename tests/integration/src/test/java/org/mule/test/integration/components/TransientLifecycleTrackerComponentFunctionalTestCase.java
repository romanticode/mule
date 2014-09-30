/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.integration.components;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.mule.api.client.MuleClient;
import org.mule.tck.lifecycle.AbstractLifecycleTracker;
import org.mule.functional.junit4.FunctionalTestCase;

import org.junit.Test;

/**
 * @author David Dossot (david@dossot.net) See
 *         http://mule.mulesoft.org/jira/browse/MULE-3846
 */
public class TransientLifecycleTrackerComponentFunctionalTestCase extends FunctionalTestCase
{

    @Override
    protected String getConfigFile()
    {
        return "org/mule/test/integration/components/component-lifecycle-config-flow.xml";
    }

    /**
     * ASSERT: - Mule stop/start lifecycle methods invoked - Mule initialize/dipose
     * lifecycle methods NOT invoked - Spring lifecycle methods invoked - Service and
     * muleContext injected (Component implements ServiceAware/MuleContextAware)
     * NOTE: muleContext is injected twice, once by registry and once by
     * lifecycleAdaptor
     */
    @Test
    public void testSpringBeanServiceLifecycle() throws Exception
    {
        String expectedLifeCycle = "[setProperty, setMuleContext, springInitialize, setFlowConstruct, start, stop, springDestroy]";

        testComponentLifecycle("SpringBeanService", expectedLifeCycle);
    }

    /**
     * ASSERT: - Mule stop/start lifecycle methods invoked - Mule initialize/dipose
     * lifecycle methods NOT invoked - Spring lifecycle methods NOT invoked - Service
     * and muleContext injected (Component implements ServiceAware/MuleContextAware)
     * NOTE: muleContext is injected twice, once by registry and once by
     * lifecycleAdaptor
     */
    @Test
    public void testSpringBeanService2Lifecycle() throws Exception
    {
        String expectedLifeCycle = "[setProperty, setMuleContext, setFlowConstruct, start, stop]";

        testComponentLifecycle("SpringBeanService2", expectedLifeCycle);
    }

    /**
     * ASSERT: - Mule lifecycle methods invoked - Service and muleContext injected
     * (Component implements ServiceAware/MuleContextAware)
     */
    @Test
    public void testSingletonServiceLifecycle() throws Exception
    {
        String expectedLifeCycle = "[setProperty, setFlowConstruct, setMuleContext, initialise, start, stop, dispose]";

        testComponentLifecycle("MuleSingletonService", expectedLifeCycle);
    }

    /**
     * ASSERT: - Mule lifecycle methods invoked - Service and muleContext injected
     * (Component implements ServiceAware/MuleContextAware)
     */
    @Test
    public void testMulePrototypeServiceLifecycle() throws Exception
    {
        String expectedLifeCycle = "[setProperty, setFlowConstruct, setMuleContext, initialise, start, stop, dispose]";

        testComponentLifecycle("MulePrototypeService", expectedLifeCycle);
    }

    /**
     * ASSERT: - Mule lifecycle methods invoked - Service and muleContext injected
     * (Component implements ServiceAware/MuleContextAware)
     */
    @Test
    public void testMulePooledPrototypeServiceLifecycle() throws Exception
    {
        String expectedLifeCycle = "[setProperty, setFlowConstruct, setMuleContext, initialise, start, stop, dispose]";

        testComponentLifecycle("MulePooledPrototypeService", expectedLifeCycle);
    }

    /**
     * ASSERT: - Mule lifecycle methods invoked each time singleton is used to create
     * new object in pool NOTE: injecting service/muleContext only once since this is
     * a pooled singleton
     */
    @Test
    public void testMulePooledSingletonServiceLifecycle() throws Exception
    {
        String expectedLifeCycle = "[setProperty, setFlowConstruct, setMuleContext, initialise, initialise, initialise, start, start, start, stop, stop, stop, dispose, dispose, dispose]";

        testComponentLifecycle("MulePooledSingletonService", expectedLifeCycle);
    }

    private void testComponentLifecycle(final String serviceName, final String expectedLifeCycle)
        throws Exception
    {
        AbstractLifecycleTracker tracker = exerciseComponent(serviceName);

        muleContext.dispose();

        assertEquals(serviceName, expectedLifeCycle, tracker.getTracker().toString());
    }

    private AbstractLifecycleTracker exerciseComponent(final String serviceName) throws Exception
    {
        MuleClient client = muleContext.getClient();

        final AbstractLifecycleTracker ltc = (AbstractLifecycleTracker) client.send(
            "vm://" + serviceName + ".In", null, null).getPayload();
        assertNotNull(ltc);
        return ltc;
    }
}
