// File: app/src/main/java/com/example/educonnect/fragments/MessageAdapter.java
package com.example.educonnect.fragments;

import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.educonnect.R;
import com.example.educonnect.entities.Message;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Binds each Firestore‐backed Message entity to a RecyclerView row (item_message.xml).
 * Tracks per‐row selection state in a Map<messageId, Boolean>. Exposes a method to reset
 * that map whenever the backing list is re‐filtered.
 *
 * Also formats createdAt timestamps as “EEE dd MMM HH:mm” in French (e.g. “Sam 31 mai 20:18”).
 */
public class MessageAdapter
        extends RecyclerView.Adapter<MessageAdapter.VH> {

    public interface OnMessageClickListener {
        void onMessageClick(Message message);
    }

    public interface OnMessageActionListener {
        void onDelete(Message message);
        void onToggleImportant(Message message);
    }

    private final List<Message> items;
    private final OnMessageClickListener clickListener;
    private final OnMessageActionListener actionListener;

    /** Maps messageId → whether that row is currently checked/selected. */
    private final Map<String, Boolean> selectedMap = new HashMap<>();

    /**
     * Date formatter for French abbreviated day/month, e.g. "sam. 31 mai 20:18".
     * We'll strip off the trailing period and capitalize the first letter.
     */
    private final SimpleDateFormat dateFormatter =
            new SimpleDateFormat("EEE dd MMM HH:mm", Locale.FRENCH);

    public MessageAdapter(
            List<Message> items,
            OnMessageClickListener clickListener,
            OnMessageActionListener actionListener) {
        this.items = items;
        this.clickListener = clickListener;
        this.actionListener = actionListener;

        // Initialize selection state for every message to “false”
        resetSelection();
    }

    /**
     * Select or deselect every message in the adapter.
     * @param select True to check all, false to uncheck all.
     */
    public void selectAll(boolean select) {
        for (Message m : items) {
            selectedMap.put(m.getId(), select);
        }
        notifyDataSetChanged();
    }

    /**
     * Expose the selected‐states map so the fragment can implement bulk actions.
     */
    public Map<String, Boolean> getSelectedMap() {
        return selectedMap;
    }

    /**
     * Re‐initializes selectedMap so that every messageId in `items` is set to false.
     * Call this anytime you’ve changed `items` (e.g. after re‐filtering).
     */
    public void resetSelection() {
        selectedMap.clear();
        for (Message m : items) {
            selectedMap.put(m.getId(), false);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Message m = items.get(pos);

        // 1) Subject / Snippet
        h.tvSubject.setText(m.getSubject());
        h.tvSnippet.setText(m.getContent());

        // 2) Format the createdAt timestamp to French “EEE dd MMM HH:mm”
        if (m.getCreatedAt() != null) {
            String raw = dateFormatter.format(m.getCreatedAt().toDate());
            // raw might be like "sam. 31 mai 20:18"
            // Remove the dot after the day abbreviation (e.g. "sam." → "sam")
            String noDot = raw.replaceFirst("\\.", "");
            // Capitalize the first letter (e.g. "sam 31 mai 20:18" → "Sam 31 mai 20:18")
            String formatted = noDot.substring(0, 1).toUpperCase() + noDot.substring(1);
            h.tvDate.setText(formatted);
        } else {
            h.tvDate.setText("");
        }

        // 3) Checkbox state
        Boolean isSelected = selectedMap.get(m.getId());
        h.cb.setChecked(isSelected != null && isSelected);

        // 4) “Important” star icon
        boolean isImportant = m.getImportant() != null && m.getImportant();
        h.ivImportant.setVisibility(isImportant ? View.VISIBLE : View.GONE);

        // 5) Overflow menu (three‐dot) logic
        h.btnMenu.setOnClickListener(view -> {
            PopupMenu popup = new PopupMenu(view.getContext(), view);
            popup.inflate(R.menu.message_item_menu);

            // Show “Mark as Important” only if not yet important, else “Unmark as Important”
            MenuItem markItem   = popup.getMenu().findItem(R.id.action_mark_important);
            MenuItem unmarkItem = popup.getMenu().findItem(R.id.action_unmark_important);
            markItem.setVisible(!isImportant);
            unmarkItem.setVisible(isImportant);

            popup.setOnMenuItemClickListener(menuItem -> {
                int id = menuItem.getItemId();
                if (id == R.id.action_delete) {
                    actionListener.onDelete(m);
                    return true;
                }
                if (id == R.id.action_mark_important || id == R.id.action_unmark_important) {
                    actionListener.onToggleImportant(m);
                    return true;
                }
                return false;
            });
            popup.show();
        });

        // 6) Entire row click → open detail
        h.rootView.setOnClickListener(view -> {
            clickListener.onMessageClick(m);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class VH extends RecyclerView.ViewHolder {
        final View      rootView;
        final CheckBox  cb;
        final TextView  tvSubject, tvSnippet, tvDate;
        final ImageView ivImportant;
        final ImageButton btnMenu;

        VH(@NonNull View v) {
            super(v);
            rootView    = v;
            cb          = v.findViewById(R.id.cbMessage);
            tvSubject   = v.findViewById(R.id.tvSubject);
            tvSnippet   = v.findViewById(R.id.tvSnippet);
            tvDate      = v.findViewById(R.id.tvDate);
            ivImportant = v.findViewById(R.id.ivImportant);
            btnMenu     = v.findViewById(R.id.btnMenu);

            // When the row's checkbox is toggled, flip the boolean in selectedMap
            cb.setOnClickListener(x -> {
                int pos = getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                Message msg = items.get(pos);
                boolean newVal = cb.isChecked();
                selectedMap.put(msg.getId(), newVal);
            });
        }
    }
}
