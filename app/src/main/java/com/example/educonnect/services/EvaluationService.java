package com.example.educonnect.services;

import com.example.educonnect.entities.Evaluation;
import com.google.firebase.firestore.Filter;

import java.util.List;
import java.util.function.Consumer;

public class EvaluationService extends BaseService<Evaluation> {
    public EvaluationService() {
        super("evaluations");
    }
    /**
     * Fetch all evaluations whose courseId equals the given value.
     */
    public void getByCourseId(String courseId, Consumer<List<Evaluation>> onSuccess) {
        Filter filter = Filter.equalTo("courseId", courseId);
        super.get(filter, onSuccess);
    }
}
