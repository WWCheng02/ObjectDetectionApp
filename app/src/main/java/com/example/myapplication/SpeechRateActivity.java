package com.example.myapplication;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class SpeechRateActivity extends Activity {
    public float speechRateSelected;
    @Override
    protected void onCreate(final Bundle savedInstanceState) {

        super.onCreate(null);
        setContentView(R.layout.speech_rate);

        Button slowButton = findViewById(R.id.slowButton);
        Button normalButton = findViewById(R.id.normalButton);
        Button fastButton = findViewById(R.id.fastButton);

        slowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                speechRateSelected=0.5f;
                Intent intent= new Intent();
                intent.putExtra("speechRateSelected",speechRateSelected);
                setResult(RESULT_OK, intent);
                finish();
            }
        });
    }

}
