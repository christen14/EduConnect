package com.example.educonnect.services;

import com.example.educonnect.entities.StudentCourse;
import com.google.firebase.firestore.Filter;

import java.util.List;
import java.util.function.Consumer;

public class StudentCourseService extends BaseService<StudentCourse> {
    public StudentCourseService() {
        super("studentsCourses");
    }
    /**
     * Fetch all StudentCourse entries for a given student email.
     */
    public void getByStudentEmail(String email, Consumer<List<StudentCourse>> onSuccess) {
        Filter filter = Filter.equalTo("studentEmail", email);
        super.get(filter, onSuccess);
    }
}
