/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.validation.internal.validator;

import org.mule.extension.validation.internal.ValidationContext;

import java.util.Locale;

/**
 * A {@link NumberValidator} for {@link Float} numbers
 *
 * @since 3.7.0
 */
public class FloatValidator extends NumberValidator
{

    public FloatValidator(NumberValidationOptions options, ValidationContext validationContext)
    {
        super(options, validationContext);
    }

    @Override
    protected Number validateWithPattern(String value, String pattern, Locale locale)
    {
        return org.apache.commons.validator.routines.FloatValidator.getInstance().validate(value, pattern, locale);
    }

    @Override
    protected Number validateWithoutPattern(String value, Locale locale)
    {
        return org.apache.commons.validator.routines.FloatValidator.getInstance().validate(value, locale);
    }

    /**
     * @return the {@link Float} class
     */
    @Override
    protected Class<? extends Number> getNumberType()
    {
        return Float.class;
    }
}
