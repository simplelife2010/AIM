package de.db.aim;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * A simple {@link Fragment} subclass.
 */
public class ServiceStatusFragment extends Fragment {

    private static final String TAG = ServiceStatusFragment.class.getSimpleName();

    private List<Map<String, String>> mServiceStatus;

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String service = intent.getStringExtra("service");
            String status = intent.getStringExtra("status");
            Log.d(TAG, "Received from: " + service + " status: " + status);
            for (int i = 0; i < mServiceStatus.size(); i++) {
                if (mServiceStatus.get(i).get("service").equals(service)) {
                    mServiceStatus.remove(i);
                }
            }
            Map<String, String> statusMap = new HashMap<String, String>();
            statusMap.put("service", service);
            statusMap.put("status", status);
            mServiceStatus.add(statusMap);
            Collections.sort(mServiceStatus, new Comparator<Map<String, String>>() {
                @Override
                public int compare(Map<String, String> lhs, Map<String, String> rhs) {
                    return lhs.get("service").compareTo(rhs.get("service"));
                }
            });
            SimpleAdapter adapter = new SimpleAdapter(getContext(),
                    mServiceStatus,
                    android.R.layout.simple_list_item_2,
                    new String[] {"service", "status"},
                    new int[] {android.R.id.text1, android.R.id.text2});
            ListView listView = (ListView) getView().findViewById(R.id.list_view);
            listView.setAdapter(adapter);
        }
    };

    public ServiceStatusFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_service_status, container, false);

        mServiceStatus = new ArrayList<Map<String, String>>();

        LocalBroadcastManager.getInstance(getContext()).registerReceiver(
                mMessageReceiver, new IntentFilter("service-status"));

        SimpleAdapter adapter = new SimpleAdapter(getContext(),
                mServiceStatus,
                android.R.layout.simple_list_item_2,
                new String[] {"service", "status"},
                new int[] {android.R.id.text1, android.R.id.text2});
        ListView listView = (ListView) view.findViewById(R.id.list_view);
        listView.setAdapter(adapter);
        return view;
    }

    @Override
    public void onDestroyView() {
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(
                mMessageReceiver);

        super.onDestroyView();
    }
}
