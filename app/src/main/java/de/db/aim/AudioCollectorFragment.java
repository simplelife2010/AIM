package de.db.aim;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;


/**
 * A simple {@link Fragment} subclass.
 */
public class AudioCollectorFragment extends Fragment {


    public AudioCollectorFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_audio_collector, container, false);
        WebView webView = (WebView) view.findViewById(R.id.waveforms);
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("http://google.com");
        return view;
    }

}
