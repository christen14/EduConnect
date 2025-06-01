package com.example.educonnect.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.educonnect.R;

public class NavBarFragment extends Fragment {

    // Bottom navigation icons
    private ImageView navProfile, navDashboard, navBack;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_nav_bar, container, false);

        // Initialize views
        navProfile = view.findViewById(R.id.navProfile);
        navDashboard = view.findViewById(R.id.navDashboard);
        navBack = view.findViewById(R.id.navBack);

        // Set listeners for bottom navigation icons
//        navMessage.setOnClickListener(v -> startActivity(new Intent(getActivity(), MessagingActivity.class)));

        navDashboard.setOnClickListener(v -> {
            // replace whatever’s in R.id.fragment_container with your DashboardFragment
            FragmentManager fm = requireActivity().getSupportFragmentManager();
            fm.beginTransaction()
                    .replace(R.id.fragment_container, new DashboardFragment())
                    .addToBackStack(null)
                    .commit();
        });

        navProfile.setOnClickListener(v -> {
            FragmentManager fm = requireActivity().getSupportFragmentManager();
            fm.beginTransaction()
                    .replace(R.id.fragment_container, new ProfileFragment())
                    .addToBackStack(null)
                    .commit();
        });


        navBack.setOnClickListener(v -> {
            // Option A: pop the fragment back‐stack
            requireActivity()
                    .getSupportFragmentManager()
                    .popBackStack();

            // Option B: or you can simply delegate to the Activity’s back action
            // requireActivity().onBackPressed();
        });

        return view;
    }
}
