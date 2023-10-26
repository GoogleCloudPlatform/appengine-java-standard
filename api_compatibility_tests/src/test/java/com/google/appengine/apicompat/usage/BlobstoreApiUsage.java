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

package com.google.appengine.apicompat.usage;

import static com.google.appengine.apicompat.Utils.classes;

import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobInfoFactory;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreFailureException;
import com.google.appengine.api.blobstore.BlobstoreInputStream;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.blobstore.ByteRange;
import com.google.appengine.api.blobstore.FileInfo;
import com.google.appengine.api.blobstore.IBlobstoreServiceFactory;
import com.google.appengine.api.blobstore.IBlobstoreServiceFactoryProvider;
import com.google.appengine.api.blobstore.RangeFormatException;
import com.google.appengine.api.blobstore.UnsupportedRangeFormatException;
import com.google.appengine.api.blobstore.UploadOptions;
import com.google.appengine.api.blobstore.dev.BlobInfoStorage;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.apicompat.ExhaustiveApiInterfaceUsage;
import com.google.appengine.apicompat.ExhaustiveApiUsage;
import com.google.appengine.apicompat.UsageTracker;
import com.google.appengine.spi.FactoryProvider;
import com.google.appengine.tools.development.testing.LocalBlobstoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Exhaustive usage of the Blobstore Api. Used for backward compatibility checks. */
public class BlobstoreApiUsage {

  /**
   * Exhaustive use of {@link BlobstoreServiceFactory}.
   */
  public static class BlobstoreServiceFactoryUsage
      extends ExhaustiveApiUsage<BlobstoreServiceFactory> {

    @Override
    public Set<Class<?>> useApi() {
      BlobstoreService bc = BlobstoreServiceFactory.getBlobstoreService();
      BlobstoreServiceFactory factory = new BlobstoreServiceFactory(); // ugh
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link IBlobstoreServiceFactory}.
   */
  public static class IBlobstoreServiceFactoryUsage
      extends ExhaustiveApiInterfaceUsage<IBlobstoreServiceFactory> {

    @Override
    public Set<Class<?>> useApi(IBlobstoreServiceFactory iBlobstoreServiceFactory) {
      iBlobstoreServiceFactory.getBlobstoreService();
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link IBlobstoreServiceFactoryProvider}.
   */
  public static class IBlobstoreServiceFactoryProviderUsage
      extends ExhaustiveApiUsage<IBlobstoreServiceFactoryProvider> {

    @Override
    public Set<Class<?>> useApi() {
      IBlobstoreServiceFactoryProvider iBlobstoreServiceFactoryProvider
          = new IBlobstoreServiceFactoryProvider();
      return classes(FactoryProvider.class, Comparable.class, Object.class);
    }
  }


  /**
   * Exhaustive use of {@link BlobstoreService}.
   */
  public static class BlobstoreServiceUsage extends ExhaustiveApiInterfaceUsage<BlobstoreService> {

    int ___apiConstant_MAX_BLOB_FETCH_SIZE;

    @Override
    @SuppressWarnings("deprecation")
    protected Set<Class<?>> useApi(BlobstoreService blobstoreService) {
      BlobKey blobKey = blobstoreService.createGsBlobKey("yar");
      String strVal = blobstoreService.createUploadUrl("yar");
      strVal = blobstoreService.createUploadUrl("yar", UploadOptions.Builder.withDefaults());
      blobstoreService.delete(blobKey);
      byte[] byteVal = blobstoreService.fetchData(blobKey, 0, 10);
      HttpServletRequest req = null;
      ByteRange byteRange = blobstoreService.getByteRange(req);
      Map<String, BlobKey> mapVal = blobstoreService.getUploadedBlobs(req);
      Map<String, List<BlobKey>> uploadsMap = blobstoreService.getUploads(req);
      Map<String, List<BlobInfo>> blobInfos = blobstoreService.getBlobInfos(req);
      Map<String, List<FileInfo>> fileInfos = blobstoreService.getFileInfos(req);
      HttpServletResponse resp = null;
      try {
        blobstoreService.serve(blobKey, resp);
      } catch (IOException e) {
        // ok
      }
      try {
        blobstoreService.serve(blobKey, "yar", resp);
      } catch (IOException e) {
        // ok
      }
      try {
        blobstoreService.serve(blobKey, byteRange, resp);
      } catch (IOException e) {
        // ok
      }
      ___apiConstant_MAX_BLOB_FETCH_SIZE = BlobstoreService.MAX_BLOB_FETCH_SIZE;
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link BlobInfo}.
   */
  public static class BlobInfoUsage extends ExhaustiveApiUsage<BlobInfo> {

    public static class MyBlobInfo extends BlobInfo {

      @UsageTracker.DoNotTrackConstructorInvocation
      MyBlobInfo(BlobKey blobKey, String contentType,
                 Date creation, String filename, long size, String md5Hash, String gsObjectName) {
        super(blobKey, contentType, creation, filename, size, md5Hash, gsObjectName);
        md5Hash = this.md5Hash;
        size = this.size;
        filename = this.filename;
        contentType = this.contentType;
        blobKey = this.blobKey;
        creation = this.creation;
        gsObjectName = this.gsObjectName;
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<Class<?>> useApi() {
      BlobKey blobKey = new BlobKey("yar");
      BlobInfo blobInfo = new BlobInfo(blobKey, "content type", new Date(), "filename", 32);
      blobInfo = new BlobInfo(blobKey, "content type", new Date(), "filename", 32, "md5 hash");
      blobInfo =
          new BlobInfo(
              blobKey, "content type", new Date(), "filename", 32, "md5 hash", "/bucket/object");
      boolean boolVal = blobInfo.equals(blobInfo);
      blobKey = blobInfo.getBlobKey();
      String strVal = blobInfo.getContentType();
      Date dateVal = blobInfo.getCreation();
      strVal = blobInfo.getFilename();
      strVal = blobInfo.getMd5Hash();
      strVal = blobInfo.getGsObjectName();
      long longVal = blobInfo.getSize();
      int intVal = blobInfo.hashCode();
      strVal = blobInfo.toString();
      blobInfo = new MyBlobInfo(
          new BlobKey("yar"), "content type", new Date(), "filename", 32, "hash", "/bucket/object");
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link FileInfo}.
   */
  public static class FileInfoUsage extends ExhaustiveApiUsage<FileInfo> {

    @Override
    @SuppressWarnings("unchecked")
    public Set<Class<?>> useApi() {
      FileInfo fileInfo = new FileInfo("content type", new Date(), "filename", 32, "md5 hash",
                                       "/gs/bucket/filename");
      boolean boolVal = fileInfo.equals(fileInfo);
      int hashCode = fileInfo.hashCode();
      String strVal = fileInfo.getContentType();
      Date dateVal = fileInfo.getCreation();
      strVal = fileInfo.getFilename();
      strVal = fileInfo.getMd5Hash();
      long longVal = fileInfo.getSize();
      strVal = fileInfo.getGsObjectName();
      strVal = fileInfo.toString();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link BlobInfo}.
   */
  public static class BlobInfoFactoryUsage extends ExhaustiveApiUsage<BlobInfoFactory> {

    String ___apiConstant_KIND;
    String ___apiConstant_FILENAME;
    String ___apiConstant_CONTENT_TYPE;
    String ___apiConstant_MD5_HASH;
    String ___apiConstant_SIZE;
    String ___apiConstant_CREATION;
    String ___apiConstant_GS_OBJECT_NAME;

    @Override
    @SuppressWarnings("unchecked")
    public Set<Class<?>> useApi() {
      LocalServiceTestHelper helper = new LocalServiceTestHelper(
          new LocalDatastoreServiceTestConfig(), new LocalBlobstoreServiceTestConfig());
      helper.setUp();
      try {
        BlobInfoFactory factory = new BlobInfoFactory();
        factory = new BlobInfoFactory(DatastoreServiceFactory.getDatastoreService());
        Entity entity = new Entity("yar", "key name");
        ___apiConstant_CONTENT_TYPE = BlobInfoFactory.CONTENT_TYPE;
        ___apiConstant_FILENAME = BlobInfoFactory.FILENAME;
        ___apiConstant_CREATION = BlobInfoFactory.CREATION;
        ___apiConstant_KIND = BlobInfoFactory.KIND;
        ___apiConstant_SIZE = BlobInfoFactory.SIZE;
        ___apiConstant_MD5_HASH = BlobInfoFactory.MD5_HASH;
        ___apiConstant_GS_OBJECT_NAME = BlobInfoFactory.GS_OBJECT_NAME;
        entity.setProperty(BlobInfoFactory.CONTENT_TYPE, "content type");
        entity.setProperty(BlobInfoFactory.FILENAME, "filename");
        entity.setProperty(BlobInfoFactory.CREATION, new Date());
        entity.setProperty(BlobInfoFactory.SIZE, 23L);
        BlobInfo blobInfo = factory.createBlobInfo(entity);
        blobInfo = factory.loadBlobInfo(new BlobKey("yar"));
        Iterator<BlobInfo> blobInfos = factory.queryBlobInfos();
        blobInfos = factory.queryBlobInfosAfter(new BlobKey("key"));
        return classes(Object.class);
      } finally {
        helper.tearDown();
      }
    }
  }

  /**
   * Exhaustive use of {@link BlobstoreFailureException}.
   */
  public static class BlobstoreFailureExceptionUsage
      extends ExhaustiveApiUsage<BlobstoreFailureException> {

    @Override
    @SuppressWarnings({"unchecked", "ThrowableInstanceNeverThrown"})
    public Set<Class<?>> useApi() {
      BlobstoreFailureException unused1 = new BlobstoreFailureException("boom");
      BlobstoreFailureException unused2 = new BlobstoreFailureException("boom", new Throwable());
      return classes(Object.class, RuntimeException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link BlobstoreInputStream}.
   */
  public static class BlobstoreInputStreamUsage extends ExhaustiveApiUsage<BlobstoreInputStream> {

    @Override
    @SuppressWarnings("unchecked")
    public Set<Class<?>> useApi() {
      LocalServiceTestHelper helper = new LocalServiceTestHelper(
              new LocalDatastoreServiceTestConfig(), new LocalBlobstoreServiceTestConfig());
      helper.setUp();
      BlobKey blobKey = new BlobKey("yar");
      BlobInfo blobInfo = new BlobInfo(blobKey, "content type", new Date(), "filename", 23);
      BlobInfoStorage storage = new BlobInfoStorage();
      storage.saveBlobInfo(blobInfo);
      try {
        BlobstoreInputStream bis = null;
        try {
          bis = new BlobstoreInputStream(blobKey);
        } catch (IOException e) {
          // ok
        }
        try {
          bis = new BlobstoreInputStream(blobKey, 2);
        } catch (IOException e) {
          // ok
        }
        try {
          bis.close();
        } catch (IOException e) {
          // ok
        }
        bis.mark(1);
        boolean boolVal = bis.markSupported();
        try {
          int intVal = bis.read();
        } catch (IOException e) {
          // ok
        }
        try {
          int intVal = bis.read(new byte[1], 0, 1);
        } catch (IOException e) {
          // ok
        }
        try {
          bis.reset();
        } catch (IOException e) {
          // ok
        }
        // This is a hack to get around the fact that java 7 classes are not yet available in
        // blaze but tests are running under a Java 7 runtime.
        // TODO(maxr): Reference AutoCloseable directly once java 7 classes are available at compile
        // time.
        Class<?> autoCloseable = Closeable.class.getInterfaces()[0];
        return classes(Object.class, InputStream.class, autoCloseable, Closeable.class);
      } finally {
        helper.tearDown();
      }
    }
  }

  /**
   * Exhaustive use of {@link BlobstoreInputStream.BlobstoreIOException}.
   */
  public static class BlobstoreIOExceptionUsage
      extends ExhaustiveApiUsage<BlobstoreInputStream.BlobstoreIOException> {

    @Override
    @SuppressWarnings({"unchecked", "ThrowableInstanceNeverThrown"})
    public Set<Class<?>> useApi() {
      BlobstoreInputStream.BlobstoreIOException unused1 =
          new BlobstoreInputStream.BlobstoreIOException("boom");
      BlobstoreInputStream.BlobstoreIOException unused2 =
          new BlobstoreInputStream.BlobstoreIOException("boom", new Throwable());
      return classes(Object.class, IOException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link BlobstoreInputStream.ClosedStreamException}.
   */
  public static class ClosedStreamExceptionUsage
      extends ExhaustiveApiUsage<BlobstoreInputStream.ClosedStreamException> {

    @Override
    @SuppressWarnings({"unchecked", "ThrowableInstanceNeverThrown"})
    public Set<Class<?>> useApi() {
      BlobstoreInputStream.ClosedStreamException unused1 =
          new BlobstoreInputStream.ClosedStreamException("boom");
      BlobstoreInputStream.ClosedStreamException unused2 =
          new BlobstoreInputStream.ClosedStreamException("boom", new Throwable());
      return classes(Object.class, IOException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link ByteRange}.
   */
  public static class ByteRangeUsage extends ExhaustiveApiUsage<ByteRange> {

    public static class MyByteRange extends ByteRange {

      @UsageTracker.DoNotTrackConstructorInvocation
      public MyByteRange(long start, Long end) {
        super(start, end);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<Class<?>> useApi() {
      ByteRange range = new ByteRange(0L);
      range = new ByteRange(0L, 1L);
      boolean boolVal = range.equals(range);
      long longVal = range.getEnd();
      longVal = range.getStart();
      boolVal = range.hasEnd();
      int intVal = range.hashCode();
      String strVal = range.toString();
      try {
        range = ByteRange.parse("invalid");
      } catch (RangeFormatException e) {
        // ok
      }
      try {
        range = ByteRange.parseContentRange("invalid");
      } catch (RangeFormatException e) {
        // ok
      }
      range = new MyByteRange(0, Long.valueOf(2));
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link RangeFormatException}.
   */
  public static class RangeFormatExceptionUsage extends ExhaustiveApiUsage<RangeFormatException> {

    @Override
    @SuppressWarnings("ThrowableInstanceNeverThrown")
    public Set<Class<?>> useApi() {
      RangeFormatException unused1 = new RangeFormatException("boom");
      RangeFormatException unused2 = new RangeFormatException("boom", new Throwable());
      return classes(Object.class, RuntimeException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link UnsupportedRangeFormatException}.
   */
  public static class UnsupportedRangeFormatExceptionUsage
      extends ExhaustiveApiUsage<UnsupportedRangeFormatException> {

    @Override
    @SuppressWarnings("ThrowableInstanceNeverThrown")
    public Set<Class<?>> useApi() {
      UnsupportedRangeFormatException unused1 = new UnsupportedRangeFormatException("boom");
      UnsupportedRangeFormatException unused2 =
          new UnsupportedRangeFormatException("boom", new Throwable());
      return classes(Object.class, RangeFormatException.class, RuntimeException.class,
          Exception.class, Throwable.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link UploadOptions}.
   */
  public static class UploadOptionsUsage extends ExhaustiveApiUsage<UploadOptions> {

    @Override
    public Set<Class<?>> useApi() {
      UploadOptions opts = UploadOptions.Builder.withDefaults();
      boolean boolVal = opts.equals(opts);
      opts = opts.googleStorageBucketName("yar");
      opts = opts.maxUploadSizeBytes(10);
      opts = opts.maxUploadSizeBytesPerBlob(10);
      int intVal = opts.hashCode();
      String strVal = opts.toString();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link UploadOptions.Builder}.
   */
  public static class UploadOptionsBuilderUsage extends ExhaustiveApiUsage<UploadOptions.Builder> {

    @Override
    public Set<Class<?>> useApi() {
      UploadOptions opts = UploadOptions.Builder.withDefaults();
      opts = UploadOptions.Builder.withGoogleStorageBucketName("yar");
      opts = UploadOptions.Builder.withMaxUploadSizeBytes(10);
      opts = UploadOptions.Builder.withMaxUploadSizeBytesPerBlob(10);
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link BlobKey}.
   */
  public static class BlobKeyUsage extends ExhaustiveApiUsage<BlobKey> {

    @Override
    public Set<Class<?>> useApi() {
      BlobKey blobKey = new BlobKey("yar");
      BlobKey blobKey2 = new BlobKey("yar");
      int intVal = blobKey.compareTo(blobKey2);
      boolean boolVal = blobKey.equals(blobKey2);
      String strVal = blobKey.getKeyString();
      intVal = blobKey.hashCode();
      strVal = blobKey.toString();
      return classes(Object.class, Serializable.class, Comparable.class);
    }
  }
}
