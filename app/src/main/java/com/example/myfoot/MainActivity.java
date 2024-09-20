package com.example.myfoot;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final float TARGET_DISTANCE_IN_METERS = 30.0f;
    private static final float TOTAL_DISTANCE_FOR_ESTIMATION = 100.0f;
    private static final float DEFAULT_STEP_LENGTH = 0.7f; // 기본 평균 걸음 길이 설정 (0.7m) K

    private TextView tvStepCount;
    private TextView tvDistanceCovered;
    private TextView tvStepEstimation;
    private SensorManager sensorManager;
    private Sensor stepCounterSensor;
    private int initialStepCount = -1;
    private int currentStepCount = 0;
    private boolean isCalibrationComplete = false;
    private float stepLengthInMeters = DEFAULT_STEP_LENGTH; // 초기 걸음 길이 설정
    private float totalDistanceCovered = 0.0f;
    private int stepsAtCalibration = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI 요소 초기화
        tvStepCount = findViewById(R.id.tvStepCount);
        tvDistanceCovered = findViewById(R.id.tvDistanceCovered);
        tvStepEstimation = findViewById(R.id.tvStepEstimation);
        Button btnReset = findViewById(R.id.btnReset);

        // 센서 매니저 초기화
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        }

        // 리셋 버튼 클릭 리스너
        btnReset.setOnClickListener(view -> resetStepCount());

        // 걸음 수 센서 권한 체크 (Android 10 이상일 경우 필요)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 걸음 센서 리스너 등록
        if (stepCounterSensor != null) {
            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 걸음 센서 리스너 해제
        if (stepCounterSensor != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // 걸음 센서 데이터가 변경되었을 때 호출
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            if (initialStepCount == -1) {
                // 앱이 처음 시작될 때 초기 걸음 수 저장
                initialStepCount = (int) event.values[0];
            }

            // 현재 걸음 수 계산
            currentStepCount = (int) event.values[0] - initialStepCount;
            tvStepCount.setText("현재 걸음 수: " + currentStepCount);

            // 걸음 수에 따른 거리 업데이트
            updateProgress(currentStepCount);
        }
    }

    private void updateProgress(int steps) {
        if (steps > 0) {
            if (!isCalibrationComplete) {
                // 보정 전: 평균 걸음 길이로 거리 계산
                float distanceCovered = steps * stepLengthInMeters;
                tvDistanceCovered.setText(String.format("현재 이동 거리: %.2fm", distanceCovered));

                if (distanceCovered >= TARGET_DISTANCE_IN_METERS) {
                    completeCalibration(steps);
                }
            } else {
                // 보정 완료 후: 계산된 걸음 길이를 사용하여 거리 업데이트
                updateDistanceProgress(steps);
            }
        }
    }

    // 보정 완료 후 걸음 수에 따른 추가 거리 계산
    private void updateDistanceProgress(int steps) {
        int stepsAfterCalibration = steps - stepsAtCalibration;
        float additionalDistance = stepsAfterCalibration * stepLengthInMeters;
        totalDistanceCovered = TARGET_DISTANCE_IN_METERS + additionalDistance;

        tvDistanceCovered.setText(String.format("현재 이동 거리: %.2fm", totalDistanceCovered));
    }

    // 보정 완료 처리: 걸음 길이 재조정 후 예상 걸음 수 계산
    private void completeCalibration(int steps) {
        // 30m를 걸었을 때 현재까지의 걸음 수로 보정
        stepLengthInMeters = TARGET_DISTANCE_IN_METERS / steps; // 걸음 길이 보정
        isCalibrationComplete = true;
        stepsAtCalibration = steps;

        // 100m 예상 걸음 수 계산 및 출력
        updateEstimatedSteps();
    }

    private void updateEstimatedSteps() {
        // 보정된 걸음 길이를 사용하여 100미터를 걷는 데 필요한 걸음 수 계산
        int estimatedStepsFor100m = (int) (TOTAL_DISTANCE_FOR_ESTIMATION / stepLengthInMeters);
        tvStepEstimation.setText(String.format("100m 예상 걸음 수: %d", estimatedStepsFor100m));

        // 예상 걸음 수를 화면에 표시하기 위해 visibility 변경
        tvStepEstimation.setVisibility(View.VISIBLE);
    }

    // 걸음 수와 거리 초기화
    private void resetStepCount() {
        initialStepCount = -1;
        currentStepCount = 0;
        stepLengthInMeters = DEFAULT_STEP_LENGTH; // 기본 걸음 길이로 재설정
        totalDistanceCovered = 0.0f; // 이동 거리 초기화
        isCalibrationComplete = false;
        stepsAtCalibration = 0;

        // UI 초기화
        tvStepCount.setText("현재 걸음 수: " + currentStepCount);
        tvDistanceCovered.setText("현재 이동 거리: 0m");
        tvStepEstimation.setText("");
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 사용하지 않음
    }

    // 권한 요청 결과 처리
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // 권한이 승인되면 센서 리스너 등록
            if (stepCounterSensor != null) {
                sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_UI);
            }
        } else {
            Toast.makeText(this, "걸음 수 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
        }
    }
}
