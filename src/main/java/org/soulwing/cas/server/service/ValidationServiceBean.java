/*
 * File created on Mar 9, 2018
 *
 * Copyright (c) 2018 Carl Harris, Jr
 * and others as noted
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.soulwing.cas.server.service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.soulwing.cas.server.*;

/**
 * A {@link ValidationService} implemented as an injectable bean.
 *
 * @author Carl Harris
 */
@ApplicationScoped
class ValidationServiceBean implements ValidationService {

  @Inject
  TicketService ticketService;

  @Inject
  ServiceResponseBuilderFactory builderFactory;

  @Inject
  AttributesService attributesService;

  @Override
  public ServiceResponse validate(ValidationRequest request) {
    final TicketState state = ticketService.validate(request.getTicket());
    if (state == null) {
      return builderFactory.createAuthenticationFailureBuilder()
              .code(ProtocolError.INVALID_TICKET)
              .message("invalid ticket")
              .build();
    }

    final String username = state.getUsername();
    final List<AttributeValue> attributes =
            attributesService.getAttributes(username);

    String pgtIou = "PGTIOU-" + UUID.randomUUID();

    // If proxy callback URL is provided, issue a PGT and call back the service
    final String proxyCallback = request.getProxyCallbackUrl();
    if (proxyCallback != null && !proxyCallback.trim().isEmpty()) {
      // issue a PGT for this user
      final Ticket pgt = ticketService.issueFor(username);
      try {
        // build callback URL with parameters pgtId and pgtIou
        final String charset = StandardCharsets.UTF_8.name();
        final String encodedPgtId = URLEncoder.encode(pgt.getValue(), charset);
        final String encodedPgtIou = URLEncoder.encode(pgtIou, charset);
        final String sep = proxyCallback.contains("?") ? "&" : "?";
        final String callbackUrl = proxyCallback + sep + "pgtId=" + encodedPgtId + "&pgtIou=" + encodedPgtIou;

        URL url = new URL(callbackUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.connect();
        int code = conn.getResponseCode();
        conn.disconnect();

        if (code < 200 || code >= 300) {
          // callback failed -> respond with invalid proxy callback error
          return builderFactory.createAuthenticationFailureBuilder()
                  .code(ProtocolError.INVALID_PROXY_CALLBACK)
                  .message("proxy callback returned status " + code)
                  .build();
        }
      }
      catch (Exception ex) {
        // any exception calling the proxy callback is treated as invalid proxy callback
        return builderFactory.createAuthenticationFailureBuilder()
                .code(ProtocolError.INVALID_PROXY_CALLBACK)
                .message("proxy callback failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage())
                .build();
      }
    }

    return builderFactory.createAuthenticationSuccessBuilder()
            .user(username)
            .proxyGrantingTicket(pgtIou)
            .attributes(attributes)
            .build();
  }


}
