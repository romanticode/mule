/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.integration.exceptions;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import org.mule.api.MuleMessage;
import org.mule.api.client.LocalMuleClient;
import org.mule.api.context.notification.ExceptionNotificationListener;
import org.mule.api.context.notification.ServerNotification;
import org.mule.functional.junit4.FunctionalTestCase;
import org.mule.util.concurrent.Latch;

import java.util.concurrent.TimeUnit;

import org.hamcrest.core.IsNull;
import org.junit.Test;

public class CatchExceptionStrategyTransactionTestCase extends FunctionalTestCase
{

    public static final int TIMEOUT = 5000;
    public static final String MESSAGE = "any message";
    public static final int SHORT_TIMEOUT = 500;
    private Latch messageConsumed = new Latch();

    @Override
    protected String getConfigFile()
    {
        return "org/mule/test/integration/exceptions/catch-exception-strategy-transaction-flow.xml";
    }

    @Test
    public void testSingleTransactionIsCommittedOnFailure() throws Exception
    {
        LocalMuleClient client = muleContext.getClient();
        muleContext.registerListener(new ExceptionNotificationListener() {
            @Override
            public void onNotification(ServerNotification notification)
            {
                messageConsumed.release();
            }
        });
        client.dispatch("jms://in1?connector=activeMq", MESSAGE, null);
        messageConsumed.await(TIMEOUT, TimeUnit.MILLISECONDS);
        stopFlowConstruct("singleTransactionBehavior");
        MuleMessage request = client.request("jms://in?connector=activeMq", TIMEOUT);
        assertThat(request, IsNull.<Object>nullValue());
    }

    @Test
    public void testXaTransactionIsCommittedOnFailure() throws Exception
    {
        LocalMuleClient client = muleContext.getClient();
        muleContext.registerListener(new ExceptionNotificationListener() {
            @Override
            public void onNotification(ServerNotification notification)
            {
                messageConsumed.release();
            }
        });
        client.dispatch("jms://in2?connector=activeMq", MESSAGE, null);
        messageConsumed.await(TIMEOUT, TimeUnit.MILLISECONDS);
        stopFlowConstruct("xaTransactionBehavior");
        MuleMessage outMessage = client.request("jms://out2?connector=activeMq", TIMEOUT);
        assertThat(outMessage,IsNull.<Object>notNullValue());
        assertThat(outMessage.getPayloadAsString(), is(MESSAGE));
        MuleMessage inMessage = client.request("jms://in2?connector=activeMq", TIMEOUT);
        assertThat(inMessage,IsNull.<Object>nullValue());
        MuleMessage inVmMessage = client.request("vm://vmIn2",TIMEOUT);
        assertThat(inVmMessage, IsNull.<Object>notNullValue());
        assertThat(inVmMessage.getPayloadAsString(), is(MESSAGE));
    }

}
