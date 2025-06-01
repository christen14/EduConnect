package com.example.educonnect.fragments;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.educonnect.R;
import com.example.educonnect.entities.Exam;
import com.example.educonnect.entities.StudentExam;
import com.example.educonnect.services.ExamService;
import com.example.educonnect.services.StudentsExamsService;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.Filter;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ExamsFragment displays a list of "exam convocations" for the current user
 */
public class ExamsFragment extends Fragment {
    private final SimpleDateFormat dateFmt =
            new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
    private final SimpleDateFormat timeFmt =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    private RecyclerView rvExams;
    private TextView tvLoading;   // optional: show "Loading..." until data arrives
    private final StudentsExamsService studentsExamService = new StudentsExamsService();
    private final ExamService examService = new ExamService();
    private FirebaseAuth mAuth = FirebaseAuth.getInstance();

    // Will hold the combined data to display
    private List<ExamUI> examUIList = new ArrayList<>();
    private ExamsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle saved) {
        return inflater.inflate(R.layout.fragment_exams, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        rvExams = v.findViewById(R.id.rvExams);
        tvLoading = v.findViewById(R.id.tvExamsTitle);

        rvExams.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ExamsAdapter(examUIList);
        rvExams.setAdapter(adapter);

        // Start fetching data
        fetchStudentExams();

        // PDF button logic (placeholder)
        v.findViewById(R.id.btnDownloadPdf).setOnClickListener(x -> {
            generatePdfFromCurrentList();
            File f = new File("/storage/emulated/0/Android/data/com.example.educonnect/files/Convocations_Exams.pdf");
            openGeneratedPdf(f);
        });

        // Bottom nav‐bar
        getChildFragmentManager().beginTransaction()
                .replace(R.id.bottom_navigation_container, new NavBarFragment())
                .commit();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void fetchStudentExams() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(getContext(), getString(R.string.non_connecte), Toast.LENGTH_SHORT).show();
            return;
        }

        String studentEmail = currentUser.getEmail();
        if (studentEmail == null || studentEmail.isEmpty()) {
            Toast.makeText(getContext(), getString(R.string.email_invalide), Toast.LENGTH_SHORT).show();
            return;
        }

        // Show a “Loading…” message in the title TextView
        tvLoading.setText(R.string.chargement_convocations);

        // 1) Query StudentExam where studentEmail == currentUser.email
        Filter seFilter = Filter.equalTo("studentEmail", studentEmail);
        studentsExamService.get(seFilter, studentExamList -> {
            // If no StudentExam entries, update UI and return
            if (studentExamList.isEmpty()) {
                requireActivity().runOnUiThread(() -> {
                    tvLoading.setText(R.string.aucune_convocation);
                    examUIList.clear();
                    adapter.notifyDataSetChanged();
                });
                return;
            }

            // Prepare to fetch each Exam by its document ID (examId)
            examUIList.clear();
            final int totalExams = studentExamList.size();
            // Using a 1‐element array so we can mutate within inner classes
            final int[] completedCount = {0};

            for (StudentExam se : studentExamList) {
                String examId = se.getExamId();
                if (examId == null || examId.isEmpty()) {
                    // Skip any invalid examId, but still count it as “completed”
                    synchronized (completedCount) {
                        completedCount[0]++;
                        if (completedCount[0] == totalExams) {
                            // All “fetch attempts” are done (even if skipped)
                            requireActivity().runOnUiThread(() -> {
                                adapter.notifyDataSetChanged();
                                tvLoading.setText(R.string.convocation_examens);
                            });
                        }
                    }
                    continue;
                }

                // 2) Directly fetch the Exam document by its Firestore document ID
                FirebaseFirestore.getInstance()
                        .collection("exams")
                        .document(examId)
                        .get()
                        .addOnSuccessListener(docSnap -> {
                            if (docSnap.exists()) {
                                Exam e = docSnap.toObject(Exam.class);
                                if (e != null) {
                                    // Build an ExamUI from the combined data
                                    ExamUI ui = new ExamUI();
                                    ui.courseCode = e.getCourseId();
                                    ui.date = e.getStartTime();
                                    ui.startTime = e.getStartTime();
                                    ui.durationMinutes = e.getDuration();
                                    ui.room = se.getRoom();
                                    ui.seatNumber = se.getSeat();

                                    synchronized (examUIList) {
                                        examUIList.add(ui);
                                    }
                                }
                            }
                            // After handling this document (whether found or not), increment counter
                            synchronized (completedCount) {
                                completedCount[0]++;
                                if (completedCount[0] == totalExams) {
                                    // All fetches are done → update UI on main thread
                                    requireActivity().runOnUiThread(() -> {
                                        adapter.notifyDataSetChanged();
                                        tvLoading.setText(R.string.convocation_examens);
                                    });
                                }
                            }
                        })
                        .addOnFailureListener(e -> {
                            // Log or show a Toast if you like
                            synchronized (completedCount) {
                                completedCount[0]++;
                                if (completedCount[0] == totalExams) {
                                    requireActivity().runOnUiThread(() -> {
                                        adapter.notifyDataSetChanged();
                                        tvLoading.setText(R.string.convocation_examens);
                                    });
                                }
                            }
                        });
            }
        });
    }


    /**
     * Simple data model for what we display in each row:
     * courseCode, date, startTime, duration (min), room, seatNumber
     */
    private static class ExamUI {
        String courseCode;
        Timestamp date;         // or com.google.firebase.Timestamp
        Timestamp startTime;    // or Timestamp
        Long durationMinutes;
        String room;
        Long seatNumber;
    }

    /**
     * RecyclerView Adapter to bind a list of ExamUI to item_exam_row.xml
     **/
    private static class ExamsAdapter
            extends RecyclerView.Adapter<ExamsAdapter.VH> {

        private final List<ExamUI> data;
        private final SimpleDateFormat dateFmt =
                new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
        private final SimpleDateFormat timeFmt =
                new SimpleDateFormat("HH:mm", Locale.getDefault());

        ExamsAdapter(List<ExamUI> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_exam_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ExamUI ui = data.get(position);
            holder.tvCourse.setTextSize(13);
            holder.tvDate.setTextSize(13);
            holder.tvStart.setTextSize(13);
            holder.tvDuration.setTextSize(13);
            holder.tvRoom.setTextSize(13);
            holder.tvSeat.setTextSize(13);

            holder.tvCourse.setText(ui.courseCode);
            holder.tvDate.setText(dateFmt.format(ui.startTime.toDate()));
            holder.tvStart.setText(timeFmt.format(ui.startTime.toDate()));
            holder.tvDuration.setText(String.valueOf(ui.durationMinutes));
            holder.tvRoom.setText(ui.room != null ? ui.room : "");
            if (ui.seatNumber != null) {
                holder.tvSeat.setText(String.valueOf(ui.seatNumber));
            } else {
                holder.tvSeat.setText("");
            }
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvCourse, tvDate, tvStart, tvDuration, tvRoom, tvSeat;

            VH(@NonNull View itemView) {
                super(itemView);
                tvCourse = itemView.findViewById(R.id.tvItemCourse);
                tvDate = itemView.findViewById(R.id.tvItemDate);
                tvStart = itemView.findViewById(R.id.tvItemStart);
                tvDuration = itemView.findViewById(R.id.tvItemDuration);
                tvRoom = itemView.findViewById(R.id.tvItemRoom);
                tvSeat = itemView.findViewById(R.id.tvItemSeat);
            }
        }
    }

    private void generatePdfFromCurrentList() {
        if (examUIList.isEmpty()) {
            Toast.makeText(getContext(), getString(R.string.aucune_donnee), Toast.LENGTH_SHORT).show();
            return;
        }

        // 1) Create a PdfDocument
        PdfDocument pdfDoc = new PdfDocument();

        // 2) Basic Paint to draw text
        Paint paint = new Paint();
        paint.setTextSize(12);

        // 3) Page sizing—595×842 is roughly A4 at 72 PPI in portrait
        final int pageWidth = 595;
        final int pageHeight = 842;

        int pageNumber = 1;
        int yPosition = 30;      // vertical cursor (in points)

        // 4) Start first page
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
        PdfDocument.Page page = pdfDoc.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        // 5) Draw a title at the top
        paint.setTextSize(16);
        canvas.drawText(getString(R.string.convocation_examens), 20, yPosition, paint);
        yPosition += 30;

        // 6) Draw column headers
        paint.setTextSize(12);
        int colX = 20;      // starting X for first column
        int colWidth = 90;  // width allotted to each column
        String[] headers = {getString(R.string.cours), getString(R.string.date),
                getString(R.string.debut), getString(R.string.duree),
                getString(R.string.salle), getString(R.string.num_place)};
        for (int i = 0; i < headers.length; i++) {
            canvas.drawText(headers[i], colX + (colWidth * i), yPosition, paint);
        }
        yPosition += 20;

        // 7) Draw a separator line
        canvas.drawLine(20, yPosition, pageWidth - 20, yPosition, paint);
        yPosition += 20;

        // 8) Iterate over examUIList and write each row
        for (int idx = 0; idx < examUIList.size(); idx++) {
            ExamUI ui = examUIList.get(idx);

            // If we’re near bottom of page, finish and start a new one
            if (yPosition + 20 > pageHeight - 20) {
                pdfDoc.finishPage(page);
                pageNumber++;
                pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                page = pdfDoc.startPage(pageInfo);
                canvas = page.getCanvas();
                yPosition = 30;

                // (Optional) redraw headers on new page
                paint.setTextSize(16);
                canvas.drawText(getString(R.string.convocation_examens_suite), 20, yPosition, paint);
                yPosition += 30;
                paint.setTextSize(12);

                for (int i = 0; i < headers.length; i++) {
                    canvas.drawText(headers[i], colX + (colWidth * i), yPosition, paint);
                }
                yPosition += 20;
                canvas.drawLine(20, yPosition, pageWidth - 20, yPosition, paint);
                yPosition += 20;
            }

            // Convert Firebase Timestamp to a java.util.Date if needed
            // In your code, you used `ui.startTime` (a Timestamp). Format accordingly:
            String dateText = (ui.startTime != null)
                    ? dateFmt.format(ui.startTime.toDate())
                    : "";
            String timeText = (ui.startTime != null)
                    ? timeFmt.format(ui.startTime.toDate())
                    : "";

            // Duration as minutes
            String durationText = (ui.durationMinutes != null)
                    ? ui.durationMinutes.toString()
                    : "";

            String roomText = (ui.room != null) ? ui.room : "";
            String seatText = (ui.seatNumber != null) ? ui.seatNumber.toString() : "";

            // Draw each field in its column
            paint.setTextSize(12);
            canvas.drawText(ui.courseCode, colX + (colWidth * 0), yPosition, paint);
            canvas.drawText(dateText, colX + (colWidth * 1), yPosition, paint);
            canvas.drawText(timeText, colX + (colWidth * 2), yPosition, paint);
            canvas.drawText(durationText, colX + (colWidth * 3), yPosition, paint);
            canvas.drawText(roomText, colX + (colWidth * 4), yPosition, paint);
            canvas.drawText(seatText, colX + (colWidth * 5), yPosition, paint);

            yPosition += 20;
        }

        // 9) Finish the last page
        pdfDoc.finishPage(page);

        // 10) Write the PDF file to external‐files directory (private to this app)
        File outDir = requireContext().getExternalFilesDir(null);
        if (outDir == null) {
            Toast.makeText(getContext(), getString(R.string.impossible_stockage), Toast.LENGTH_SHORT).show();
            pdfDoc.close();
            return;
        }
        File outFile = new File(outDir, "Convocations_Exams.pdf");

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            pdfDoc.writeTo(fos);
            Toast.makeText(getContext(),
                    getString(R.string.pdf_genere) + outFile.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(),
                    getString(R.string.erreur_pdf) + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        } finally {
            pdfDoc.close();
        }
    }

    private void openGeneratedPdf(File pdfFile) {
        Uri uri = Uri.fromFile(pdfFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(),
                    getString(R.string.pas_dapplication), Toast.LENGTH_LONG).show();
        }
    }
}
