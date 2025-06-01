package com.example.educonnect.fragments;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.educonnect.R;
import com.example.educonnect.entities.Course;
import com.example.educonnect.entities.StudentCourse;
import com.example.educonnect.services.CourseService;
import com.example.educonnect.services.StudentCourseService;
import com.example.educonnect.services.UserService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Filter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Profile screen: shows email, then two lines:
 *   “Cours du semestre 1 : …”
 *   “Cours du semestre 2 : …”
 * followed by “Année : 2024-2025” (hard-coded in XML).
 */
public class ProfileFragment extends Fragment {
    private FirebaseAuth mAuth;
    private final UserService userService = new UserService();
    private final StudentCourseService studentCourseService = new StudentCourseService();
    private final CourseService courseService = new CourseService();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(getContext(),
                    "Utilisateur non connecté",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String userEmail = user.getEmail();

        // 1) Wire up views
        TextView tvEmail = v.findViewById(R.id.tvEmail);
        TextView tvCoursInscrits = v.findViewById(R.id.coursInscrits);
        Button btnSignOut = v.findViewById(R.id.btnSignOut);

        tvEmail.setText(userEmail);

        // 2) Fetch all StudentCourse where studentEmail == currentUser.getEmail()
        Filter filter = Filter.equalTo("studentEmail", userEmail);
        studentCourseService.get(filter, (studentCourses) -> {
            if (studentCourses.isEmpty()) {
                // If the student is not enrolled in any course, show “Aucun” for both semesters
                requireActivity().runOnUiThread(() -> {
                    String text = "Cours du semestre 1 : Aucun\n" +
                            "Cours du semestre 2 : Aucun";
                    tvCoursInscrits.setText(text);
                    setupSignOutAndNav(btnSignOut);
                });
                return;
            }

            // Extract all course codes from the StudentCourse list
            List<String> courseCodes = new ArrayList<>();
            for (StudentCourse sc : studentCourses) {
                courseCodes.add(sc.getCourseCode());
            }

            // 3) For each courseCode, fetch its Course document so we can read the 'semester' field.
            Map<String, Course> codeToCourseMap = new HashMap<>();
            final int total = courseCodes.size();
            final int[] doneCount = {0};

            for (String code : courseCodes) {
                Filter courseFilter = Filter.equalTo("code", code);
                courseService.get(courseFilter, courses -> {
                    if (!courses.isEmpty()) {
                        // Put the first matching Course into our map
                        codeToCourseMap.put(code, courses.get(0));
                    }
                    doneCount[0]++;

                    // Once all course fetches are done, build the per-semester lists
                    if (doneCount[0] == total) {
                        List<String> sem1List = new ArrayList<>();
                        List<String> sem2List = new ArrayList<>();

                        for (StudentCourse sc : studentCourses) {
                            String cc = sc.getCourseCode();
                            Course c = codeToCourseMap.get(cc);
                            if (c != null) {
                                Long sem = c.getSemester();
                                // “code – name” format
                                String display = c.getCode() + " – " + c.getName();
                                if (sem != null && sem == 1L) {
                                    sem1List.add(display);
                                } else if (sem != null && sem == 2L) {
                                    sem2List.add(display);
                                }
                            }
                        }

                        // Build the final two-line string
                        final String sem1Text = sem1List.isEmpty()
                                ? "Aucun"
                                : TextUtils.join("\n", sem1List) + "\n";
                        final String sem2Text = sem2List.isEmpty()
                                ? "Aucun"
                                : TextUtils.join("\n", sem2List);

                        requireActivity().runOnUiThread(() -> {
                            String combined =
                                    "Cours du semestre 1 : " + "\n" + sem1Text + "\n" +
                                            "Cours du semestre 2 : " + "\n" + sem2Text;
                            tvCoursInscrits.setText(combined);

                            setupSignOutAndNav(btnSignOut);
                        });
                    }
                });
            }
        });
    }

    /**
     * Sets up the sign-out button and bottom nav-bar. Called once we've filled in courses.
     */
    private void setupSignOutAndNav(Button btnSignOut) {
        // Sign-out: clear “rememberMe” and return to LoginFragment
        btnSignOut.setOnClickListener(x -> {
            requireActivity().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("rememberMe", false).apply();
            mAuth.signOut();

            FragmentManager fm = requireActivity().getSupportFragmentManager();
            fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            fm.beginTransaction()
                    .replace(R.id.fragment_container, new LoginFragment())
                    .commit();
        });

        // Attach bottom nav bar
        getChildFragmentManager().beginTransaction()
                .replace(R.id.bottom_navigation_container, new NavBarFragment())
                .commit();
    }
}
