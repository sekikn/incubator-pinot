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
package org.apache.pinot.core.indexsegment.mutable;

import org.apache.pinot.common.data.Schema;
import org.apache.pinot.core.data.GenericRow;
import org.apache.pinot.core.data.readers.JSONRecordReader;
import org.apache.pinot.core.data.readers.RecordReader;
import org.apache.pinot.core.data.recordtransformer.CompositeTransformer;
import org.apache.pinot.core.segment.index.data.source.ColumnDataSource;
import org.apache.pinot.core.segment.index.readers.NullValueVectorReader;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class MutableSegmentImplNullValueVectorTest {
  private static final String PINOT_SCHEMA_FILE_PATH = "data/test_null_value_vector_pinot_schema.json";
  private static final String DATA_FILE = "data/test_null_value_vector_data.json";
  private static CompositeTransformer _recordTransformer;
  private static Schema _schema;
  private static MutableSegmentImpl _mutableSegmentImpl;
  private static List<String> _finalNullColumns;

  @BeforeClass
  public void setup()
      throws IOException {
    URL schemaResourceUrl = this.getClass().getClassLoader().getResource(PINOT_SCHEMA_FILE_PATH);
    URL dataResourceUrl = this.getClass().getClassLoader().getResource(DATA_FILE);
    _schema = Schema.fromFile(new File(schemaResourceUrl.getFile()));
    _recordTransformer = CompositeTransformer.getDefaultTransformer(_schema);
    File avroFile = new File(dataResourceUrl.getFile());
    _mutableSegmentImpl = MutableSegmentImplTestUtils
        .createMutableSegmentImpl(_schema, Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
            false, true);
    GenericRow reuse = new GenericRow();
    try (RecordReader recordReader = new JSONRecordReader(avroFile, _schema)) {
      while (recordReader.hasNext()) {
        recordReader.next(reuse);
        GenericRow transformedRow = _recordTransformer.transform(reuse);
        _mutableSegmentImpl.index(transformedRow, null);
        reuse.clear();
      }
    }
    _finalNullColumns = Arrays.asList("signup_email", "cityid");
  }

  @Test
  public void testNullValueVector()
      throws Exception {
    ColumnDataSource cityIdDataSource = _mutableSegmentImpl.getDataSource("cityid");
    ColumnDataSource descriptionDataSource = _mutableSegmentImpl.getDataSource("description");
    NullValueVectorReader cityIdNullValueVector = cityIdDataSource.getNullValueVector();
    NullValueVectorReader descNullValueVector = descriptionDataSource.getNullValueVector();
    Assert.assertFalse(cityIdNullValueVector.isNull(1));
    Assert.assertTrue(cityIdNullValueVector.isNull(0));
    Assert.assertFalse(descNullValueVector.isNull(0));
    Assert.assertFalse(descNullValueVector.isNull(1));
  }

  @Test
  public void testGetRecord() {
    GenericRow reuse = new GenericRow();
    _mutableSegmentImpl.getRecord(0, reuse);
    Assert.assertEquals(reuse.getNullValueFields(), _finalNullColumns);

    reuse.clear();
    _mutableSegmentImpl.getRecord(1, reuse);
    Assert.assertTrue(reuse.getNullValueFields().isEmpty());
  }
}
