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

package javax.mail;

/**
 * A representation of a Quota item for a given quota root.
 *
 * @version $Rev: 578802 $ $Date: 2007-09-24 08:16:44 -0500 (Mon, 24 Sep 2007) $
 */
public class Quota {
    /**
     * The name of the quota root.
     */
    public String quotaRoot;

    /**
     * The resources associated with this quota root.
     */
    public Resource[] resources;


    /**
     * Create a Quota with the given name and no resources.
     *
     * @param quotaRoot The quota root name.
     */
    public Quota(String quotaRoot) {
        this.quotaRoot = quotaRoot;
    }

    /**
     * Set a limit value for a resource.  If the resource is not
     * currently associated with this Quota, a new Resource item is
     * added to the resources list.
     *
     * @param name   The target resource name.
     * @param limit  The new limit value for the resource.
     */
    public void setResourceLimit(String name, long limit) {
        Resource target = findResource(name);
        target.limit = limit;
    }

    /**
     * Locate a particular named resource, adding one to the list
     * if it does not exist.
     *
     * @param name   The target resource name.
     *
     * @return A Resource item for this named resource (either existing or new).
     */
    private Resource findResource(String name) {
        // no resources yet?  Make it so.
        if (resources == null) {
            Resource target = new Resource(name, 0, 0);
            resources = new Resource[] { target };
            return target;
        }

        // see if this one exists and return it.
        for (int i = 0; i < resources.length; i++) {
            Resource current = resources[i];
            if (current.name.equalsIgnoreCase(name)) {
                return current;
            }
        }

        // have to extend the array...this is a pain.
        Resource[] newResources = new Resource[resources.length + 1];
        System.arraycopy(resources, 0, newResources, 0, resources.length);
        Resource target = new Resource(name, 0, 0);
        newResources[resources.length] = target;
        resources = newResources;
        return target;
    }



    /**
     * A representation of a given resource definition.
     */
    public static class Resource {
        /**
         * The resource name.
         */
        public String name;
        /**
         * The current resource usage.
         */
        public long usage;
        /**
         * The limit value for this resource.
         */
        public long limit;


        /**
         * Construct a Resource object from the given name and usage/limit
         * information.
         *
         * @param name   The Resource name.
         * @param usage  The current resource usage.
         * @param limit  The Resource limit value.
         */
        public Resource(String name, long usage, long limit) {
            this.name = name;
            this.usage = usage;
            this.limit = limit;
        }
    }
}
