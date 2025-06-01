package com.example.educonnect;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.educonnect.fragments.LoginFragment;

// Example snippet to batch‐add five Assignment documents to Firestore.
// You can run this from an Activity, Fragment, or any initialization helper in your app.

import android.util.Log;

import com.example.educonnect.entities.Assignment;
import com.example.educonnect.services.AssignmentService;
import com.google.firebase.Timestamp;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Add LoginFragment to the container
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new LoginFragment())
                    .commit();
        }
//        seedSampleAssignments();
    }

    public void seedSampleAssignments() {
        AssignmentService assignmentService = new AssignmentService();

        // Helper to create a Firestore Timestamp for a given year/month/day/hour/minute.
        // (Month is 0‐based: January = 0, December = 11.)
        final Calendar cal = Calendar.getInstance();

        // 1) TP 1 – Machine Learning (due April 20, 2025 23:59)
        cal.set(2025, Calendar.APRIL, 20, 23, 59, 0);
        Timestamp due1 = new Timestamp(cal.getTime());
        Assignment a1 = new Assignment();
        a1.setTitle("TP 1 – Machine Learning");
        a1.setContent(
                "• Implémenter k‐means clustering\n" +
                        "• Générer les histogrammes des distances\n" +
                        "• Analyser les résultats"
        );
        a1.setCreatedAt(Timestamp.now());
        a1.setDueAt(due1);
        a1.setCourseId("HAI817I");

        // 2) TP 2 – Machine Learning (due May 15, 2025 23:59)
        cal.set(2025, Calendar.MAY, 15, 23, 59, 0);
        Timestamp due2 = new Timestamp(cal.getTime());
        Assignment a2 = new Assignment();
        a2.setTitle("TP 2 – Machine Learning");
        a2.setContent(
                "• Implémenter un perceptron multicouche\n" +
                        "• Tester avec le dataset MNIST\n" +
                        "• Afficher la courbe d’apprentissage"
        );
        a2.setCreatedAt(Timestamp.now());
        a2.setDueAt(due2);
        a2.setCourseId("HAI817I");

        // 3) Rendu projet – Conduite de projet (due April 30, 2025 23:59)
        cal.set(2025, Calendar.APRIL, 30, 23, 59, 0);
        Timestamp due3 = new Timestamp(cal.getTime());
        Assignment a3 = new Assignment();
        a3.setTitle("Rendu projet – Conduite de projet");
        a3.setContent(
                "• Rapport de planification (Gantt)\n" +
                        "• Cahier des charges\n" +
                        "• Présentation du risque"
        );
        a3.setCreatedAt(Timestamp.now());
        a3.setDueAt(due3);
        a3.setCourseId("HAI810I");

        // 4) TP 1 – Langage naturel 1 (due May 10, 2025 23:59)
        cal.set(2025, Calendar.MAY, 10, 23, 59, 0);
        Timestamp due4 = new Timestamp(cal.getTime());
        Assignment a4 = new Assignment();
        a4.setTitle("TP 1 – Langage naturel 1");
        a4.setContent(
                "• Tokenisation et lemmatisation\n" +
                        "• Implémenter un POS‐tagger simple\n" +
                        "• Analyser la fréquence des n‐grammes"
        );
        a4.setCreatedAt(Timestamp.now());
        a4.setDueAt(due4);
        a4.setCourseId("HAI815I");

        // 5) TP 1 – Programmation mobile (due May 5, 2025 23:59)
        cal.set(2025, Calendar.MAY, 5, 23, 59, 0);
        Timestamp due5 = new Timestamp(cal.getTime());
        Assignment a5 = new Assignment();
        a5.setTitle("TP 1 – Programmation mobile");
        a5.setContent(
                "• Créer une Activity principale\n" +
                        "• Utiliser RecyclerView pour lister des données\n" +
                        "• Intégrer Firebase Authentication"
        );
        a5.setCreatedAt(Timestamp.now());
        a5.setDueAt(due5);
        a5.setCourseId("HAI811I");

        // Collect them into a list for batch‐creation
        List<Assignment> sampleAssignments = Arrays.asList(a1, a2, a3, a4, a5);

        // Firestore calls: create each document
        for (Assignment asg : sampleAssignments) {
            assignmentService.create(asg, created -> {
                // 'created' is the Assignment returned with its generated Firestore ID
                Log.d("SeedAssignments", "Added assignment with ID: " + created.getId());
            });
        }
    }

}

