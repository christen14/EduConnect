package com.example.educonnect.services;

import com.example.educonnect.entities.CourseDocument;

import java.util.function.Consumer;

/**
 * Service for CourseDocument CRUD operations.
 */
public class CourseDocumentService extends BaseService<CourseDocument> {

    public CourseDocumentService() {
        super("coursesDocuments");
    }

    /**
     * Helper to create a new CourseDocument.
     */
    public void createDocument(CourseDocument cd, Consumer<CourseDocument> onSuccess) {
        super.create(cd, onSuccess);
    }
}
