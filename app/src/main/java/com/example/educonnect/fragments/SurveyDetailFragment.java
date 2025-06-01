package com.example.educonnect.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.educonnect.R;
import com.example.educonnect.entities.Evaluation;
import com.example.educonnect.services.EvaluationService;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * SurveyDetailFragment dynamically renders five questions with RadioGroup (1–5).
 * On “Save,” it collects the answers and writes a new Evaluation to Firestore.
 */
public class SurveyDetailFragment extends Fragment {
    private static final String ARG_COURSE = "arg_course";

    private final EvaluationService evaluationService = new EvaluationService();

    public static SurveyDetailFragment newInstance(String courseName) {
        Bundle args = new Bundle();
        args.putString(ARG_COURSE, courseName);
        SurveyDetailFragment f = new SurveyDetailFragment();
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_survey_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        String courseFullName = requireArguments().getString(ARG_COURSE);
        ((TextView) v.findViewById(R.id.tvDetailTitle))
                .setText("Enquête pour " + courseFullName);

        // 1) Hardcoded questions
        List<String> questions = new ArrayList<>();
        questions.add("1. Qualité globale de l’enseignement");
        questions.add("2. Clarté des explications");
        questions.add("3. Pertinence des supports de cours");
        questions.add("4. Interaction et disponibilité de l’enseignant");
        questions.add("5. Adéquation de la charge de travail");

        // 2) Dynamically add each question + horizontal RadioGroup (choices 1–5)
        ViewGroup containerQuestions = v.findViewById(R.id.llQuestions);
        LayoutInflater li = LayoutInflater.from(getContext());
        List<RadioGroup> radioGroups = new ArrayList<>();

        for (String q : questions) {
            // Add question text
            TextView tvQ = new TextView(getContext());
            tvQ.setText(q);
            tvQ.setTextSize(16f);
            tvQ.setPadding(0, 12, 0, 6);
            containerQuestions.addView(tvQ);

            // Create a horizontal RadioGroup with 5 RadioButtons
            RadioGroup rg = new RadioGroup(getContext());
            rg.setOrientation(RadioGroup.HORIZONTAL);

            for (int i = 1; i <= 5; i++) {
                RadioButton rb = new RadioButton(getContext());
                rb.setText(String.valueOf(i));
                // Spread them equally:
                RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(
                        0,
                        RadioGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );
                rb.setLayoutParams(lp);
                rg.addView(rb);
            }
            containerQuestions.addView(rg);
            radioGroups.add(rg);
        }

        // 3) “Save” button: check all answered, then create Evaluation
        v.findViewById(R.id.btnSaveSurvey).setOnClickListener(x -> {
            // Ensure user answered every question
            for (RadioGroup rg : radioGroups) {
                if (rg.getCheckedRadioButtonId() == -1) {
                    Toast.makeText(getContext(),
                            "Veuillez répondre à toutes les questions",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // All five answered ⇒ build an Evaluation object
            Evaluation ev = new Evaluation();
            // q1..q5 = the integer value of each checked RadioButton (1–5)
            ev.setQ1((long) (radioGroups.get(0).indexOfChild(
                    radioGroups.get(0).findViewById(radioGroups.get(0).getCheckedRadioButtonId())) + 1));
            ev.setQ2((long) (radioGroups.get(1).indexOfChild(
                    radioGroups.get(1).findViewById(radioGroups.get(1).getCheckedRadioButtonId())) + 1));
            ev.setQ3((long) (radioGroups.get(2).indexOfChild(
                    radioGroups.get(2).findViewById(radioGroups.get(2).getCheckedRadioButtonId())) + 1));
            ev.setQ4((long) (radioGroups.get(3).indexOfChild(
                    radioGroups.get(3).findViewById(radioGroups.get(3).getCheckedRadioButtonId())) + 1));
            ev.setQ5((long) (radioGroups.get(4).indexOfChild(
                    radioGroups.get(4).findViewById(radioGroups.get(4).getCheckedRadioButtonId())) + 1));

            // No comment field in UI; leave blank or null
            ev.setComment("");

            // submittedAt = now
            ev.setSubmittedAt(Timestamp.now());
            // dueAt = null (or you could set a real due date if you have one)

            // courseId = just take the code part before “ – ”
            String[] parts = courseFullName.split(" – ");
            ev.setCourseId(parts[0].trim());

            // studentId = currently signed-in user’s email
            String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            ev.setStudentEmail(email);

            // 4) Save Evaluation to Firestore
            evaluationService.create(ev, createdEv -> {
                // On success: go back to the list
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(),
                            "Enquête enregistrée. Merci !",
                            Toast.LENGTH_SHORT).show();
                    requireActivity().getSupportFragmentManager().popBackStack();
                });
            });
        });

        // 5) Bottom nav bar
        getChildFragmentManager().beginTransaction()
                .replace(R.id.bottom_navigation_container, new NavBarFragment())
                .commit();
    }
}
