/*
 ***************************************************************************************
 *  Copyright (C) 2006 EsperTech, Inc. All rights reserved.                            *
 *  http://www.espertech.com/esper                                                     *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 ***************************************************************************************
 */
package com.espertech.esper.common.internal.compile.stage1.spec;

import java.util.List;

public class ContextNested implements ContextSpec {
    private final List<CreateContextDesc> contexts;

    public ContextNested(List<CreateContextDesc> contexts) {
        this.contexts = contexts;
    }

    public List<CreateContextDesc> getContexts() {
        return contexts;
    }
}
