package com.example.educonnect.fragments;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.educonnect.R;
import com.example.educonnect.entities.Assignment;
import com.example.educonnect.entities.AssignmentDocument;
import com.example.educonnect.entities.Course;
import com.example.educonnect.entities.User;
import com.example.educonnect.services.AssignmentDocumentService;
import com.example.educonnect.services.AssignmentService;
import com.example.educonnect.services.CourseService;
import com.example.educonnect.services.UserService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Filter;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RendusProfessorFragment allows professors to see every assignment for their courses,
 * view all student submissions, and assign grades.
 */
public class RendusProfessorFragment extends Fragment {
    private Spinner spinnerCoursesProf;
    private RecyclerView rvAssignmentsProf;

    private final CourseService courseService = new CourseService();
    private final AssignmentService assignmentService = new AssignmentService();
    private final AssignmentDocumentService assignmentDocumentService = new AssignmentDocumentService();
    private final UserService userService = new UserService();

    private final List<Course> professorCourses = new ArrayList<>();
    private final List<Assignment> courseAssignments = new ArrayList<>();

    private AssignmentAdapter assignmentAdapter;

    private String currentProfessorEmail;

    private static final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rendus_professor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v,
                              @Nullable Bundle b) {
        super.onViewCreated(v, b);

        spinnerCoursesProf = v.findViewById(R.id.spinnerCoursesProf);
        rvAssignmentsProf = v.findViewById(R.id.rvAssignmentsProf);

        // 1) Determine current user’s email (professor)
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser == null || fbUser.getEmail() == null) {
            Toast.makeText(getContext(),
                    "Utilisateur non connecté", Toast.LENGTH_SHORT).show();
            return;
        }
        currentProfessorEmail = fbUser.getEmail().trim();

        // 2) Fetch the professor’s User document to get UID (id)
        Filter userFilter = Filter.equalTo("email", currentProfessorEmail);
        userService.get(userFilter, users -> {
            if (users.isEmpty()) {
                Toast.makeText(getContext(),
                        "Impossible de trouver votre profil.", Toast.LENGTH_SHORT).show();
                return;
            }
            User u = users.get(0);
            String professorId = u.getId();

            // 3) Fetch courses taught by this professor
            courseService.getByProfessor(professorId, courses -> {
                professorCourses.clear();
                professorCourses.addAll(courses);

                if (professorCourses.isEmpty()) {
                    Toast.makeText(getContext(),
                            "Aucun cours associé à votre compte.", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Populate spinnerCoursesProf with course codes
                List<String> codes = new ArrayList<>();
                for (Course c : professorCourses) {
                    codes.add(c.getCode() + " – " + c.getName());
                }
                ArrayAdapter<String> courseAdapter = new ArrayAdapter<>(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        codes
                );
                courseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerCoursesProf.setAdapter(courseAdapter);

                // 4) When a course is selected, load assignments
                spinnerCoursesProf.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(android.widget.AdapterView<?> parent,
                                               View view,
                                               int position,
                                               long id) {
                        Course chosen = professorCourses.get(position);
                        loadAssignmentsForCourse(chosen.getId());
                    }

                    @Override
                    public void onNothingSelected(android.widget.AdapterView<?> parent) { /* nop */ }
                });
            });
        });

        // Set up RecyclerView (vertical list)
        rvAssignmentsProf.setLayoutManager(new LinearLayoutManager(requireContext()));
        assignmentAdapter = new AssignmentAdapter(courseAssignments);
        rvAssignmentsProf.setAdapter(assignmentAdapter);

        // 5) Attach bottom nav bar
        if (getChildFragmentManager() != null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.bottom_navigation_container, new NavBarFragment())
                    .commit();
        }
    }

    /**
     * Fetches all assignments belonging to a given course (by Firestore document ID).
     */
    private void loadAssignmentsForCourse(String courseId) {
        System.out.printf("ID :" + courseId);
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("courses")
                .document(courseId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Course course = documentSnapshot.toObject(Course.class);
                        if (course != null) {
                            String courseCode = course.getCode();
                            Log.d("RNDX", "Fetched course → code: " + courseCode);

                            Filter filter = Filter.equalTo("courseId", courseCode);
                            assignmentService.getSorted(filter, "dueAt", assignments -> {
                                courseAssignments.clear();
                                courseAssignments.addAll(assignments);
                                requireActivity().runOnUiThread(() -> {
                                    assignmentAdapter.notifyDataSetChanged();
                                });
                            });
                        } else {
                            Log.w("RNDX", "documentSnapshot.toObject(Course.class) returned null");
                        }
                    } else {
                        Log.w("RNDX", "No document found with ID: " + courseId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("RNDX", "Error fetching course document", e);
                });

    }

    // ————————————————
    // Adapter for “assignments” (top‐level list)
    // ————————————————
    private class AssignmentAdapter
            extends RecyclerView.Adapter<AssignmentAdapter.AVH> {

        private final List<Assignment> items;
        // Track expanded state by assignmentId → boolean
        private final java.util.Map<String, Boolean> expandedMap = new java.util.HashMap<>();

        AssignmentAdapter(List<Assignment> items) {
            this.items = items;
            // Initialize all as “collapsed”
            for (Assignment a : items) {
                expandedMap.put(a.getId(), false);
            }
        }

        @NonNull
        @Override
        public AVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_assignment_rendu_professor, parent, false);
            return new AVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull AVH holder, int pos) {
            Assignment a = items.get(pos);

            // 1) Title & due date
            holder.tvTitle.setText(a.getTitle());
            if (a.getDueAt() != null) {
                holder.tvDue.setText("Fin : " +
                        dateFormat.format(a.getDueAt().toDate()));
            } else {
                holder.tvDue.setText("Fin : N/A");
            }

            // 2) Toggle “Voir soumissions”
            holder.btnToggle.setOnClickListener(click -> {
                boolean isExpanded = expandedMap.getOrDefault(a.getId(), false);
                expandedMap.put(a.getId(), !isExpanded);
                notifyItemChanged(pos);
            });

            // 3) Show or hide nested RecyclerView
            boolean expanded = expandedMap.getOrDefault(a.getId(), false);
            holder.rvSubs.setVisibility(expanded ? View.VISIBLE : View.GONE);
            holder.btnToggle.setText(expanded ? "Masquer soumissions" : "Voir soumissions");

            // 4) If expanded, bind the SubmissionAdapter to show student submissions
            if (expanded) {
                SubmissionAdapter subAdapter = new SubmissionAdapter(a.getId());
                holder.rvSubs.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));
                holder.rvSubs.setAdapter(subAdapter);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class AVH extends RecyclerView.ViewHolder {
            final TextView tvTitle, tvDue;
            final Button btnToggle;
            final RecyclerView rvSubs;

            AVH(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvAssignmentTitleProf);
                tvDue = itemView.findViewById(R.id.tvAssignmentDueProf);
                btnToggle = itemView.findViewById(R.id.btnToggleSubsProf);
                rvSubs = itemView.findViewById(R.id.rvSubmissionsProf);
            }
        }
    }

    // ————————————————
    // Adapter for “submissions” (nested under each assignment)
    // ————————————————
    private class SubmissionAdapter
            extends RecyclerView.Adapter<SubmissionAdapter.SVH> {

        private final List<AssignmentDocument> subs = new ArrayList<>();
        private final String assignmentId;

        SubmissionAdapter(String assignmentId) {
            this.assignmentId = assignmentId;
            loadSubmissions();
        }

        private void loadSubmissions() {
            Filter filter = Filter.equalTo("assignmentId", assignmentId);
            assignmentDocumentService.get(filter, docs -> {
                subs.clear();
                subs.addAll(docs);
                requireActivity().runOnUiThread(this::notifyDataSetChanged);
            });
        }

        @NonNull
        @Override
        public SVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_submission_rendu_professor, parent, false);
            return new SVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull SVH holder, int pos) {
            AssignmentDocument doc = subs.get(pos);

            // 1) Student ID
            holder.tvStudentId.setText(doc.getStudentId());

            // 2) Download link
            holder.tvDownload.setOnClickListener(click -> {
                // If filePath is a Storage path ("<course>/<folder>/<filename>"),
                // construct a download URL. Here, we assume you want to open the browser.
                // If you have a direct download URL, use that instead.
                String path = doc.getFilePath(); // e.g. "HAI8171/cours/..."
                // For simplicity, assume bucket: "https://firebasestorage.googleapis.com/v0/b/YOUR_BUCKET/o"
                // and you have no special URL. If your cd.getFilePath() is a "gs://..." path, you may need
                // to call FirebaseStorage.getReference(path).getDownloadUrl(...) asynchronously and then open.
                // For brevity, we just treat filePath as a direct URL if it starts with "http":
                if (path.startsWith("http")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(path)));
                } else {
                    // Obtain a download URL:
                    FirebaseStorage.getInstance()
                            .getReference(path)
                            .getDownloadUrl()
                            .addOnSuccessListener(uri -> {
                                startActivity(new Intent(Intent.ACTION_VIEW, uri));
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(getContext(),
                                        "Impossible d'obtenir le lien de téléchargement.",
                                        Toast.LENGTH_SHORT).show();
                            });
                }
            });

            // 3) Grade vs “Noter” button
            if (doc.getGrade() != null) {
                holder.tvGrade.setText(String.format(Locale.getDefault(),
                        "Note : %.1f", doc.getGrade()));
                holder.tvGrade.setVisibility(View.VISIBLE);
                holder.btnGrade.setVisibility(View.GONE);
            } else {
                holder.tvGrade.setVisibility(View.GONE);
                holder.btnGrade.setVisibility(View.VISIBLE);
                holder.btnGrade.setOnClickListener(click -> {
                    // Open grade‐entry dialog
                    showGradeEntryDialog(doc, pos);
                });
            }
        }

        @Override
        public int getItemCount() {
            return subs.size();
        }

        class SVH extends RecyclerView.ViewHolder {
            final TextView tvStudentId, tvDownload, tvGrade;
            final Button btnGrade;

            SVH(@NonNull View itemView) {
                super(itemView);
                tvStudentId = itemView.findViewById(R.id.tvStudentIdProf);
                tvDownload = itemView.findViewById(R.id.tvDownloadLinkProf);
                tvGrade = itemView.findViewById(R.id.tvGradeProf);
                btnGrade = itemView.findViewById(R.id.btnGradeProf);
            }
        }

        /**
         * Shows a small AlertDialog containing an EditText for the professor to enter a grade.
         * When “OK” is tapped, updates the AssignmentDocument in Firestore, then reloads.
         */
        private void showGradeEntryDialog(AssignmentDocument doc, int position) {
            View dialogView = LayoutInflater.from(getContext())
                    .inflate(R.layout.dialog_grade_entry, null);
            final EditText etGrade = dialogView.findViewById(R.id.etGradeEntry);
            etGrade.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

            new AlertDialog.Builder(requireContext())
                    .setTitle("Entrer la note pour " + doc.getStudentId())
                    .setView(dialogView)
                    .setPositiveButton("OK", (dialog, which) -> {
                        String txt = etGrade.getText().toString().trim();
                        if (txt.isEmpty()) {
                            Toast.makeText(getContext(),
                                    "La note ne peut pas être vide.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            double grade = Double.parseDouble(txt);
                            if (grade < 0 || grade > 20) {
                                Toast.makeText(getContext(),
                                        "La note doit être entre 0 et 20.", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            // Update Firestore
                            doc.setGrade(grade);
                            assignmentDocumentService.update(doc.getId(), doc, () -> {
                                // On success, refresh this one submission
                                subs.set(position, doc);
                                requireActivity().runOnUiThread(() -> {
                                    notifyItemChanged(position);
                                });
                            });
                        } catch (NumberFormatException ex) {
                            Toast.makeText(getContext(),
                                    "Format de note invalide.", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Annuler", (dialog, which) -> dialog.dismiss())
                    .show();
        }
    }
}
