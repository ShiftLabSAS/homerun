package com.shiftlab.homerun.sample.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors the shape returned by
 * https://jsonplaceholder.typicode.com/posts/{id}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Post {
    private int userId;
    private int id;
    private String title;
    private String body;
}
