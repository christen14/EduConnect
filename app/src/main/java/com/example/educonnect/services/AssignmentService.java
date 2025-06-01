package com.example.educonnect.services;

import com.example.educonnect.entities.Assignment;
import com.google.firebase.firestore.Filter;

import java.util.List;
import java.util.function.Consumer;

public class AssignmentService extends BaseService<Assignment> {

    public AssignmentService() {
        super("assignments");
        // “assignments” is the Firestore collection name for Assignment
    }

    /**
     * Fetch all assignments (no filter).
     */
    public void getAllAssignments(Consumer<List<Assignment>> onSuccess) {
        super.getAll(onSuccess);
    }

    /**
     * Fetch all assignments that match a given filter, sorted by some field.
     */
    public void getAssignmentsFilteredSorted(Filter filter,
                                             String sortField,
                                             Consumer<List<Assignment>> onSuccess) {
        super.getSorted(filter, sortField, onSuccess);
    }

    /** Fetch all assignments for a given courseId, sorted by dueAt ascending */
    public void getByCourseId(String courseId, Consumer<List<Assignment>> onSuccess) {
        Filter filter = Filter.equalTo("courseId", courseId);
        super.getSorted(filter, "dueAt", onSuccess);
    }
}
