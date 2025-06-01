package com.example.educonnect.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.educonnect.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginFragment extends Fragment {

    private EditText etEmail, etPassword;
    private ImageView ivTogglePassword;
    private CheckBox cbRemember;
    private TextView tvForgotPassword, tvCreateAccount;
    private Button btnLogin;

    private FirebaseAuth mAuth;

    private boolean isPasswordVisible = false;

    SharedPreferences prefs;
    boolean remember;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        prefs = getActivity().getSharedPreferences("loginPrefs", getActivity().MODE_PRIVATE);
        remember = prefs.getBoolean("rememberMe", false);
        if (currentUser != null && remember) {
                // If the user is already logged in and wants to be remembered, go to Dashboard
                DashboardFragment dashboardFragment = new DashboardFragment();
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, dashboardFragment)
                        .commit();
        } else if (currentUser != null) {
            // Utilisateur connecté MAIS n’a pas demandé à être mémorisé → on nettoie la session
            mAuth.signOut();
            // et on reste sur l’écran de login
        }
        // Sinon (currentUser == null) → on reste sur le formulaire

        // Initialize views
        etEmail = view.findViewById(R.id.etEmail);
        etPassword = view.findViewById(R.id.etPassword);
        ivTogglePassword = view.findViewById(R.id.ivTogglePassword);
        cbRemember = view.findViewById(R.id.cbRemember);
        tvForgotPassword = view.findViewById(R.id.tvForgotPassword);
        tvCreateAccount = view.findViewById(R.id.tvCreateAccount);
        btnLogin = view.findViewById(R.id.btnLogin);

        // Toggle password visibility
        ivTogglePassword.setOnClickListener(v -> {
            if (isPasswordVisible) {
                etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                ivTogglePassword.setImageResource(android.R.drawable.ic_menu_view);
            } else {
                etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                ivTogglePassword.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            }
            isPasswordVisible = !isPasswordVisible;
            etPassword.setSelection(etPassword.getText().length());
        });

        // Handle forgot password click
        tvForgotPassword.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (email.isEmpty()) {
                Toast.makeText(getActivity(), "Entrez votre adresse mail pour réinitialiser le mot de passe", Toast.LENGTH_SHORT).show();
                return;
            }
            mAuth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(getActivity(), "Email de réinitialisation envoyé", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getActivity(), "Erreur lors de l'envoi de l'email", Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        // Handle "Create Account" click
        tvCreateAccount.setOnClickListener(v -> {
            Toast.makeText(getActivity(), "Vers la création de compte", Toast.LENGTH_SHORT).show();
            // Go to RegisterFragment
            RegisterFragment registerFragment = new RegisterFragment();
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, registerFragment)
                    .commit();
        });

        // Handle login button click
        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(getActivity(), getString(R.string.champs_manquants), Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Save "Remember Me" choice
                            boolean remember = cbRemember.isChecked();
                            SharedPreferences prefs = getActivity().getSharedPreferences("loginPrefs", getActivity().MODE_PRIVATE);
                            prefs.edit().putBoolean("rememberMe", remember).apply();

                            Toast.makeText(getActivity(), "Connexion réussie", Toast.LENGTH_SHORT).show();

                            // Go to DashboardFragment
                            DashboardFragment dashboardFragment = new DashboardFragment();
                            getActivity().getSupportFragmentManager().beginTransaction()
                                    .replace(R.id.fragment_container, dashboardFragment)
                                    .commit();
                        } else {
                            Toast.makeText(getActivity(), "Erreur d'authentification : " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        });

        return view;
    }
}
