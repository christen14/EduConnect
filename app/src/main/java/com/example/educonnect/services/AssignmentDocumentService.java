package com.example.educonnect.services;

import com.example.educonnect.entities.AssignmentDocument;
import com.google.firebase.firestore.Filter;

import java.util.List;
import java.util.function.Consumer;

/**
 * Service for CRUD operations on AssignmentDocument documents.
 */
public class AssignmentDocumentService extends BaseService<AssignmentDocument> {

    public AssignmentDocumentService() {
        super("assignmentsDocuments");
    }

    /**
     * Create a new AssignmentDocument (for a student’s upload).
     */
    public void createDocument(AssignmentDocument doc, Consumer<AssignmentDocument> onSuccess) {
        super.create(doc, onSuccess);
    }

    /** Fetch all student submissions for a given assignmentId */
    public void getByAssignmentId(String assignmentId, Consumer<List<AssignmentDocument>> onSuccess) {
        Filter filter = Filter.equalTo("assignmentId", assignmentId);
        super.get(filter, onSuccess);
    }
}
