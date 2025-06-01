package com.example.educonnect.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.educonnect.R;
import com.example.educonnect.entities.User;
import com.example.educonnect.services.CourseService;
import com.example.educonnect.services.UserService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Filter;

public class DashboardFragment extends Fragment {

    // Buttons (all default to GONE in XML)
    private Button btnPlanning;
    private Button btnMesCours;
    private Button btnMessagerieGrid;
    private Button btnForum;
    private Button btnRendus;
    private Button btnModeApp;
    private Button btnExamens;
    private Button btnEnquetes;
    private Button btnUploadDocs;

    private final UserService userService = new UserService();
    private final CourseService courseService = new CourseService();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        // 1) Bind all buttons (they are GONE by default)
        btnPlanning = view.findViewById(R.id.btnPlanning);
        btnMesCours = view.findViewById(R.id.btnMesCours);
        btnMessagerieGrid = view.findViewById(R.id.btnMessagerieGrid);
        btnForum = view.findViewById(R.id.btnForum);
        btnRendus = view.findViewById(R.id.btnRendus);
        btnModeApp = view.findViewById(R.id.btnModeApp);
        btnExamens = view.findViewById(R.id.btnExamens);
        btnEnquetes = view.findViewById(R.id.btnEnquetes);
        btnUploadDocs = view.findViewById(R.id.btnUploadDocs);

        // 2) Set up click listeners now (even though they’re still hidden)
        btnPlanning.setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new PlanningFragment())
                    .addToBackStack(null)
                    .commit();
        });

        btnMesCours.setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new CoursesFragment())
                    .addToBackStack(null)
                    .commit();
        });

        btnMessagerieGrid.setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new MessagingFragment())
                    .addToBackStack(null)
                    .commit();
        });

        btnForum.setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new ForumFragment())
                    .addToBackStack(null)
                    .commit();
        });

        btnRendus.setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new RendusFragment())
                    .addToBackStack(null)
                    .commit();
        });

        btnExamens.setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new ExamsFragment())
                    .addToBackStack(null)
                    .commit();
        });

        btnEnquetes.setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new SurveysFragment())
                    .addToBackStack(null)
                    .commit();
        });

        btnModeApp.setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new ModesFragment())
                    .addToBackStack(null)
                    .commit();
        });

        btnUploadDocs.setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new CourseDocumentUploadFragment())
                    .addToBackStack(null)
                    .commit();
        });

        // 3) Immediately fetch current user’s role (async)
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser != null && fbUser.getEmail() != null) {
            String email = fbUser.getEmail().trim();
            Filter userFilter = Filter.equalTo("email", email);

            userService.get(userFilter, users -> {
                if (!users.isEmpty()) {
                    User u = users.get(0);
                    long role = (u.getRole() != null ? u.getRole() : 0);

                    // On the UI thread, un-hide exactly the buttons for this role
                    requireActivity().runOnUiThread(() -> {
                        if (role == 1) {
                            // PROFESSOR:
                            btnMessagerieGrid.setVisibility(View.VISIBLE);
                            btnForum.setVisibility(View.VISIBLE);
                            btnRendus.setVisibility(View.VISIBLE);
                            btnEnquetes.setVisibility(View.VISIBLE);
                            btnModeApp.setVisibility(View.VISIBLE);
                            btnUploadDocs.setVisibility(View.VISIBLE);

                            // Hide student‐only buttons (they’re already GONE in XML)
                            // (btnPlanning, btnMesCours, btnExamens remain GONE)

                            // Override "Rendus" and "Enquêtes" for professor
                            btnRendus.setOnClickListener(v -> {
                                getParentFragmentManager().beginTransaction()
                                        .replace(R.id.fragment_container, new RendusProfessorFragment())
                                        .addToBackStack(null)
                                        .commit();
                            });
                            btnEnquetes.setOnClickListener(v -> {
                                getParentFragmentManager().beginTransaction()
                                        .replace(R.id.fragment_container, new EvaluationsProfessorFragment())
                                        .addToBackStack(null)
                                        .commit();
                            });
                        } else {
                            // STUDENT:
                            btnPlanning.setVisibility(View.VISIBLE);
                            btnMesCours.setVisibility(View.VISIBLE);
                            btnMessagerieGrid.setVisibility(View.VISIBLE);
                            btnForum.setVisibility(View.VISIBLE);
                            btnRendus.setVisibility(View.VISIBLE);
                            btnExamens.setVisibility(View.VISIBLE);
                            btnEnquetes.setVisibility(View.VISIBLE);
                            btnModeApp.setVisibility(View.VISIBLE);
                            // Leave btnUploadDocs GONE
                        }
                    });
                }
            });
        }

        // 4) Add bottom nav bar
        if (getFragmentManager() != null) {
            NavBarFragment bottomNavFragment = new NavBarFragment();
            getFragmentManager().beginTransaction()
                    .replace(R.id.bottom_navigation_container, bottomNavFragment)
                    .commit();
        }

        return view;
    }
}
