package com.example.myapplication;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class SurveyActivity extends AppCompatActivity {

    private RadioGroup rgFrequency, rgPurpose;
    private SeekBar sbSatisfaction;
    private TextView tvSatisfactionValue;
    private EditText etSuggestion;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_survey);

        dbHelper = DatabaseHelper.getInstance(this);
        initViews();
    }

    private void initViews() {
        rgFrequency = findViewById(R.id.rg_frequency);
        rgPurpose = findViewById(R.id.rg_purpose);
        sbSatisfaction = findViewById(R.id.sb_satisfaction);
        tvSatisfactionValue = findViewById(R.id.tv_satisfaction_value);
        etSuggestion = findViewById(R.id.et_suggestion);
        Button btnSubmit = findViewById(R.id.btn_submit_survey);
        Button btnBack = findViewById(R.id.btn_back);

        sbSatisfaction.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvSatisfactionValue.setText(progress + " 分");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnSubmit.setOnClickListener(v -> submitSurvey());
        btnBack.setOnClickListener(v -> finish());
    }

    private void submitSurvey() {
        int freqId = rgFrequency.getCheckedRadioButtonId();
        String frequency = "";
        if (freqId != -1) {
            RadioButton rb = findViewById(freqId);
            frequency = rb.getText().toString();
        }

        int purposeId = rgPurpose.getCheckedRadioButtonId();
        String purpose = "";
        if (purposeId != -1) {
            RadioButton rb = findViewById(purposeId);
            purpose = rb.getText().toString();
        }

        int satisfaction = sbSatisfaction.getProgress();
        String suggestion = etSuggestion.getText().toString().trim();

        if (freqId == -1 || purposeId == -1) {
            Toast.makeText(this, "请完成所有必选项", Toast.LENGTH_SHORT).show();
            return;
        }

        // 保存到SQLite数据库
        long result = dbHelper.insertSurveyResult(
            1,  // 默认用户ID
            frequency,
            purpose,
            satisfaction,
            suggestion
        );

        if (result > 0) {
            String msg = String.format(Locale.CHINA,
                "问卷提交成功!\n跑步频率: %s\n跑步目的: %s\n满意度: %d分\n建议: %s",
                frequency, purpose, satisfaction, suggestion.isEmpty() ? "无" : suggestion);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show();
            return;
        }

        // 重置并返回
        rgFrequency.clearCheck();
        rgPurpose.clearCheck();
        sbSatisfaction.setProgress(5);
        etSuggestion.setText("");
        
        finish();
    }
}
