package com.example.educonnect.fragments;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.educonnect.R;
import com.example.educonnect.entities.CourseDocument;
import com.example.educonnect.services.CourseDocumentService;
import com.google.firebase.firestore.Filter;
import com.google.firebase.storage.FirebaseStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.firebase.storage.StorageReference;

public class DocumentsFragment extends Fragment {

    private static final String ARG_COURSE_CODE = "ARG_COURSE_CODE";
    private static final String ARG_COURSE_NAME = "ARG_COURSE_NAME";

    private ExpandableListView elv;
    private SimpleExpandableListAdapter adapter;

    private List<Map<String, String>> groupData = new ArrayList<>();
    private List<List<Map<String, String>>> childData = new ArrayList<>();

    // Group titles (fixed)
    private final String[] groups = {"Diapos Cours", "TDs/TPs", "Autres"};

    private String courseCode, courseName;
    private final CourseDocumentService cdService = new CourseDocumentService();

    public static DocumentsFragment newInstance(String courseCode, String courseName) {
        Bundle args = new Bundle();
        args.putString(ARG_COURSE_CODE, courseCode);
        args.putString(ARG_COURSE_NAME, courseName);
        DocumentsFragment fragment = new DocumentsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_documents, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        elv = view.findViewById(R.id.elvDocs);

        Bundle args = getArguments();
        if (args != null) {
            courseCode = args.getString(ARG_COURSE_CODE);
            courseName = args.getString(ARG_COURSE_NAME);

            TextView tvCourse = view.findViewById(R.id.tvDocsCourse);
            tvCourse.setText("Cours : " + courseCode + " - " + courseName);
        }

        groupData.clear();
        childData.clear();

        for (String group : groups) {
            Map<String, String> g = new HashMap<>();
            g.put("GROUP", group);
            groupData.add(g);
            childData.add(new ArrayList<>());
        }

        adapter = new SimpleExpandableListAdapter(
                getContext(),
                groupData,
                R.layout.item_doc_group,
                new String[]{"GROUP"},
                new int[]{R.id.tvGroupHeader},
                childData,
                R.layout.item_doc_child,
                new String[]{"CHILD"},
                new int[]{R.id.tvDocName}
        );

        elv.setAdapter(adapter);

        // Prepare map for grouping files
        Map<Integer, List<String>> filesByGroup = new HashMap<>();
        for (int i = 0; i < groups.length; i++) {
            filesByGroup.put(i, new ArrayList<>());
        }

        // Async fetch documents
        Filter filter = Filter.equalTo("courseId", courseCode);


        cdService.getSorted(filter, "uploadedAt", courseDocuments -> {
            for (CourseDocument cd : courseDocuments) {
                String cdFilePath = cd.getFilePath();
                // Use braces to avoid logic bugs
                if (cd.getFolder().equalsIgnoreCase("cours")) {
                    filesByGroup.get(0).add(cdFilePath);
                }
                else if (cd.getFolder().equalsIgnoreCase("tds")) {
                    filesByGroup.get(1).add(cdFilePath);
                }
                else if (cd.getFolder().equalsIgnoreCase("autres")) {
                    filesByGroup.get(2).add(cdFilePath);
                }
            }

            // IMPORTANT: update UI *after* data fetch completes
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    for (Map.Entry<Integer, List<String>> entry : filesByGroup.entrySet()) {
                        updateFilesForGroup(entry.getKey(), entry.getValue());
                    }
                    // Expand all groups after updating
                    for (int i = 0; i < groups.length; i++) {
                        elv.expandGroup(i);
                    }
                });
            }
        });

        // On file click
        elv.setOnChildClickListener((parent, v, groupPosition, childPosition, id) -> {
            // Get the file name/path from childData structure
            String filePath = childData.get(groupPosition).get(childPosition).get("FULL_PATH");

            if (filePath != null && !filePath.isEmpty()) {
                System.out.println("FILEPATH IS " + filePath);
                openPdfFromFirebase(filePath);
            }

            return true; // handled click
        });


        // Attach bottom nav bar
        getChildFragmentManager().beginTransaction()
                .replace(R.id.bottom_navigation_container, new NavBarFragment())
                .commit();
    }


    private void openPdfFromFirebase(String gsUri) {
        StorageReference storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(gsUri);
        storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
            // uri is a public HTTPS URL to the file
            openPdf(uri.toString());
        }).addOnFailureListener(e -> {
            Toast.makeText(getContext(), "Impossible d'obtenir l'URL de téléchargement.", Toast.LENGTH_SHORT).show();
        });
    }

    private void openPdf(String url) {
        Uri pdfUri = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(pdfUri, "application/pdf");
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(), "Aucune application pour ouvrir les fichiers PDF n'est installée.", Toast.LENGTH_LONG).show();
        }
    }



    public void updateFilesForGroup(int groupIndex, List<String> filePaths) {
        if (groupIndex < 0 || groupIndex >= groups.length) return;

        List<Map<String, String>> childrenList = new ArrayList<>();
        for (String fullPath : filePaths) {
            String fileName = fullPath.substring(fullPath.lastIndexOf('/') + 1);
            Map<String, String> childMap = new HashMap<>();
            childMap.put("CHILD", fileName);        // Display filename only
            childMap.put("FULL_PATH", fullPath);    // Store full path for later use
            childrenList.add(childMap);
        }

        childData.set(groupIndex, childrenList);

        // Notify adapter on UI thread
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                adapter.notifyDataSetChanged();
                elv.expandGroup(groupIndex);
            });
        }
    }

}
