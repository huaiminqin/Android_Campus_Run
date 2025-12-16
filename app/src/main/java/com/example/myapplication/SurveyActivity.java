package com.example.myapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.api.ApiCallback;
import com.example.myapplication.api.AuthManager;
import com.example.myapplication.api.SurveyApi;
import com.example.myapplication.api.SurveyRequest;
import com.example.myapplication.api.UserInfo;

import java.util.Locale;

public class SurveyActivity extends AppCompatActivity {

    private RadioGroup rgFrequency, rgPurpose;
    private SeekBar sbSatisfaction;
    private TextView tvSatisfactionValue;
    private EditText etSuggestion;
    private Button btnSubmit;
    private ProgressBar progressBar;
    private DatabaseHelper dbHelper;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_survey);

        dbHelper = DatabaseHelper.getInstance(this);
        authManager = AuthManager.getInstance(this);
        initViews();
    }

    private void initViews() {
        rgFrequency = findViewById(R.id.rg_frequency);
        rgPurpose = findViewById(R.id.rg_purpose);
        sbSatisfaction = findViewById(R.id.sb_satisfaction);
        tvSatisfactionValue = findViewById(R.id.tv_satisfaction_value);
        etSuggestion = findViewById(R.id.et_suggestion);
        btnSubmit = findViewById(R.id.btn_submit_survey);
        Button btnBack = findViewById(R.id.btn_back);
        progressBar = findViewById(R.id.progress_bar);

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

        // 获取当前用户ID
        UserInfo user = authManager.getCurrentUser();
        int userId = user != null && user.getId() != null ? user.getId() : 1;

        // 保存到本地SQLite数据库
        long localResult = dbHelper.insertSurveyResult(
            userId,
            frequency,
            purpose,
            satisfaction,
            suggestion
        );

        // 提交到服务器
        setLoading(true);
        
        final String finalFrequency = frequency;
        final String finalPurpose = purpose;
        
        SurveyRequest request = new SurveyRequest(userId, frequency, purpose, satisfaction, suggestion);
        SurveyApi.getInstance().submitSurvey(request, new ApiCallback<Object>() {
            @Override
            public void onSuccess(Object data) {
                setLoading(false);
                String msg = String.format(Locale.CHINA,
                    "问卷提交成功!\n跑步频率: %s\n跑步目的: %s\n满意度: %d分",
                    finalFrequency, finalPurpose, satisfaction);
                Toast.makeText(SurveyActivity.this, msg, Toast.LENGTH_LONG).show();
                resetAndFinish();
            }

            @Override
            public void onError(int code, String message) {
                setLoading(false);
                // 服务器提交失败，但本地已保存
                Toast.makeText(SurveyActivity.this, 
                    "已保存到本地，服务器提交失败: " + message, Toast.LENGTH_LONG).show();
                resetAndFinish();
            }

            @Override
            public void onNetworkError(Exception e) {
                setLoading(false);
                // 网络错误，但本地已保存
                Toast.makeText(SurveyActivity.this, 
                    "已保存到本地，网络连接失败", Toast.LENGTH_LONG).show();
                resetAndFinish();
            }
        });
    }

    private void setLoading(boolean loading) {
        btnSubmit.setEnabled(!loading);
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void resetAndFinish() {
        rgFrequency.clearCheck();
        rgPurpose.clearCheck();
        sbSatisfaction.setProgress(5);
        etSuggestion.setText("");
        finish();
    }
}
