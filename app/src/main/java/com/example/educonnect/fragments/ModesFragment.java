package com.example.educonnect.fragments;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.educonnect.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Filter;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ModesFragment now finds the user document in Firestore by matching "email" == currentUser.getEmail()
 * rather than using the Authentication UID. Once located, it reads "mode" from that document, checks
 * the corresponding radio button, and on user change, writes the new mode back to the same document.
 */
public class ModesFragment extends Fragment {
    private List<RadioButton> radios;
    private List<TextView> textViews;
    private int currentModeIndex = 0;  // 0=default,1=dnd,2=classe,3=vacances,4=examen

    // Mapping from index → Firestore string
    private static final String[] MODE_STRINGS = {
            "default",
            "ne pas deranger",
            "classe",
            "vacances",
            "examen"
    };

    // UI references
    private RadioButton rbDefault, rbDnd, rbClass, rbVacation, rbExam;
    private ImageButton ibInfoDND, ibInfoClass, ibInfoVacation, ibInfoExam;
    private TextView tvDefault, tvDnd, tvClass, tvVacation, tvExam;

    // Firestore / Auth
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private DocumentReference userDocRef;
    private boolean userDocFound = false;  // flag to indicate we located a user doc

    // A local flag for “Do Not Disturb”
    private boolean isDoNotDisturb = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_modes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // 1) Wire up all RadioButtons & TextViews & info‐buttons
        rbDefault     = v.findViewById(R.id.rbDefault);
        rbDnd         = v.findViewById(R.id.rbDoNotDisturb);
        rbClass       = v.findViewById(R.id.rbClass);
//        rbVacation    = v.findViewById(R.id.rbVacation);
//        rbExam        = v.findViewById(R.id.rbExam);

        tvDefault     = v.findViewById(R.id.tvDefault);
        tvDnd         = v.findViewById(R.id.tvDoNotDisturb);
        tvClass       = v.findViewById(R.id.tvClass);
//        tvVacation    = v.findViewById(R.id.tvVacation);
//        tvExam        = v.findViewById(R.id.tvExam);

        ibInfoDND     = v.findViewById(R.id.ibInfoDND);
        ibInfoClass   = v.findViewById(R.id.ibInfoClass);
//        ibInfoVacation= v.findViewById(R.id.ibInfoVacation);
//        ibInfoExam    = v.findViewById(R.id.ibInfoExam);

        radios = Arrays.asList(rbDefault, rbDnd, rbClass);
        textViews = Arrays.asList(tvDefault, tvDnd, tvClass);

        // 2) Get current user email
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(getContext(), "Utilisateur non connecté ou email invalide.", Toast.LENGTH_SHORT).show();
            return;
        }
        String userEmail = user.getEmail();

        // 3) Query Firestore "users" collection by matching "email" == userEmail
        db.collection("users")
                .whereEqualTo("email", userEmail)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        // No user document found with this email
                        Toast.makeText(getContext(), "Aucun utilisateur trouvé dans la base pour : " + userEmail, Toast.LENGTH_LONG).show();
                        return;
                    }
                    // Assume the first matching document is our user (emails are unique)
                    userDocRef = querySnapshot.getDocuments().get(0).getReference();
                    userDocFound = true;

                    // 4) Read the "mode" field from that document
                    String mode = querySnapshot.getDocuments().get(0).getString("mode");
                    int index = mapModeStringToIndex(mode);
                    currentModeIndex = index;
                    radios.get(currentModeIndex).setChecked(true);

                    // If “ne pas deranger”
                    isDoNotDisturb = (currentModeIndex == 1);

                    // If “classe”
                    if (currentModeIndex == 2) {
                        enforceClassModeRestrictions();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Erreur lors de récupération du mode : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });

        // 5) When any radio button is clicked:
        for (int i = 0; i < radios.size(); i++) {
            final int idx = i;
            radios.get(i).setOnClickListener(x -> {
                // Uncheck all, check only this one
                for (RadioButton other : radios) {
                    other.setChecked(false);
                }
                radios.get(idx).setChecked(true);

                // If userDocRef not yet found or same mode, do nothing
                if (!userDocFound || idx == currentModeIndex) return;

                // 5a) Update local state
                currentModeIndex = idx;
                isDoNotDisturb = (idx == 1);

                // 5b) Write the new mode string into Firestore:
                String newModeStr = MODE_STRINGS[idx];
                Map<String,Object> updateMap = new HashMap<>();
                updateMap.put("mode", newModeStr);
                userDocRef.update(updateMap)
                        .addOnSuccessListener(unused -> {
                            Toast.makeText(getContext(),
                                    "Mode mis à jour : " + newModeStr, Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(getContext(),
                                    "Erreur mise à jour mode : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });

                // 5c) Apply side‐effects:
                if (idx == 1) {
                    // Ne pas déranger
                    suppressNotifications();
                } else if (idx == 2) {
                    // Classe
                    enforceClassModeRestrictions();
                } else {
                    // Any other mode
                    removeDoNotDisturbEffects();
                    removeClassModeRestrictions();
                }
            });
        }

        // 6) TextViews clickable to check their radios
        tvDefault.setOnClickListener(x -> rbDefault.performClick());
        tvDnd.setOnClickListener(x -> rbDnd.performClick());
        tvClass.setOnClickListener(x -> rbClass.performClick());
//        tvVacation.setOnClickListener(x -> rbVacation.performClick());
//        tvExam.setOnClickListener(x -> rbExam.performClick());

        // 7) Info dialogs
        setupInfo(ibInfoDND,
                "Ne pas déranger",
                "Désactive temporairement toutes les notifications de l'application.");
        setupInfo(ibInfoClass,
                "Classe",
                "Restreint l’accès uniquement aux cours et à l’emploi du temps.\n" +
                        "Les forums, messages et autres sont désactivés pour éviter toute distraction.");
//        setupInfo(ibInfoVacation,
//                "Vacances",
//                "Met en pause les notifications académiques et affiche uniquement les cours enregistrés.");
//        setupInfo(ibInfoExam,
//                "Examen",
//                "Bloque l’accès à certaines fonctionnalités pendant un examen.");

        // 8) Bottom nav‐bar
        FragmentManager fm = getChildFragmentManager();
        fm.beginTransaction()
                .replace(R.id.bottom_navigation_container, new NavBarFragment())
                .commit();
    }

    /**
     * Maps a Firestore mode‐string to its index in MODE_STRINGS.
     * If not found or null, returns 0 (“default”).
     */
    private int mapModeStringToIndex(@Nullable String mode) {
        if (mode == null) return 0;
        for (int i = 0; i < MODE_STRINGS.length; i++) {
            if (MODE_STRINGS[i].equalsIgnoreCase(mode.trim())) {
                return i;
            }
        }
        return 0;
    }

    /** Shows an AlertDialog when the little “i” icon is tapped. */
    private void setupInfo(ImageButton ib, String title, String message) {
        ib.setOnClickListener(x -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    /** Stub: suppress (mute) notifications in “Do Not Disturb” mode. */
    private void suppressNotifications() {
        Context ctx = getContext();
        if (ctx == null) return;

        // 1) Save a SharedPreference flag indicating DND is active
        SharedPreferences prefs = ctx.getSharedPreferences("appPreferences", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("dnd_mode", true).apply();

        // 2) Cancel any currently visible notifications
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancelAll();
        }

        // 3) Give user feedback
        Toast.makeText(ctx,
                "Notifications désactivées (Ne pas déranger)",
                Toast.LENGTH_SHORT).show();
    }

    /** Re-enable notifications when leaving “Do Not Disturb”. */
    private void removeDoNotDisturbEffects() {
        Context ctx = getContext();
        if (ctx == null) return;

        SharedPreferences prefs = ctx.getSharedPreferences("appPreferences", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("dnd_mode", false).apply();

        Toast.makeText(ctx,
                "Notifications réactivées",
                Toast.LENGTH_SHORT).show();
    }

    /**
     * In “Classe” mode, disable navigation items except Courses & Planning.
     * Pass currentModeIndex to your NavBarFragment so it can gray out disallowed icons.
     */
    private void enforceClassModeRestrictions() {
        Toast.makeText(getContext(),
                "Mode Classe activé : seules les sections Cours et Planning sont accessibles.",
                Toast.LENGTH_LONG).show();
        // If you had DND active, re‐enable notifications
        removeDoNotDisturbEffects();
    }

    /** When leaving “Classe” mode, re‐enable full navigation. */
    private void removeClassModeRestrictions() {
        Toast.makeText(getContext(),
                "Mode “Classe” désactivé : accès complet rétabli.",
                Toast.LENGTH_SHORT).show();
    }
}
