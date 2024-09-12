package com.example.myfoot;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
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

    private static final int PERMISSION_REQUEST_ACTIVITY_RECOGNITION = 1;
    private static final int PERMISSION_REQUEST_LOCATION = 2;
    private static final float TARGET_DISTANCE_IN_METERS = 30.0f;  // 30m
    private static final float TOTAL_DISTANCE_FOR_ESTIMATION = 100.0f;  // 100m

    private TextView tvStepCount;
    private TextView tvDistanceCovered;
    private TextView tvStepEstimation;
    private TextView tvLocation;
    private SensorManager sensorManager;
    private Sensor stepCounterSensor;
    private int initialStepCount = -1;
    private int currentStepCount = 0; //현재까지 걸은 총 걸음 수 저장
    private boolean isCalibrationComplete = false;
    private float stepLengthInMeters = 0.0f;
    private Location previousLocation = null; // 이전 위치 저장
    private float totalDistanceCovered = 0.0f; // 전체 누적된 거리를 저장할 변수

    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStepCount = findViewById(R.id.tvStepCount);
        tvDistanceCovered = findViewById(R.id.tvDistanceCovered);
        tvStepEstimation = findViewById(R.id.tvStepEstimation);
        tvLocation = findViewById(R.id.tvLocation);
        Button btnReset = findViewById(R.id.btnReset);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        btnReset.setOnClickListener(view -> resetStepCount());

        requestLocationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (stepCounterSensor != null) {
            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_UI);
        }
        getCurrentLocation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (stepCounterSensor != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            if (initialStepCount == -1) {
                // 첫 번째 값은 초기값으로 설정
                initialStepCount = (int) event.values[0];
            }

            // 현재 걸음 수를 실시간으로 업데이트
            currentStepCount = (int) event.values[0] - initialStepCount;

            // 현재 걸음 수를 UI에 업데이트
            tvStepCount.setText("현재 걸음 수 : " + currentStepCount);

            // 보정 진행 여부에 따라 처리
            if (!isCalibrationComplete) {
                updateCalibrationProgress(currentStepCount); // 보정 중
            } else {
                updateDistanceProgress(currentStepCount); // 보정 완료 후 거리 계산
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //안 씀
    }


    private void updateCalibrationProgress(int steps) {
        if (steps > 0) {
            if (stepLengthInMeters == 0.0f) {
                stepLengthInMeters = 0.7f; // 성인 평균 걸음 길이로 설정 (0.7m 예시)
            }

            float distanceCovered = steps * stepLengthInMeters;

            if (!isCalibrationComplete) {
                // 보정 진행 중, 30m 목표 거리까지 계산
                if (distanceCovered >= TARGET_DISTANCE_IN_METERS) {
                    // 보정 완료
                    stepLengthInMeters = TARGET_DISTANCE_IN_METERS / steps;
                    isCalibrationComplete = true;

                    // 보정 완료 후 30m로 유지
                    totalDistanceCovered = TARGET_DISTANCE_IN_METERS;
                } else {
                    // 보정 진행 중, 거리 업데이트
                    totalDistanceCovered = distanceCovered;
                }

                tvDistanceCovered.setText(String.format("현재 이동 미터 수: %.2fm", totalDistanceCovered));

                if (isCalibrationComplete) {
                    tvStepEstimation.setVisibility(TextView.VISIBLE);
                    updateEstimatedSteps(); // 보정 완료 후 100m 예상 걸음 수 출력
                }
            } else {
                // 보정 완료 후, 거리 계산 및 업데이트
                totalDistanceCovered = distanceCovered;
                tvDistanceCovered.setText(String.format("현재 이동 미터 수: %.2fm", totalDistanceCovered));
            }
        }
    }

    private void updateDistanceProgress(int steps) {
        if (isCalibrationComplete) {
            // 보정이 완료된 후, 걸음 수에 걸음 길이를 곱해서 거리를 계산합니다.
            float distanceCovered = steps * stepLengthInMeters;

            // 누적된 거리 업데이트
            totalDistanceCovered += distanceCovered;

            // 누적 거리 업데이트
            tvDistanceCovered.setText(String.format("현재 이동 미터 수: %.2fm", totalDistanceCovered));
        }
    }
    private void updateEstimatedSteps() {
        if (isCalibrationComplete) {
            // 100m를 걷기 위한 예상 걸음 수 계산
            int estimatedStepsFor100m = (int) (TOTAL_DISTANCE_FOR_ESTIMATION / stepLengthInMeters);
            tvStepEstimation.setText(String.format("100m 예상 걸음 수: %d", estimatedStepsFor100m));
        }
    }

    private void updateCalibrationWithLocation(int steps, Location currentLocation) {
        if (previousLocation != null) {
            // 이전 위치와 현재 위치 간의 거리를 계산하여 실제로 걸은 거리를 얻습니다.
            float[] results = new float[1];
            Location.distanceBetween(previousLocation.getLatitude(), previousLocation.getLongitude(),
                    currentLocation.getLatitude(), currentLocation.getLongitude(), results);
            float actualDistance = results[0]; // 실제 이동 거리(m)

            // GPS 기반으로 걸음 길이를 재보정하고 거리 누적
            if (steps > 0 && actualDistance > 0) {
                stepLengthInMeters = actualDistance / steps;
            }
            // GPS 기반 거리 누적
            totalDistanceCovered += actualDistance;
            tvDistanceCovered.setText(String.format("현재 이동 미터 수: %.2fm", totalDistanceCovered));
        }
        previousLocation = currentLocation; // 다음 계산을 위해 현재 위치 저장
    }
    private void resetStepCount() {
        // 걸음 수 및 초기화 상태
        initialStepCount = -1;
        currentStepCount = 0;
        stepLengthInMeters = 0.0f;
        totalDistanceCovered = 0.0f; // 누적 거리 초기화
        isCalibrationComplete = false;

        // UI 업데이트
        tvStepCount.setText("현재 걸음 수 : " + currentStepCount);
        tvDistanceCovered.setText("현재 이동 미터 수: 0m");
        tvStepEstimation.setVisibility(TextView.GONE);
        tvLocation.setText("위도: --, 경도: --"); // 위치 정보 초기화
    }

    @Override
    protected void onStart() {
        super.onStart();
        requestActivityRecognitionPermission();
    }

    private void requestActivityRecognitionPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                    PERMISSION_REQUEST_ACTIVITY_RECOGNITION);
        }
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_REQUEST_LOCATION);
        } else {
            getCurrentLocation();
        }
    }

    private void getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            LocationRequest locationRequest = LocationRequest.create()
                    .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                    .setInterval(10000)
                    .setFastestInterval(5000);

            fusedLocationClient.requestLocationUpdates(locationRequest, new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult == null) {
                        tvLocation.setText("위치 정보를 가져올 수 없습니다.");
                        return;
                    }

                    // 최신 위치를 가져옴
                    Location currentLocation = locationResult.getLastLocation();
                    if (currentLocation != null) {
                        double latitude = currentLocation.getLatitude();
                        double longitude = currentLocation.getLongitude();
                        tvLocation.setText(String.format("위도: %.4f, 경도: %.4f", latitude, longitude));

                        // 위치 기반 보정
                        updateCalibrationWithLocation(currentStepCount, currentLocation);
                    }
                }
            }, getMainLooper());

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }


}
