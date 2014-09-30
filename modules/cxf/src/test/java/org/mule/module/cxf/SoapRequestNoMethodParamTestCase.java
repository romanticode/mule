/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.cxf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.mule.DefaultMuleMessage;
import org.mule.api.MuleMessage;
import org.mule.api.client.MuleClient;
import org.mule.api.endpoint.InboundEndpoint;
import org.mule.functional.junit4.FunctionalTestCase;
import org.mule.tck.junit4.rule.DynamicPort;

import org.junit.Rule;
import org.junit.Test;

public class SoapRequestNoMethodParamTestCase extends FunctionalTestCase
{
    private static final String request = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><soap:Body><receive xmlns=\"http://www.muleumo.org\"><src xmlns=\"http://www.muleumo.org\">Test String</src></receive></soap:Body></soap:Envelope>";
    private static final String response = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><ns1:receiveResponse xmlns:ns1=\"http://services.testmodels.tck.mule.org/\"><ns1:return>Received: null</ns1:return></ns1:receiveResponse></soap:Body></soap:Envelope>";

    @Rule
    public DynamicPort port1 = new DynamicPort("port1");

    @Override
    protected String getConfigFile()
    {
        return "soap-request-conf-flow.xml";
    }

    @Test
    public void testCXFSoapRequest() throws Exception
    {
        MuleClient client = muleContext.getClient();

        MuleMessage msg = client.send(
            ((InboundEndpoint) muleContext.getRegistry().lookupObject("httpInbound")).getAddress(),
            new DefaultMuleMessage(request, muleContext));

        assertNotNull(msg);
        assertNotNull(msg.getPayload());
        assertEquals(response, msg.getPayloadAsString());
    }
}
