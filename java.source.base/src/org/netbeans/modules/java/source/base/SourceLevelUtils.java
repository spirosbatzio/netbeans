/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.netbeans.modules.java.source.base;

import com.sun.tools.javac.code.Source;
import java.util.HashMap;
import java.util.Map;
import org.netbeans.api.annotations.common.NonNull;

/**
 *
 * @author lahvac
 */
public class SourceLevelUtils {
    
    public static final Source JDK1_5 = Source.lookup("5");
    public static final Source JDK1_6 = Source.lookup("6");
    public static final Source JDK1_7 = Source.lookup("7");
    public static final Source JDK1_8 = Source.lookup("8");
    public static final Source JDK1_9 = Source.lookup("9");
    private static final Map<String, Integer> versionMap = new HashMap<String, Integer> ();
    
    public static boolean allowDefaultMethods(Source in) {
        return in.compareTo(JDK1_8) >= 0;
    }
    
    //returns Integer value for JDK version, e.g. 8, 9, 10 etc
    //may return null
    public static Integer lookupJDKIntVersion() {
        Integer ver = null;
        populateVersionMap();
        String version = System.getProperty("java.vm.specification.version");
        if ((version != null) && Character.isDigit(version.charAt(0))) {
            ver = versionMap.get(version);
        }
        return ver;
    }

    private static void populateVersionMap() {
        if (versionMap.isEmpty()) {
            versionMap.put("1.4", 4);
            versionMap.put("1.5", 5);
            versionMap.put("1.6", 6);
            versionMap.put("1.7", 7);
            versionMap.put("1.8", 8);
            versionMap.put("1.9", 9);
            versionMap.put("10", 10);
        }
    }
}
