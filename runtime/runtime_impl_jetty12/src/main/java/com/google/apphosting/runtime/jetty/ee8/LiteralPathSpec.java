/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.apphosting.runtime.jetty.ee8;

import org.eclipse.jetty.http.pathmap.AbstractPathSpec;
import org.eclipse.jetty.http.pathmap.MatchedPath;
import org.eclipse.jetty.http.pathmap.PathSpecGroup;
import org.eclipse.jetty.util.StringUtil;

public class LiteralPathSpec extends AbstractPathSpec
{
    private final String _pathSpec;
    private final int _pathDepth;

    public LiteralPathSpec(String pathSpec)
    {
        if (StringUtil.isEmpty(pathSpec))
            throw new IllegalArgumentException();
        _pathSpec = pathSpec;

        int pathDepth = 0;
        for (int i = 0; i < _pathSpec.length(); i++)
        {
            char c = _pathSpec.charAt(i);
            if (c < 128)
            {
                if (c == '/')
                    pathDepth++;
            }
        }
        _pathDepth = pathDepth;
    }

    @Override
    public int getSpecLength()
    {
        return _pathSpec.length();
    }

    @Override
    public PathSpecGroup getGroup()
    {
        return PathSpecGroup.EXACT;
    }

    @Override
    public int getPathDepth()
    {
        return _pathDepth;
    }

    @Override
    public String getPathInfo(String path)
    {
        return _pathSpec.equals(path) ? "" : null;
    }

    @Override
    public String getPathMatch(String path)
    {
        return _pathSpec.equals(path) ? _pathSpec : null;
    }

    @Override
    public String getDeclaration()
    {
        return _pathSpec;
    }

    @Override
    public String getPrefix()
    {
        return null;
    }

    @Override
    public String getSuffix()
    {
        return null;
    }

    @Override
    public MatchedPath matched(String path)
    {
        if (_pathSpec.equals(path))
            return MatchedPath.from(_pathSpec, null);
        return null;
    }

    @Override
    public boolean matches(String path)
    {
        return _pathSpec.equals(path);
    }
}
