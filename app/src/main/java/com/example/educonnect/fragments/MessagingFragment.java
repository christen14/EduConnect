// File: app/src/main/java/com/example/educonnect/fragments/MessagingFragment.java
package com.example.educonnect.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.educonnect.R;
import com.example.educonnect.entities.Message;
import com.example.educonnect.services.MessageService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Displays the inbox for the signed‐in user, plus a new “Envoyés” (Sent) filter.
 *
 * Spinner positions (0..4):
 *   0 => Tous (Inbox: no filter)
 *   1 => Importants (Inbox: important == true)
 *   2 => Non lus (Inbox: openedAt == null)
 *   3 => Lus (Inbox: openedAt != null)
 *   4 => Envoyés (Sent: simply show all messages where senderId == currentUserEmail)
 *
 * We maintain two separate lists under the hood:
 *   • allInboxMessages (receiverId == currentUserEmail)
 *   • allSentMessages  (senderId == currentUserEmail)
 *
 * filteredMessages always holds what’s currently shown in the RecyclerView.
 * We also update the header label (tvInboxLabel) to say either “Boîte de réception” or “Envoyés.”
 */
public class MessagingFragment extends Fragment {

    private RecyclerView rvMessages;
    private TextView tvInboxLabel;
    private TextView tvInboxCount;
    private Spinner spinnerFilters;
    private CheckBox cbSelectAll;

    private ImageButton btnDeleteSelected;
    private ImageButton btnImportantSelected;

    private final MessageService messageService = new MessageService();
    /** Inbox: messages where receiverId == currentUserEmail */
    private final List<Message> allInboxMessages = new ArrayList<>();
    /** Sent: messages where senderId == currentUserEmail */
    private final List<Message> allSentMessages = new ArrayList<>();
    /** Always hold the messages currently being displayed (either from inbox or sent) */
    private final List<Message> filteredMessages = new ArrayList<>();
    private MessageAdapter adapter;

    private String currentUserEmail;
    /** Are we currently viewing “Envoyés”? */
    private boolean viewingSent = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_messaging, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        // 1) “Compose” button
        v.findViewById(R.id.btnCompose).setOnClickListener(x -> {
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new ComposeFragment())
                    .addToBackStack(null)
                    .commit();
        });

        // 2) Reference UI elements
        rvMessages           = v.findViewById(R.id.rvMessages);
        tvInboxLabel        = v.findViewById(R.id.tvInboxLabel);
        tvInboxCount         = v.findViewById(R.id.tvInboxCount);
        spinnerFilters       = v.findViewById(R.id.spinnerFilters);
        cbSelectAll          = v.findViewById(R.id.cbSelectAll);
        btnDeleteSelected    = v.findViewById(R.id.btnDeleteSelected);
        btnImportantSelected = v.findViewById(R.id.btnImportantSelected);

        // 3) Setup RecyclerView & Adapter
        rvMessages.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new MessageAdapter(
                filteredMessages,
                message -> {
                    // On message click: open detail
                    MessageDetailFragment detail = MessageDetailFragment.newInstance(
                            message.getId(),
                            message.getSenderId(),
                            message.getReceiverId(),
                            message.getSubject(),
                            message.getContent(),
                            message.getCreatedAt()
                    );
                    requireActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, detail)
                            .addToBackStack(null)
                            .commit();
                },
                new MessageAdapter.OnMessageActionListener() {
                    @Override
                    public void onDelete(Message m) {
                        messageService.delete(m.getId(), () -> {
                            // After delete, reload whichever collection is active
                            reloadCurrentMessages();
                        });
                    }

                    @Override
                    public void onToggleImportant(Message m) {
                        boolean nowImportant = m.getImportant() == null ? true : !m.getImportant();
                        m.setImportant(nowImportant);
                        messageService.update(m.getId(), m, () -> {
                            // After toggling important, reload whichever collection is active
                            reloadCurrentMessages();
                        });
                    }
                }
        );
        rvMessages.setAdapter(adapter);

        // 4) Set up Spinner (filter options: “Tous / Importants / Non lus / Lus / Envoyés”)
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.filter_options,   // ["Tous", "Importants", "Non lus", "Lus", "Envoyés"]
                android.R.layout.simple_spinner_item
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilters.setAdapter(spinnerAdapter);

        // When an item is selected, either (a) apply one of the 4 inbox filters,
        // or (b) if index == 4 (“Envoyés”), load all sent messages.
        spinnerFilters.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos == 4) {
                    // “Envoyés” selected
                    viewingSent = true;
                    loadAllSentMessagesFromFirestore();
                } else {
                    // One of the inbox filters (Tous / Importants / Non lus / Lus)
                    viewingSent = false;
                    showInboxFiltered(pos);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { /* no-op */ }
        });

        // 5) “Select All” checkbox toggles selection
        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            adapter.selectAll(isChecked);
        });

        // 6) Bulk‐action buttons
        btnDeleteSelected.setOnClickListener(x -> deleteSelectedMessages());
        btnImportantSelected.setOnClickListener(x -> markImportantSelectedMessages());

        // 7) Bottom nav bar
        FragmentManager fm = getChildFragmentManager();
        fm.beginTransaction()
                .replace(R.id.bottom_navigation_container, new NavBarFragment())
                .commit();

        // 8) Get current user email
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(getContext(),
                    "Utilisateur non connecté",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        currentUserEmail = user.getEmail();

        // 9) Load the inbox initially
        loadAllInboxMessagesFromFirestore();
    }

    /**
     * Reload whichever collection is currently being viewed (inbox or sent).
     */
    private void reloadCurrentMessages() {
        if (viewingSent) {
            loadAllSentMessagesFromFirestore();
        } else {
            loadAllInboxMessagesFromFirestore();
        }
    }

    /**
     * STEP 1 for INBOX: Fetch all messages where receiverId == currentUserEmail, sort by createdAt desc.
     */
    private void loadAllInboxMessagesFromFirestore() {
        Filter filter = Filter.equalTo("receiverId", currentUserEmail);
        messageService.getSorted(filter, "createdAt", messages -> {
            allInboxMessages.clear();
            allInboxMessages.addAll(messages);

            // Sort descending by createdAt (just to be safe)
            Collections.sort(allInboxMessages, (m1, m2) -> {
                if (m1.getCreatedAt() == null || m2.getCreatedAt() == null) {
                    return 0;
                }
                return m2.getCreatedAt().compareTo(m1.getCreatedAt());
            });

            // On the UI thread, re‐apply whichever inbox filter is currently selected
            requireActivity().runOnUiThread(() -> {
                int sel = spinnerFilters.getSelectedItemPosition();
                if (sel >= 0 && sel <= 3) {
                    showInboxFiltered(sel);
                } else {
                    // If spinner was on “Envoyés” when we refresh the inbox, switch back to “Tous”
                    spinnerFilters.setSelection(0);
                    showInboxFiltered(0);
                }
            });
        });
    }

    /**
     * STEP 1 for SENT: Fetch all messages where senderId == currentUserEmail, sort by createdAt desc.
     */
    private void loadAllSentMessagesFromFirestore() {
        System.out.println("AAAAAA");
        Filter filter = Filter.equalTo("senderId", currentUserEmail);
        messageService.getSorted(filter, "createdAt", messages -> {
            allSentMessages.clear();
            allSentMessages.addAll(messages);

            // Sort descending by createdAt
            Collections.sort(allSentMessages, (m1, m2) -> {
                if (m1.getCreatedAt() == null || m2.getCreatedAt() == null) {
                    return 0;
                }
                return m2.getCreatedAt().compareTo(m1.getCreatedAt());
            });

            // On the UI thread, display all sent messages
            requireActivity().runOnUiThread(this::displaySentMessages);
        });
    }

    /**
     * STEP 2 for INBOX: Filter allInboxMessages according to spinner index (0..3).
     *   0 => “Tous”      (no filter)
     *   1 => “Importants” (important == true)
     *   2 => “Non lus”    (openedAt == null)
     *   3 => “Lus”        (openedAt != null)
     *
     * After filtering, update the RecyclerView.
     */
    private void showInboxFiltered(int selectionIndex) {
        filteredMessages.clear();

        switch (selectionIndex) {
            case 1: // “Importants”
                for (Message m : allInboxMessages) {
                    if (m.getImportant() != null && m.getImportant()) {
                        filteredMessages.add(m);
                    }
                }
                break;

            case 2: // “Non lus”
                for (Message m : allInboxMessages) {
                    if (m.getOpenedAt() == null) {
                        filteredMessages.add(m);
                    }
                }
                break;

            case 3: // “Lus”
                for (Message m : allInboxMessages) {
                    if (m.getOpenedAt() != null) {
                        filteredMessages.add(m);
                    }
                }
                break;

            default: // 0 => “Tous”
                filteredMessages.addAll(allInboxMessages);
        }

        // Update header label & count
        tvInboxLabel.setText("Boîte de réception");
        tvInboxCount.setText(String.valueOf(filteredMessages.size()));
        cbSelectAll.setChecked(false);

        // Reset selection map and refresh UI
        adapter.resetSelection();
        adapter.notifyDataSetChanged();
    }

    /**
     * STEP 2 for SENT: Simply show allSentMessages (no sub‐filtering).
     */
    private void displaySentMessages() {
        filteredMessages.clear();
        filteredMessages.addAll(allSentMessages);

        // Update header label & count
        tvInboxLabel.setText("Envoyés");
        tvInboxCount.setText(String.valueOf(filteredMessages.size()));
        cbSelectAll.setChecked(false);

        // Reset selection map and refresh UI
        adapter.resetSelection();
        adapter.notifyDataSetChanged();
    }

    /**
     * Bulk‐delete all currently selected messages (in filteredMessages).
     * Afterwards, reload whichever collection is active.
     */
    private void deleteSelectedMessages() {
        List<Message> toDelete = new ArrayList<>();
        for (Message m : filteredMessages) {
            Boolean isSelected = adapter.getSelectedMap().get(m.getId());
            if (isSelected != null && isSelected) {
                toDelete.add(m);
            }
        }
        if (toDelete.isEmpty()) {
            Toast.makeText(getContext(),
                    "Aucun message sélectionné",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Fire off delete for each selected message
        for (Message m : toDelete) {
            messageService.delete(m.getId(), () -> {
                // no‐op on success; we will reload after loop
            });
        }

        // After issuing deletes, reload whichever collection is active
        reloadCurrentMessages();
    }

    /**
     * Bulk‐toggle “important” on all currently selected messages (in filteredMessages).
     * Afterwards, reload whichever collection is active.
     */
    private void markImportantSelectedMessages() {
        List<Message> toToggle = new ArrayList<>();
        for (Message m : filteredMessages) {
            Boolean isSelected = adapter.getSelectedMap().get(m.getId());
            if (isSelected != null && isSelected) {
                toToggle.add(m);
            }
        }
        if (toToggle.isEmpty()) {
            Toast.makeText(getContext(),
                    "Aucun message sélectionné",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // For each selected message, flip its “important” flag
        for (Message m : toToggle) {
            boolean currentlyImportant = m.getImportant() != null && m.getImportant();
            m.setImportant(!currentlyImportant);
            messageService.update(m.getId(), m, () -> {
                // no‐op here; we will reload after the loop
            });
        }

        // After issuing all updates, reload whichever collection is active
        reloadCurrentMessages();
    }
}
