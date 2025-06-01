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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.educonnect.R;
import com.example.educonnect.entities.Course;
import com.example.educonnect.entities.CourseDocument;
import com.example.educonnect.entities.User;
import com.example.educonnect.services.CourseDocumentService;
import com.example.educonnect.services.CourseService;
import com.example.educonnect.services.UserService;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Filter;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment that allows a professor to upload a document to one of their courses,
 * choosing also a category (cours/tds/autres).
 */
public class CourseDocumentUploadFragment extends Fragment {
    private static final int REQUEST_CODE_PICK_FILE = 2001;
    private Spinner spinnerCourses;
    private Spinner spinnerFolder;
    private TextView tvChosenFile;
    private final CourseService courseService = new CourseService();
    private final CourseDocumentService documentService = new CourseDocumentService();
    private final UserService userService = new UserService();
    private final List<Course> professorCourses = new ArrayList<>();
    private Uri selectedFileUri = null;
    private String selectedFileName = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_course_doc_upload, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v,
                              @Nullable Bundle b) {
        super.onViewCreated(v, b);

        spinnerCourses = v.findViewById(R.id.spinnerCourses);
        spinnerFolder = v.findViewById(R.id.spinnerFolder);
        Button btnChooseFile = v.findViewById(R.id.btnChooseFile);
        Button btnUploadFile = v.findViewById(R.id.btnUploadFile);
        tvChosenFile = v.findViewById(R.id.tvChosenFile);

        // 1) Determine current user’s email (professor)
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser == null || fbUser.getEmail() == null) {
            Toast.makeText(getContext(),
                    getString(R.string.non_connecte), Toast.LENGTH_SHORT).show();
            return;
        }
        String currentProfessorEmail = fbUser.getEmail().trim();

        // 2) Populate the folder‐category spinner with fixed labels:
        //    "Diapos Cours" -> "cours"
        //    "TDs/TPs"      -> "tds"
        //    "Autres"       -> "autres"
        List<String> folderLabels = new ArrayList<>();
        folderLabels.add(getString(R.string.diapos_cours));
        folderLabels.add(getString(R.string.tds_tps));
        folderLabels.add(getString(R.string.autres));
        ArrayAdapter<String> folderAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                folderLabels
        );
        folderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFolder.setAdapter(folderAdapter);

        // 3) Fetch the professor’s User document to get their UID (id),
        //    then fetch all courses where professorId == that UID.
        Filter userFilter = Filter.equalTo("email", currentProfessorEmail);
        userService.get(userFilter, users -> {
            if (users.isEmpty()) {
                Toast.makeText(getContext(),
                        "Impossible de trouver votre profil.", Toast.LENGTH_SHORT).show();
                requireActivity().getSupportFragmentManager().popBackStack();
                return;
            }
            User u = users.get(0);
            String professorId = u.getId(); // Firestore document ID for this professor

            // 4) Now fetch courses taught by this professor
            courseService.getByProfessor(professorId, courses -> {
                professorCourses.clear();
                professorCourses.addAll(courses);

                if (professorCourses.isEmpty()) {
                    Toast.makeText(getContext(),
                            "Aucun cours associé à votre compte.", Toast.LENGTH_SHORT).show();
                    requireActivity().getSupportFragmentManager().popBackStack();
                    return;
                }

                // Populate spinnerCourses with course codes
                List<String> codes = new ArrayList<>();
                for (Course c : professorCourses) {
                    codes.add(c.getCode());
                }
                ArrayAdapter<String> courseAdapter = new ArrayAdapter<>(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        codes
                );
                courseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerCourses.setAdapter(courseAdapter);
            });
        });

        // 5) “Choisir un fichier” button
        btnChooseFile.setOnClickListener(click -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, REQUEST_CODE_PICK_FILE);
        });

        // 6) “Téléverser” button
        btnUploadFile.setOnClickListener(click -> {
            if (selectedFileUri == null) {
                Toast.makeText(getContext(),
                        getString(R.string.choisir_fichier), Toast.LENGTH_SHORT).show();
                return;
            }

            int courseIndex = spinnerCourses.getSelectedItemPosition();
            Course chosenCourse = professorCourses.get(courseIndex);
            String courseCode = chosenCourse.getCode();

            int folderIndex = spinnerFolder.getSelectedItemPosition();
            // Map the spinner index to our internal folder keys
            String folderKey;
            switch (folderIndex) {
                case 0:
                    folderKey = "cours";
                    break;   // Diapos Cours
                case 1:
                    folderKey = "tds";
                    break;   // TDs/TPs
                default:
                    folderKey = "autres";        // Autres
            }

            uploadFileToStorage(courseCode, folderKey, selectedFileUri, selectedFileName);
        });

        // 7) Bottom nav bar
        FragmentManager fm = getChildFragmentManager();
        fm.beginTransaction()
                .replace(R.id.bottom_navigation_container, new NavBarFragment())
                .commit();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_FILE
                && resultCode == Activity.RESULT_OK
                && data != null) {
            selectedFileUri = data.getData();
            if (selectedFileUri != null) {
                selectedFileName = getFileNameFromUri(selectedFileUri);
                tvChosenFile.setText(selectedFileName);
            }
        }
    }

    /**
     * get filename from a content Uri
     */
    private String getFileNameFromUri(Uri uri) {
        String result = "fichier";
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

    /**
     * Uploads the picked file under
     * path: "<courseCode>/<folderKey>/<timestamp>_<origName>"
     * in Firebase Storage, then creates a Firestore CourseDocument with that folderKey.
     */
    private void uploadFileToStorage(String courseCode, String folderKey, Uri fileUri, String origName) {
        // Include folderKey in the Storage path
        String remotePath = courseCode + "/" + origName;

        StorageReference storageRef = FirebaseStorage.getInstance()
                .getReference()
                .child(remotePath);

        // Start upload
        storageRef.putFile(fileUri)
                .addOnSuccessListener(taskSnapshot -> {
                    // Build CourseDocument entry
                    CourseDocument cd = new CourseDocument();
                    cd.setCourseId(courseCode);
                    cd.setFolder(folderKey);         // Store exactly the chosen folderKey
                    cd.setFilePath("gs://educonnect-a9e0f.firebasestorage.app/" + storageRef.getPath());
                    cd.setUploadedAt(Timestamp.now());

                    // Save to Firestore
                    documentService.createDocument(cd, created -> {
                        Toast.makeText(getContext(),
                                getString(R.string.succes_televersement), Toast.LENGTH_SHORT).show();
                        // Reset chosen file display
                        selectedFileUri = null;
                        selectedFileName = null;
                        tvChosenFile.setText(getString(R.string.aucun_fichier));
                    });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(),
                            getString(R.string.echec_televersement) + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }
}
