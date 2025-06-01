package com.example.educonnect.services;

import com.example.educonnect.entities.CourseSession;
import com.google.firebase.firestore.Filter;

import java.util.List;
import java.util.function.Consumer;

public class CourseSessionService extends BaseService<CourseSession> {
    public CourseSessionService() {
        super("coursesSessions");
    }

    /**
     * Fetch all CourseSession entries (no filter).
     */
    public void getAllSessions(Consumer<List<CourseSession>> onSuccess) {
        super.getAll(onSuccess);
    }
}
