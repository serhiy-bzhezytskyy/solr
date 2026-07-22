/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.solr.index;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.index.MergePolicy;
import org.apache.solr.util.SolrPluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MergePolicyFactoryArgs {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /**
   * Merge-policy settings that existed in earlier Lucene versions but were removed in Lucene 11, so
   * they no longer have a corresponding setter on the merge policy. A configuration that still
   * specifies one of these is tolerated (with a warning) so that a core configured against an older
   * Lucene keeps loading after the upgrade, rather than failing initialization.
   *
   * <ul>
   *   <li>{@code maxMergeAtOnce} - removed from TieredMergePolicy (GITHUB#14165); the policy now
   *       determines this automatically.
   *   <li>{@code noCFSRatio} / {@code maxCFSSegmentSizeMB} - compound-file selection moved off the
   *       merge policy and onto the codec's CompoundFormat (GITHUB#15295).
   * </ul>
   */
  static final Set<String> REMOVED_LUCENE11_KEYS =
      Set.of("maxMergeAtOnce", "noCFSRatio", "maxCFSSegmentSizeMB");

  final Map<String, Object> args;

  public MergePolicyFactoryArgs() {
    this.args = new HashMap<>();
  }

  public MergePolicyFactoryArgs(Iterable<? extends Map.Entry<String, ?>> args) {
    this.args = new HashMap<>();
    for (final Map.Entry<String, ?> arg : args) {
      this.args.put(arg.getKey(), arg.getValue());
    }
  }

  public void add(String key, Object val) {
    args.put(key, val);
  }

  public Object remove(String key) {
    return args.remove(key);
  }

  public Object get(String key) {
    return args.get(key);
  }

  public Set<String> keys() {
    return args.keySet();
  }

  public void invokeSetters(MergePolicy policy) {
    Map<String, Object> effectiveArgs = args;
    for (String removed : REMOVED_LUCENE11_KEYS) {
      if (args.containsKey(removed)) {
        if (effectiveArgs == args) {
          effectiveArgs = new HashMap<>(args);
        }
        effectiveArgs.remove(removed);
        log.warn(
            "Ignoring merge policy setting '{}' on {}: it was removed in Lucene 11 and has no "
                + "effect. Remove it from your configuration.",
            removed,
            policy.getClass().getName());
      }
    }
    SolrPluginUtils.invokeSetters(policy, effectiveArgs.entrySet());
  }

  @Override
  public String toString() {
    return args.toString();
  }
}
