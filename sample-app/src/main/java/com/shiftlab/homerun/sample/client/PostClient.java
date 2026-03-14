package com.shiftlab.homerun.sample.client;

import com.shiftlab.homerun.sample.model.Post;

/**
 * Downstream-service interface for JSONPlaceholder.
 * Both the real HTTP client and the mock client implement this contract.
 */
public interface PostClient {

    /**
     * Fetches a single post by ID from the downstream service.
     *
     * @param id post identifier (1-based)
     * @return the post record
     */
    Post getPost(int id);
}
