// File: app/src/main/java/com/example/educonnect/fragments/ForumFragment.java
package com.example.educonnect.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.educonnect.R;
import com.example.educonnect.entities.Course;
import com.example.educonnect.entities.Post;
import com.example.educonnect.entities.StudentCourse;
import com.example.educonnect.entities.User;
import com.example.educonnect.services.CourseService;
import com.example.educonnect.services.PostService;
import com.example.educonnect.services.StudentCourseService;
import com.example.educonnect.services.UserService;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Filter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ForumFragment displays:
 *   1) A left‐side list of courses (for a student: enrolled courses; for a professor: courses they teach).
 *   2) When a course is tapped, the right‐side shows that course's posts.
 *   3) A “Nouveau post” button to create a new Post in Firestore.
 *
 * Professors are identified by their Firestore document ID (User.getId()), not by email or Auth UID.
 */
public class ForumFragment extends Fragment {

    private RecyclerView rvCoursesSide, rvPosts;
    private TextView tvCourseHeader;
    private Button btnCreatePost;

    private FirebaseAuth mauth;
    private FirebaseUser currentUser;

    private final UserService userService = new UserService();
    private final StudentCourseService studentCourseService = new StudentCourseService();
    private final CourseService courseService = new CourseService();
    private final PostService postService = new PostService();

    /** Holds course codes (either enrolled courses for student, or taught courses for professor). */
    private List<String> allCourses = new ArrayList<>();

    /** Maps courseCode → list of Post objects for that course. */
    private Map<String, List<Post>> allPosts = new HashMap<>();

    // Adapters
    private SideCourseAdapter courseAdapter;
    private PostAdapter postAdapter;

    // Tracks which course is currently selected
    private String currentCourseId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_forum, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        mauth = FirebaseAuth.getInstance();
        currentUser = mauth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(getContext(), "Utilisateur non connecté", Toast.LENGTH_SHORT).show();
            return;
        }

        // Wire up UI references
        rvCoursesSide  = v.findViewById(R.id.rvCoursesSide);
        rvPosts        = v.findViewById(R.id.rvPosts);
        tvCourseHeader = v.findViewById(R.id.tvForumCourse);
        btnCreatePost  = v.findViewById(R.id.btnCreatePost);

        String userEmail = currentUser.getEmail();
        if (userEmail == null) {
            Toast.makeText(getContext(), "Adresse e‐mail introuvable", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1) Fetch the User document by email, so we can read its ID and role
        userService.get(Filter.equalTo("email", userEmail), users -> {
            if (users.isEmpty()) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(),
                                "Utilisateur non trouvé dans la base", Toast.LENGTH_SHORT).show()
                );
                return;
            }

            User me = users.get(0);
            Long role = me.getRole();
            String myDocId = me.getId(); // Firestore document ID for this User

            if (role != null && role == 1L) {
                // → Professor path (use the user's document ID as professorId)
                loadProfessorCourses(myDocId);
            } else {
                // → Student path
                loadStudentCourses(userEmail);
            }
        });
    }

    // ─── Professor: load all courses where Course.professorId == this user's doc ID ─────────────
    private void loadProfessorCourses(String professorDocId) {
        Filter courseFilter = Filter.equalTo("professorId", professorDocId);
        courseService.get(courseFilter, courses -> {
            allCourses.clear();
            for (Course c : courses) {
                allCourses.add(c.getCode());
            }

            requireActivity().runOnUiThread(() -> {
                if (allCourses.isEmpty()) {
                    tvCourseHeader.setText("Vous n'enseignez aucun cours");
                    btnCreatePost.setVisibility(View.GONE);
                    rvCoursesSide.setVisibility(View.GONE);
                    rvPosts.setVisibility(View.GONE);
                } else {
                    setupSidePanel();

                    // Load posts for the first course by default
                    currentCourseId = allCourses.get(0);
                    tvCourseHeader.setText("Cours : " + currentCourseId);
                    loadPostsForCourse(currentCourseId);

                    btnCreatePost.setVisibility(View.VISIBLE);
                    btnCreatePost.setOnClickListener(click -> showCreatePostDialog());
                }

                // Always attach the bottom nav‐bar
                getChildFragmentManager().beginTransaction()
                        .replace(R.id.bottom_navigation_container, new NavBarFragment())
                        .commit();
            });
        });
    }

    // ─── Student: load all courses where StudentCourse.studentEmail == currentUserEmail ──────────
    private void loadStudentCourses(String userEmail) {
        Filter scFilter = Filter.equalTo("studentEmail", userEmail);
        studentCourseService.get(scFilter, studentCourses -> {
            allCourses.clear();
            for (StudentCourse sc : studentCourses) {
                allCourses.add(sc.getCourseCode());
            }

            requireActivity().runOnUiThread(() -> {
                if (allCourses.isEmpty()) {
                    tvCourseHeader.setText("Inscrit dans aucun cours");
                    btnCreatePost.setVisibility(View.GONE);
                    rvCoursesSide.setVisibility(View.GONE);
                    rvPosts.setVisibility(View.GONE);
                } else {
                    setupSidePanel();

                    // Load posts for the first course by default
                    currentCourseId = allCourses.get(0);
                    tvCourseHeader.setText("Cours : " + currentCourseId);
                    loadPostsForCourse(currentCourseId);

                    btnCreatePost.setVisibility(View.VISIBLE);
                    btnCreatePost.setOnClickListener(click -> showCreatePostDialog());
                }

                // Always attach the bottom nav‐bar
                getChildFragmentManager().beginTransaction()
                        .replace(R.id.bottom_navigation_container, new NavBarFragment())
                        .commit();
            });
        });
    }

    /**
     * Sets up the left‐side RecyclerView with allCourses. Clicking a course reloads posts.
     */
    private void setupSidePanel() {
        rvCoursesSide.setLayoutManager(new LinearLayoutManager(getContext()));
        courseAdapter = new SideCourseAdapter(allCourses, selectedCourse -> {
            currentCourseId = selectedCourse;
            tvCourseHeader.setText("Cours : " + selectedCourse);
            loadPostsForCourse(selectedCourse);
        });
        rvCoursesSide.setAdapter(courseAdapter);
    }

    /**
     * Fetches posts for a given courseId (student or professor), sorts them by "createdAt",
     * and updates the right‐side RecyclerView.
     */
    private void loadPostsForCourse(String courseId) {
        // 1) Prepare an empty list in case it isn’t already present
        allPosts.put(courseId, new ArrayList<>());

        // 2) Query Firestore: Filter by courseId, sorted by createdAt ascending
        Filter filterPosts = Filter.equalTo("courseId", courseId);
        postService.getSorted(filterPosts, "createdAt", posts -> {
            allPosts.get(courseId).clear();
            allPosts.get(courseId).addAll(posts);

            requireActivity().runOnUiThread(() -> {
                setupPostsRecycler(courseId);
            });
        });
    }

    /**
     * Configures the right‐side RecyclerView (rvPosts) using a new PostAdapter
     * for the given courseId’s list of posts.
     */
    private void setupPostsRecycler(String courseId) {
        rvPosts.setLayoutManager(new LinearLayoutManager(getContext()));
        List<Post> postsForThisCourse = allPosts.getOrDefault(courseId, new ArrayList<>());
        postAdapter = new PostAdapter(postsForThisCourse, post -> {
            ThreadDetailFragment detail = ThreadDetailFragment.newInstance(
                    post.getId(),
                    post.getUserEmail(),
                    courseId,
                    post.getTitle(),
                    post.getContent(),
                    post.getCreatedAt()
            );
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, detail)
                    .addToBackStack(null)
                    .commit();
        });
        rvPosts.setAdapter(postAdapter);
    }

    /**
     * Shows a dialog (title + content). On confirmation, creates a new Post in Firestore
     * under currentCourseId (for both students and professors), then refreshes the post list.
     */
    private void showCreatePostDialog() {
        if (currentCourseId == null) {
            Toast.makeText(getContext(),
                    "Aucun cours sélectionné", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_create_post, null);

        final EditText etTitle   = dialogView.findViewById(R.id.etPostTitle);
        final EditText etContent = dialogView.findViewById(R.id.etPostContent);

        new AlertDialog.Builder(requireContext())
                .setTitle("Nouveau post")
                .setView(dialogView)
                .setPositiveButton("Créer", (dialog, which) -> {
                    String title = etTitle.getText().toString().trim();
                    String content = etContent.getText().toString().trim();

                    if (TextUtils.isEmpty(title)) {
                        Toast.makeText(getContext(),
                                "Le titre ne peut pas être vide.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (TextUtils.isEmpty(content)) {
                        Toast.makeText(getContext(),
                                "Le contenu ne peut pas être vide.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Build new Post object
                    Post newPost = new Post();
                    newPost.setTitle(title);
                    newPost.setContent(content);
                    newPost.setCourseId(currentCourseId);
                    newPost.setUserEmail(currentUser.getEmail());
                    newPost.setCreatedAt(Timestamp.now());

                    // Write to Firestore
                    postService.create(newPost, createdPost -> {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(),
                                    "Post créé avec succès.", Toast.LENGTH_SHORT).show();
                            loadPostsForCourse(currentCourseId);
                        });
                    });
                })
                .setNegativeButton("Annuler", (dialog, which) -> dialog.dismiss())
                .show();
    }

    // ——— Adapter for the side‐panel (list of course codes) ———
    private static class SideCourseAdapter
            extends RecyclerView.Adapter<SideCourseAdapter.VH> {

        interface OnCourseClick { void onCourse(String courseCode); }

        private final List<String> courses;
        private final OnCourseClick listener;

        SideCourseAdapter(List<String> courses, OnCourseClick listener) {
            this.courses = courses;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_course_side, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            String course = courses.get(position);
            holder.tv.setText(course);
            holder.itemView.setOnClickListener(x -> listener.onCourse(course));
        }

        @Override
        public int getItemCount() {
            return courses.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tv;
            VH(@NonNull View itemView) {
                super(itemView);
                tv = itemView.findViewById(R.id.tvCourseSide);
            }
        }
    }

    // ——— Adapter for the posts list ———
    private static class PostAdapter
            extends RecyclerView.Adapter<PostAdapter.VH> {

        interface OnPostClick { void onPost(Post post); }

        private final List<Post> posts;
        private final OnPostClick listener;
        private final SimpleDateFormat dateFormat =
                new SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault());

        PostAdapter(List<Post> posts, OnPostClick listener) {
            this.posts = posts;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_post, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Post p = posts.get(pos);
            h.tvTitle.setText(p.getTitle());

            String content = p.getContent() != null ? p.getContent() : "";
            String snippet = content.length() > 50
                    ? content.substring(0, 50) + "…"
                    : content;
            h.tvSnippet.setText(snippet);

            if (p.getCreatedAt() != null) {
                String dateText = dateFormat.format(p.getCreatedAt().toDate());
                h.tvDate.setText(dateText);
            } else {
                h.tvDate.setText("");
            }

            h.itemView.setOnClickListener(x -> listener.onPost(p));
        }

        @Override
        public int getItemCount() {
            return posts.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvTitle, tvSnippet, tvDate;
            VH(@NonNull View itemView) {
                super(itemView);
                tvTitle   = itemView.findViewById(R.id.tvPostTitle);
                tvSnippet = itemView.findViewById(R.id.tvPostSnippet);
                tvDate    = itemView.findViewById(R.id.tvPostDate);
            }
        }
    }
}
