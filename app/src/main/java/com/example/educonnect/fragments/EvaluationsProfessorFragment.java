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
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.educonnect.R;
import com.example.educonnect.entities.Course;
import com.example.educonnect.entities.Evaluation;
import com.example.educonnect.entities.User;
import com.example.educonnect.services.CourseService;
import com.example.educonnect.services.EvaluationService;
import com.example.educonnect.services.UserService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class EvaluationsProfessorFragment extends Fragment {
    private RecyclerView rvEvals;

    private final UserService userService = new UserService();
    private final CourseService courseService = new CourseService();
    private final EvaluationService evaluationService = new EvaluationService();

    // Maps courseId → courseCode
    private final Map<String, String> idToCourseCode = new HashMap<>();

    // Maps courseCode → List<Evaluation>
    private final Map<String, List<Evaluation>> byCourseCode = new HashMap<>();

    // A flat list of items where each item is either a CourseHeader or an Evaluation
    private final List<Object> flatList = new ArrayList<>();

    private static final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_evaluations_professor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v,
                              @Nullable Bundle b) {
        super.onViewCreated(v, b);
        rvEvals = v.findViewById(R.id.rvEvalsProf);

        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser == null || fbUser.getEmail() == null) {
            Toast.makeText(getContext(),
                    getString(R.string.non_connecte), Toast.LENGTH_SHORT).show();
            return;
        }
        String email = fbUser.getEmail().trim();

        // 1) Look up the professor’s Firestore ID via UserService
        userService.get(
                com.google.firebase.firestore.Filter.equalTo("email", email),
                users -> {
                    if (users.isEmpty()) {
                        Toast.makeText(getContext(),
                                getString(R.string.erreur_profil), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    User u = users.get(0);
                    String professorId = u.getId();

                    // 2) Fetch all courses taught by this professor
                    courseService.getByProfessor(professorId, courses -> {
                        if (courses.isEmpty()) {
                            Toast.makeText(getContext(),
                                    getString(R.string.aucun_cours_enseigne), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        idToCourseCode.clear();
                        for (Course c : courses) {
                            idToCourseCode.put(c.getId(), c.getCode());
                        }
                        // 3) Now fetch all evaluations, then filter & build flat list
                        loadAllEvaluations();
                    });
                });

        // Attach bottom nav bar
        FragmentManager fm = getChildFragmentManager();
        fm.beginTransaction()
                .replace(R.id.bottom_navigation_container, new NavBarFragment())
                .commit();
    }

    /**
     * Fetches all Evaluation documents, then filters to only those whose courseId
     * belongs to this professor.
     */
    private void loadAllEvaluations() {
        evaluationService.getAll(evals -> {
            byCourseCode.clear();

            Collection<String> myCourseIds = idToCourseCode.values();
            for (Evaluation e : evals) {
                String cid = e.getCourseId();
                if (myCourseIds.contains(cid)) {
                    String code = idToCourseCode.get(cid);
                    byCourseCode.computeIfAbsent(code, k -> new ArrayList<>()).add(e);
                }
            }

            // Sort courseCodes alphabetically
            List<String> courseCodesOrdered = new ArrayList<>(byCourseCode.keySet());
            Collections.sort(courseCodesOrdered);

            // Sort each course’s evaluations by submittedAt descending
            for (List<Evaluation> list : byCourseCode.values()) {
                list.sort((e1, e2) -> {
                    if (e1.getSubmittedAt() == null && e2.getSubmittedAt() == null) return 0;
                    if (e1.getSubmittedAt() == null) return 1;
                    if (e2.getSubmittedAt() == null) return -1;
                    return e2.getSubmittedAt().compareTo(e1.getSubmittedAt());
                });
            }

            // Build flatList
            flatList.clear();
            for (String code : courseCodesOrdered) {
                // Add a header object
                flatList.add(new CourseHeader(code));
                // Then all evaluations under that course
                flatList.addAll(byCourseCode.get(code));
            }

            // Update RecyclerView on main thread
            requireActivity().runOnUiThread(() -> {
                rvEvals.setLayoutManager(new LinearLayoutManager(requireContext()));
                rvEvals.setAdapter(new EvalsFlatAdapter(flatList));
            });
        });
    }

    /**
     * Simple wrapper to mark an item as a “header” for a courseCode.
     */
    private static class CourseHeader {
        final String courseCode;

        CourseHeader(String code) {
            this.courseCode = code;
        }
    }

    // ————————————————————————————————
    // RecyclerView.Adapter for a flat list of mixed CourseHeader/Evaluation
    // ————————————————————————————————
    private class EvalsFlatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int VIEW_TYPE_HEADER = 0;
        private static final int VIEW_TYPE_EVAL = 1;

        private final List<Object> items;

        EvalsFlatAdapter(List<Object> items) {
            this.items = items;
        }

        @Override
        public int getItemViewType(int position) {
            return (items.get(position) instanceof CourseHeader)
                    ? VIEW_TYPE_HEADER
                    : VIEW_TYPE_EVAL;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == VIEW_TYPE_HEADER) {
                View view = inflater.inflate(R.layout.item_eval_group, parent, false);
                return new HeaderVH(view);
            } else {
                View view = inflater.inflate(R.layout.item_eval_child, parent, false);
                return new EvalVH(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == VIEW_TYPE_HEADER) {
                CourseHeader ch = (CourseHeader) items.get(position);
                ((HeaderVH) holder).bind(ch);
            } else {
                Evaluation ev = (Evaluation) items.get(position);
                ((EvalVH) holder).bind(ev);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        // — ViewHolder for header (courseCode) —
        class HeaderVH extends RecyclerView.ViewHolder {
            final TextView tvHeader;

            HeaderVH(@NonNull View itemView) {
                super(itemView);
                tvHeader = itemView.findViewById(R.id.tvEvalGroup);
            }

            void bind(CourseHeader ch) {
                tvHeader.setText(ch.courseCode);
            }
        }

        // — ViewHolder for each Evaluation —
        class EvalVH extends RecyclerView.ViewHolder {
            final TextView tvStudentEmail, tvDate, tvRatings, tvComment;

            EvalVH(@NonNull View itemView) {
                super(itemView);
                tvStudentEmail = itemView.findViewById(R.id.tvEvalStudentEmail);
                tvDate = itemView.findViewById(R.id.tvEvalDate);
                tvRatings = itemView.findViewById(R.id.tvEvalRatings);
                tvComment = itemView.findViewById(R.id.tvEvalComment);
            }

            void bind(Evaluation ev) {
                tvStudentEmail.setText(ev.getStudentEmail());

                if (ev.getSubmittedAt() != null) {
                    String dateStr = dateFormat.format(ev.getSubmittedAt().toDate());
                    tvDate.setText(getString(R.string.soumis_le) + dateStr);
                } else {
                    tvDate.setText(R.string.date_inconnue);
                }

                String ratings = String.format(Locale.getDefault(),
                        "Q1: %d   Q2: %d   Q3: %d   Q4: %d   Q5: %d",
                        ev.getQ1(), ev.getQ2(), ev.getQ3(), ev.getQ4(), ev.getQ5());
                tvRatings.setText(ratings);

                String comment = ev.getComment();
                if (comment == null || comment.trim().isEmpty()) {
                    tvComment.setText("Commentaire : (aucun)");
                } else {
                    tvComment.setText("Commentaire : " + comment);
                }
            }
        }
    }
}
