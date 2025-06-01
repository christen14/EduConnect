package com.example.educonnect.fragments;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.educonnect.R;
import com.example.educonnect.entities.Assignment;
import com.example.educonnect.entities.AssignmentDocument;
import com.example.educonnect.entities.StudentCourse;
import com.example.educonnect.services.AssignmentDocumentService;
import com.example.educonnect.services.AssignmentService;
import com.example.educonnect.services.StudentCourseService;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Filter;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * RendusFragment (student view) lets a student upload their work for each assignment.
 * It now properly uploads selected files to Firebase Storage and then creates
 * an AssignmentDocument in Firestore with the download URL.
 */
public class RendusFragment extends Fragment {
    private static final int REQUEST_CODE_PICK_FILE = 1001;

    private ExpandableListView elv;

    private List<String> courseCodes; // sorted list of enrolled course codes
    private final Map<String, List<Assignment>> byCourse = new HashMap<>();
    private final Map<String, Assignment> assignmentMap = new HashMap<>();
    private String pendingAssignmentId;

    private final AssignmentService assignmentService = new AssignmentService();
    private final AssignmentDocumentService documentService = new AssignmentDocumentService();
    private final StudentCourseService studentCourseService = new StudentCourseService();

    private String currentStudentId;

    // Keep track of assignments student already submitted
    private final Set<String> submittedAssignmentIds = new HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup ctr,
                             @Nullable Bundle saved) {
        return inf.inflate(R.layout.fragment_rendus, ctr, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);
        elv = v.findViewById(R.id.elvRendus);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(getContext(),
                    "Utilisateur non connecté",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        currentStudentId = user.getEmail();

        // Load student's submitted docs first, then courses, then assignments
        loadSubmittedDocumentsThenStudentCourses();

        // Bottom nav bar
        FragmentManager fm = getChildFragmentManager();
        fm.beginTransaction()
                .replace(R.id.bottom_navigation_container, new NavBarFragment())
                .commit();
    }

    /**
     * Load all AssignmentDocuments submitted by current student,
     * then load courses + assignments after.
     */
    private void loadSubmittedDocumentsThenStudentCourses() {
        submittedAssignmentIds.clear();

        Filter filterStudent = Filter.equalTo("studentId", currentStudentId);
        documentService.get(filterStudent, docs -> {
            for (AssignmentDocument doc : docs) {
                submittedAssignmentIds.add(doc.getAssignmentId());
            }
            // Now load student's courses
            loadStudentCourses();
        });
    }

    /**
     * Load courses where student is enrolled.
     * Then load assignments only for those courses.
     */
    private void loadStudentCourses() {
        studentCourseService.getByStudentEmail(currentStudentId, studentCourses -> {
            // Extract course codes student is enrolled in
            Set<String> courseSet = new HashSet<>();
            for (StudentCourse sc : studentCourses) {
                courseSet.add(sc.getCourseCode());
            }
            courseCodes = new ArrayList<>(courseSet);
            java.util.Collections.sort(courseCodes);

            // Load assignments filtered by enrolled courses
            loadAssignmentsForCourses(courseSet);
        });
    }

    /**
     * Load assignments whose courseId is in courseSet, then group by courseId.
     */
    private void loadAssignmentsForCourses(Set<String> courseSet) {
        assignmentService.getAllAssignments(assignments -> {
            byCourse.clear();
            assignmentMap.clear();

            for (Assignment a : assignments) {
                if (a.getCourseId() != null && courseSet.contains(a.getCourseId())) {
                    assignmentMap.put(a.getId(), a);
                    byCourse.computeIfAbsent(a.getCourseId(), k -> new ArrayList<>()).add(a);
                }
            }
            // Sort assignments inside each course by dueAt ascending
            for (List<Assignment> assignmentsList : byCourse.values()) {
                assignmentsList.sort((a1, a2) -> {
                    if (a1.getDueAt() == null && a2.getDueAt() == null) return 0;
                    if (a1.getDueAt() == null) return 1;
                    if (a2.getDueAt() == null) return -1;
                    return a1.getDueAt().compareTo(a2.getDueAt());
                });
            }
            requireActivity().runOnUiThread(() -> {
                elv.setAdapter(new RendusAdapter());
                // Expand all groups for better UX
                for (int i = 0; i < courseCodes.size(); i++) {
                    elv.expandGroup(i);
                }
            });
        });
    }

    @Override
    public void onActivityResult(int requestCode,
                                 int resultCode,
                                 @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_FILE
                && resultCode == Activity.RESULT_OK
                && data != null) {
            Uri uri = data.getData();
            if (uri != null && pendingAssignmentId != null) {
                // 1) Determine the assignment and course
                Assignment assignment = assignmentMap.get(pendingAssignmentId);
                if (assignment == null) {
                    Toast.makeText(getContext(),
                            "Impossible de trouver l’assignement.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                String courseId = assignment.getCourseId();

                // 2) Extract original filename
                String filename = getFileNameFromUri(uri);

                // 3) Build a Storage path:
                //    e.g. "submissions/<courseId>/<assignmentId>/<studentId>_<timestamp>_<origFilename>"
                long timestamp = System.currentTimeMillis();
                String childPath = "submissions/"
                        + courseId + "/"
                        + currentStudentId.replaceAll("[^a-zA-Z0-9_.@-]", "_")
                        + "_" + timestamp + "_" + filename;

                StorageReference storageRef = FirebaseStorage.getInstance()
                        .getReference()
                        .child(childPath);

                // 4) Upload the file to Storage
                storageRef.putFile(uri)
                        .addOnSuccessListener(taskSnapshot -> {
                            // 5) Once upload succeeds, fetch the download URL
                            storageRef.getDownloadUrl()
                                    .addOnSuccessListener(downloadUri -> {
                                        // 6) Create the AssignmentDocument with download URL
                                        AssignmentDocument doc = new AssignmentDocument();
                                        doc.setAssignmentId(pendingAssignmentId);
                                        doc.setStudentId(currentStudentId);
                                        doc.setFilePath(downloadUri.toString());
                                        doc.setCreatedAt(Timestamp.now());

                                        documentService.createDocument(doc, createdDoc -> {
                                            // 7) Update local state so UI disables the button immediately
                                            submittedAssignmentIds.add(pendingAssignmentId);
                                            requireActivity().runOnUiThread(() -> {
                                                Toast.makeText(getContext(),
                                                        "Rendu téléversé avec succès.",
                                                        Toast.LENGTH_SHORT).show();
                                                // Refresh the adapter so that the “Rendu effectué” state appears
                                                elv.setAdapter(new RendusAdapter());
                                            });
                                        });
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(getContext(),
                                                "Échec de la récupération du lien de téléchargement.",
                                                Toast.LENGTH_LONG).show();
                                    });
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(getContext(),
                                    "Échec du téléversement : " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        });
            }
        }
    }

    /**
     * Helper: get the actual filename from a content Uri
     */
    private String getFileNameFromUri(Uri uri) {
        String result = "document";
        Cursor cursor = null;
        try {
            cursor = requireContext().getContentResolver()
                    .query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    result = cursor.getString(idx);
                }
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return result;
    }

    // ———————————————————————————————
    // ExpandableListAdapter for (course → assignments)
    // ———————————————————————————————
    private class RendusAdapter extends BaseExpandableListAdapter {
        private final SimpleDateFormat frenchDateFormat =
                new SimpleDateFormat("EEEE dd MMM HH:mm", Locale.FRENCH);

        @Override
        public int getGroupCount() {
            return (courseCodes == null) ? 0 : courseCodes.size();
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            String cId = courseCodes.get(groupPosition);
            List<Assignment> list = byCourse.get(cId);
            return (list == null) ? 0 : list.size();
        }

        @Override
        public Object getGroup(int groupPosition) {
            return courseCodes.get(groupPosition);
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            String cId = courseCodes.get(groupPosition);
            return byCourse.get(cId).get(childPosition);
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getGroupView(int groupPosition,
                                 boolean isExpanded,
                                 View convertView,
                                 ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_rendus_group, parent, false);
            }
            String courseId = (String) getGroup(groupPosition);
            TextView tvGroup = convertView.findViewById(R.id.tvGroup);
            tvGroup.setText(courseId);
            return convertView;
        }

        @Override
        public View getChildView(int groupPosition,
                                 int childPosition,
                                 boolean isLastChild,
                                 View convertView,
                                 ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_rendus_child, parent, false);
            }

            Assignment assignment = (Assignment) getChild(groupPosition, childPosition);
            String assignmentId = assignment.getId();

            // Build the button’s text (title + due date)
            String dueText = "";
            if (assignment.getDueAt() != null) {
                String raw = frenchDateFormat.format(assignment.getDueAt().toDate());
                String noDot = raw.replaceFirst("\\.", "");
                dueText = noDot.substring(0, 1).toUpperCase() + noDot.substring(1);
            }

            String btnText = assignment.getTitle();
            if (!dueText.isEmpty()) {
                btnText += " – Fin : " + dueText;
            }

            Button btnUpload = convertView.findViewById(R.id.btnUpload);
            TextView tvDesc = convertView.findViewById(R.id.tvDesc);

            // Build bullet list from assignment content
            List<String> bullets = new ArrayList<>();
            String content = assignment.getContent();
            if (content != null && !content.trim().isEmpty()) {
                for (String line : content.split("\n")) {
                    if (!line.trim().isEmpty()) bullets.add(line.trim());
                }
            }
            if (bullets.isEmpty()) {
                bullets.add("Pas de description disponible.");
            }

            StringBuilder sb = new StringBuilder();
            for (String bullet : bullets) {
                sb.append("• ").append(bullet).append("\n");
            }
            tvDesc.setText(sb.toString().trim());

            // Check if already submitted
            boolean alreadySubmitted = submittedAssignmentIds.contains(assignmentId);

            if (alreadySubmitted) {
                btnUpload.setText("Rendu effectué");
                btnUpload.setEnabled(false);
                btnUpload.setBackgroundColor(
                        convertView.getResources().getColor(android.R.color.darker_gray)
                );
            } else {
                btnUpload.setText(btnText);
                btnUpload.setEnabled(true);
                btnUpload.setBackgroundColor(
                        convertView.getResources().getColor(android.R.color.holo_blue_light)
                );
                btnUpload.setOnClickListener(v -> {
                    pendingAssignmentId = assignmentId;
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, REQUEST_CODE_PICK_FILE);
                });
            }

            return convertView;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return false;
        }
    }
}
