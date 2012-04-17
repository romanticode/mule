/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.routing;

import org.mule.DefaultMuleEvent;
import org.mule.DefaultMuleMessage;
<<<<<<< .working
import org.mule.OptimizedRequestContext;
=======
import org.mule.VoidMuleEvent;
>>>>>>> .merge-right.r24267
import org.mule.api.MessagingException;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.MuleRuntimeException;
import org.mule.api.endpoint.EndpointBuilder;
import org.mule.api.endpoint.EndpointException;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.processor.MessageProcessor;
import org.mule.api.retry.RetryCallback;
import org.mule.api.retry.RetryContext;
import org.mule.api.retry.RetryNotifier;
import org.mule.api.retry.RetryPolicyTemplate;
import org.mule.api.store.ListableObjectStore;
import org.mule.api.store.ObjectStoreException;
import org.mule.config.i18n.MessageFactory;
import org.mule.retry.async.AsynchronousRetryTemplate;
import org.mule.retry.policies.SimpleRetryPolicyTemplate;
import org.mule.routing.filters.ExpressionFilter;
import org.mule.routing.outbound.AbstractOutboundRouter;
import org.mule.util.SystemUtils;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

/**
 * UntilSuccessful attempts to route a message to the message processor it contains in an asynchronous manner. Routing
 * is considered successful if no exception has been raised and, optionally, if the response matches an expression.
 * UntilSuccessful can optionally be configured to synchronously return an acknowledgment message when it has scheduled
 * the event for processing. UntilSuccessful is backed by a {@link ListableObjectStore} for storing the events that are
 * pending (re)processing.
 */
public class UntilSuccessful extends AbstractOutboundRouter
{
    public static class EventStoreKey implements Serializable
    {
        private static final long serialVersionUID = 1L;
        private final String value;

        private EventStoreKey(final String value)
        {
            this.value = value;
        }

        public static EventStoreKey buildFor(final MuleEvent muleEvent)
        {
            // the key is built in way to prevent UntilSuccessful workers across a cluster to compete for the same
            // events over a shared object store
            String key = muleEvent.getFlowConstruct() + "@"
                + muleEvent.getMuleContext().getClusterId() + ":" + muleEvent.getId();
            return new EventStoreKey(SystemUtils.legalizeFileName(key));
        }

        @Override
        public String toString()
        {
            return value;
        }

        @Override
        public int hashCode()
        {
            return value.hashCode();
        }

        @Override
        public boolean equals(final Object obj)
        {
            if (!(obj instanceof EventStoreKey))
            {
                return false;
            }

            return value.equals(((EventStoreKey) obj).value);
        }
    }

    public static final String PROCESS_ATTEMPT_COUNT_PROPERTY_NAME = "process.attempt.count";

    private static final int DEFAULT_PROCESS_ATTEMPT_COUNT_PROPERTY_VALUE = 1;

    private ListableObjectStore<MuleEvent> objectStore;
    private int maxRetries = 5;
    private long secondsBetweenRetries = 60L;
    private String failureExpression;
    private String ackExpression;
    private ExpressionFilter failureExpressionFilter;
    private String eventKeyPrefix;
    private Object deadLetterQueue;
    private MessageProcessor dlqMP;

    @Override
    public void initialise() throws InitialisationException
    {
        if (routes.isEmpty())
        {
            throw new InitialisationException(
                MessageFactory.createStaticMessage("One message processor must be configured within UntilSuccessful."),
                this);
        }

        if (routes.size() > 1)
        {
            throw new InitialisationException(
                MessageFactory.createStaticMessage("Only one message processor is allowed within UntilSuccessful."
                                                   + " Use a Processor Chain to group several message processors into one."),
                this);
        }

        if (objectStore == null)
        {
            throw new InitialisationException(
                MessageFactory.createStaticMessage("A ListableObjectStore must be configured on UntilSuccessful."),
                this);
        }

        super.initialise();

        if (deadLetterQueue != null)
        {
            if (deadLetterQueue instanceof EndpointBuilder)
            {
                try
                {

                    dlqMP = ((EndpointBuilder) deadLetterQueue).buildOutboundEndpoint();
                }
                catch (final EndpointException ee)
                {
                    throw new InitialisationException(
                        MessageFactory.createStaticMessage("deadLetterQueue-ref is not a valid endpoint builder: "
                                                           + deadLetterQueue), ee, this);
                }
            }
            else if (deadLetterQueue instanceof MessageProcessor)
            {
                dlqMP = (MessageProcessor) deadLetterQueue;
            }
            else
            {
                throw new InitialisationException(
                    MessageFactory.createStaticMessage("deadLetterQueue-ref is not a valid mesage processor: "
                                                       + deadLetterQueue), null, this);
            }
        }

        if (failureExpression != null)
        {
            failureExpressionFilter = new ExpressionFilter(failureExpression);
        }
        else
        {
            failureExpressionFilter = new ExpressionFilter("exception-type:");
        }
        failureExpressionFilter.setMuleContext(muleContext);

        if ((ackExpression != null) && (!muleContext.getExpressionManager().isExpression(ackExpression)))
        {
            throw new InitialisationException(MessageFactory.createStaticMessage("Invalid ackExpression: "
                                                                                 + ackExpression), this);
        }

        eventKeyPrefix = flowConstruct.getName() + "@" + muleContext.getClusterId() + ":";
    }

    @Override
    public void start() throws MuleException
    {
        super.start();
        scheduleAllPendingEventsForProcessing();
    }

    @Override
    public boolean isMatch(final MuleMessage message) throws MuleException
    {
        return true;
    }

    @Override
    protected MuleEvent route(final MuleEvent event) throws MessagingException
    {
        try
        {
            ensurePayloadSerializable(event);
        }
        catch (final Exception e)
        {
            throw new MessagingException(
                MessageFactory.createStaticMessage("Failed to prepare message for processing"), event, e, this);
        }

        try
        {
            final EventStoreKey eventStoreKey = storeEvent(event);
            scheduleForProcessing(eventStoreKey);

            if (ackExpression == null)
            {
                return VoidMuleEvent.getInstance();
            }

            final Object ackResponsePayload = muleContext.getExpressionManager().evaluate(ackExpression,
                event);

            event.getMessage().setPayload(ackResponsePayload);
            return event;   
        }
        catch (final Exception e)
        {
            throw new MessagingException(
                MessageFactory.createStaticMessage("Failed to schedule the event for processing"), event, e, this);
        }
    }

    private void scheduleAllPendingEventsForProcessing() throws ObjectStoreException
    {
        for (final Serializable eventStoreKey : objectStore.allKeys())
        {
            try
            {
                scheduleForProcessing((EventStoreKey) eventStoreKey);
            }
            catch (final Exception e)
            {
                logger.error(
                    MessageFactory.createStaticMessage("Failed to schedule for processing event stored with key: "
                                                       + eventStoreKey), e);
            }
        }
    }

    private void scheduleForProcessing(final EventStoreKey eventStoreKey) throws Exception
    {
        final RetryCallback callback = new RetryCallback()
        {
            @Override
            public String getWorkDescription()
            {
                return "Until successful processing of event stored under key: " + eventStoreKey;
            }

            @Override
            public void doWork(final RetryContext context) throws Exception
            {
                retrieveAndProcessEvent(eventStoreKey);
            }
        };

        final SimpleRetryPolicyTemplate simpleRetryPolicyTemplate = new SimpleRetryPolicyTemplate(
            TimeUnit.SECONDS.toMillis(secondsBetweenRetries), maxRetries);

        final RetryPolicyTemplate retryPolicyTemplate = new AsynchronousRetryTemplate(
            simpleRetryPolicyTemplate);
        retryPolicyTemplate.setNotifier(new RetryNotifier()
        {
            @Override
            public void onSuccess(final RetryContext context)
            {
                removeFromStore(eventStoreKey);
            }

            @Override
            public void onFailure(final RetryContext context, final Throwable e)
            {
                incrementProcessAttemptCountOrRemoveFromStore(eventStoreKey);
            }
        });

        retryPolicyTemplate.execute(callback, muleContext.getWorkManager());
    }

    private EventStoreKey storeEvent(final MuleEvent event) throws ObjectStoreException
    {
        final MuleMessage message = event.getMessage();
        final Integer deliveryAttemptCount = message.getInvocationProperty(
            PROCESS_ATTEMPT_COUNT_PROPERTY_NAME, DEFAULT_PROCESS_ATTEMPT_COUNT_PROPERTY_VALUE);
        return storeEvent(event, deliveryAttemptCount);
    }

    private EventStoreKey storeEvent(final MuleEvent event, final int deliveryAttemptCount)
        throws ObjectStoreException
    {
        final MuleMessage message = event.getMessage();
        message.setInvocationProperty(PROCESS_ATTEMPT_COUNT_PROPERTY_NAME, deliveryAttemptCount);
        final EventStoreKey eventStoreKey = EventStoreKey.buildFor(event);
        objectStore.store(eventStoreKey, event);
        return eventStoreKey;
    }

    private void incrementProcessAttemptCountOrRemoveFromStore(final EventStoreKey eventStoreKey)
    {
        try
        {
            final MuleEvent event = objectStore.remove(eventStoreKey);
            final MuleEvent mutableEvent = DefaultMuleEvent.copy(event);
            OptimizedRequestContext.unsafeSetEvent(mutableEvent);

            final MuleMessage message = mutableEvent.getMessage();
            final Integer deliveryAttemptCount = message.getInvocationProperty(
                PROCESS_ATTEMPT_COUNT_PROPERTY_NAME, DEFAULT_PROCESS_ATTEMPT_COUNT_PROPERTY_VALUE);

            if (deliveryAttemptCount <= getMaxRetries())
            {
                // we store the incremented version unless the max attempt count has been reached
                message.setInvocationProperty(PROCESS_ATTEMPT_COUNT_PROPERTY_NAME, deliveryAttemptCount + 1);
                objectStore.store(eventStoreKey, mutableEvent);
            }
            else
            {
                abandonRetries(event, mutableEvent);
            }
        }
        catch (final ObjectStoreException ose)
        {
            logger.error("Failed to increment failure count for event stored with key: " + eventStoreKey);
        }
    }

    private void abandonRetries(final MuleEvent event, final MuleEvent mutableEvent)
    {
        if (dlqMP == null)
        {
            logger.info("Retry attempts exhausted and no DLQ defined, dropping message: " + event);
            return;
        }

        try
        {
            logger.info("Retry attempts exhausted, routing message to DLQ: " + dlqMP);
            dlqMP.process(mutableEvent);
        }
        catch (final MuleException me)
        {
            logger.error("Failed to route message to DLQ: " + dlqMP + ", dropping message: " + event, me);
        }
    }

    private void removeFromStore(final EventStoreKey eventStoreKey)
    {
        try
        {
            objectStore.remove(eventStoreKey);
        }
        catch (final ObjectStoreException ose)
        {
            logger.warn("Failed to remove following event from store with key: " + eventStoreKey);
        }
    }

    private void retrieveAndProcessEvent(final EventStoreKey eventStoreKey) throws ObjectStoreException
    {
        final MuleEvent persistedEvent = objectStore.retrieve(eventStoreKey);
        final MuleEvent mutableEvent = DefaultMuleEvent.copy(persistedEvent);
        OptimizedRequestContext.unsafeSetEvent(mutableEvent);
        processEvent(mutableEvent);
    }

    private void processEvent(final MuleEvent event)
    {
        if (routes.isEmpty())
        {
            return;
        }

        MuleEvent returnEvent;
        try
        {
            returnEvent = routes.get(0).process(event);
        }
        catch (final MuleException me)
        {
            throw new MuleRuntimeException(me);
        }

        if (returnEvent == null || VoidMuleEvent.getInstance().equals(returnEvent))
        {
            return;
        }

        final MuleMessage msg = returnEvent.getMessage();
        if (msg == null)
        {
            throw new MuleRuntimeException(
                MessageFactory.createStaticMessage("No message found in response to processing, which is therefore considered failed for event: "
                                                   + event));
        }

        final boolean errorDetected = failureExpressionFilter.accept(msg);
        if (errorDetected)
        {
            throw new MuleRuntimeException(
                MessageFactory.createStaticMessage("Failure expression positive when processing event: "
                                                   + event));
        }
    }

    private void ensurePayloadSerializable(final MuleEvent event) throws Exception
    {
        final MuleMessage message = event.getMessage();
        if (message instanceof DefaultMuleMessage)
        {
            if (((DefaultMuleMessage) message).isConsumable())
            {
                message.getPayloadAsBytes();
            }
        }
        else
        {
            message.getPayloadAsBytes();
        }
    }

    public ListableObjectStore<MuleEvent> getObjectStore()
    {
        return objectStore;
    }

    public void setObjectStore(final ListableObjectStore<MuleEvent> objectStore)
    {
        this.objectStore = objectStore;
    }

    public int getMaxRetries()
    {
        return maxRetries;
    }

    public void setMaxRetries(final int maxRetries)
    {
        this.maxRetries = maxRetries;
    }

    public long getSecondsBetweenRetries()
    {
        return secondsBetweenRetries;
    }

    public void setSecondsBetweenRetries(final long secondsBetweenRetries)
    {
        this.secondsBetweenRetries = secondsBetweenRetries;
    }

    public String getFailureExpression()
    {
        return failureExpression;
    }

    public void setFailureExpression(final String failureExpression)
    {
        this.failureExpression = failureExpression;
    }

    public String getAckExpression()
    {
        return ackExpression;
    }

    public void setAckExpression(final String ackExpression)
    {
        this.ackExpression = ackExpression;
    }

    public void setDeadLetterQueue(final Object deadLetterQueue)
    {
        this.deadLetterQueue = deadLetterQueue;
    }

    public Object getDeadLetterQueue()
    {
        return deadLetterQueue;
    }

    public String getEventKeyPrefix()
    {
        return eventKeyPrefix;
    }

    public ExpressionFilter getFailureExpressionFilter()
    {
        return failureExpressionFilter;
    }
}