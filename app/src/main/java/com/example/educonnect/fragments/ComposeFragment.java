package com.example.educonnect.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.educonnect.R;
import com.example.educonnect.entities.Message;
import com.example.educonnect.services.MessageService;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Objects;

public class ComposeFragment extends Fragment {

    private final MessageService messageService = new MessageService();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_compose, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        EditText etTo = v.findViewById(R.id.etTo);
        EditText etSubject = v.findViewById(R.id.etSubject);
        EditText etBody = v.findViewById(R.id.etBody);
        Button btnSend = v.findViewById(R.id.btnSend);

        btnSend.setOnClickListener(x -> {
            String to = etTo.getText().toString().trim();
            String subj = etSubject.getText().toString().trim();
            String body = etBody.getText().toString().trim();

            if (TextUtils.isEmpty(to) || TextUtils.isEmpty(subj) || TextUtils.isEmpty(body)) {
                Toast.makeText(getContext(),
                        getString(R.string.champs_manquants), Toast.LENGTH_SHORT).show();
                return;
            }

            // Build a new Message entity
            Message msg = new Message();
            msg.setSenderId(Objects.requireNonNull(FirebaseAuth.getInstance()
                    .getCurrentUser()).getEmail());
            msg.setReceiverId(to);
            msg.setSubject(subj);
            msg.setContent(body);
            msg.setCreatedAt(Timestamp.now());
            msg.setOpenedAt(null);

            // Save to Firestore
            messageService.create(msg, createdMsg -> {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(),
                            getString(R.string.message_envoye), Toast.LENGTH_SHORT).show();
                    // Pop back to inbox
                    FragmentManager fm = requireActivity().getSupportFragmentManager();
                    fm.popBackStack();
                });
            });
        });

        // 4) Attach bottom nav bar
        FragmentManager fm = getChildFragmentManager();
        fm.beginTransaction()
                .replace(R.id.bottom_navigation_container, new NavBarFragment())
                .commit();
    }
}
