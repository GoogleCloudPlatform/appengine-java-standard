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

package com.google.appengine.api.memcache;

import static com.google.common.base.Throwables.throwIfInstanceOf;
import static com.google.common.base.Throwables.throwIfUnchecked;

import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.ApiProxy;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * Helper methods and constants shared by classes that implement the java api
 * of MemcacheService.
 *
 */
class MemcacheServiceApiHelper {

  //@VisibleForTesting
  static final String PACKAGE = "memcache";
  private static final Logger logger = Logger.getLogger(MemcacheServiceApiHelper.class.getName());

  private MemcacheServiceApiHelper() {
    // Utility class
  }

  public interface Provider<T> {
    T get();
  }

  interface Transformer<F, T> {
    T transform(F from);
  }

  /**
   * An RPC response handler to convert an ApiProxy rpc response
   * (byte[] or exceptions) to an API level response.
   */
  static class RpcResponseHandler<M extends Message, T> {

    private final String errorText;
    private final Message.Builder builder;
    private final Transformer<M, T> responseTransfomer;
    private final ErrorHandler errorHandler;

    RpcResponseHandler(M response, String errorText,
        Transformer<M, T> responseTransfomer, ErrorHandler errorHandler) {
      this.builder = response.newBuilderForType();
      this.errorText = errorText;
      this.responseTransfomer = responseTransfomer;
      this.errorHandler = errorHandler;
    }

    T convertResponse(byte[] responseBytes) throws InvalidProtocolBufferException {
      @SuppressWarnings("unchecked")
      M response = (M) builder.mergeFrom(responseBytes).build();
      return responseTransfomer.transform(response);
    }

    void handleApiProxyException(Throwable cause) throws Exception {
      handleApiProxyException(cause, errorHandler);
    }

    void handleApiProxyException(Throwable cause, ErrorHandler errorHandler) throws Exception {
      try {
        throw cause;
      } catch (InvalidProtocolBufferException ex) {
        errorHandler.handleServiceError(
            new MemcacheServiceException("Could not decode response:", ex));
      } catch (ApiProxy.ApplicationException ex) {
        // We don't want to propagate the app exception outside the api layer
        // but we can at least log the details.  Make sure we log before
        // we delegate to the handler because the handler implementation might throw.
        logger.info(errorText + ": " + ex.getErrorDetail());
        errorHandler.handleServiceError(new MemcacheServiceException(errorText));
      } catch (ApiProxy.ApiProxyException ex) {
        errorHandler.handleServiceError(new MemcacheServiceException(errorText, ex));
      } catch (MemcacheServiceException ex) {
        if (errorHandler instanceof ConsistentErrorHandler) {
          errorHandler.handleServiceError(ex);
        } else {
          // MemcacheServiceException thrown by responseTransfomer will not
          // be delegated to errorHandlers which do not implement the
          // ConsistentErrorHandler to preserve backward compatibility
          throw ex;
        }
      } catch (Throwable ex) {
        throwIfInstanceOf(ex, Exception.class);
        throwIfUnchecked(ex);
        throw new RuntimeException(ex);
      }
    }

    Logger getLogger() {
      return logger;
    }

    ErrorHandler getErrorHandler() {
      return errorHandler;
    }
  }

  /**
   * Issue an async rpc against the memcache package with the given request and
   * response pbs as input and apply standard exception handling.  Do not
   * use this helper function if you need non-standard exception handling.
   */
  static <M extends Message, T> Future<T> makeAsyncCall(String methodName, Message request,
      final RpcResponseHandler<M, T> responseHandler, final Provider<T> defaultValue) {
    Future<byte[]> asyncResp = ApiProxy.makeAsyncCall(PACKAGE, methodName, request.toByteArray());
    return new FutureWrapper<byte[], T>(asyncResp) {

      @Override
      protected T wrap(byte[] bytes) throws Exception {
        try {
          // This null check is mainly for the benefit of unit tests
          // (specifically ones using EasyMock, where the default behavior is to return null).
          return bytes == null ? null : responseHandler.convertResponse(bytes);
        } catch (Exception ex) {
          return absorbParentException(ex);
        }
      }

      @Override
      protected T absorbParentException(Throwable cause) throws Exception {
        responseHandler.handleApiProxyException(cause);
        return defaultValue.get();
      }

      @Override
      protected Throwable convertException(Throwable cause) {
        return cause;
      }
    };
  }
}
