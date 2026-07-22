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

import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.index.UpgradeIndexMergePolicy;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.core.SolrResourceLoader;
import org.apache.solr.schema.IndexSchema;

/** Unit tests for {@link WrapperMergePolicyFactory}. */
public class WrapperMergePolicyFactoryTest extends SolrTestCaseJ4 {

  private final SolrResourceLoader resourceLoader = new SolrResourceLoader(createTempDir());

  public void testReturnsDefaultMergePolicyIfNoneSpecified() {
    final MergePolicyFactoryArgs args = new MergePolicyFactoryArgs();
    MergePolicyFactory mpf = new DefaultingWrapperMergePolicyFactory(resourceLoader, args, null);
    assertSame(mpf.getMergePolicy(), NoMergePolicy.INSTANCE);
  }

  public void testFailsIfNoClassSpecifiedForWrappedPolicy() {
    final MergePolicyFactoryArgs args = new MergePolicyFactoryArgs();
    args.add(WrapperMergePolicyFactory.WRAPPED_PREFIX, "foo");
    expectThrows(
        IllegalArgumentException.class,
        () -> new DefaultingWrapperMergePolicyFactory(resourceLoader, args, null).getMergePolicy());
  }

  public void testProperlyInitializesWrappedMergePolicy() {
    final TieredMergePolicy defaultTMP = new TieredMergePolicy();
    final double testSegmentsPerTier = defaultTMP.getSegmentsPerTier() * 2;
    final double testMaxMergedSegmentMB = defaultTMP.getMaxMergedSegmentMB() * 10;

    final MergePolicyFactoryArgs args = new MergePolicyFactoryArgs();
    args.add(WrapperMergePolicyFactory.WRAPPED_PREFIX, "test");
    args.add("test.class", TieredMergePolicyFactory.class.getName());
    args.add("test.segmentsPerTier", testSegmentsPerTier);
    args.add("test.maxMergedSegmentMB", testMaxMergedSegmentMB);
    MergePolicyFactory mpf =
        new DefaultingWrapperMergePolicyFactory(resourceLoader, args, null) {
          @Override
          protected MergePolicy getDefaultWrappedMergePolicy() {
            throw new IllegalStateException("Should not have reached here!");
          }
        };
    final MergePolicy mp = mpf.getMergePolicy();
    assertSame(mp.getClass(), TieredMergePolicy.class);
    final TieredMergePolicy tmp = (TieredMergePolicy) mp;
    assertEquals("segmentsPerTier", testSegmentsPerTier, tmp.getSegmentsPerTier(), 0.0d);
    assertEquals("maxMergedSegmentMB", testMaxMergedSegmentMB, tmp.getMaxMergedSegmentMB(), 0.0d);
  }

  public void testUpgradeIndexMergePolicyFactory() {
    final int N = 10;
    // NOTE (Lucene 11): the wrapping policy is UpgradeIndexMergePolicy, which no longer exposes any
    // bean setters, so a wrapping-side arg can no longer be applied (invokeSetters would throw).
    // We therefore only vary the WRAPPED policy's arg (maxMergedSegmentMB, a surviving
    // TieredMergePolicy setter that stands in for the removed noCFSRatio). The wrapping-vs-wrapped
    // overlap detection is still covered by testUpgradeIndexMergePolicyFactoryOverlap below.
    final Double wrappedMaxMergedSegmentMB =
        random().nextBoolean() ? null : (double) random().nextInt(N + 1);

    final MergePolicyFactoryArgs args = new MergePolicyFactoryArgs();
    args.add(WrapperMergePolicyFactory.WRAPPED_PREFIX, "wrapped");
    args.add("wrapped.class", TieredMergePolicyFactory.class.getName());
    if (wrappedMaxMergedSegmentMB != null) {
      args.add("wrapped.maxMergedSegmentMB", wrappedMaxMergedSegmentMB);
    }

    final MergePolicyFactory mpf = new UpgradeIndexMergePolicyFactory(resourceLoader, args, null);
    for (int ii = 1; ii <= 2; ++ii) { // it should be okay to call getMergePolicy() more than once
      final MergePolicy mp = mpf.getMergePolicy();
      assertSame(mp.getClass(), UpgradeIndexMergePolicy.class);
    }
  }

  public void testUpgradeIndexMergePolicyFactoryOverlap() {
    // Overlap detection happens at factory construction (before any setter is invoked), so it does
    // not depend on the wrapping policy having a setter for the overlapping key.
    final MergePolicyFactoryArgs args = new MergePolicyFactoryArgs();
    args.add("maxMergedSegmentMB", 1.0);
    args.add(WrapperMergePolicyFactory.WRAPPED_PREFIX, "wrapped");
    args.add("wrapped.class", TieredMergePolicyFactory.class.getName());
    args.add("wrapped.maxMergedSegmentMB", 2.0);

    final IllegalArgumentException iae =
        expectThrows(
            IllegalArgumentException.class,
            () -> new UpgradeIndexMergePolicyFactory(resourceLoader, args, null));
    assertEquals(
        "Wrapping and wrapped merge policy args overlap! [maxMergedSegmentMB]", iae.getMessage());
  }

  private static class DefaultingWrapperMergePolicyFactory extends WrapperMergePolicyFactory {

    DefaultingWrapperMergePolicyFactory(
        SolrResourceLoader resourceLoader, MergePolicyFactoryArgs wrapperArgs, IndexSchema schema) {
      super(resourceLoader, wrapperArgs, schema);
      if (!args.keys().isEmpty()) {
        throw new IllegalArgumentException(
            "All arguments should have been claimed by the wrapped policy but some ("
                + args
                + ") remain.");
      }
    }

    @Override
    protected MergePolicy getDefaultWrappedMergePolicy() {
      return NoMergePolicy.INSTANCE;
    }

    @Override
    protected MergePolicy getMergePolicyInstance(MergePolicy wrappedMP) {
      return getWrappedMergePolicy();
    }
  }
}
