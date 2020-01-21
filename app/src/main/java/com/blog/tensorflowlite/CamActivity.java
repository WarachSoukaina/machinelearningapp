package com.blog.tensorflowlite;

import android.app.Activity;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class CamActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cam);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction().
                    replace(R.id.container, MainCameraFragment.newInstance())
                    .commit();
        }

    }



    }

