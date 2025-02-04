package utils;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import database.SQLiteHelper;
import database.User;
import database.UsersSync;

public class AppLifecycleManager extends Application implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "AppLifecycleManager";
    private int activityReferences = 0;
    private boolean isActivityChangingConfigurations = false;
    private boolean isLoginTimeUpdated = false; // 🔹 Evita doble actualización del login_time
    private String lastUserId = null; // 🔹 Último usuario autenticado

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);

        // 🔹 Cargar el último usuario registrado en SharedPreferences
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        lastUserId = prefs.getString("LAST_USER_ID", null);

        // 🔹 Si la app fue cerrada a la fuerza, registrar logout del último usuario
        if (lastUserId != null) {
            registerForcedLogout(lastUserId);
        }
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {}

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        Log.d(TAG, activity.getLocalClassName() + " - onActivityStarted");

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            String userId = firebaseUser.getUid();

            // 🔹 Si el usuario ha cambiado o no se ha actualizado login_time, registrarlo
            if (!userId.equals(lastUserId) || !isLoginTimeUpdated) {
                updateLoginTime(userId);
                lastUserId = userId;
                isLoginTimeUpdated = true; // Marcar como actualizado

                // 🔹 Guardar el nuevo usuario en SharedPreferences
                SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                prefs.edit().putString("LAST_USER_ID", userId).apply();
            }
        }

        if (++activityReferences == 1 && !isActivityChangingConfigurations) {
            Log.d(TAG, "App en primer plano.");
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {}

    @Override
    public void onActivityPaused(@NonNull Activity activity) {}

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        Log.d(TAG, activity.getLocalClassName() + " - onActivityStopped");
        isActivityChangingConfigurations = activity.isChangingConfigurations();

        if (--activityReferences == 0 && !isActivityChangingConfigurations) {
            FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            if (firebaseUser != null) {
                updateLogoutTime(firebaseUser.getUid());
            }
        }
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {}

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {}

    /**
     * Registra el login_time del usuario.
     */
    private void updateLoginTime(String userId) {
        String currentTime = getCurrentTime();
        SQLiteHelper dbHelper = SQLiteHelper.getInstance(this);
        User user = dbHelper.getUser(userId);

        if (user == null) {
            user = new User(userId, "", "", currentTime, "", "", "", "");
            dbHelper.addUser(user);
        } else {
            user.setLoginTime(currentTime);
            dbHelper.addUser(user);
        }

        Log.d(TAG, "Login registrado para usuario: " + userId);
        new UsersSync(this, userId).syncActivityLog();
    }

    /**
     * Registra el logout_time del usuario y permite volver a registrar logins.
     */
    private void updateLogoutTime(String userId) {
        String currentTime = getCurrentTime();
        SQLiteHelper dbHelper = SQLiteHelper.getInstance(this);
        User user = dbHelper.getUser(userId);

        if (user != null) {
            user.setLogoutTime(currentTime);
            dbHelper.addUser(user);
            Log.d(TAG, "Logout registrado para usuario: " + userId);
            new UsersSync(this, userId).syncActivityLog();
        }

        // 🔹 Habilitar el booleano para permitir registrar logins nuevamente
        isLoginTimeUpdated = false;
    }

    /**
     * Si la app fue cerrada a la fuerza, registrar el logout del último usuario.
     */
    private void registerForcedLogout(String userId) {
        Log.d(TAG, "Detectado cierre forzado. Registrando logout para " + userId);
        updateLogoutTime(userId);
    }

    /**
     * Restablece el estado de login para permitir nuevos logins tras un logout.
     */
    public void resetLoginState() {
        isLoginTimeUpdated = false;
        lastUserId = null;

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit().remove("LAST_USER_ID").apply();

        Log.d(TAG, "Estado de login restablecido. Se puede iniciar sesión nuevamente.");
    }

    /**
     * Obtiene la fecha y hora actual en formato "yyyy-MM-dd HH:mm:ss".
     */
    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }
}