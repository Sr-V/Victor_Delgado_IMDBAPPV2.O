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

/**
 * Clase encargada de la sincronización de datos entre SQLite y Firestore para el usuario.
 * Proporciona métodos para sincronizar la actividad del usuario y los datos de su perfil entre ambas bases de datos.
 */
public class UsersSync {

    private static final String TAG = "UsersSync"; // Etiqueta para los logs.
    private final FirebaseFirestore firestore; // Instancia de Firestore para la sincronización.
    private final SQLiteHelper dbHelper; // Instancia de la base de datos SQLite local.
    private final String userId; // Identificador único del usuario.

    /**
     * Constructor de la clase.
     * @param context El contexto de la aplicación, necesario para la creación de la base de datos local.
     * @param userId El identificador único del usuario.
     */
    public UsersSync(Context context, String userId) {
        this.firestore = FirebaseFirestore.getInstance(); // Obtiene la instancia de Firestore.
        this.dbHelper = SQLiteHelper.getInstance(context); // Obtiene la instancia de SQLiteHelper.
        this.userId = userId; // Asigna el identificador del usuario.
    }

    /**
     * Sincroniza la información del registro de actividad (activity_log) desde la base de datos local (SQLite) a Firestore.
     * @return Task<Void> Tarea que representa el proceso de sincronización.
     */
    public Task<Void> syncActivityLog() {
        DocumentReference userDocRef = firestore.collection("users").document(userId); // Referencia al documento del usuario en Firestore.
        User user = dbHelper.getUser(userId); // Obtiene el usuario desde la base de datos local.

        if (user == null) {
            return Tasks.forResult(null); // Si el usuario no existe, termina la tarea.
        }

        final String localLoginTime = user.getLoginTime(); // Hora de inicio de sesión del usuario.
        final String localLogoutTime = user.getLogoutTime(); // Hora de cierre de sesión del usuario.

        TaskCompletionSource<Void> tcs = new TaskCompletionSource<>(); // Fuente de tarea para controlar el resultado de la tarea.

        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(userDocRef); // Obtiene el documento del usuario desde Firestore.
            List<Map<String, Object>> activityLog = snapshot.contains("activity_log")
                    ? (List<Map<String, Object>>) snapshot.get("activity_log") // Obtiene el registro de actividad si existe.
                    : new ArrayList<>(); // Si no existe, crea una nueva lista.

            if (activityLog == null) activityLog = new ArrayList<>(); // Si el registro está vacío, inicializa la lista.

            boolean addNewLogin = true;
            if (!activityLog.isEmpty()) {
                Map<String, Object> lastEntry = activityLog.get(activityLog.size() - 1); // Obtiene la última entrada del registro.
                String lastLogin = (String) lastEntry.get("login_time");
                if (lastLogin != null && lastLogin.equals(localLoginTime)) {
                    addNewLogin = false; // Si el inicio de sesión local ya existe, no lo agrega nuevamente.
                }
            }

            if (addNewLogin && localLoginTime != null) {
                Map<String, Object> newEntry = new HashMap<>();
                newEntry.put("login_time", localLoginTime);
                newEntry.put("logout_time", null); // Establece la hora de cierre como nula inicialmente.
                activityLog.add(newEntry); // Agrega el nuevo registro al log de actividad.
            }

            // Si existen registros de actividad y hay una hora de cierre de sesión local, actualiza el último registro.
            if (!activityLog.isEmpty() && localLogoutTime != null && localLoginTime != null) {
                Map<String, Object> lastEntry = activityLog.get(activityLog.size() - 1);
                if (lastEntry.get("logout_time") == null) {
                    if (localLogoutTime.compareTo(localLoginTime) > 0) {
                        lastEntry.put("logout_time", localLogoutTime); // Actualiza la hora de cierre si es válida.
                    } else {
                        Log.d(TAG, "Logout time no es posterior al login time. No se actualiza.");
                    }
                }
            }

            // Datos adicionales del usuario para sincronizar.
            Map<String, Object> extraData = new HashMap<>();
            extraData.put("email", user.getEmail());
            extraData.put("name", user.getName() != null ? user.getName() : "desconocido");
            extraData.put("user_id", userId);

            Map<String, Object> updateData = new HashMap<>();
            updateData.put("activity_log", activityLog); // Actualiza el registro de actividad.
            updateData.putAll(extraData); // Agrega los datos adicionales.

            transaction.set(userDocRef, updateData, SetOptions.merge()); // Realiza la transacción de actualización en Firestore.
            return null;
        }).addOnSuccessListener(aVoid -> {
            Log.d(TAG, "Se sincronizó el activity_log en Firestore.");
            tcs.setResult(null); // Finaliza la tarea si la sincronización fue exitosa.
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error al sincronizar activity_log.", e);
            tcs.setException(e); // Finaliza la tarea con error si ocurre un fallo.
        });

        return tcs.getTask(); // Devuelve la tarea.
    }

    /**
     * Sincroniza los datos del usuario desde Firestore a SQLite si no existen localmente.
     * Llama al callback `onComplete.run()` cuando la sincronización finaliza.
     * @param onComplete El callback que se llama cuando la sincronización termina.
     */
    public void syncFromCloudToLocal(Runnable onComplete) {
        DocumentReference userDocRef = firestore.collection("users").document(userId); // Referencia al documento del usuario en Firestore.

        userDocRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    Log.d(TAG, "Datos del usuario obtenidos de Firestore.");

                    // Obtiene los datos del usuario desde Firestore.
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

                    User localUser = dbHelper.getUser(userId); // Obtiene el usuario local desde SQLite.

                    if (localUser == null) {
                        // Si el usuario no existe localmente, lo crea en SQLite.
                        User newUser = new User(userId, name, email, lastLoginTime, lastLogoutTime, address, phone, image);
                        boolean success = dbHelper.addUser(newUser);
                        Log.d(TAG, success ? "Usuario agregado a la base local." : "Error al agregar usuario.");
                    } else {
                        // Si el usuario existe, actualiza los datos si están vacíos en la base local.
                        boolean updated = false;

                        if ((localUser.getLoginTime() == null || localUser.getLoginTime().isEmpty()) && lastLoginTime != null) {
                            localUser.setLoginTime(lastLoginTime);
                            updated = true;
                        }
                        if ((localUser.getLogoutTime() == null || localUser.getLogoutTime().isEmpty()) && lastLogoutTime != null) {
                            localUser.setLogoutTime(lastLogoutTime);
                            updated = true;
                        }
                        if ((localUser.getName() == null || localUser.getName().isEmpty())) {
                            if (!name.isEmpty()) {
                                localUser.setName(name);
                                updated = true;
                            }
                        }
                        if ((localUser.getEmail() == null || localUser.getEmail().isEmpty())) {
                            if (!email.isEmpty()) {
                                localUser.setEmail(email);
                                updated = true;
                            }
                        }
                        if ((localUser.getAddress() == null || localUser.getAddress().isEmpty())) {
                            if (!address.isEmpty()) {
                                localUser.setAddress(address);
                                updated = true;
                            }
                        }
                        if ((localUser.getPhone() == null || localUser.getPhone().isEmpty())) {
                            if (!phone.isEmpty()) {
                                localUser.setPhone(phone);
                                updated = true;
                            }
                        }
                        if ((localUser.getImage() == null || localUser.getImage().isEmpty())) {
                            if (!image.isEmpty()) {
                                localUser.setImage(image);
                                updated = true;
                            }
                        }

                        if (updated) {
                            dbHelper.addUser(localUser); // Actualiza el usuario en SQLite si se modificaron los datos.
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

            // Llama al callback después de completar la sincronización.
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /**
     * Sincroniza campos específicos del usuario desde SQLite a Firestore.
     * @return Task<Void> Tarea que representa el proceso de sincronización.
     */
    public Task<Void> syncSpecificFields() {
        DocumentReference userDocRef = firestore.collection("users").document(userId); // Referencia al documento del usuario en Firestore.
        User localUser = dbHelper.getUser(userId); // Obtiene el usuario local desde SQLite.

        if (localUser == null) {
            Log.w(TAG, "No se puede sincronizar: usuario no existe en SQLite.");
            return Tasks.forResult(null); // Si el usuario no existe localmente, termina la tarea.
        }

        TaskCompletionSource<Void> tcs = new TaskCompletionSource<>(); // Fuente de tarea para controlar el resultado.

        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            Map<String, Object> updateData = new HashMap<>(); // Datos que se van a actualizar en Firestore.

            if (!localUser.getName().isEmpty()) updateData.put("name", localUser.getName());
            if (!localUser.getEmail().isEmpty()) updateData.put("email", localUser.getEmail());
            if (!localUser.getAddress().isEmpty()) updateData.put("address", localUser.getAddress());
            if (!localUser.getImage().isEmpty()) updateData.put("image", localUser.getImage());
            if (!localUser.getPhone().isEmpty()) updateData.put("phone", localUser.getPhone());

            if (updateData.isEmpty()) return null; // Si no hay campos para actualizar, termina la tarea.

            transaction.set(userDocRef, updateData, SetOptions.merge()); // Realiza la transacción de actualización.
            return null;
        }).addOnSuccessListener(aVoid -> {
            Log.d(TAG, "Campos específicos sincronizados en Firestore.");
            tcs.setResult(null); // Finaliza la tarea si fue exitosa.
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error sincronizando campos en Firestore.", e);
            tcs.setException(e); // Finaliza la tarea con error si hubo fallo.
        });

        return tcs.getTask(); // Devuelve la tarea.
    }
}