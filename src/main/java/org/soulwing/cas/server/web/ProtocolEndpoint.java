/*
 * File created on Sep 9, 2014 
 *
 * Copyright (c) Carl Harris, Jr.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.soulwing.cas.server.web;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.soulwing.cas.server.ProtocolError;
import org.soulwing.cas.server.ServiceResponseBuilderFactory;
import org.soulwing.cas.server.Ticket;
import org.soulwing.cas.server.ValidationRequest;
import org.soulwing.cas.server.service.ProxyGrantingTicketStore;
import org.soulwing.cas.server.service.TicketService;
import org.soulwing.cas.server.service.ValidationService;

import java.net.URI;
import java.util.UUID;

/**
 * A JAX-RS endpoint that handles CAS protocol requests.
 *
 * @author Carl Harris
 */
@Path("/")
public class ProtocolEndpoint {

  @Inject
  ValidationService validationService;

  @Inject
  TicketService ticketService;

  @Inject
  ProxyGrantingTicketStore pgtStore;

  @Inject
  ServiceResponseBuilderFactory builderFactory;


  @GET
  @Path("/serviceValidate")
  public Response serviceValidate(
          @QueryParam("ticket") String ticket,
          @QueryParam("service") String service,
          @QueryParam("pgtUrl") String pgtUrl,
          @QueryParam("renew") String renew,
          @QueryParam("format") String format) {
    final ValidationRequest request =
            newValidationRequest(ticket, service, pgtUrl, renew, format);
    return Response.ok(validationService.validate(request)).build();
  }

  @GET
  @Path("/proxyValidate")
  public Response proxyValidate(
          @QueryParam("ticket") String ticket,
          @QueryParam("service") String service,
          @QueryParam("pgtUrl") String pgtUrl,
          @QueryParam("renew") String renew,
          @QueryParam("format") String format) {
    final ValidationRequest request =
            newValidationRequest(ticket, service, pgtUrl, renew, format);
    return Response.ok(validationService.validate(request)).build();
  }


  @GET
  @Path("/p3/serviceValidate")
  public Response p3ServiceValidate(@QueryParam("ticket") String ticket,
                                    @QueryParam("service") String service) {
    return serviceValidate(ticket, service, null, null, null);
  }

  @GET
  @Path("/p3/proxyValidate")
  public Response p3ProxyValidate(@QueryParam("ticket") String ticket,
                                  @QueryParam("service") String service) {
    return proxyValidate(ticket, service, null, null, null);
  }

  private ValidationRequest newValidationRequest(String ticket,
                                                 String service, String pgtUrl, String renew, String format) {
    final ValidationRequest request = new ValidationRequest();
    request.setTicket(ticket);
    request.setService(service);
    if (pgtUrl != null) {
      request.setProxyCallbackUrl(pgtUrl);
    }
    if (renew != null) {
      request.setRenew(Boolean.parseBoolean(renew));
    }
    if (format != null) {
      request.setFormat(format);
    }
    return request;
  }



  @GET
  @Path("/proxy")
  public Response proxy(
          @QueryParam("pgt") String pgt,
          @QueryParam("targetService") String targetService) {

    if (targetService == null || targetService.isEmpty()) {
      return Response.ok(builderFactory.createProxyFailureBuilder()
              .code(ProtocolError.INVALID_REQUEST)
              .message("targetService parameter is required")
              .build()).build();
    }

    String username = pgtStore.get(pgt);

    if (username == null) {
      return Response.ok(
              builderFactory.createProxyFailureBuilder()
                      .code(ProtocolError.INVALID_TICKET)
                      .message("invalid pgt")
                      .build()
      ).build();
    }

// ✅ ensuite on génère PT pour CE user
    final Ticket proxyTicket = ticketService.issueProxyTicketFor(username);


    return Response.ok(
            builderFactory.createProxySuccessBuilder()
                    .proxyTicket(proxyTicket.getValue())
                    .build()
    ).build();

  }


  @GET
  @Path("/logout")
  public Response logout(@QueryParam("url") String url) {
    // Redirection optionnelle après déconnexion
    if (url != null && !url.isEmpty()) {
      return Response.seeOther(URI.create(url)).build();
    }
    return Response.ok("Logout successful").build();
  }


}
