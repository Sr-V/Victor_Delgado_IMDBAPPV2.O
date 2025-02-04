package edu.pmdm.delgado_victorimdbapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.common.api.Status;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.Arrays;
import java.util.List;

/**
 * Actividad para seleccionar una dirección utilizando Google Maps y la API de Autocomplete.
 */
public class SelectAddressActivity extends AppCompatActivity implements OnMapReadyCallback {

    private EditText edtAddress; // Campo de texto para mostrar la dirección seleccionada
    private MapView mapView; // Vista del mapa
    private GoogleMap googleMap; // Instancia de GoogleMap para trabajar con el mapa

    // Lanza la actividad de autocompletar para seleccionar una dirección
    private final ActivityResultLauncher<Intent> autocompleteLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        // Si la actividad de autocompletar fue exitosa
                        if (result.getResultCode() == RESULT_OK) {
                            Intent data = result.getData();
                            // Obtener el lugar seleccionado
                            assert data != null;
                            Place place = Autocomplete.getPlaceFromIntent(data);
                            edtAddress.setText(place.getAddress());

                            // Actualizar el mapa con la ubicación seleccionada
                            LatLng selectedLocation = place.getLatLng();
                            if (selectedLocation != null) {
                                googleMap.clear();  // Limpiar cualquier marcador anterior
                                googleMap.addMarker(new MarkerOptions().position(selectedLocation).title("Ubicación seleccionada"));
                                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(selectedLocation, 15)); // Mover la cámara al lugar seleccionado
                            }
                        } else if (result.getResultCode() == AutocompleteActivity.RESULT_ERROR) {
                            // Si ocurre un error durante la búsqueda
                            Intent data = result.getData();
                            assert data != null;
                            Status status = Autocomplete.getStatusFromIntent(data);
                            Log.e("Error de Autocompletar", status.getStatusMessage() != null ? status.getStatusMessage() : "Error desconocido");
                        }
                    });

    /**
     * Se ejecuta al crear la actividad.
     * Aquí se inicializan los elementos de la interfaz y las configuraciones necesarias.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_address);

        // Inicializar la API de Places
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
        }

        // Obtener las referencias de los elementos de la interfaz
        edtAddress = findViewById(R.id.edtAddress);
        Button btnSearchLocation = findViewById(R.id.btnSearchLocation);
        Button btnConfirmLocation = findViewById(R.id.btnConfirmLocation);
        mapView = findViewById(R.id.mapView);

        // Inicializar el mapa
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        // Establecer el escuchador de clics para el botón de búsqueda
        btnSearchLocation.setOnClickListener(v -> {
            // Iniciar la actividad de autocompletar para seleccionar una dirección
            List<Place.Field> fields = Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG);
            Intent intent = new Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
                    .build(SelectAddressActivity.this);
            autocompleteLauncher.launch(intent);
        });

        // Establecer el escuchador de clics para el botón de confirmar ubicación
        btnConfirmLocation.setOnClickListener(v -> {
            // Obtener la dirección seleccionada y devolverla a la actividad anterior
            String selectedAddress = edtAddress.getText().toString();
            Intent resultIntent = new Intent();
            resultIntent.putExtra("SELECTED_ADDRESS", selectedAddress);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }

    /**
     * Método llamado cuando el mapa está listo para usarse.
     * Se establece una ubicación predeterminada si no se ha seleccionado ninguna dirección.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;

        // Ubicación predeterminada si no se ha seleccionado una dirección
        LatLng defaultLocation = new LatLng(-33.8688, 151.2093);  // Ejemplo: Sídney, Australia
        googleMap.addMarker(new MarkerOptions().position(defaultLocation).title("Ubicación predeterminada"));
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15));
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume(); // Asegurar que el mapa esté activo cuando se reanude la actividad
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause(); // Asegurar que el mapa se pause cuando se pause la actividad
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy(); // Limpiar recursos cuando la actividad se destruya
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory(); // Liberar recursos si hay poca memoria
    }
}