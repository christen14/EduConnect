package com.example.educonnect.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.educonnect.R;
import com.example.educonnect.entities.Course;
import com.example.educonnect.entities.StudentCourse;
import com.example.educonnect.services.StudentCourseService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Filter;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CoursesFragment extends Fragment {
    private ExpandableListView elvCourses;

    private final List<String> semesters = Arrays.asList("Semestre 1", "Semestre 2");
    private final Map<String, List<Course>> allCourses = new HashMap<>();

    private CoursesAdapter adapter;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private FirebaseAuth mAuth;

    private StudentCourseService studentCourseService = new StudentCourseService();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_courses, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        elvCourses = view.findViewById(R.id.elvCourses);

        // 1) Initialize with empty lists for each semester
        for (String semester : semesters) {
            allCourses.put(semester, new ArrayList<>());
        }

        // 2) Create adapter once with empty data, set to ExpandableListView
        adapter = new CoursesAdapter(getContext(), semesters, allCourses);
        elvCourses.setAdapter(adapter);

        // Get courses the student is enrolled in
        assert currentUser != null;
        Filter filter = Filter.equalTo("studentEmail", currentUser.getEmail());
        studentCourseService.get(filter, (studentCourses) -> {
            ArrayList<String> enrolledCourseCodes = new ArrayList<>();
            for (StudentCourse sc : studentCourses) {
                enrolledCourseCodes.add(sc.getCourseCode());
            }

            db.collection("courses")
                    .whereEqualTo("semester", 1)
                    .whereIn("code", enrolledCourseCodes)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        List<Course> courses = new ArrayList<>();
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            courses.add(doc.toObject(Course.class));
                        }
                        // update your UI on main thread
                        requireActivity().runOnUiThread(() -> {
                            allCourses.get("Semestre 1").clear();
                            allCourses.get("Semestre 1").addAll(courses);
                            adapter.notifyDataSetChanged();
                        });
                    });
            db.collection("courses")
                    .whereEqualTo("semester", 2)
                    .whereIn("code", enrolledCourseCodes)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        List<Course> courses = new ArrayList<>();
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            courses.add(doc.toObject(Course.class));
                        }
                        // update your UI on main thread
                        requireActivity().runOnUiThread(() -> {
                            allCourses.get("Semestre 2").clear();
                            allCourses.get("Semestre 2").addAll(courses);
                            adapter.notifyDataSetChanged();
                        });
                    });
        });

        // 5) Optionally expand all groups
//        for (int i = 0; i < semesters.size(); i++) {
//            elvCourses.expandGroup(i);
//        }

        // 6) Handle child clicks to open documents fragment
        elvCourses.setOnChildClickListener((parent, childView, groupPos, childPos, id) -> {
            Course clickedCourse = allCourses.get(semesters.get(groupPos)).get(childPos);
            String courseCode = clickedCourse.getCode();
            String courseName = clickedCourse.getName();

            DocumentsFragment docs = DocumentsFragment.newInstance(courseCode, courseName);
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, docs)
                    .addToBackStack(null)
                    .commit();

            return true;
        });

        // 7) Attach bottom nav bar (if needed)
        getChildFragmentManager().beginTransaction()
                .replace(R.id.bottom_navigation_container, new NavBarFragment())
                .commit();
    }

}
