package com.example.educonnect.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.educonnect.R;
import com.example.educonnect.entities.CourseSession;
import com.example.educonnect.entities.StudentCourse;
import com.example.educonnect.services.CourseSessionService;
import com.example.educonnect.services.StudentCourseService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Filter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Displays a weekly planning (Monday → Friday) of the student’s course sessions:
 *   - Only sessions for courses the student is enrolled in.
 *   - Sessions are defined by startTime (Firestore Timestamp), room, and courseId.
 *   - We treat a “template” week (e.g. Sept 2–6, 2024) as repeating weekly.
 */
public class PlanningFragment extends Fragment {

    private View rootView;
    private TextView tvDayLabel;
    private ImageButton btnDayLeft, btnDayRight;
    private List<String> dayLabels = Collections.emptyList(); // ["Lundi","Mardi",...]
    private int currentDayIndex = 0; // 0 = Monday, ... 4 = Friday

    // Services
    private final StudentCourseService studentCourseService = new StudentCourseService();
    private final CourseSessionService sessionService = new CourseSessionService();

    // Holds the course codes the student is enrolled in
    private final Set<String> enrolledCourseCodes = new HashSet<>();

    // After fetching sessions, we group them by weekday (0..4)
    private final Map<Integer, List<CourseSession>> sessionsByWeekday = new HashMap<>();

    private String currentStudentEmail;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_planning, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1) Wire up views
        tvDayLabel = rootView.findViewById(R.id.tvDayLabel);
        btnDayLeft = rootView.findViewById(R.id.btnDayLeft);
        btnDayRight = rootView.findViewById(R.id.btnDayRight);

        // 2) Day labels (Monday→Friday in French)
        dayLabels = new ArrayList<>();
        Collections.addAll(dayLabels, "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi");

        // 3) Get current student’s email
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(getContext(),
                    "Utilisateur non connecté",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        currentStudentEmail = user.getEmail();

        // 4) Load enrolled Course → then fetch sessions
        loadStudentCourses();

        // 5) Arrow click listeners
        btnDayLeft.setOnClickListener(v -> {
            if (currentDayIndex > 0) {
                currentDayIndex--;
                updateDayLabel();
                loadPlanningForDay();
            }
        });
        btnDayRight.setOnClickListener(v -> {
            if (currentDayIndex < dayLabels.size() - 1) {
                currentDayIndex++;
                updateDayLabel();
                loadPlanningForDay();
            }
        });

        // 6) Bottom nav bar
        FragmentManager fm = getChildFragmentManager();
        fm.beginTransaction()
                .replace(R.id.bottom_navigation_container, new NavBarFragment())
                .commit();
    }

    /**
     * Step A: Fetch all StudentCourse entries for this student to build enrolledCourseCodes.
     */
    private void loadStudentCourses() {
        Filter filter = Filter.equalTo("studentEmail", currentStudentEmail);
        studentCourseService.get(filter, studentCourses -> {
            enrolledCourseCodes.clear();
            for (StudentCourse sc : studentCourses) {
                if (sc.getCourseCode() != null) {
                    enrolledCourseCodes.add(sc.getCourseCode());
                }
            }
            // Now fetch all sessions (we’ll filter client-side by enrolledCourseCodes)
            loadAllCourseSessions();
        });
    }

    /**
     * Step B: Fetch all CourseSession documents, then filter by courseCode and group by weekday.
     */
    private void loadAllCourseSessions() {
        sessionService.getAllSessions(allSessions -> {
            // Clear previous
            sessionsByWeekday.clear();
            for (int i = 0; i < 5; i++) {
                sessionsByWeekday.put(i, new ArrayList<>());
            }

            Calendar cal = Calendar.getInstance();
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

            for (CourseSession cs : allSessions) {
                String courseId = cs.getCourseId();
                if (courseId == null || !enrolledCourseCodes.contains(courseId)) {
                    // skip sessions for courses not enrolled
                    continue;
                }

                if (cs.getStartTime() == null) continue;
                // Convert Firestore timestamp → java Date → Calendar
                cal.setTime(cs.getStartTime().toDate());
                int dow = cal.get(Calendar.DAY_OF_WEEK);
                // Map: MONDAY=2→ index0, TUESDAY=3→1, ... FRIDAY=6→4
                int weekIndex = -1;
                if (dow == Calendar.MONDAY)      weekIndex = 0;
                else if (dow == Calendar.TUESDAY)   weekIndex = 1;
                else if (dow == Calendar.WEDNESDAY) weekIndex = 2;
                else if (dow == Calendar.THURSDAY)  weekIndex = 3;
                else if (dow == Calendar.FRIDAY)    weekIndex = 4;
                else continue; // skip weekends

                sessionsByWeekday.get(weekIndex).add(cs);
            }

            // Sort each day’s sessions by startTime
            for (int i = 0; i < 5; i++) {
                List<CourseSession> list = sessionsByWeekday.get(i);
                list.sort((a, b) -> {
                    if (a.getStartTime() == null && b.getStartTime() == null) return 0;
                    if (a.getStartTime() == null) return 1;
                    if (b.getStartTime() == null) return -1;
                    return a.getStartTime().compareTo(b.getStartTime());
                });
            }

            // Now that data is ready, update UI on main thread
            requireActivity().runOnUiThread(() -> {
                currentDayIndex = 0; // start on Monday
                updateDayLabel();
                loadPlanningForDay();
            });
        });
    }

    /**
     * Update the day label text and enable/disable arrows as needed.
     */
    private void updateDayLabel() {
        tvDayLabel.setText(dayLabels.get(currentDayIndex));
        btnDayLeft.setEnabled(currentDayIndex > 0);
        btnDayRight.setEnabled(currentDayIndex < dayLabels.size() - 1);
    }

    /**
     * Populate the ScrollView’s container with that day’s sessions.
     */
    private void loadPlanningForDay() {
        LinearLayout planningGrid =
                rootView.findViewById(R.id.planning_grid_container);
        planningGrid.removeAllViews();

        // Title (e.g. “Emploi du temps pour Lundi”)
        TextView header = new TextView(getContext());
        header.setText("Emploi du temps pour " + dayLabels.get(currentDayIndex));
        header.setTextSize(18);
        header.setPadding(0, 0, 0, 16);
        planningGrid.addView(header);

        List<CourseSession> todaySessions = sessionsByWeekday.get(currentDayIndex);
        if (todaySessions == null || todaySessions.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("Aucun cours ce jour.");
            planningGrid.addView(empty);
            return;
        }

        // For each session, add a “card”
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        for (CourseSession cs : todaySessions) {
            LinearLayout card = new LinearLayout(getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(20, 14, 20, 14);
            card.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, 16);
            card.setLayoutParams(params);

            // 1) Course ID
            TextView t1 = new TextView(getContext());
            t1.setText(cs.getCourseId());
            t1.setTextSize(16);
            t1.setPadding(0, 0, 0, 4);
            card.addView(t1);

            // 2) Start time (format “HH:mm”)
            String timeText = "";
            if (cs.getStartTime() != null) {
                cal.setTime(cs.getStartTime().toDate());
                timeText = timeFormat.format(cal.getTime());
            }
            TextView t2 = new TextView(getContext());
            t2.setText("Heure : " + timeText);
            t2.setPadding(0, 0, 0, 4);
            card.addView(t2);

            // 3) Room
            TextView t3 = new TextView(getContext());
            t3.setText("Salle : " + (cs.getRoom() == null ? "—" : cs.getRoom()));
            card.addView(t3);

            planningGrid.addView(card);
        }
    }
}
