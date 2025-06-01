package com.example.educonnect.fragments;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

import com.example.educonnect.R;
import com.example.educonnect.entities.Course;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CoursesAdapter extends BaseExpandableListAdapter {
    private final Context ctx;
    private final List<String> semesters;
    private final Map<String, List<Course>> data;

    public CoursesAdapter(Context ctx, List<String> semesters, Map<String, List<Course>> data) {
        this.ctx = ctx;
        this.semesters = semesters;
        this.data = data;
    }

    @Override
    public int getGroupCount() {
        return semesters.size();
    }

    @Override
    public int getChildrenCount(int i) {
        return Objects.requireNonNull(data.get(semesters.get(i))).size();
    }

    @Override
    public Object getGroup(int i) {
        return semesters.get(i);
    }

    @Override
    public Object getChild(int g, int c) {
        return Objects.requireNonNull(data.get(semesters.get(g))).get(c);
    }

    @Override
    public long getGroupId(int i) {
        return i;
    }

    @Override
    public long getChildId(int g, int c) {
        return c;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(ctx).inflate(R.layout.item_semester_header, parent, false);
        }
        TextView tv = convertView.findViewById(R.id.tvSemester);
        tv.setText(semesters.get(groupPosition));
        return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition,
                             boolean isLastChild, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(ctx).inflate(R.layout.item_course_card, parent, false);
        }
        Course course = (Course) getChild(groupPosition, childPosition);
        TextView code = convertView.findViewById(R.id.tvCourseCode);
        TextView name = convertView.findViewById(R.id.tvCourseName);
        code.setText(course.getCode());
        name.setText(course.getName());
        return convertView;
    }

    @Override
    public boolean isChildSelectable(int i, int i1) {
        return true;
    }
}
