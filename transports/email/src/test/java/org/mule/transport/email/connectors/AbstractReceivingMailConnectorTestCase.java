/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.transport.email.connectors;

import static org.junit.Assert.assertTrue;
import org.mule.api.MuleEventContext;
import org.mule.api.config.MuleProperties;
import org.mule.api.endpoint.EndpointBuilder;
import org.mule.api.endpoint.InboundEndpoint;
import org.mule.api.processor.MessageProcessor;
import org.mule.component.DefaultJavaComponent;
import org.mule.construct.Flow;
import org.mule.object.SingletonObjectFactory;
import org.mule.functional.functional.EventCallback;
import org.mule.functional.functional.FunctionalTestComponent;
import org.mule.transport.email.transformers.EmailMessageToString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/**
 * Given an endpoint ({@link #getTestEndpointURI()}) this waits for up to 10 seconds,
 * hoping to receive the message stored in the mail server.  It also runs the unit tests
 * defined way down in {@link org.mule.tck.transport.AbstractConnectorTestCase}.
 */
public abstract class AbstractReceivingMailConnectorTestCase extends AbstractMailConnectorFunctionalTestCase
{

    public static final int POLL_PERIOD_MS = 2000;
    public static final int WAIT_PERIOD_MS = 4 * POLL_PERIOD_MS;

    protected AbstractReceivingMailConnectorTestCase(String protocol)
    {
        super(SEND_INITIAL_EMAIL, protocol);
    }

    @Test
    public void testReceiver() throws Exception
    {
        final CountDownLatch countDown = new CountDownLatch(1);

        EventCallback eventCallback = new EventCallback()
        {
            public synchronized void eventReceived(MuleEventContext context, Object component)
            {
                try
                {
                    logger.debug("woot - event received");
                    logger.debug("context: " + context);
                    logger.debug("component: " + component);
                    assertMessageOk(context.getMessage().getOriginalPayload());
                    countDown.countDown();
                }
                catch (Exception e)
                {
                    // counter will not be incremented
                    logger.error(e.getMessage(), e);
                }
            }
        };

        Flow flowConstruct = new Flow("testComponent", muleContext);
        flowConstruct.setMessageProcessors(new ArrayList<MessageProcessor>());
        FunctionalTestComponent component = new FunctionalTestComponent();
        component.setEventCallback(eventCallback);
        flowConstruct.getMessageProcessors().add(new DefaultJavaComponent(new SingletonObjectFactory(component)));
        EndpointBuilder eb = muleContext.getEndpointFactory().getEndpointBuilder(getTestEndpointURI());
        eb.setDisableTransportTransformer(true);
        InboundEndpoint ep = eb.buildInboundEndpoint();
        flowConstruct.setMessageSource(ep);
        muleContext.getRegistry().registerFlowConstruct(flowConstruct);
        if (!muleContext.isStarted())
        {
            muleContext.start();
        }

        logger.debug("waiting for count down");
        assertTrue(countDown.await(WAIT_PERIOD_MS, TimeUnit.MILLISECONDS));
    }

    protected static Map newEmailToStringServiceOverrides()
    {
        Map serviceOverrides = new HashMap();
        serviceOverrides.put(MuleProperties.CONNECTOR_INBOUND_TRANSFORMER,
                             EmailMessageToString.class.getName());
        return serviceOverrides;
    }

}
