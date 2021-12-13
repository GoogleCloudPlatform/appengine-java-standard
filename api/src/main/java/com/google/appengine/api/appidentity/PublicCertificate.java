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

package com.google.appengine.api.appidentity;

import java.io.Serializable;

/**
 * {@code PublicCertificate} contains an x509 public certificate in PEM format and a string which is
 * used to identify this certificate.
 *
 * @see <a href="http://tools.ietf.org/html/rfc5280">RFC 5280</a> for the specification of x509
 *     certificates.
 */
public final class PublicCertificate implements Serializable {
  private static final long serialVersionUID = 465858322031167202L;

  private final String certficateName;
  private final String x509CertificateInPemFormat;

  /**
   * @param certficiateName name of the certificate.
   * @param x509CertificateInPemFormat x509 certificate in pem format.
   */
  public PublicCertificate(String certficiateName, String x509CertificateInPemFormat) {
    this.certficateName = certficiateName;
    this.x509CertificateInPemFormat = x509CertificateInPemFormat;
  }

  public String getCertificateName() {
    return certficateName;
  }

  public String getX509CertificateInPemFormat() {
    return x509CertificateInPemFormat;
  }
}
