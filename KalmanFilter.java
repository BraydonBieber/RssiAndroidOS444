package com.example.rssi;

public class KalmanFilter {

    //Kalaman Terms
    double estimate;
    double error_est;
    double error_meas;
    double k_gain;

    //Continous Average Terms
    double average;
    double prev_size;
    double cur_size;

    //Initialize KalmanFilter
    KalmanFilter(int currentDistance, int initDistance, int nEvironmentLoss, int initRSSI ){
        estimate = (-10*2 * Math.log10(currentDistance/initDistance) + initRSSI); // Model 10n * log(d/d0) + A
        error_est = 3; // By observing model vs averaged results recorded from previous tests
        error_meas = 10; // Safe value by observing the fluctuation compared to averages as well as compared to model
        average = estimate; // Guessing one data point with the model
        prev_size = 1; // start after first data point
        cur_size = 2;
    }

    int getNewEst(int measurement){
        k_gain = error_est / (error_est + error_meas);
        estimate = estimate + k_gain*(measurement - estimate);
        error_est = (1- k_gain) * error_est;
        return (int)estimate;
    }

    int getNewAvg(int measurement){
        average = (prev_size*average + measurement)/cur_size;
        prev_size++;
        cur_size++;
        return (int)average;
    }
}