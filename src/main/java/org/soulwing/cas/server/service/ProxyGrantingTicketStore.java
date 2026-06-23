package org.soulwing.cas.server.service;

import javax.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ProxyGrantingTicketStore {

    private final Map<String, String> store = new ConcurrentHashMap<>();

    public void save(String pgt, String username) {
        store.put(pgt, username);
    }

    public String get(String pgt) {
        return store.get(pgt);
    }
}