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
package com.espertech.esper.common.internal.bytecodemodel.base;

import com.espertech.esper.common.client.type.EPTypeClass;
import com.espertech.esper.common.client.type.EPTypePremade;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpression;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionRef;
import com.espertech.esper.common.internal.util.CollectionUtil;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.*;

public class CodegenSetterBuilder {
    private final Class originator;
    private final String refName;
    private final CodegenClassScope classScope;
    private final boolean methodProvided;

    private CodegenMethod method;
    private boolean closed;

    public CodegenSetterBuilder(EPTypeClass returnType, Class originator, String refName, CodegenMethodScope parent, CodegenClassScope classScope) {
        this.originator = originator;
        this.refName = refName;
        this.classScope = classScope;
        this.methodProvided = false;
        method = parent.makeChild(returnType, originator, classScope);
        method.getBlock().declareVarNewInstance(returnType, refName);
    }

    public CodegenSetterBuilder(EPTypeClass returnType, Class originator, String refName, CodegenClassScope classScope, CodegenMethod method) {
        this.originator = originator;
        this.refName = refName;
        this.classScope = classScope;
        this.method = method;
        this.methodProvided = true;
        method.getBlock().declareVarNewInstance(returnType, refName);
    }

    public CodegenSetterBuilder(EPTypeClass returnType, Class originator, String refName, CodegenClassScope classScope, CodegenMethod method, CodegenExpression initializer) {
        this.originator = originator;
        this.refName = refName;
        this.classScope = classScope;
        this.method = method;
        this.methodProvided = true;
        method.getBlock().declareVar(returnType, refName, initializer);
    }

    public CodegenSetterBuilder constantExplicit(String name, Object value) {
        if (value instanceof CodegenExpression) {
            throw new IllegalArgumentException("Expected a non-expression value, received " + value);
        }
        return setValue(name, value == null ? constantNull() : CodegenExpressionBuilder.constant(value));
    }

    public CodegenSetterBuilder constantDefaultChecked(String name, Object value) {
        if (value instanceof CodegenExpression) {
            throw new IllegalArgumentException("Expected a non-expression value, received " + value);
        }
        if (value == null || value == Boolean.FALSE) {
            return this;
        }
        if (value instanceof Number && ((Number) value).doubleValue() == 0d) {
            return this;
        }
        return setValue(name, CodegenExpressionBuilder.constant(value));
    }

    public CodegenSetterBuilder expressionDefaultChecked(String name, CodegenExpression expression) {
        if (expression.equals(constantNull())) {
            return this;
        }
        return setValue(name, expression);
    }

    public CodegenSetterBuilder expression(String name, CodegenExpression expression) {
        return setValue(name, expression);
    }

    public CodegenSetterBuilder method(String name, Function<CodegenMethod, CodegenExpression> expressionFunc) {
        CodegenExpression expression = expressionFunc.apply(method);
        return setValue(name, expression == null ? constantNull() : expression);
    }

    public CodegenSetterBuilder mapOfConstants(String name, Map<String, ?> values) {
        CodegenSetterBuilderItemConsumer consumer = new CodegenSetterBuilderItemConsumer() {
            public CodegenExpression apply(Object o, CodegenMethodScope parent, CodegenClassScope scope) {
                return CodegenExpressionBuilder.constant(o);
            }
        };
        return setValue(name, buildMap(values, consumer, originator, method, classScope));
    }

    public <I> CodegenSetterBuilder map(String name, Map<String, I> values, CodegenSetterBuilderItemConsumer<I> consumer) {
        return setValue(name, buildMap(values, consumer, originator, method, classScope));
    }

    public CodegenExpressionRef getRefName() {
        return ref(refName);
    }

    public CodegenExpression build() {
        if (methodProvided) {
            throw new IllegalStateException("Builder build is reserved for the case when the method is not already provided");
        }
        if (closed) {
            throw new IllegalStateException("Builder already completed build");
        }
        closed = true;
        method.getBlock().methodReturn(ref(refName));
        return localMethod(method);
    }

    public CodegenMethod getMethod() {
        return method;
    }

    private static <V> CodegenExpression buildMap(Map<String, V> map, CodegenSetterBuilderItemConsumer<V> valueConsumer, Class originator, CodegenMethod method, CodegenClassScope classScope) {
        if (map == null) {
            return constantNull();
        }
        if (map.isEmpty()) {
            return staticMethod(Collections.class, "emptyMap");
        }
        CodegenMethod child = method.makeChild(EPTypePremade.MAP.getEPType(), originator, classScope);
        if (map.size() == 1) {
            Map.Entry<String, V> single = map.entrySet().iterator().next();
            CodegenExpression value = buildMapValue(single.getValue(), valueConsumer, originator, child, classScope);
            child.getBlock().methodReturn(staticMethod(Collections.class, "singletonMap", CodegenExpressionBuilder.constant(single.getKey()), value));
        } else {
            child.getBlock().declareVar(EPTypePremade.MAP.getEPType(), "map", newInstance(EPTypePremade.LINKEDHASHMAP.getEPType(), CodegenExpressionBuilder.constant(CollectionUtil.capacityHashMap(map.size()))));
            for (Map.Entry<String, V> entry : map.entrySet()) {
                CodegenExpression value = buildMapValue(entry.getValue(), valueConsumer, originator, child, classScope);
                child.getBlock().exprDotMethod(ref("map"), "put", CodegenExpressionBuilder.constant(entry.getKey()), value);
            }
            child.getBlock().methodReturn(ref("map"));
        }
        return localMethod(child);
    }

    private static <V> CodegenExpression buildMapValue(V value, CodegenSetterBuilderItemConsumer<V> valueConsumer, Class originator, CodegenMethod method, CodegenClassScope classScope) {
        if (value instanceof Map) {
            return buildMap((Map) value, valueConsumer, originator, method, classScope);
        }
        return valueConsumer.apply(value, method, classScope);
    }

    private CodegenSetterBuilder setValue(String name, CodegenExpression expression) {
        method.getBlock().exprDotMethod(ref(refName), "set" + getBeanCap(name), expression);
        return this;
    }

    private String getBeanCap(String name) {
        return name.substring(0, 1).toUpperCase(Locale.ENGLISH) + name.substring(1);
    }
}
