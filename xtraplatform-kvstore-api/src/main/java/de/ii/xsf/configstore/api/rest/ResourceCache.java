package de.ii.xsf.configstore.api.rest;

import de.ii.xsf.configstore.api.Transaction;
import de.ii.xsf.configstore.api.TransactionSupport;
import de.ii.xsf.configstore.api.WriteTransaction;
import de.ii.xsf.core.api.Resource;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 * @author fischer
 */
public class ResourceCache<T extends Resource> implements TransactionSupport<T> {

    private final boolean fullCache;
    Map<String, T> resourceCache;
    List<String> resourceIdCache;

    public ResourceCache(boolean fullCache) {
        this.fullCache = fullCache;
        this.resourceIdCache = new CopyOnWriteArrayList<>();
        if (this.fullCache) {
            resourceCache = new ConcurrentHashMap<>();
        }
    }

    public void put(String id, T resource) {
        if (!this.resourceIdCache.contains(id)) {
            this.resourceIdCache.add(id);
        }
        if (this.fullCache) {
            this.resourceCache.put(id, resource);
        }
    }

    public void add(List<String> ids) {
        this.resourceIdCache = ids;
    }

    public void add(Map<String, T> resourceCache) {
        this.resourceCache = resourceCache;
    }

    public T get(String id) {
        if (this.fullCache) {
            return resourceCache.get(id);
        }
        // TODO ?
        return null;
    }

    public boolean hasResource(String id) {
        if (fullCache) {
            return this.resourceCache.containsKey(id);
        } else {
            return this.resourceIdCache.contains(id);
        }
    }

    public List<String> getResourceIds() {
        return this.resourceIdCache;
    }

    public void remove(String id) {
        this.resourceIdCache.remove(id);
        if (this.fullCache) {
            this.resourceCache.remove(id);
        }
    }

    @Override
    public Transaction openDeleteTransaction(String key) {
        return new DeleteCacheTransaction<>(this, key);
    }

    @Override
    public WriteTransaction<T> openWriteTransaction(String key) {
        return new WriteCacheTransaction<>(this, key);
    }

}
