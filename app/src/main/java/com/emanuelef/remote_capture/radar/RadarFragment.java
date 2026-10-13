package com.emanuelef.remote_capture.radar;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class RadarFragment extends Fragment {
    private TextView status;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (24 * d);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, pad, pad, pad);

        status = new TextView(requireContext());
        status.setTextSize(16);
        status.setPadding(0, 0, 0, pad);
        root.addView(status);

        Button open = new Button(requireContext());
        open.setText("Открыть радар");
        open.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), RadarActivity.class)));
        root.addView(open, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button overlay = new Button(requireContext());
        overlay.setText("Оверлей");
        overlay.setOnClickListener(v -> {
            Intent i = new Intent(requireContext(), RadarActivity.class);
            i.putExtra("overlay", true);
            startActivity(i);
        });
        root.addView(overlay, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (status != null)
            status.setText(RadarRunner.isRunning() ? "Радар запущен" : "Радар остановлен");
    }
}
