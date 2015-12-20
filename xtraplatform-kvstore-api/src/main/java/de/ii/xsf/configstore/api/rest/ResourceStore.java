package de.ii.xsf.configstore.api.rest;

import de.ii.xsf.core.api.Resource;
import java.io.IOException;
import java.util.List;

/**
 *
 * @author zahnen
 * @param <T>
 */
public interface ResourceStore<T extends Resource> {

    List<String> getResourceIds();

    T getResource(String id);

    boolean hasResource(String id);

    void addResource(T resource) throws IOException;

    void deleteResource(String id) throws IOException;

    void updateResource(T resource) throws IOException;

    void updateResourceOverrides(String id, T resource) throws IOException;
    
    ResourceStore<T> withParent(String storeId);

    ResourceStore<T> withChild(String storeId);
    
    List<String[]> getAllPaths();
}
