/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.sdk.io.aws.s3;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.RETURNS_SMART_NULLS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.model.UploadPartResult;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests {@link S3WritableByteChannel}.
 */
@RunWith(JUnit4.class)
public class S3WritableByteChannelTest {

  @Test
  public void write() throws IOException {
    AmazonS3 mockAmazonS3 = mock(AmazonS3.class, withSettings().defaultAnswer(RETURNS_SMART_NULLS));

    InitiateMultipartUploadResult initiateMultipartUploadResult =
        new InitiateMultipartUploadResult();
    initiateMultipartUploadResult.setUploadId("upload-id");
    when(mockAmazonS3.initiateMultipartUpload(
        argThat(notNullValue(InitiateMultipartUploadRequest.class))))
        .thenReturn(initiateMultipartUploadResult);
    UploadPartResult result = new UploadPartResult();
    result.setETag("etag");
    when(mockAmazonS3.uploadPart(argThat(notNullValue(UploadPartRequest.class))))
        .thenReturn(result);

    S3ResourceId path = S3ResourceId.fromUri("s3://bucket/dir/file");
    int uploadBufferSize = 10;

    S3WritableByteChannel channel =
        new S3WritableByteChannel(mockAmazonS3, path, "text/plain", "STANDARD", uploadBufferSize);
    int contentSize = 65;
    ByteBuffer uploadContent = ByteBuffer.allocate((int) (contentSize * 2.5));
    for (byte i = 0; i < contentSize; i++) {
      uploadContent.put(i);
    }
    uploadContent.flip();

    int uploadedSize = channel.write(uploadContent);
    assertEquals(contentSize, uploadedSize);

    channel.close();

    verify(mockAmazonS3, times(1))
        .initiateMultipartUpload(notNull(InitiateMultipartUploadRequest.class));
    int partQuantity = (int) Math.ceil((double) contentSize / uploadBufferSize);
    verify(mockAmazonS3, times(partQuantity)).uploadPart(notNull(UploadPartRequest.class));
    verify(mockAmazonS3, times(1))
        .completeMultipartUpload(notNull(CompleteMultipartUploadRequest.class));
    verifyNoMoreInteractions(mockAmazonS3);
  }
}
