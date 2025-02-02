package edu.pmdm.delgado_victorimdbapp;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import database.SQLiteHelper;
import database.User;

public class AppLifecycleManager extends Application implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "AppLifecycleManager";
    private int activityReferences = 0;
    private boolean isActivityChangingConfigurations = false;

    @Override
    public void onCreate() {
        super.onCreate();
        // Registrar el callback para conocer el ciclo de vida de las actividades
        registerActivityLifecycleCallbacks(this);

        // Al iniciar la app, si hay un usuario autenticado, actualiza el campo login_time
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            String userId = firebaseUser.getUid();
            String currentTime = getCurrentTime();
            SQLiteHelper dbHelper = SQLiteHelper.getInstance(this);
            User user = dbHelper.getUser(userId);
            if (user != null) {
                user.setLoginTime(currentTime);
                // Se utiliza insertWithOnConflict con CONFLICT_REPLACE para actualizar el registro
                dbHelper.addUser(user);
                Log.d(TAG, "Login time updated at app start for user: " + userId);
            }
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        // No se requiere acción
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (++activityReferences == 1 && !isActivityChangingConfigurations) {
            // La app entra en primer plano
            FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            if (firebaseUser != null) {
                String userId = firebaseUser.getUid();
                String currentTime = getCurrentTime();
                SQLiteHelper dbHelper = SQLiteHelper.getInstance(this);
                User user = dbHelper.getUser(userId);
                if (user != null) {
                    user.setLoginTime(currentTime);
                    dbHelper.addUser(user);
                    Log.d(TAG, "App entered foreground. Updated login time for user: " + userId);
                }
            }
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        // No se requiere acción
    }

    @Override
    public void onActivityPaused(Activity activity) {
        // No se requiere acción
    }

    @Override
    public void onActivityStopped(Activity activity) {
        isActivityChangingConfigurations = activity.isChangingConfigurations();
        if (--activityReferences == 0 && !isActivityChangingConfigurations) {
            // La app pasa a segundo plano: actualiza el campo logout_time
            FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            if (firebaseUser != null) {
                String userId = firebaseUser.getUid();
                String currentTime = getCurrentTime();
                SQLiteHelper dbHelper = SQLiteHelper.getInstance(this);
                User user = dbHelper.getUser(userId);
                if (user != null) {
                    user.setLogoutTime(currentTime);
                    dbHelper.addUser(user);
                    Log.d(TAG, "App entered background. Updated logout time for user: " + userId);
                }
            }
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        // No se requiere acción
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        // No se requiere acción
    }

    /**
     * Obtiene la fecha y hora actual en formato "yyyy-MM-dd HH:mm:ss".
     *
     * @return La fecha y hora formateada.
     */
    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }
}