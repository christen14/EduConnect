// File: app/src/main/java/com/example/educonnect/fragments/SurveysFragment.java
package com.example.educonnect.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.educonnect.R;
import com.example.educonnect.entities.Course;
import com.example.educonnect.entities.Evaluation;
import com.example.educonnect.entities.StudentCourse;
import com.example.educonnect.services.CourseService;
import com.example.educonnect.services.EvaluationService;
import com.example.educonnect.services.StudentCourseService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Displays a list of “Surveys” (one per course the student is enrolled in),
 * but excludes any survey that the user has already answered (an Evaluation record exists).
 *
 * Steps:
 *   (1) Fetch all StudentCourse where studentEmail == currentUser.getEmail().
 *   (2) Fetch all Evaluation where studentEmail == currentUser.getEmail().
 *   (3) Build a Set of answered course‐codes from those Evaluations.
 *   (4) Fetch each Course’s details, but only build a Survey item if that courseCode is NOT in the “answered” set.
 */
public class SurveysFragment extends Fragment {

    private final StudentCourseService studentCourseService = new StudentCourseService();
    private final CourseService courseService = new CourseService();
    private final EvaluationService evaluationService = new EvaluationService();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_surveys, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        // 1) Reference and configure the RecyclerView
        RecyclerView rv = v.findViewById(R.id.rvSurveys);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        // 2) Get the current user’s email
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(getContext(),
                    "Utilisateur non connecté",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String userEmail = user.getEmail();

        // 3) Fetch all StudentCourse documents for this student
        Filter filterSC = Filter.equalTo("studentEmail", userEmail);
        studentCourseService.get(filterSC, studentCourses -> {

            // If the student is not enrolled in any course, there are no surveys to show
            if (studentCourses.isEmpty()) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(),
                            "Aucun cours trouvé pour affichage d’enquête",
                            Toast.LENGTH_SHORT).show();
                    rv.setAdapter(new SurveyAdapter(
                            Collections.emptyList(),
                            survey -> { /* no‐op */ }
                    ));
                });
                return;
            }

            // Collect all courseCodes that the student is enrolled in
            List<String> courseCodes = new ArrayList<>();
            for (StudentCourse sc : studentCourses) {
                courseCodes.add(sc.getCourseCode());
            }

            // 4) Fetch all Evaluation documents where studentEmail == currentUser.getEmail()
            //    so that we know which course‐codes the student has already answered.
            Filter filterEval = Filter.equalTo("studentEmail", userEmail);
            evaluationService.get(filterEval, evaluations -> {

                // Build a Set of all courseIds (courseCodes) that have already been evaluated by this student
                Set<String> answeredCourseCodes = new HashSet<>();
                for (Evaluation e : evaluations) {
                    // Each Evaluation has e.getCourseId() set to the code of the course they evaluated
                    if (e.getCourseId() != null) {
                        answeredCourseCodes.add(e.getCourseId());
                    }
                }

                // 5) Now fetch Course details for each courseCode.
                //    But when building the final Survey list, skip any code already in answeredCourseCodes.
                Map<String, Course> codeToCourseMap = new HashMap<>();
                final int totalCourses = courseCodes.size();
                final int[] doneCount = {0};

                for (String code : courseCodes) {
                    Filter filterCourse = Filter.equalTo("code", code);
                    courseService.get(filterCourse, courses -> {
                        if (!courses.isEmpty()) {
                            Course c = courses.get(0);
                            codeToCourseMap.put(code, c);
                        }
                        doneCount[0]++;

                        // Once we’ve fetched all Course documents, build the “pending surveys” list
                        if (doneCount[0] == totalCourses) {
                            List<Survey> surveys = new ArrayList<>();
                            for (String cc : courseCodes) {
                                // If the student already answered this course’s survey, skip it
                                if (answeredCourseCodes.contains(cc)) {
                                    continue;
                                }

                                // Otherwise, build a Survey item
                                Course c = codeToCourseMap.get(cc);
                                String title;
                                if (c != null) {
                                    title = c.getCode() + " – " + c.getName();
                                } else {
                                    title = cc + " – (nom introuvable)";
                                }
                                // Hardcode the deadline text (unchanged)
                                String deadline = "05/06/2025 à 23h59";
                                surveys.add(new Survey(cc, title, deadline));
                            }

                            // Update the RecyclerView on the UI thread
                            requireActivity().runOnUiThread(() -> {
                                if (surveys.isEmpty()) {
                                    Toast.makeText(getContext(),
                                            "Vous avez déjà répondu à toutes les enquêtes",
                                            Toast.LENGTH_LONG).show();
                                }

                                SurveyAdapter adapter = new SurveyAdapter(
                                        surveys,
                                        survey -> {
                                            // → Redirect to detail only if it’s in “surveys” (i.e. not answered)
                                            SurveyDetailFragment detail =
                                                    SurveyDetailFragment.newInstance(survey.title);
                                            requireActivity().getSupportFragmentManager().beginTransaction()
                                                    .replace(R.id.fragment_container, detail)
                                                    .addToBackStack(null)
                                                    .commit();
                                        });
                                rv.setAdapter(adapter);
                            });
                        }
                    });
                }
            });
        });

        // 6) Attach bottom nav bar
        getChildFragmentManager().beginTransaction()
                .replace(R.id.bottom_navigation_container, new NavBarFragment())
                .commit();
    }

    /**
     * Simple model for “Survey” displayed in the list.
     * We keep the courseCode (so we know which course it is),
     * plus a title (code + name) and a deadline string.
     */
    private static class Survey {
        final String courseCode;
        final String title;
        final String deadline;

        Survey(String courseCode, String title, String deadline) {
            this.courseCode = courseCode;
            this.title = title;
            this.deadline = deadline;
        }
    }

    /**
     * Adapter for the list of surveys.  Clicking on a Survey item launches SurveyDetailFragment.
     */
    private static class SurveyAdapter
            extends RecyclerView.Adapter<SurveyAdapter.VH> {

        interface OnSurveyClick { void onClick(Survey survey); }

        private final List<Survey> items;
        private final OnSurveyClick listener;

        SurveyAdapter(List<Survey> items, OnSurveyClick listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_survey, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Survey s = items.get(pos);
            h.tvCourse.setText(s.title);
            h.tvDeadline.setText("se termine le : " + s.deadline);
            h.root.setOnClickListener(x -> listener.onClick(s));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final View    root;
            final TextView tvCourse, tvDeadline;
            VH(@NonNull View view) {
                super(view);
                root        = view.findViewById(R.id.llSurveyButton);
                tvCourse    = view.findViewById(R.id.tvSurveyCourse);
                tvDeadline  = view.findViewById(R.id.tvSurveyDeadline);
            }
        }
    }
}
