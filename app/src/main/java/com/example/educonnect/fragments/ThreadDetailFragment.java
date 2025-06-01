package com.example.educonnect.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.educonnect.R;
import com.example.educonnect.entities.PostReply;
import com.example.educonnect.services.PostReplyService;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.Filter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ThreadDetailFragment shows:
 *   1) The original Post (author, content, date) at the top.
 *   2) A list of PostReply items (if any) below, each with an Upvote button + vote count.
 *   3) A “Répondre” button to let the user submit a new reply to the post.
 *
 * Now supports toggling upvotes: clicking the arrow:
 *   - If user has NOT yet upvoted, adds their email to `upvotedBy` and increments `upvotes`.
 *   - If user already upvoted, removes their email from `upvotedBy` and decrements `upvotes`.
 */
public class ThreadDetailFragment extends Fragment {
    private final PostReplyService postReplyService = new PostReplyService();

    private static final String ARG_POST_ID       = "arg_post_id";
    private static final String ARG_USER_EMAIL    = "arg_user_email";
    private static final String ARG_COURSE        = "arg_course";
    private static final String ARG_SUBJECT       = "arg_subject";
    private static final String ARG_SNIPPET       = "arg_snippet";
    private static final String ARG_DATE          = "arg_date";

    private static final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd/MM/yy, HH:mm", Locale.getDefault());

    // UI references
    private TextView tvThreadTitle, tvThreadCourse;
    private LinearLayout containerReplies;
    private ScrollView scrollReplies;
    private Button btnReply;

    // Post details (from arguments)
    private String postId;
    private String authorEmail;
    private String courseId;
    private String subject;
    private String content;
    private String dateString; // already formatted by caller

    // Current user’s email
    private String currentUserEmail;

    public static ThreadDetailFragment newInstance(String postId,
                                                   String authorEmail,
                                                   String course,
                                                   String subject,
                                                   String snippet,
                                                   Timestamp createdAt) {
        Bundle args = new Bundle();
        args.putString(ARG_POST_ID, postId);
        args.putString(ARG_USER_EMAIL, authorEmail);
        args.putString(ARG_COURSE, course);
        args.putString(ARG_SUBJECT, subject);
        args.putString(ARG_SNIPPET, snippet);
        args.putString(ARG_DATE, dateFormat.format(createdAt.toDate()));

        ThreadDetailFragment f = new ThreadDetailFragment();
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_thread_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v,
                              @Nullable Bundle b) {
        super.onViewCreated(v, b);

        // 1) Unpack arguments
        Bundle args = requireArguments();
        postId       = args.getString(ARG_POST_ID);
        authorEmail  = args.getString(ARG_USER_EMAIL);
        courseId     = args.getString(ARG_COURSE);
        subject      = args.getString(ARG_SUBJECT);
        content      = args.getString(ARG_SNIPPET);
        dateString   = args.getString(ARG_DATE);

        // 2) Wire up views
        tvThreadTitle    = v.findViewById(R.id.tvThreadTitle);
        tvThreadCourse   = v.findViewById(R.id.tvThreadCourse);
        containerReplies = v.findViewById(R.id.containerReplies);
        scrollReplies    = v.findViewById(R.id.scrollReplies);
        btnReply         = v.findViewById(R.id.btnReply);

        // 3) Determine current user’s email
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUserEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        } else {
            currentUserEmail = null; // should never happen if user is signed in
        }

        // 4) Header: “Cours : X” and “Sujet + date”
        tvThreadCourse.setText("Cours : " + courseId);
        tvThreadTitle.setText(subject + "\nPost créé le " + dateString);

        // 5) Render the thread (original post + replies, with upvoting)
        renderThread();

        // 6) Wire up “Répondre” button
        btnReply.setOnClickListener(click -> showCreateReplyDialog());

        // 7) Attach bottom nav bar
        FragmentManager fm = getChildFragmentManager();
        fm.beginTransaction()
                .replace(R.id.bottom_navigation_container, new NavBarFragment())
                .commit();
    }

    /**
     * Renders the original post at top, followed by all replies (sorted by createdAt ascending).
     * Each reply shows author, content, date, upvote button, and current upvote count.
     */
    private void renderThread() {
        containerReplies.removeAllViews();
        LayoutInflater li = LayoutInflater.from(getContext());

        // 5a) Add the “original post” at the top – but hide upvote UI
        View row0 = li.inflate(R.layout.item_reply, containerReplies, false);
        ((TextView) row0.findViewById(R.id.tvReplyAuthor))
                .setText(authorEmail);
        ((TextView) row0.findViewById(R.id.tvReplyContent))
                .setText(content);
        ((TextView) row0.findViewById(R.id.tvReplyDate))
                .setText(dateString);

        // Hide upvote controls for the original post
        View btnUp0 = row0.findViewById(R.id.btnUpvote);
        View tvCount0 = row0.findViewById(R.id.tvUpvoteCount);
        btnUp0.setVisibility(View.GONE);
        tvCount0.setVisibility(View.GONE);

        containerReplies.addView(row0);

        // 5b) Fetch all replies for this post, sorted by createdAt ascending
        Filter filter = Filter.equalTo("postId", postId);
        postReplyService.getSorted(filter, "createdAt", postReplies -> {
            requireActivity().runOnUiThread(() -> {
                // Remove any existing reply rows (keep row0)
                int childCount = containerReplies.getChildCount();
                containerReplies.removeViews(1, childCount - 1);

                for (PostReply r : postReplies) {
                    View row = li.inflate(R.layout.item_reply, containerReplies, false);

                    // Author, content, date
                    ((TextView) row.findViewById(R.id.tvReplyAuthor))
                            .setText(r.getUserEmail());
                    ((TextView) row.findViewById(R.id.tvReplyContent))
                            .setText(r.getContent());
                    ((TextView) row.findViewById(R.id.tvReplyDate))
                            .setText(dateFormat.format(r.getCreatedAt().toDate()));

                    // Upvote count
                    Long upv = r.getUpvotes();
                    if (upv == null) upv = 0L;
                    TextView tvCount = row.findViewById(R.id.tvUpvoteCount);
                    tvCount.setText(String.valueOf(upv));

                    // Determine if current user has already upvoted this reply
                    List<String> voters = r.getUpvotedBy();
                    boolean hasUpvoted = (voters != null &&
                            currentUserEmail != null &&
                            voters.contains(currentUserEmail));

                    // Upvote button (now an ImageButton)
                    ImageButton btnUp = row.findViewById(R.id.btnUpvote);
                    // Set initial icon: “↓” if already upvoted, else “↑”
                    btnUp.setImageResource(
                            hasUpvoted
                                    ? android.R.drawable.arrow_down_float
                                    : android.R.drawable.arrow_up_float
                    );

                    btnUp.setOnClickListener(click -> {
                        // Ensure voter list is non‐null
                        if (r.getUpvotedBy() == null) {
                            r.setUpvotedBy(new ArrayList<>());
                        }

                        if (currentUserEmail == null) {
                            Toast.makeText(getContext(),
                                    "Utilisateur non connecté", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        List<String> updatedVoters = r.getUpvotedBy();
                        Long currentUpvotes = (r.getUpvotes() == null) ? 0L : r.getUpvotes();

                        if (updatedVoters.contains(currentUserEmail)) {
                            // User has already upvoted → remove upvote
                            updatedVoters.remove(currentUserEmail);
                            r.setUpvotes(currentUpvotes - 1);
                        } else {
                            // User has not upvoted yet → add upvote
                            updatedVoters.add(currentUserEmail);
                            r.setUpvotes(currentUpvotes + 1);
                        }

                        // Save the updated PostReply back to Firestore
                        postReplyService.update(r.getId(), r, () -> {
                            // On success, re‐render entire thread
                            requireActivity().runOnUiThread(this::renderThread);
                        });
                    });

                    containerReplies.addView(row);
                }

                // Scroll to bottom for newest replies or updated counts
                scrollReplies.post(() -> scrollReplies.fullScroll(View.FOCUS_DOWN));
            });
        });
    }


    /**
     * Shows an AlertDialog with a single EditText to allow the user to write a reply.
     * On “Créer”, it creates a new PostReply (with upvotes=0, upvotedBy=[ ]) and then re‐renders.
     */
    private void showCreateReplyDialog() {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_create_reply, null);

        final EditText etContent = dialogView.findViewById(R.id.etReplyContent);

        new AlertDialog.Builder(requireContext())
                .setTitle("Nouvelle réponse")
                .setView(dialogView)
                .setPositiveButton("Créer", (dialog, which) -> {
                    String replyText = etContent.getText().toString().trim();
                    if (TextUtils.isEmpty(replyText)) {
                        Toast.makeText(getContext(),
                                "Le contenu ne peut pas être vide.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (currentUserEmail == null) {
                        Toast.makeText(getContext(),
                                "Utilisateur non connecté", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Build new PostReply with upvotes = 0, and empty upvotedBy list
                    PostReply newReply = new PostReply();
                    newReply.setPostId(postId);
                    newReply.setUserEmail(currentUserEmail);
                    newReply.setContent(replyText);
                    newReply.setCreatedAt(Timestamp.now());
                    newReply.setUpvotes(0L);
                    newReply.setUpvotedBy(new ArrayList<>());

                    // Create in Firestore
                    postReplyService.create(newReply, created -> {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(),
                                    "Réponse créée avec succès.", Toast.LENGTH_SHORT).show();
                            // Refresh the thread so the new reply appears
                            renderThread();
                        });
                    });
                })
                .setNegativeButton("Annuler", (dialog, which) -> dialog.dismiss())
                .show();
    }
}
