/*
 * Copyright (c) 2010-2013 - The Amdatu Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.ii.xsf.core.web.amdatu;

import java.util.Comparator;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Provides a container for the default pages.
 */
public class DefaultPages {
    private static final String SLASH = "/";

    private final SortedMap<String, String> m_defaultPages;
    private String m_globalDefault;

    /**
     * Creates a new {@link DefaultPages} instance.
     */
    public DefaultPages() {
        m_defaultPages = new TreeMap<String, String>(new LengthComparator());
        m_globalDefault = "index.html";
    }

    /**
     * Adds a default page for the given path.
     * 
     * @param path the path to add the default page for, cannot be <code>null</code>;
     * @param page the default page to add, cannot be <code>null</code> or empty.
     * @throws IllegalArgumentException in case one of the given arguments was <code>null</code>.
     */
    public void addDefault(String path, String page) {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null or empty!");
        }
        if (page == null || "".equals(page.trim())) {
            throw new IllegalArgumentException("Page cannot be null or empty!");
        }

        //path = appendSlash(path);
        m_defaultPages.put(path, page);
    }

    /**
     * Add a given page to the list of "global" defaults.
     * 
     * @param page the page to add as global default, cannot be <code>null</code>.
     * @throws IllegalArgumentException in case the given page was <code>null</code> or empty.
     */
    public void addGlobalDefault(String page) {
        m_globalDefault = page;
    }

    public String getDefaultPageFor(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null!");
        }
        String pathSlash = appendSlash(path);

        for (String key : m_defaultPages.keySet()) {
            if (key.equals(path) || pathSlash.equals(key)) {
                return m_defaultPages.get(key);
            }
        }
        return m_globalDefault;
    }

    /**
     * Appends a trailing slash to the given value, if it does not already contain a trailing slash.
     */
    private String appendSlash(String value) {
        if (!value.endsWith(SLASH)) {
            return value.concat(SLASH);
        }
        return value;
    }

    /**
     * Sorts strings on descending order.
     */
    static final class LengthComparator implements Comparator<String> {
        @Override
        public int compare(String o1, String o2) {
            return o1.length() - o2.length();
        }
    }
}