package com.example.educonnect.services;

import com.example.educonnect.entities.Course;
import com.google.firebase.firestore.Filter;

import java.util.List;
import java.util.function.Consumer;

public class CourseService extends BaseService<Course> {
    public CourseService() {
        super("courses");
    }

    /**
     * Fetch all Course documents where professorId == given email.
     */
    public void getByProfessor(String professorId, Consumer<List<Course>> onSuccess) {
        Filter filter = Filter.equalTo("professorId", professorId);
        super.get(filter, onSuccess);
    }
}
