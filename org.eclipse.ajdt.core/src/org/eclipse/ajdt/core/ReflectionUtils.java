/*******************************************************************************
 * Copyright (c) 2005 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      SpringSource - Initial API and implementation
 *******************************************************************************/
package org.eclipse.ajdt.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;


/**
 * @author Andrew Eisenberg
 * @created May 8, 2009
 *
 * common functionality for accessing private fields and methods
 *
 *
 */
public class ReflectionUtils {

    private static final Map<String, Field> fieldMap = new HashMap<>();

    public static <T> Object getPrivateField(Class<T> clazz, String fieldName, T target) {
        String key = clazz.getCanonicalName() + fieldName;
        Field field = fieldMap.get(key);
        try {
            if (field == null) {
                field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                fieldMap.put(key, field);
            }
            return field.get(target);
        } catch (Exception ignored) {
        }
        return null;
    }

    public static <T> void setPrivateField(Class<T> clazz, String fieldName, T target, Object newValue) {
        String key = clazz.getCanonicalName() + fieldName;
        Field field = fieldMap.get(key);
        try {
            if (field == null) {
                field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                fieldMap.put(key, field);
            }
            field.set(target, newValue);
        } catch (Exception ignored) {
        }
    }

    public static <T> Object executePrivateMethod(Class<T> clazz, String methodName, Class<?>[] types, org.eclipse.jdt.internal.ui.refactoring.UserInterfaceManager target, Object[] args) {
        // forget caching for now...
        try {
            Method method = clazz.getDeclaredMethod(methodName, types);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Exception ignored) {
        }
        return null;
    }


    public static <T> T executePrivateConstructor(Class<T> clazz, Class<?>[] parameterTypes, Object[] args) {
        try {
            Constructor<T> cons = clazz.getDeclaredConstructor(parameterTypes);
            cons.setAccessible(true);
            return cons.newInstance(args);
        } catch (Exception ignored) {
        }
        return null;
    }

}
