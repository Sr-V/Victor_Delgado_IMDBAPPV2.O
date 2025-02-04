package database;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UsersSync {

    private static final String TAG = "UsersSync";
    private final FirebaseFirestore firestore;
    private final SQLiteHelper dbHelper;
    private final String userId;

    public UsersSync(Context context, String userId) {
        this.firestore = FirebaseFirestore.getInstance();
        this.dbHelper = SQLiteHelper.getInstance(context);
        this.userId = userId;
    }

    /**
     * Sincroniza la información de activity_log desde la base local a Firestore.
     */
    public Task<Void> syncActivityLog() {
        DocumentReference userDocRef = firestore.collection("users").document(userId);
        User user = dbHelper.getUser(userId);
        if (user == null) {
            return Tasks.forResult(null);
        }

        final String localLoginTime = user.getLoginTime();
        final String localLogoutTime = user.getLogoutTime();

        TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();

        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(userDocRef);
            List<Map<String, Object>> activityLog = snapshot.contains("activity_log")
                    ? (List<Map<String, Object>>) snapshot.get("activity_log")
                    : new ArrayList<>();

            if (activityLog == null) activityLog = new ArrayList<>();

            boolean addNewLogin = true;
            if (!activityLog.isEmpty()) {
                Map<String, Object> lastEntry = activityLog.get(activityLog.size() - 1);
                String lastLogin = (String) lastEntry.get("login_time");
                if (lastLogin != null && lastLogin.equals(localLoginTime)) {
                    addNewLogin = false;
                }
            }

            if (addNewLogin && localLoginTime != null) {
                Map<String, Object> newEntry = new HashMap<>();
                newEntry.put("login_time", localLoginTime);
                newEntry.put("logout_time", null);
                activityLog.add(newEntry);
            }

            if (!activityLog.isEmpty() && localLogoutTime != null && localLoginTime != null) {
                Map<String, Object> lastEntry = activityLog.get(activityLog.size() - 1);
                if (lastEntry.get("logout_time") == null) {
                    if (localLogoutTime.compareTo(localLoginTime) > 0) {
                        lastEntry.put("logout_time", localLogoutTime);
                    } else {
                        Log.d(TAG, "Logout time no es posterior al login time. No se actualiza.");
                    }
                }
            }

            Map<String, Object> extraData = new HashMap<>();
            extraData.put("email", user.getEmail());
            extraData.put("name", user.getName() != null ? user.getName() : "desconocido");
            extraData.put("user_id", userId);

            Map<String, Object> updateData = new HashMap<>();
            updateData.put("activity_log", activityLog);
            updateData.putAll(extraData);

            transaction.set(userDocRef, updateData, SetOptions.merge());
            return null;
        }).addOnSuccessListener(aVoid -> {
            Log.d(TAG, "Se sincronizó el activity_log en Firestore.");
            tcs.setResult(null);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error al sincronizar activity_log.", e);
            tcs.setException(e);
        });

        return tcs.getTask();
    }

    /**
     * Sincroniza datos del usuario desde Firestore a SQLite si no existen localmente.
     * Llama al callback `onComplete.run()` cuando la sincronización finaliza.
     */
    public void syncFromCloudToLocal(Runnable onComplete) {
        DocumentReference userDocRef = firestore.collection("users").document(userId);

        userDocRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    Log.d(TAG, "Datos del usuario obtenidos de Firestore.");

                    String name = document.contains("name") ? document.getString("name") : "";
                    String email = document.contains("email") ? document.getString("email") : "";
                    String address = document.contains("address") ? document.getString("address") : "";
                    String phone = document.contains("phone") ? document.getString("phone") : "";
                    String image = document.contains("image") ? document.getString("image") : "";

                    List<Map<String, Object>> activityLog = (List<Map<String, Object>>) document.get("activity_log");
                    String lastLoginTime = null;
                    String lastLogoutTime = null;
                    if (activityLog != null && !activityLog.isEmpty()) {
                        Map<String, Object> lastEntry = activityLog.get(activityLog.size() - 1);
                        lastLoginTime = (String) lastEntry.get("login_time");
                        lastLogoutTime = (String) lastEntry.get("logout_time");
                    }

                    User localUser = dbHelper.getUser(userId);

                    if (localUser == null) {
                        // 🔹 Crear nuevo usuario en SQLite si no existe
                        User newUser = new User(userId, name, email, lastLoginTime, lastLogoutTime, address, phone, image);
                        boolean success = dbHelper.addUser(newUser);
                        Log.d(TAG, success ? "Usuario agregado a la base local." : "Error al agregar usuario.");
                    } else {
                        // 🔹 Actualizar datos solo si están vacíos en local
                        boolean updated = false;

                        if ((localUser.getLoginTime() == null || localUser.getLoginTime().isEmpty()) && lastLoginTime != null) {
                            localUser.setLoginTime(lastLoginTime);
                            updated = true;
                        }
                        if ((localUser.getLogoutTime() == null || localUser.getLogoutTime().isEmpty()) && lastLogoutTime != null) {
                            localUser.setLogoutTime(lastLogoutTime);
                            updated = true;
                        }
                        if ((localUser.getName() == null || localUser.getName().isEmpty()) && !name.isEmpty()) {
                            localUser.setName(name);
                            updated = true;
                        }
                        if ((localUser.getEmail() == null || localUser.getEmail().isEmpty()) && !email.isEmpty()) {
                            localUser.setEmail(email);
                            updated = true;
                        }
                        if ((localUser.getAddress() == null || localUser.getAddress().isEmpty()) && !address.isEmpty()) {
                            localUser.setAddress(address);
                            updated = true;
                        }
                        if ((localUser.getPhone() == null || localUser.getPhone().isEmpty()) && !phone.isEmpty()) {
                            localUser.setPhone(phone);
                            updated = true;
                        }
                        if ((localUser.getImage() == null || localUser.getImage().isEmpty()) && !image.isEmpty()) {
                            localUser.setImage(image);
                            updated = true;
                        }

                        if (updated) {
                            dbHelper.addUser(localUser);
                            Log.d(TAG, "Usuario actualizado en la base local.");
                        } else {
                            Log.d(TAG, "No se necesitaba actualizar la base local.");
                        }
                    }
                } else {
                    Log.d(TAG, "No hay datos en Firestore para este usuario.");
                }
            } else {
                Log.e(TAG, "Error al obtener datos desde Firestore.", task.getException());
            }

            // 🔹 Llamar al callback después de que Firestore termine
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /**
     * Sincroniza campos específicos de SQLite a Firestore.
     */
    public Task<Void> syncSpecificFields() {
        DocumentReference userDocRef = firestore.collection("users").document(userId);
        User localUser = dbHelper.getUser(userId);

        if (localUser == null) {
            Log.w(TAG, "No se puede sincronizar: usuario no existe en SQLite.");
            return Tasks.forResult(null);
        }

        TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();

        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(userDocRef);
            Map<String, Object> updateData = new HashMap<>();

            if (!localUser.getName().isEmpty()) updateData.put("name", localUser.getName());
            if (!localUser.getEmail().isEmpty()) updateData.put("email", localUser.getEmail());
            if (!localUser.getAddress().isEmpty()) updateData.put("address", localUser.getAddress());
            if (!localUser.getImage().isEmpty()) updateData.put("image", localUser.getImage());
            if (!localUser.getPhone().isEmpty()) updateData.put("phone", localUser.getPhone());

            if (updateData.isEmpty()) return null;

            transaction.set(userDocRef, updateData, SetOptions.merge());
            return null;
        }).addOnSuccessListener(aVoid -> {
            Log.d(TAG, "Campos específicos sincronizados en Firestore.");
            tcs.setResult(null);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error sincronizando campos en Firestore.", e);
            tcs.setException(e);
        });

        return tcs.getTask();
    }
}