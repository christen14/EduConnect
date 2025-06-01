// File: app/src/main/java/com/example/educonnect/fragments/MessageDetailFragment.java
package com.example.educonnect.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.educonnect.R;
import com.example.educonnect.entities.Message;
import com.example.educonnect.services.MessageService;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Shows the full details of a message (From, To, Subject, Body, Date).
 * If the message was never opened (openedAt == null), this fragment will mark it opened now.
 *
 * Now accepts the date originally as a Firestore Timestamp, rather than a pre‐formatted String.
 */
public class MessageDetailFragment extends Fragment {
    private static final String ARG_ID      = "arg_id";
    private static final String ARG_FROM    = "arg_from";
    private static final String ARG_TO      = "arg_to";
    private static final String ARG_SUBJECT = "arg_subject";
    private static final String ARG_BODY    = "arg_body";
    private static final String ARG_DATE_TS = "arg_date_ts";

    private final MessageService messageService = new MessageService();

    /** Call with (messageId, fromEmail, toEmail, subject, body, createdAtTimestamp) */
    public static MessageDetailFragment newInstance(
            String id,
            String from,
            String to,
            String subject,
            String body,
            @Nullable Timestamp dateTime
    ) {
        Bundle args = new Bundle();
        args.putString(ARG_ID,      id);
        args.putString(ARG_FROM,    from);
        args.putString(ARG_TO,      to);
        args.putString(ARG_SUBJECT, subject);
        args.putString(ARG_BODY,    body);
        // We store the Timestamp as a Parcelable in the Bundle
        if (dateTime != null) {
            args.putParcelable(ARG_DATE_TS, dateTime);
        }
        MessageDetailFragment f = new MessageDetailFragment();
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_message_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        Bundle args = requireArguments();
        String msgId   = args.getString(ARG_ID);
        String from    = args.getString(ARG_FROM);
        String to      = args.getString(ARG_TO);
        String subject = args.getString(ARG_SUBJECT);
        String body    = args.getString(ARG_BODY);
        Timestamp dateTs = args.getParcelable(ARG_DATE_TS);

        // Populate UI
        ((TextView) v.findViewById(R.id.tvDetailFrom)).setText("Expéditeur : " + from);
        ((TextView) v.findViewById(R.id.tvDetailTo)).setText("À : " + to);
        ((TextView) v.findViewById(R.id.tvDetailSubject)).setText(subject);
        ((TextView) v.findViewById(R.id.tvDetailBody)).setText(body);

        // If we have a Timestamp, format it (e.g. “EEE dd MMM yyyy HH:mm”)
        String formattedDate = "";
        if (dateTs != null) {
            // You may adjust the format as needed. Here’s a simple example in French locale:
            SimpleDateFormat sdf = new SimpleDateFormat("EEE dd MMM yyyy HH:mm", Locale.FRENCH);
            String raw = sdf.format(dateTs.toDate());
            // Capitalize first letter and remove any trailing dot (so “mer. 01 janv. 12:34” → “Mer 01 janv 12:34”)
            String noDot = raw.replaceFirst("\\.", "");
            formattedDate = noDot.substring(0, 1).toUpperCase() + noDot.substring(1);
        }
        ((TextView) v.findViewById(R.id.tvDetailDate)).setText(formattedDate);

        // If openedAt is null, mark as opened now
        messageService.getAll(messages -> {
            for (Message m : messages) {
                if (m.getId().equals(msgId)) {
                    if (m.getOpenedAt() == null) {
                        m.setOpenedAt(Timestamp.now());
                        messageService.update(msgId, m, () -> {
                            // success; nothing else to do
                        });
                    }
                    break;
                }
            }
        });

        // Bottom nav bar
        FragmentManager fm = getChildFragmentManager();
        fm.beginTransaction()
                .replace(R.id.bottom_navigation_container, new NavBarFragment())
                .commit();
    }
}
