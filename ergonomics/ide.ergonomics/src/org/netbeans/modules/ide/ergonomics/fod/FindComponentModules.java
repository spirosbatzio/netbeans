/*
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

package org.netbeans.modules.ide.ergonomics.fod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.prefs.Preferences;
import org.netbeans.api.autoupdate.InstallSupport;
import org.netbeans.api.autoupdate.OperationContainer;
import org.netbeans.api.autoupdate.OperationSupport;
import org.netbeans.api.autoupdate.UpdateElement;
import org.netbeans.api.autoupdate.UpdateManager;
import org.netbeans.api.autoupdate.UpdateUnit;
import org.netbeans.api.autoupdate.UpdateUnitProvider;
import org.netbeans.api.autoupdate.UpdateUnitProviderFactory;
import org.openide.util.Exceptions;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;
import org.openide.util.Task;
import org.openide.util.TaskListener;

/**
 *
 * @author Jirka Rechtacek
 */
public final class FindComponentModules extends Task {
    private static final RequestProcessor RP = new RequestProcessor("Find Modules");
    
    private final Collection<String> codeNames;
    private final FeatureInfo[] infos;
    public final String DO_CHECK = "do-check";
    private final String ENABLE_LATER = "enable-later";
    private final RequestProcessor.Task findingTask;
    private Collection<UpdateElement> forInstall;
    private Collection<UpdateElement> forEnable;
    /**
     * Features, whose 'extra' modules were not found.
     */
    private Map<FeatureInfo, List<FeatureInfo.ExtraModuleInfo>>   incompleteFeatures = new HashMap<>();
    
    /**
     * Errors encountered during updating component descriptions
     */
    private List<IOException> updateErrors = new ArrayList<>();
    
    public FindComponentModules(FeatureInfo info, FeatureInfo... additional) {
        ArrayList<FeatureInfo> l = new ArrayList<FeatureInfo>();
        l.add(info);
        l.addAll(Arrays.asList(additional));
        this.infos = l.toArray(new FeatureInfo[0]);
        if (infos.length == 1) {
            codeNames = info.getCodeNames();
        } else {
            codeNames = new HashSet<String>(info.getCodeNames());
            for (FeatureInfo fi : additional) {
                codeNames.addAll(fi.getCodeNames());
            }
        }
        findingTask = RP.post(doFind);
    }
    

    public Collection<UpdateElement> getModulesForInstall () {
        findingTask.waitFinished();
        return forInstall;
    }
    public Collection<UpdateElement> getModulesForEnable () {
        findingTask.waitFinished();
        return forEnable;
    }
    
    public Collection<FeatureInfo> getIncompleteFeatures() {
        findingTask.waitFinished();
        return incompleteFeatures.keySet();
    }
    
    void markIncompleteFeature(FeatureInfo fi, FeatureInfo.ExtraModuleInfo moduleInfo) {
        incompleteFeatures.computeIfAbsent(fi, (f) -> new ArrayList<>())
                .add(moduleInfo);
    }
    
    public Collection<FeatureInfo.ExtraModuleInfo> getMissingModules(FeatureInfo info) {
        return incompleteFeatures.getOrDefault(info, Collections.emptyList());
    }
    
    public Collection<IOException> getUpdateErrors() {
        return updateErrors;
    }
    
    /** Associates a listener with currently running computation.
     * One the results for {@link #getModulesForEnable()} and
     * {@link #getModulesForInstall()} is available, the listener 
     * will be called back.
     * 
     * @param l the listener which receives <code>this</code> as a task
     *   once the result is available
     */
    public void onFinished(final TaskListener l) {
        findingTask.addTaskListener(new TaskListener() {
            @Override
            public void taskFinished(Task task) {
                l.taskFinished(FindComponentModules.this);
            }
        });
    }
    
    public void writeEnableLater (Collection<UpdateElement> modules) {
        Preferences pref = FindComponentModules.getPreferences ();
        if (modules == null) {
            pref.remove (ENABLE_LATER);
            return ;
        }
        String value = "";
        for (UpdateElement m : modules) {
            value += value.length () == 0 ? m.getCodeName () : ", " + m.getCodeName (); // NOI18N
        }
        if (value.trim ().length () == 0) {
            pref.remove (ENABLE_LATER);
        } else {
            pref.put (ENABLE_LATER, value);
        }
    }


    private Set<String> clusterClosure(Collection<UpdateElement> all) {
        HashSet<String> closure = new HashSet<String>();
        for (UpdateElement ue : all) {
            for (FeatureInfo featureInfo : FeatureManager.features()) {
                if (featureInfo.getCodeNames().contains(ue.getCodeName())) {
                    closure.addAll(featureInfo.getCodeNames());
                }
            }
        }
        return closure;
    }
    
    private Collection<UpdateElement> readEnableLater () {
        Set<UpdateElement> res = new HashSet<UpdateElement> ();
        Preferences pref = FindComponentModules.getPreferences ();
        String value = pref.get (ENABLE_LATER, null);
        if (value != null && value.trim ().length () > 0) {
            Enumeration en = new StringTokenizer (value, ","); // NOI18N
            while (en.hasMoreElements ()) {
                String codeName = ((String) en.nextElement ()).trim ();
                UpdateElement el = findUpdateElement (codeName, true);
                if (el != null) {
                    res.add (el);
                }
            }
        }
        return res;
    }
    
    public Collection<UpdateElement> getVisibleUpdateElements (Collection<UpdateElement> elems) {
        String prefCNB = infos[0].getPreferredCodeNameBase();

        Collection<UpdateElement> res = new HashSet<UpdateElement> ();
        for (UpdateElement el : new LinkedList<UpdateElement> (elems)) {
            if (UpdateManager.TYPE.KIT_MODULE.equals (el.getUpdateUnit ().getType ())) {
                res.add (el);
            }
            if (el.getUpdateUnit().getCodeName().equals(prefCNB)) {
                return Collections.singleton(el);
            }
        }
        if (res.size() > 1) {
            FoDLayersProvider.LOG.warning("No prefCNB found " + prefCNB + " using multiple " + res);
        }
        return res;
    }

    public static Preferences getPreferences () {
        return NbPreferences.forModule (FindComponentModules.class);
    }

    private Runnable doFind = new Runnable () {
        public void run() {
            findComponentModules ();
        }
    };

    private void findComponentModules () {
        long start = System.currentTimeMillis();
        Collection<UpdateUnit> units = null;
        Collection<UpdateElement> elementsForInstall = null;
        for (int[] refresh = { 2 }; refresh[0] > 0; ) {
            if (units != null) {
                List<UpdateUnitProvider> providers = UpdateUnitProviderFactory.getDefault().getUpdateUnitProviders(true);
                for (UpdateUnitProvider p : providers) {
                    try {
                        p.refresh(null, true);
                    } catch (IOException ex) {
                        updateErrors.add(ex);
                        Exceptions.attachSeverity(ex, Level.INFO).printStackTrace();
                    }
                }
                
            }
            units = UpdateManager.getDefault ().getUpdateUnits (UpdateManager.TYPE.MODULE);
            // install missing modules
            elementsForInstall = getMissingModules(units, refresh);
        }
        forInstall = getAllForInstall (elementsForInstall);

        // install disabled modules
        Collection<UpdateElement> elementsForEnable = getDisabledModules (units);
        forEnable = getAllForEnable (elementsForEnable, units);
    }
    
    private Collection<UpdateElement> getMissingModules(Collection<UpdateUnit> allUnits, int[] refresh) {
        Set<UpdateElement> res = new HashSet<UpdateElement> ();
        for (UpdateUnit unit : allUnits) {
            if (unit.getInstalled () == null && (
                codeNames.contains(unit.getCodeName ())
            )) {
                res.add (unit.getAvailableUpdates ().get (0));
            }
        }
        boolean decr = true;
        for (FeatureInfo fi : this.infos) {
            Set<FeatureInfo.ExtraModuleInfo> extraModules = fi.getExtraModules();
            FOUND: for (FeatureInfo.ExtraModuleInfo moduleInfo : extraModules) {
                boolean found = false;
                for (UpdateUnit unit : allUnits) {
                    if (moduleInfo.matches(unit.getCodeName())) {
                        if (unit.getInstalled() != null) {
                            continue FOUND;
                        } else {
                            res.add(unit.getAvailableUpdates().get(0));
                            found = true;
                        }
                    }
                }
                if (found) {
                    continue FOUND;
                }
                
                // not found
                if (decr) {
                    if (--refresh[0] > 0) {
                        // try to refresh
                        return res;
                    }
                    // decrement just once
                    decr = false;
                }
                // extra module(s) for the feature weren't found
                markIncompleteFeature(fi, moduleInfo);
            }
        }
        refresh[0] = 0;
        return res;
    }
    
    private Collection<UpdateElement> getAllForInstall (Collection<UpdateElement> elements) {
        Collection<UpdateElement> all = new HashSet<UpdateElement> ();
        for (UpdateElement el : elements) {
            OperationContainer<InstallSupport> ocForInstall = OperationContainer.createForInstall ();
            if (ocForInstall.canBeAdded (el.getUpdateUnit (), el)) {
                OperationContainer.OperationInfo<InstallSupport> info = ocForInstall.add (el);
                if (info == null) {
                    continue;
                }
                Set<UpdateElement> reqs = info.getRequiredElements ();
                ocForInstall.add (reqs);
                Set<String> breaks = info.getBrokenDependencies ();
                if (breaks.isEmpty ()) {
                    all.add (el);
                    all.addAll (reqs);
                }
            }
        }
        return all;
    }
    
    private Collection<UpdateElement> getDisabledModules (Collection<UpdateUnit> allUnits) {
        Set<UpdateElement> res = new HashSet<UpdateElement> ();
        for (UpdateUnit unit : allUnits) {
            if (unit.getInstalled () != null && codeNames.contains(unit.getCodeName ())) {
                if (! unit.getInstalled ().isEnabled ()) {
                    res.add (unit.getInstalled ());
                }
            }
        }
        return res;
    }
    
    private Collection<UpdateElement> getAllForEnable (Collection<UpdateElement> elements, Collection<UpdateUnit> units) {
        Collection<UpdateElement> toAdd = elements;
        Collection<UpdateElement> all = new HashSet<UpdateElement> ();
        Collection<String> ignore = new HashSet<String>();
        OperationContainer<OperationSupport> ocForEnable = OperationContainer.createForEnable ();
        for (;;) {
            if (toAdd.isEmpty()) {
                break;
            }
            for (UpdateElement el : toAdd) {
                if (el == null) {
                    continue;
                }
                if (ocForEnable.canBeAdded (el.getUpdateUnit (), el)) {
                    OperationContainer.OperationInfo<OperationSupport> inf = ocForEnable.add (el);
                    if (inf == null) {
                        continue;
                    }
                    Set<UpdateElement> reqs = inf.getRequiredElements ();
                    for (UpdateElement ue : reqs) {
                        if (ocForEnable.canBeAdded(ue.getUpdateUnit(), ue)) {
                            ocForEnable.add(ue.getUpdateUnit(), ue);
                        }
                    }
//                    Set<String> breaks = inf.getBrokenDependencies ();
//                    if (breaks.isEmpty ()) {
                        all.add (el);
                        all.addAll (reqs);
//                    } else {
//                        FoDLayersProvider.LOG.fine("Cannot enable " + el.getCodeName() + " broken deps: " + breaks); // NOI18N
//                        ignore.add(el.getCodeName());
//                    }
                } else {
                    ignore.add(el.getCodeName());
                }
            }

            Set<String> clusterClosure = clusterClosure(all);
            for (UpdateElement el : all) {
                clusterClosure.remove(el.getCodeName());
            }
            clusterClosure.removeAll(ignore);
            if (clusterClosure.isEmpty()) {
                break;
            }
            toAdd = new HashSet<UpdateElement>();
            for (UpdateUnit uu : units) {
                if (clusterClosure.contains(uu.getCodeName())) {
                    toAdd.add(uu.getInstalled());
                }
            }
        }
        return all;
    }
    
    private static UpdateElement findUpdateElement (String codeName, boolean isInstalled) {
        UpdateElement res = null;
        for (UpdateUnit u : UpdateManager.getDefault ().getUpdateUnits (UpdateManager.TYPE.MODULE)) {
            if (codeName.equals (u.getCodeName ())) {
                if (isInstalled && u.getInstalled () != null) {
                    res = u.getInstalled ();
                } else if (! isInstalled && ! u.getAvailableUpdates ().isEmpty ()) {
                    res = u.getAvailableUpdates ().get (0);
                }
                break;
            }
        }
        return res;
    }
}
