/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.transport.udp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import org.mule.api.endpoint.InboundEndpoint;
import org.mule.api.transport.Connector;
import org.mule.construct.Flow;
import org.mule.tck.transport.AbstractConnectorTestCase;

import java.net.DatagramPacket;

import org.junit.Test;

public class UdpConnectorTestCase extends AbstractConnectorTestCase
{

    @Override
    public Connector createConnector() throws Exception
    {
        UdpConnector c = new UdpConnector(muleContext);
        c.setName("UdpConnector");
        return c;
    }

    @Override
    public String getTestEndpointURI()
    {
        return "udp://localhost:61024";
    }

    @Override
    public Object getValidMessage() throws Exception
    {
        return new DatagramPacket("Hello".getBytes(), 5);
    }

    @Test
    public void testValidListener() throws Exception
    {
        Connector connector = getConnector();

        InboundEndpoint endpoint2 = muleContext.getEndpointFactory()
            .getInboundEndpoint("udp://localhost:3456");

        connector.registerListener(endpoint2, getSensingNullMessageProcessor(), mock(Flow.class));
        try
        {
            connector.registerListener(endpoint2, getSensingNullMessageProcessor(), mock(Flow.class));
            fail("cannot register on the same endpointUri");
        }
        catch (Exception e)
        {
            // expected
        }
    }

    @Test
    public void testProperties() throws Exception
    {
        UdpConnector connector = (UdpConnector)this.getConnector();

        connector.setReceiveBufferSize(1024);
        assertEquals(1024, connector.getReceiveBufferSize());
        connector.setReceiveBufferSize(0);
        assertEquals(UdpConnector.DEFAULT_BUFFER_SIZE, connector.getReceiveBufferSize());

        connector.setTimeout(-1);
        assertEquals(UdpConnector.DEFAULT_SOCKET_TIMEOUT, connector.getTimeout());
    }

}
