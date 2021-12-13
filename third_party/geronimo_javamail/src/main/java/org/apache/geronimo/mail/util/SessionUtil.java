/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.geronimo.mail.util;

import java.security.Security;

import javax.mail.Session;

/**
 * Simple utility class for managing session properties.
 */
public class SessionUtil {

    /**
     * Get a property associated with this mail session.  Returns
     * the provided default if it doesn't exist.
     *
     * @param session The attached session.
     * @param name    The name of the property.
     *
     * @return The property value (returns null if the property has not been set).
     */
    static public String getProperty(Session session, String name) {
        // occasionally, we get called with a null session if an object is not attached to
        // a session.  In that case, treat this like an unknown parameter.
        if (session == null) {
            return null;
        }

        return session.getProperty(name);
    }


    /**
     * Get a property associated with this mail session.  Returns
     * the provided default if it doesn't exist.
     *
     * @param session The attached session.
     * @param name    The name of the property.
     * @param defaultValue
     *                The default value to return if the property doesn't exist.
     *
     * @return The property value (returns defaultValue if the property has not been set).
     */
    static public String getProperty(Session session, String name, String defaultValue) {
        String result = getProperty(session, name);
        if (result == null) {
            return defaultValue;
        }
        return result;
    }


    /**
     * Process a session property as a boolean value, returning
     * either true or false.
     *
     * @param session The source session.
     * @param name
     *
     * @return True if the property value is "true".  Returns false for any
     *         other value (including null).
     */
    static public boolean isPropertyTrue(Session session, String name) {
        String property = getProperty(session, name);
        if (property != null) {
            return property.equals("true");
        }
        return false;
    }

    /**
     * Process a session property as a boolean value, returning
     * either true or false.
     *
     * @param session The source session.
     * @param name
     *
     * @return True if the property value is "false".  Returns false for
     *         other value (including null).
     */
    static public boolean isPropertyFalse(Session session, String name) {
        String property = getProperty(session, name);
        if (property != null) {
            return property.equals("false");
        }
        return false;
    }

    /**
     * Get a property associated with this mail session as an integer value.  Returns
     * the default value if the property doesn't exist or it doesn't have a valid int value.
     *
     * @param session The source session.
     * @param name    The name of the property.
     * @param defaultValue
     *                The default value to return if the property doesn't exist.
     *
     * @return The property value converted to an int.
     */
    static public int getIntProperty(Session session, String name, int defaultValue) {
        String result = getProperty(session, name);
        if (result != null) {
            try {
                // convert into an int value.
                return Integer.parseInt(result);
            } catch (NumberFormatException e) {
            }
        }
        // return default value if it doesn't exist is isn't convertable.
        return defaultValue;
    }


    /**
     * Get a property associated with this mail session as a boolean value.  Returns
     * the default value if the property doesn't exist or it doesn't have a valid boolean value.
     *
     * @param session The source session.
     * @param name    The name of the property.
     * @param defaultValue
     *                The default value to return if the property doesn't exist.
     *
     * @return The property value converted to a boolean.
     */
    static public boolean getBooleanProperty(Session session, String name, boolean defaultValue) {
        String result = getProperty(session, name);
        if (result != null) {
            return Boolean.valueOf(result).booleanValue();
        }
        // return default value if it doesn't exist is isn't convertable.
        return defaultValue;
    }


    /**
     * Get a system property associated with this mail session as a boolean value.  Returns
     * the default value if the property doesn't exist or it doesn't have a valid boolean value.
     *
     * @param name    The name of the property.
     * @param defaultValue
     *                The default value to return if the property doesn't exist.
     *
     * @return The property value converted to a boolean.
     */
    static public boolean getBooleanProperty(String name, boolean defaultValue) {
        try {
            String result = System.getProperty(name);
            if (result != null) {
                return Boolean.valueOf(result).booleanValue();
            }
        } catch (SecurityException e) {
            // we can't access the property, so for all intents, it doesn't exist.
        }
        // return default value if it doesn't exist is isn't convertable.
        return defaultValue;
    }


    /**
     * Get a system property associated with this mail session as a boolean value.  Returns
     * the default value if the property doesn't exist.
     *
     * @param name    The name of the property.
     * @param defaultValue
     *                The default value to return if the property doesn't exist.
     *
     * @return The property value
     */
    static public String getProperty(String name, String defaultValue) {
        try {
            String result = System.getProperty(name);
            if (result != null) {
                return result;
            }
        } catch (SecurityException e) {
            // we can't access the property, so for all intents, it doesn't exist.
        }
        // return default value if it doesn't exist is isn't convertable.
        return defaultValue;
    }


    /**
     * Get a system property associated with this mail session as a boolean value.  Returns
     * the default value if the property doesn't exist.
     *
     * @param name    The name of the property.
     *
     * @return The property value
     */
    static public String getProperty(String name) {
        try {
            return System.getProperty(name);
        } catch (SecurityException e) {
            // we can't access the property, so for all intents, it doesn't exist.
        }
        // return null if we got an exception.
        return null;
    }
}
