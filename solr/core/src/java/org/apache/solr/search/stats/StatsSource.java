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
package org.apache.solr.search.stats;

import java.io.IOException;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.FieldStats;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Weight;
import org.apache.solr.search.SolrIndexSearcher;

/**
 * The purpose of this class is only to provide two pieces of information necessary to create {@link
 * Weight} from a {@link Query}, that is {@link org.apache.lucene.search.TermStats} for a term and
 * {@link FieldStats} for the whole collection.
 */
public abstract class StatsSource {

  public abstract org.apache.lucene.search.TermStats termStatistics(
      SolrIndexSearcher localSearcher, Term term, int docFreq, long totalTermFreq)
      throws IOException;

  public abstract FieldStats collectionStatistics(SolrIndexSearcher localSearcher, String field)
      throws IOException;
}
