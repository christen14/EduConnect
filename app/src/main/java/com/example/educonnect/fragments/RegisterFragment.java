package com.example.educonnect.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.educonnect.R;
import com.example.educonnect.entities.User;
import com.example.educonnect.services.UserService;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class RegisterFragment extends Fragment {

    private EditText etEmail, etPassword1, etPassword2;
    private Button btnRegister;
    private TextView tvBackToLogin;
    private ImageView ivTogglePassword1, ivTogglePassword2;
    private RadioGroup rgRole;
    private Long selectedRole;
    private FirebaseAuth mAuth;
    private boolean isPasswordVisible1 = false;
    private boolean isPasswordVisible2 = false;
    private final UserService userService = new UserService();


    private static final String TAG = "RegisterFragment";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_register, container, false);
    }

    private void addUserToDb(String email, Long role) {
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setMode("default");
        newUser.setRole(role);

        userService.create(newUser, (userDb) -> {
            System.out.println("User added successfully");
            System.out.println("-------------- User: " + userDb.getId());
            System.out.println("Email : " + userDb.getEmail());
            System.out.println("Role : " + userDb.getRole());
            System.out.println("Mode : " + userDb.getMode());
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Initialize Firebase Auth

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // If the user is already logged in, go to Dashboard
            DashboardFragment dashboardFragment = new DashboardFragment();
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, dashboardFragment)
                    .commit();
        }

        // Initialize UI components
        etEmail = view.findViewById(R.id.etEmail);
        etPassword1 = view.findViewById(R.id.etPassword1);
        etPassword2 = view.findViewById(R.id.etPassword2);
        btnRegister = view.findViewById(R.id.btnRegister);
        tvBackToLogin = view.findViewById(R.id.tvBackToLogin);
        ivTogglePassword1 = view.findViewById(R.id.ivTogglePassword1);
        ivTogglePassword2 = view.findViewById(R.id.ivTogglePassword2);
        rgRole = view.findViewById(R.id.rgRole);
        rgRole.check(R.id.rbEtudiant);  // Select "Étudiant" by default

        // Toggle password visibility for password 1
        ivTogglePassword1.setOnClickListener(v -> {
            if (isPasswordVisible1) {
                etPassword1.setTransformationMethod(PasswordTransformationMethod.getInstance());
                ivTogglePassword1.setImageResource(android.R.drawable.ic_menu_view);
            } else {
                etPassword1.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                ivTogglePassword1.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            }
            isPasswordVisible1 = !isPasswordVisible1;
            etPassword1.setSelection(etPassword1.getText().length());
        });

        // Toggle password visibility for password 2
        ivTogglePassword2.setOnClickListener(v -> {
            if (isPasswordVisible2) {
                etPassword2.setTransformationMethod(PasswordTransformationMethod.getInstance());
                ivTogglePassword2.setImageResource(android.R.drawable.ic_menu_view);
            } else {
                etPassword2.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                ivTogglePassword2.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            }
            isPasswordVisible2 = !isPasswordVisible2;
            etPassword2.setSelection(etPassword2.getText().length());
        });

        // Register button click listener
        btnRegister.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password1 = etPassword1.getText().toString();
            String password2 = etPassword2.getText().toString();
            selectedRole = (long) -1;
            int checkedId = rgRole.getCheckedRadioButtonId();
            if (checkedId == R.id.rbEtudiant) {
                selectedRole = (long) 0;
            } else if (checkedId == R.id.rbProfesseur) {
                selectedRole = (long) 1;
            }

            if (email.isEmpty() || password1.isEmpty() || password2.isEmpty()) {
                Toast.makeText(getActivity(), "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show();
                return;
            } else if (!password1.equals(password2)) {
                Toast.makeText(getActivity(), "Les 2 mots de passe sont differents", Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.createUserWithEmailAndPassword(email, password1)
                    .addOnCompleteListener(getActivity(), new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {

                            if (task.isSuccessful()) {
                                // Registration success, update UI with signed-in user's info
                                Log.d(TAG, "createUserWithEmail:success");
                                Toast.makeText(getActivity(), "Compte créé.", Toast.LENGTH_SHORT).show();
                                addUserToDb(email, selectedRole);

                                // Redirect to DashboardFragment
                                com.example.educonnect.fragments.DashboardFragment dashboardFragment = new com.example.educonnect.fragments.DashboardFragment();
                                getActivity().getSupportFragmentManager().beginTransaction()
                                        .replace(R.id.fragment_container, dashboardFragment)
                                        .commit();
                            } else {
                                // If registration fails, display a message to the user.
                                Log.w(TAG, "createUserWithEmail:failure", task.getException());

                                // Check if the error is caused by the email already being in use
                                if (task.getException() instanceof com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                                    // The email is already registered
                                    Toast.makeText(getActivity(), "This email is already associated with an account.", Toast.LENGTH_SHORT).show();
                                } else {
                                    // Handle other errors
                                    Toast.makeText(getActivity(), "Authentication failed. Please try again", Toast.LENGTH_SHORT).show();
                                }
                            }

                        }
                    });
        });


        // Back to login screen
        tvBackToLogin.setOnClickListener(v -> {
            // Optionally, you can navigate to the LoginFragment here (not using Intent)
            LoginFragment loginFragment = new LoginFragment();
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, loginFragment)
                    .commit();
        });

    }

}
