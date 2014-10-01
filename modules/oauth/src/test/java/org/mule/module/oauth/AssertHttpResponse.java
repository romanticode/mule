package org.mule.module.oauth;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

import org.mule.util.ClassUtils;

import org.apache.http.client.methods.CloseableHttpResponse;

public class AssertHttpResponse
{

    protected final CloseableHttpResponse response;

    public AssertHttpResponse(CloseableHttpResponse response)
    {
        this.response = response;
    }

    public AssertHttpResponse assertStatusCodeIs(int statusCode)
    {
        assertThat(response.getStatusLine().getStatusCode(), is(statusCode));
        return this;
    }

    public AssertHttpResponse assertHasHeader(String headerName)
    {
        assertThat(response.getFirstHeader(headerName), notNullValue());
        return this;
    }

    public <U> U as(Class<U> assertClass)
    {
        try
        {
            return ClassUtils.instanciateClass(assertClass, response);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
